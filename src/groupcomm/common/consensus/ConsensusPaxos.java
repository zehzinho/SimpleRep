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
import framework.libraries.serialization.TArrayList;
import framework.libraries.serialization.TBoolean;
import framework.libraries.serialization.TCollection;
import framework.libraries.serialization.THashMap;
import framework.libraries.serialization.THashSet;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TList;
import framework.libraries.serialization.TMap;
import framework.libraries.serialization.TSet;

/**
 * <b> Class implementing the common code for Consensus of Paxos building block. </b> <br>
 * This implementation is able to start several consensus instances at the same time.
 * <hr>
 * <b> Handled events: </b>
 * <dl>
 *     <dt> <i>Propose</i> </dt> <dd> Start a new consensus execution. </dd>
 *     <dt> <i>Pt2PtDeliver</i> </dt> <dd> Receive a new point-to-point message from the 
 * underlying layer. </dd>
 *     <dt> <i>NewLeader</i> </dt> <dd> Reports about change of Leader </dd>
 * </dl>
 */
public class ConsensusPaxos {

    private PID myself;

    /** Le trigger pour d�clencher les �v�nements.
     */
    private Trigger trigger;
    private int optValue = ConsensusPaxosExecution.P1;

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

    private TMap monitored = new THashMap();
    private TMap groupMonitored = new THashMap();

    private static final Logger logger =
	Logger.getLogger(ConsensusPaxos.class.getName());

    /**
     * Constructeur. <br>
     * Initialise les diff�rents champs.
     */
    public ConsensusPaxos(Trigger trigger, FlowControl fc, PID myself) {
	logger.entering("ConsensusPaxosHandlers", "<constr>");
	this.trigger = trigger;
	this.flow_control = fc;
	//flow_control.setThreshold(fc_key, MAX_CONSENSUS);
    fc_threshold = MAX_CONSENSUS;
	this.myself = myself;
	//this.diffusion = new ReliableBroadcast(this, trigger, myself);
	logger.exiting("ConsensusPaxosHandlers", "<constr>");
    }

    /**
     * Handler pour l'�v�nement <i>Run</i>.
     * Lance une nouvelle �x�cution de consensus.
     *
     * @param e <dl>
     *              <dt> arg1 : Set[PID] </dt> <dd> Les membres du groupes qui participent � ConsensusPaxos. </dd>
     *              <dt> arg2 : Object   </dt> <dd> La valeur initiale propos�e par le processus. </dd>
     *          </dl>
     */
    public void handleRun(GroupCommEventArgs e)
	throws GroupCommException, IOException, ClassNotFoundException {

	logger.entering("ConsensusPaxosHandlers", "handleRun");
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
					 "ConsensusPaxos: The localhost "
					 + myself
					 + " is not in the group passed as parameter: "
					 + group);
	}

	// Flow control
	//flow_control.alloc(fc_key, 1);
    fc_used++; if(fc_used >= fc_threshold) flow_control.block(fc_key);

	if (group.size() == 1) {
	    // ConsensusPaxos with one only process
	    // We decide the value proposed
	    triggerDecision(deepClone(o), k_parObj);
	    logger.exiting("ConsensusPaxosHandlers", "handleRun");
	    return;
	}

	Transportable decision = (Transportable) decided.get(k_parObj);
	// Has its decision already arrived??
	if (decision != null) {
	    decided.remove(k_parObj);
	    Transportable clone = deepClone(decision);
	    reSendDecision(
			   decision,
			   k_parObj,
			   group,
			   null);
	    executions.remove(k_parObj);
	    triggerDecision(clone, k_parObj);
	    removeProcesses(k_parObj);
	    logger.exiting("ConsensusPaxosHandlers", "handleRun");
	    return;
	}

	if (finished.contains((Compressable) k_parObj))
	    throw new GroupCommException("Impossible to finish before starting consensus!");

	addProcesses(k_parObj, group);
	getExecution(k_parObj).processStart(o, group);
	logger.exiting("ConsensusPaxosHandlers", "handleRun");
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
	logger.entering("ConsensusPaxosHandlers", "handlePt2PtDeliver");
	GroupCommMessage m = (GroupCommMessage) e.get(0);
	// m = <<k::type::payload>>
	Transportable kObj = (Transportable) m.tunpack();
	// m = <<type::payload>> 
	if (finished.contains((Compressable) kObj) || decided.containsKey(kObj)) {
	    logger.log(
		       Level.FINE,
		       "Late message to ConsensusPaxos instance {0}. Discarding it: {1}",
		       new Object[] { kObj, m });
	    logger.exiting("ConsensusPaxosHandlers", "handlePt2PtDeliver");
	    return;
	}
	PID source = (PID) e.get(1);
	int type = ((TInteger) m.tunpack()).intValue();
	int r;
	Transportable estimate;
	// m = <<payload>>
	switch (type) {
	case ConsensusPaxosExecution.CONS_READ:
	    // m = <<r::propose>>
	    r = ((TInteger) m.tunpack()).intValue();
	    getExecution(kObj).processRead(r, source);
	    break;
	case ConsensusPaxosExecution.CONS_ACKREAD:
	    // m = <<r::propose>>
	    r = ((TInteger) m.tunpack()).intValue();
	    int write =((TInteger) m.tunpack()).intValue();
	    estimate = m.tunpack();
	    getExecution(kObj).processAckRead(r, write, estimate);
	    break;
	case ConsensusPaxosExecution.CONS_NACKREAD:
	    // m = <<r::propose>>
	    r = ((TInteger) m.tunpack()).intValue();
	    getExecution(kObj).processNack(r);
	    break;
	case ConsensusPaxosExecution.CONS_WRITE:
	    // m = <<r::propose>>
	    r = ((TInteger) m.tunpack()).intValue();
	    estimate = m.tunpack();
	    getExecution(kObj).processWrite(r, source, estimate);
	    break;
	case ConsensusPaxosExecution.CONS_ACKWRITE:
	    // m = <<r::propose>>
	    r = ((TInteger) m.tunpack()).intValue();
	    getExecution(kObj).processAckWrite(r);
	    break;
	case ConsensusPaxosExecution.CONS_NACKWRITE:
	    // m = <<r::propose>>
	    r = ((TInteger) m.tunpack()).intValue();
	    getExecution(kObj).processNack(r);
	    break;
	case ConsensusPaxosExecution.CONS_DECISION:
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
					 "ConsensusPaxos : handlePt2Ptdeliver : "
					 + "Unknown message type: "
					 + type);
	}
	logger.exiting("ConsensusPaxosHandlers", "handlePt2PtDeliver");
    }

    /**
     * Handler pour l'�v�nement <i>NewLeader</i>.
     *
     * @param e <dl>
     *              <dt> arg1 : PID </dt> <dd> Le nouveau leader. </dd>
     *              <dt> arg2 : Set[PID] </dt> <dd> le groupe duquel il est leader. </dd>
     *          </dl>
     */
    public void handleNewLeader(GroupCommEventArgs e) throws GroupCommException {
	logger.entering("ConsensusPaxosHandlers", "handleSuspect");
	PID leader = (PID) e.get(0);
	TList group = (TList) e.get(1);
	TSet ins = (TSet) groupMonitored.get(group);

	if (ins != null) {
		Iterator i = ins.iterator();
		while (i.hasNext()) {
			Transportable kObj =  (Transportable) i.next();
			ConsensusPaxosExecution exe = getExecution(kObj);

			exe.processNewLeader(leader);
		}
	}

	logger.exiting("ConsensusPaxosHandlers", "handleSuspect");
    }

    /**
     * Envoit la solution d'un consensus � la couche sup�rieure.
     *
     * @param content La solution du ConsensusPaxos.
     */
    private void triggerDecision(Transportable o, Transportable kObj) {
	logger.entering("ConsensusPaxosHandlers", "triggerDecision");
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
	logger.exiting("ConsensusPaxosHandlers", "triggerDecision");
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
        decisionMessage.tpack(new TInteger(ConsensusPaxosExecution.CONS_DECISION));
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
	logger.entering("ConsensusPaxosHandlers", "addProcesses");
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

	// add in groupMonitored
	TSet Sk;
	if (groupMonitored.containsKey(group))
	    Sk = (TSet) groupMonitored.get(group);
	else
	    Sk = new THashSet();
	Sk.add(kObj);
	groupMonitored.put(group, Sk);

	GroupCommEventArgs e1 = new GroupCommEventArgs();
	e1.addLast(group); //Start
	e1.addLast(new TArrayList()); //Stop
	trigger.trigger(Constants.STARTSTOPMONITOR, e1);

	// Start = new processes
	if (!start.isEmpty()) {
	    GroupCommEventArgs e2 = new GroupCommEventArgs();
	    e2.addLast(start); //Join
	    e2.addLast(new THashSet()); //Remove
	    trigger.trigger(Constants.JOINREMOVELIST, e2);
	}
	logger.exiting("ConsensusPaxosHandlers", "addProcesses");
    }

    private void removeProcesses(Transportable k2) {
	logger.entering("ConsensusPaxosHandlers", "removeProcesses");
	THashSet stop = new THashSet();
	TList stopMonitor = new TArrayList();

	//For each p in group
	TCollection keys = monitored.keySet();
	Iterator i = keys.iterator();
	while (i.hasNext()) {
	    PID p = (PID) i.next();
	    TSet ins = (TSet) monitored.get(p);

	    ins.remove(k2);
	    if (ins.isEmpty()) {
		stop.add(p);
	    }
	}

	// remove from group monitored
	TCollection keysGroup = groupMonitored.keySet();
	Iterator it = keysGroup.iterator();
	while(it.hasNext()){
	    TList l = (TList) it.next();
	    TSet ins = (TSet) groupMonitored.get(l);

	    if (ins.remove(k2))
		stopMonitor = l;
	}

	// Stop = processes that don't take part in any of
	//  the remaining consensus
	i = stop.iterator();
	while (i.hasNext()) {
	    PID p = (PID) i.next();
	    monitored.remove(p);
	}

	GroupCommEventArgs e1 = new GroupCommEventArgs();
	e1.addLast(new TArrayList()); //Start
	e1.addLast(stopMonitor); //Stop
	trigger.trigger(Constants.STARTSTOPMONITOR, e1);
	    
	if (!stop.isEmpty()) {
	    GroupCommEventArgs e2 = new GroupCommEventArgs();
	    e2.addLast(new THashSet()); //Join
	    e2.addLast(stop); //Remove
	    trigger.trigger(Constants.JOINREMOVELIST, e2);

	}
	logger.exiting("ConsensusPaxosHandlers", "removeProcesses");
    }

    private ConsensusPaxosExecution getExecution(Transportable k) {
	logger.entering("ConsensusPaxosHandlers", "getExecution");
	ConsensusPaxosExecution exec =
	    (ConsensusPaxosExecution) executions.get(k);
	if (exec == null) {
	    // need to clone suspected??
	    exec = new ConsensusPaxosExecution(myself, k, trigger, optValue);
	    executions.put(k, exec);
	}
	logger.exiting("ConsensusPaxosHandlers", "getExecution");
	return exec;
    }

    /**
     * Used for debugging. </br>
     * Undocumented.
     */
    public void dump(OutputStream out) {
	PrintStream err = new PrintStream(out);
	err.println("===== ConsensusPaxos: dump =====");
	err.println("All executions: " + executions);
	err.println("Decisions arrived: " + decided);
	err.println("Finished executions: " + finished);
	err.println("Processes monitored: " + monitored);
	err.println("===================================");
    }
}
