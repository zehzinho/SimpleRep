/**
*  Fortika - Robust Group Communication
*  Copyright (C) 2002-2006  Sergio Mena de la Cruz (EPFL) (sergio.mena@epfl.ch)
*
*  This program is free software; you can redistribute it and/or modify
*  it under the terms of the GNU General Public License as published by
*  the Free Software Foundation; either version 2 of the License, or
*  (at your option) any later version.
*
*  This program is distributed in the hope that it will be useful,
*  but WITHOUT ANY WARRANTY; without even the implied warranty of
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*  GNU General Public License for more details.
*
*  You should have received a copy of the GNU General Public License
*  along with this program; if not, write to the Free Software
*  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package groupcomm.common.consensus;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import uka.transport.Transportable;
import framework.Compressable;
import framework.CompressedSet;
import framework.Constants;
import framework.GroupCommEventArgs;
import framework.GroupCommException;
import framework.GroupCommMessage;
import framework.PID;
import framework.libraries.DefaultSerialization;
import framework.libraries.FlowControl;
import framework.libraries.Trigger;
import framework.libraries.serialization.TBoolean;
import framework.libraries.serialization.TCollection;
import framework.libraries.serialization.THashMap;
import framework.libraries.serialization.THashSet;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TList;
import framework.libraries.serialization.TMap;
import framework.libraries.serialization.TSet;

/**
 * <b> Classe qui impl�mente les handlers de la couche ConsensusMR. </b> <br>
 * <hr>
 * <b> Ev�nements: </b>
 * <dl>
 *     <dt> <i>Pt2PtDeliver</i> </dt> <dd> R�ception d'un message de la couche Reliable. </dd>
 *     <dt> <i>Suspect</i>      </dt> <dd> Indique les processus qui sont suspect�s et
 *                                         ceux qui ne le sont pas.                      </dd>
 *     <dt> <i>Run</i>          </dt> <dd> Demande d'ex�cution d'un consensus.           </dd>
 * </dl>
 */
public class ConsensusMR {

	private PID myself;

	/** La classe qui impl�mente RBCast.
	 */
	//private ReliableBroadcast diffusion = null;

	/** This process (= PID.LOCALHOST)
	 */
	//private PID process;

	/** Le trigger pour d�clencher les �v�nements.
	 */
	private Trigger trigger;

	private FlowControl flow_control;
	private int fc_key = -1;
    private int fc_threshold;
    private int fc_used = 0;

	public static final int MAX_CONSENSUS = 20;

	/*
	* A given instance of consensus can be in these states:
	* 1) Not started: exec ?= null; dec == null; finished == null;
	* 2) Started: exec != null; dec == null; finished == null;
	* 3) Decided: exec == null; dec != null; finished ==null;
	* 4) Finished: exec == null; dec == null; finished != null;
	*/
	private TMap executions = new THashMap();
	private TMap decided = new THashMap();
	//private TSet finished = new THashSet();
	private CompressedSet finished = new CompressedSet();
	private TSet suspected = new THashSet();

	private TMap monitored = new THashMap();

	private static final Logger logger =
		Logger.getLogger(ConsensusMR.class.getName());

	/**
	* Constructeur. <br>
	* Initialise les diff�rents champs.
	*/
	public ConsensusMR(Trigger trigger, FlowControl fc, PID myself) {
		logger.entering("ConsensusMRHandlers", "<constr>");
		this.trigger = trigger;
		this.flow_control = fc;
		//flow_control.setThreshold(fc_key, MAX_CONSENSUS);
        fc_threshold = MAX_CONSENSUS;
		this.myself = myself;
		//this.diffusion = new ReliableBroadcast(this, trigger, myself);
		logger.exiting("ConsensusMRHandlers", "<constr>");
	}

	/**
	* Handler pour l'�v�nement <i>Run</i>.
	* Lance une nouvelle �x�cution de consensus.
	*
	* @param e <dl>
	*              <dt> arg1 : Set[PID] </dt> <dd> Les membres du groupes qui participent � ConsensusMR. </dd>
	*              <dt> arg2 : Object   </dt> <dd> La valeur initiale propos�e par le processus. </dd>
	*          </dl>
	*/
	public void handleRun(GroupCommEventArgs e)
		throws GroupCommException, IOException, ClassNotFoundException {

		logger.entering("ConsensusMRHandlers", "handleRun");
        if(fc_key == -1) fc_key = flow_control.getFreshKey();

		TList group = (TList) e.get(0);
		Transportable o = (Transportable) e.get(1);
		Transportable k_parObj = (Transportable) e.get(2);
		logger.log(
			Level.FINE,
			"Running consensus#{2}\n\tProcessGroup: {0}\tProposed Value: {1}",
			new Object[] { group, o, k_parObj });

		if (group.size() == 0)
			throw new GroupCommException("No participants provided");

		//Look for duplicate processes in the group
		// At the same time, check if the localhost is in the group 
		boolean found = false;
		for (int i = 0; i < group.size(); i++) {
			found = found || myself.equals(group.get(i));
			for (int j = i + 1; j < group.size(); j++)
				if (group.get(i).equals(group.get(j)))
					throw new GroupCommException(
						"Process"
							+ group.get(i)
							+ " appears more than once in the group.");
		}
		if (!found) {
			throw new GroupCommException(
				"ConsensusMR: The localhost "
					+ myself
					+ " is not in the group passed as parameter: "
					+ group);
		}

		// Flow control
		//flow_control.alloc(fc_key, 1);
        fc_used++; if(fc_used >= fc_threshold) flow_control.block(fc_key);

		if (group.size() == 1) {
			// ConsensusMR with one only process
			// We decide the value proposed
			triggerDecision(deepClone(o), k_parObj);
			logger.exiting("ConsensusMRHandlers", "handleRun");
			return;
		}

		Transportable decision = (Transportable) decided.get(k_parObj);
		// Has its decision already arrived??
		if (decision != null) {
			decided.remove(k_parObj);
			Transportable clone = (Transportable) deepClone(decision);
			reSendDecision(
				decision,
				k_parObj,
				group,
				null);
			executions.remove(k_parObj);
			triggerDecision(clone, k_parObj);
			removeProcesses(k_parObj);
			logger.exiting("ConsensusMRHandlers", "handleRun");
			return;
		}

		if (finished.contains((Compressable) k_parObj))
			throw new GroupCommException("Impossible to finish before starting consensus!");

		addProcesses(k_parObj, group);
		getExecution(k_parObj).processStart(o, group);
		logger.exiting("ConsensusMRHandlers", "handleRun");
	}

	/**
	 * Handler pour l'�v�nement <i>Pt2PtDeliver</i>.
	 *
	 * @param e <dl>
	 *              <dt> arg1 : GroupCommMessage </dt> <dd> Le message destin� � consensus. </dd>
	 *              <dt> arg2 : PID           </dt> <dd> La source du message.           </dd>
	 *          </dl>
	 */
	public void handlePt2PtDeliver(GroupCommEventArgs e)
		throws GroupCommException, IOException, ClassNotFoundException {
		logger.entering("ConsensusMRHandlers", "handlePt2PtDeliver");
		GroupCommMessage m = (GroupCommMessage) e.get(0);
		// m = <<k::type::payload>>
		Transportable kObj = (Transportable) m.tunpack();
		// m = <<type::payload>> 
		if (finished.contains((Compressable) kObj) || decided.containsKey(kObj)) {
			logger.log(
				Level.FINE,
				"Late message to ConsensusMR instance {0}. Discarding it: {1}",
				new Object[] { kObj, m });
			logger.exiting("ConsensusMRHandlers", "handlePt2PtDeliver");
			return;
		}
		PID source = (PID) e.get(1);
		int type = ((TInteger) m.tunpack()).intValue();
		int r;
		// m = <<payload>>
		switch (type) {
			case ConsensusMRExecution.CONS_NACK :
				// m = <<r::propose>>
			        r = ((TInteger) m.tunpack()).intValue();
				getExecution(kObj).processSuspect(r, m);
				break;
			case ConsensusMRExecution.CONS_PROPOSE :
				// m = <<r::propose>>
				r = ((TInteger) m.tunpack()).intValue();
				getExecution(kObj).processPropose(r, m);
				break;
			case ConsensusMRExecution.CONS_RBCAST :
				// m = <<decision::group>>
				Transportable decision = m.tunpack();
				// m = <<group>>

				if (getExecution(kObj).hasStarted()) {
					Transportable clone = deepClone(decision);
					if (!myself.equals(source)) {
						TList group = (TList) m.tunpack();
						// m = <<>>
						reSendDecision(decision, kObj, group, source);
					}
					executions.remove(kObj);
					triggerDecision(clone, kObj);
					removeProcesses(kObj);
				} else {
					decided.put(kObj, decision);
				}
				break;
			default :
				throw new GroupCommException(
					"ConsensusMR : handlePt2Ptdeliver : "
						+ "Unknown message type: "
						+ type);
		}
		logger.exiting("ConsensusMRHandlers", "handlePt2PtDeliver");
	}

	/**
	 * Handler pour l'�v�nement <i>Suspect</i>.
	 *
	 * @param e <dl>
	 *              <dt> arg1 : Set[PID] </dt> <dd> Les nouveaux processus suspect�s. </dd>
	 *              <dt> arg2 : Set[PID] </dt> <dd> Les processus innocent�s. </dd>
	 *          </dl>
	 */
	public void handleSuspect(GroupCommEventArgs e) throws GroupCommException {
		logger.entering("ConsensusMRHandlers", "handleSuspect");
		suspected = (TSet) e.get(0);

		Iterator it = executions.values().iterator();
		// "it" contains an UNORDERED sequence of executions 
		while (it.hasNext()) {
			ConsensusMRExecution exe = (ConsensusMRExecution) it.next();
			exe.processSuspicion(suspected);
		}
		logger.exiting("ConsensusMRHandlers", "handleSuspect");
	}

	/**
	 * Envoit la solution d'un consensus � la couche sup�rieure.
	 *
	 * @param content La solution du ConsensusMR.
	 */
	private void triggerDecision(Transportable o, Transportable kObj) {
		logger.entering("ConsensusMRHandlers", "triggerDecision");
		//flow_control.free(fc_key, 1);
        fc_used--; if(fc_used < fc_threshold) flow_control.release(fc_key);
        
		finished.add((Compressable) kObj);

		GroupCommEventArgs e = new GroupCommEventArgs();
		e.addLast(o);
		e.addLast(kObj);
		logger.log(
			Level.FINE,
			"Triggering decision#{1}: {0}",
			new Object[] { o, kObj });
		trigger.trigger(Constants.DECIDE, e);
		logger.exiting("ConsensusMRHandlers", "triggerDecision");
	}

    private Transportable deepClone(Transportable o)
        throws IOException, ClassNotFoundException {
        //TODO: There have to be better ways to do deep-clone!!!
        return DefaultSerialization.unmarshall(DefaultSerialization.marshall(o));
    }

    private void reSendDecision(
        Transportable decision,
        Transportable kObj,
        TList group,
        PID dontsend) {
        GroupCommMessage decisionMessage = new GroupCommMessage();
        //m = <<>>
        decisionMessage.tpack(group);
        //m = <<group>>
        decisionMessage.tpack(decision);
        //m = <<decision::group>>
        decisionMessage.tpack(new TInteger(ConsensusMRExecution.CONS_RBCAST));
        //m = <<CONS_BROADCAST::decision::group>>
        decisionMessage.tpack(kObj);
        //m = <<k::CONS_BROADCAST::decision::group>>
        for (int i = 0; i < group.size(); i++){
        	PID pi = (PID) group.get(i);
            if (!pi.equals(myself) && (dontsend == null || !pi.equals(dontsend)) ) {
                GroupCommEventArgs pt2ptSend = new GroupCommEventArgs();
                pt2ptSend.addLast(decisionMessage.cloneGroupCommMessage());
                pt2ptSend.addLast(pi);
                pt2ptSend.addLast(new TBoolean(false));
                // not promisc
                logger.log(
                    Level.FINE,
                    "Sending Broadcast message {0} to {1}",
                    new Object[] { decisionMessage, pi });
                trigger.trigger(Constants.PT2PTSEND, pt2ptSend);
            }
        }
    }

    private void addProcesses(Transportable kObj, TList group) {
	logger.entering("ConsensusMRHandlers", "addProcesses");
	THashSet start = new THashSet();

	//For each p in group
	Iterator i = group.iterator();
	while (i.hasNext()) {
	    PID p = (PID) i.next();
	    TSet ins = (TSet) monitored.get(p);
	    // ins is the set of instances (long) that p takes part of
	    if (ins == null) {
		start.add(p);
		ins = new THashSet();
		monitored.put(p, ins);
	    }
	    ins.add(kObj);
	}

	// Start = new processes
	if (!start.isEmpty()) {
	    GroupCommEventArgs e1 = new GroupCommEventArgs();
	    e1.addLast(start); //Start
	    e1.addLast(new THashSet()); //Stop
	    trigger.trigger(Constants.STARTSTOPMONITOR, e1);

	    GroupCommEventArgs e2 = new GroupCommEventArgs();
	    e2.addLast(start); //Join
	    e2.addLast(new THashSet()); //Remove
	    trigger.trigger(Constants.JOINREMOVELIST, e2);
	}
	logger.exiting("ConsensusMRHandlers", "addProcesses");
    }

    private void removeProcesses(Transportable k2) {
	logger.entering("ConsensusMRHandlers", "removeProcesses");
	THashSet stop = new THashSet();

	//For each p in group
	TCollection keys = monitored.keySet();
	Iterator i = keys.iterator();
	while (i.hasNext()) {
	    PID p = (PID) i.next();
	    TSet ins = (TSet) monitored.get(p);
	    // ins is the set of instances (long) that p takes part of
	    ins.remove(k2);
	    if (ins.isEmpty()) {
		stop.add(p);
	    }
	}

	// Stop = processes that don't take part in any of
	//  the remaining consensus
	i = stop.iterator();
	while (i.hasNext()) {
	    PID p = (PID) i.next();
	    monitored.remove(p);
            //Side effect: we're modifying all remaining instances, but
            // p doesn't take part in any of them
            suspected.remove(p); 
	}

	if (!stop.isEmpty()) {
	    GroupCommEventArgs e1 = new GroupCommEventArgs();
	    e1.addLast(new THashSet()); //Start
	    e1.addLast( (THashSet)stop.clone() ); //Stop
	    trigger.trigger(Constants.STARTSTOPMONITOR, e1);

	    GroupCommEventArgs e2 = new GroupCommEventArgs();
	    e2.addLast(new THashSet()); //Join
	    e2.addLast(stop); //Remove
	    trigger.trigger(Constants.JOINREMOVELIST, e2);

	}
	logger.exiting("ConsensusMRHandlers", "removeProcesses");
    }

    private ConsensusMRExecution getExecution(Transportable kObj) {
	logger.entering("ConsensusMRHandlers", "getExecution");
	ConsensusMRExecution exec =
	    (ConsensusMRExecution) executions.get(kObj);
	if (exec == null) {
	    // need to clone suspected??
	    exec = new ConsensusMRExecution(myself, kObj, suspected, trigger);
	    executions.put(kObj, exec);
	}
	logger.exiting("ConsensusMRHandlers", "getExecution");
	return exec;
    }

    /**
     * Used for debugging. </br>
     * Undocumented.
     */
    public void dump(OutputStream out) {
	PrintStream err = new PrintStream(out);
	err.println("===== ConsensusMR: dump =====");
	err.println("All executions: " + executions);
	err.println("Decisions arrived: " + decided);
	err.println("Finished executions: " + finished);
	err.println("Processes suspected: " + suspected);
	err.println("Processes monitored: " + monitored);
	err.println("===================================");
    }
}
