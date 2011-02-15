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
 * <b> Class implementing the common code for Consensus building block. </b>
 * <br>
 * This implementation is able to start several consensus instances at the same
 * time.
 * <hr>
 * <b> Handled events: </b>
 * <dl>
 * <dt> <i>Propose</i> </dt>
 * <dd> Start a new consensus execution. </dd>
 * <dt> <i>Pt2PtDeliver</i> </dt>
 * <dd> Receive a new point-to-point message from the underlying layer. </dd>
 * <dt> <i>Suspect</i> </dt>
 * <dd> Reports about currently suspected processes. It should be triggered
 * every time the list of suspected changes. </dd>
 * </dl>
 */
public class Consensus {
	/**
     * PID of the local process
     */
    private PID myself;

    /**
     * Interface to trigger events to the outside. It has to be implemented by
     * the wrapping framework component.
     */
    private Trigger trigger;

    /**
     * Interface to the flow control library. The common code assumes it to be
     * built in the framework.
     */
    private FlowControl flow_control;

    /**
     * Unique key to interact with the flow control library without interfereing
     * with other layers.
     */
    private int fc_key = -1;

    /**
     * Maximum number of consensus in parallel.
     */
    public static final int MAX_CONSENSUS = 100;

    /**
     * Number of Consensus currently running
     */
    private int nbConsensus = 0;

    /**
     * Set of suspected processes. It is handed over to the newly created
     * consensus instances. It is updated every time a <i>suspect</i> event is
     * handled.
     */
    private TSet suspected = new THashSet();

    /**
     * In this map, the keys are the processes that take part in at least one of
     * all ongoing consensus (so, they are being monitored by the Failure
     * Detector). Each of the processes is mapped to a list of all consensus
     * instance numbers in which it participates.
     */
    private TMap monitored = new THashMap();

    /**
     * It contains the instances of all ongoing consensus
     */
    private TMap executions = new THashMap();

    /**
     * It contains all decisions that arrived too early (decisions whose
     * consensus hasn't yet started in the local process.
     */
    private TMap decided = new THashMap();

    /**
     * Introduced for optimization. Now the process stops monitoring processes of the 
     * last finished consensus but 2.
     */
    private Transportable remove_k;

    /**
     * Added By ORUTTI. Is used if we receive a decision before the proposal
     */
    private TSet waitPropose = new THashSet();
    
    /**
     * It contains the instance number of all finished consensus.
     */
    //private TSet finished = new THashSet();
    private CompressedSet finished = new CompressedSet();

    // K proposed by the user
    // private HashMap userK = new HashMap();

    /*
     * A given instance of consensus can be in these states: 1) Not started:
     * executions ?= null; dec == null; finished == null; 2) Started: executions !=
     * null; dec == null; finished == null; 3) Decided: executions == null; dec !=
     * null; finished ==null; 4) Finished: executions == null; dec == null;
     * finished != null;
     */

    /**
     * It logs debugging messages.
     */
    private static final Logger logger = Logger.getLogger(Consensus.class
            .getName());

    /**
     * Constructor. <br>
     * It inisialises flow control, and copies serveral parameters.
     */
    public Consensus(Trigger trigger, FlowControl fc, PID myself) {
        logger.entering("ConsensusHandlers", "<constr>");
        this.flow_control = fc;
        // flow_control.setThreshold(fc_key, MAX_CONSENSUS);

        this.trigger = trigger;
        this.myself = myself;
        this.nbConsensus = 0;
        logger.exiting("ConsensusHandlers", "<constr>");
    }

    /**
     * Handler for event <i>Propose</i>. It spawns a new consensus instance.
     * 
     * @param e
     *            <dl>
     *            <dt> arg1 : Set[PID] </dt>
     *            <dd> The group of processes that participate in the new
     *            instance of consensus. </dd>
     *            <dt> arg2 : Object </dt>
     *            <dd> The value proposed by the local process for the new
     *            instance. </dd>
     *            <dt> arg3 : Object </dt>
     *            <dd> The instance number of the new instance. </dd>
     *            </dl>
     */
    public void handlePropose(GroupCommEventArgs e) throws GroupCommException,
            IOException, ClassNotFoundException {

        logger.entering("ConsensusHandlers", "handlePropose");
        //this sould be included in handleInit, but there's none here
        if(fc_key == -1) fc_key = flow_control.getFreshKey();

        
        TList group = (TList) e.get(0);
        Transportable o = (Transportable) e.get(1);
        Transportable k_parObj = (Transportable) e.get(2);
        // Long k_parObj = ((Long) e.get(2));
        // long k_par = k_parObj.longValue();
        logger
                .log(
                        Level.FINE,
                        "Running consensus#{2}\n\tProcessGroup: {0}\tProposed Value: {1}",
                        new Object[] { group, o, k_parObj });

        // Look for duplicate processes in the group
        // At the same time, check if the localhost is in the group
        boolean found = false;
        for (int i = 0; i < group.size(); i++) {
            found = found || myself.equals(group.get(i));
            for (int j = i + 1; j < group.size(); j++)
                if (group.get(i).equals(group.get(j)))
                    throw new GroupCommException("Process" + group.get(i)
                            + " appears more than once in the group.");
        }
        if (!found) {
            throw new GroupCommException("Consensus: The localhost " + myself
                    + " is not in the group passed as parameter: " + group);
        }

        // Flow control
        nbConsensus++;
        if (nbConsensus >= MAX_CONSENSUS)
            flow_control.block(fc_key);
        // flow_control.alloc(fc_key, 1);

        // Set instance number
        // if (k_par < 0)
        // k_par = k + 1;

        // if (k_par <= k)
        // throw new GroupCommException(
        // "Bad instance number (should be greather than)" + k);

        // for (long tune = k + 1; tune < k_par; tune++)
        // finished.add(tune);
        // this.k = k_par;

        if (group.size() == 1) {
            // Consensus with one only process
            // We decide the value proposed
            triggerDecision(deepClone(o), k_parObj);
            logger.exiting("ConsensusHandlers", "handlePropose");
            return;
        }

        Transportable decision = (Transportable) decided.get(k_parObj);

        // Has its decision already arrived??
        if (decision != null) {
            decided.remove(k_parObj);
            Transportable clone = (Transportable) deepClone(decision);
            // reSendDecision(decision, k_parObj, group, null); DONE EARLIER
            executions.remove(k_parObj);
            triggerDecision(clone, k_parObj);
            removeProcesses(k_parObj);
            logger.exiting("ConsensusHandlers", "handlePropose");
            return;
        }

        if (finished.contains((Compressable) k_parObj))
            throw new GroupCommException(
                    "Impossible to finish before starting consensus!");

        addProcesses(k_parObj, group);
        getExecution(k_parObj).processStart(o, group);
        //      START: ADDED BY ORUTTI        
        if (waitPropose.contains(k_parObj)){
            decision = getExecution(k_parObj).firstEstimate();
            if (decision != null) {
                waitPropose.remove(k_parObj);
                decided.remove(k_parObj);
                Transportable clone = (Transportable) deepClone(decision);
                // reSendDecision(decision, k_parObj, group, null); DONE EARLIER
                executions.remove(k_parObj);
                triggerDecision(clone, k_parObj);
                removeProcesses(k_parObj);
                logger.exiting("ConsensusHandlers", "handlePropose");
                return;
            }
        }
        //      END: ADDED BY ORUTTI
        
        logger.exiting("ConsensusHandlers", "handlePropose");
    }

    /**
     * Handler for event <i>Pt2PtDeliver</i>. The are four types of messages:
     * ESTIMATE, PROPOSE, ACK, RBCAST. This handler only call the corresponding
     * method in the ConsensusExecution object associated with the messages
     * execution.
     * 
     * @param e
     *            <dl>
     *            <dt> arg1 : GroupCommMessage </dt>
     *            <dd> The message. </dd>
     *            <dt> arg2 : PID </dt>
     *            <dd> The sending process. </dd>
     *            </dl>
     */
    public void handlePt2PtDeliver(GroupCommEventArgs e)
            throws GroupCommException, IOException, ClassNotFoundException {
        logger.entering("ConsensusHandlers", "handlePt2PtDeliver");
        GroupCommMessage m = (GroupCommMessage) e.get(0);
        // m = <<k::type::payload>>
        Transportable kmessObj = m.tunpack();
        // m = <<type::payload>>
        if (finished.contains((Compressable) kmessObj) || decided.containsKey(kmessObj)) {
            logger
                    .log(
                            Level.FINE,
                            "Late message to Consensus instance {0}. Discarding it: {1}",
                            new Object[] { kmessObj, m });
            logger.exiting("ConsensusHandlers", "handlePt2PtDeliver");
            return;
        }
        PID source = (PID) e.get(1);
        int type = ((TInteger) m.tunpack()).intValue();
        // m = <<payload>>
        switch (type) {
        case ConsensusExecution.CONS_ESTIMATE:
            // m = <<r::estimate::lastupdated>>
            int r = ((TInteger) m.tunpack()).intValue();
            getExecution(kmessObj).processEstimate(r, m);
            break;
        case ConsensusExecution.CONS_PROPOSE:
            // m = <<r::propose>>
            r = ((TInteger) m.tunpack()).intValue();
            getExecution(kmessObj).processPropose(r, m);
            
            if (waitPropose.contains(kmessObj)){
                Transportable decision = getExecution(kmessObj).firstEstimate();
                if (decision != null){
                    if (getExecution(kmessObj).hasStarted()){
                        waitPropose.remove(kmessObj);
                        executions.remove(kmessObj);
                        removeProcesses(kmessObj);
                        triggerDecision(decision, kmessObj);
                    } else {
                        decided.put(kmessObj, decision);
                    }
                }
            }
            break;
        case ConsensusExecution.CONS_ACK:
            // m = <<r::ack>>
            r = ((TInteger) m.tunpack()).intValue();
            getExecution(kmessObj).processAck(r, m);
            break;
        case ConsensusExecution.CONS_ABORT:
            // m = <<r>>
            r = ((TInteger) m.tunpack()).intValue();
            getExecution(kmessObj).processAbort(r);
            break;
        case ConsensusExecution.CONS_DECIDETAG:
            // START: ADDED BY ORUTTI        	
            if (getExecution(kmessObj).hasStarted()) { 
            	Transportable decision = getExecution(kmessObj).firstEstimate();
            	
                if (!myself.equals(source)) {
                    TList group = (TList) m.tunpack();
                    // m = <<>>
                    reSendDecisionTag(kmessObj, group, source);
                }
            	
            	// THE DECISION TAG IS ALREADY ARRIVED BUT NOT THE PROPOSITION
            	if (decision == null){
            		waitPropose.add(kmessObj);
            	} else {
                    executions.remove(kmessObj);
                    removeProcesses(kmessObj);
                    triggerDecision(decision, kmessObj);
            	}
            } else {
            	waitPropose.add(kmessObj);
            	
                TList group = (TList) m.tunpack();
                // m = <<>>
                reSendDecisionTag(kmessObj, group, source);
            } 
        	break;
            // END: ADDED BY ORUTTI
        case ConsensusExecution.CONS_RBCAST:
            // m = <<decision::group>>
            Transportable decision = m.tunpack();
        	// m = <<group>>
                       
            if (getExecution(kmessObj).hasStarted()) {                
            	Transportable clone = deepClone(decision);               
                
                if (!myself.equals(source)) {
                    TList group = (TList) m.tunpack();
                    // m = <<>>
                    reSendDecision(decision, kmessObj, group, source);
                }
                
                executions.remove(kmessObj);
                removeProcesses(kmessObj);
                triggerDecision(clone, kmessObj);
            } else {     
                decided.put(kmessObj, decision);
                // if (!myself.equals(source)) {Impossible!!
                TList group = (TList) m.tunpack();
                // m = <<>>
                reSendDecision(decision, kmessObj, group, source);
                //}
            }
            break;
        default:
            throw new GroupCommException("Consensus : handlePt2Ptdeliver : "
                    + "Unknown message type: " + type);
        }
        logger.exiting("ConsensusHandlers", "handlePt2PtDeliver");
    }

    /**
     * Handler for event <i>Suspect</i>. Every time the Failure Detector
     * changes its suspect list, it triggers an event that this handler is bound
     * to.
     * 
     * @param e
     *            <dl>
     *            <dt> arg1 : Set[PID] </dt>
     *            <dd> The updated suspect list. </dd>
     *            </dl>
     */
    public void handleSuspect(GroupCommEventArgs e) throws GroupCommException, IOException, ClassNotFoundException {
        logger.entering("ConsensusHandlers", "handleSuspect");
        suspected = (TSet) e.get(0);

        Iterator it = executions.values().iterator();
        // "it" contains an UNORDERED sequence of executions
        while (it.hasNext()) {
            ConsensusExecution exe = (ConsensusExecution) it.next();
            exe.processSuspicion(suspected);
        }
        logger.exiting("ConsensusHandlers", "handleSuspect");
    }
    
    /**
     * Callback function. Added by ORUTTI
     */
    public void decisionTaken(Transportable decision, Transportable kObj){
        executions.remove(kObj);
        removeProcesses(kObj);
        triggerDecision(decision, kObj);       
    }

    /**
     * Trigger a <b>decision</b> event with the instance number that has just
     * decided and the decided value.
     * 
     * @param o
     *            The value decided
     * @param k
     *            The instance number
     */

    private void triggerDecision(Transportable o, Transportable k) {
        logger.entering("ConsensusHandlers", "triggerDecision");
        nbConsensus--;
        if (nbConsensus < MAX_CONSENSUS)
            flow_control.release(fc_key);
        finished.add((Compressable) k);

        GroupCommEventArgs e = new GroupCommEventArgs();
        e.addLast(o);
        e.addLast(k);

        logger.log(Level.FINE, "Triggering decision#{1}: {0}", new Object[] {
                o, k });
        trigger.trigger(Constants.DECIDE, e);
        logger.exiting("ConsensusHandlers", "triggerDecision");
    }

    /**
     * Makes a copy in memory of the parameter. If the parameter has references
     * to other objects, they are also cloned. This is necessary to avoid
     * side-effects at higher-lever protocols.
     * 
     * @param o
     *            The object to deep-clone
     * @return
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private Transportable deepClone(Transportable o) throws IOException,
            ClassNotFoundException {
        // TODO: There have to be better ways to do deep-clone!!!
        return DefaultSerialization
                .unmarshall(DefaultSerialization.marshall(o));
    }

    /**
     * Sends the decision again. This is done to simulate the bahaviour of
     * Reliable Broadcast in static environments.
     * 
     * @param decision
     *            The value decided
     * @param kObj
     *            The instance number
     * @param group
     *            The group of processes to which the message has to be sent
     * @param dontsend
     *            A PID to which it is not necessary to send the message
     *            (optimization)
     */
    private void reSendDecision(Transportable decision, Transportable kObj,
            TList group, PID dontsend) {
        GroupCommMessage decisionMessage = new GroupCommMessage();
        // m = <<>>
        decisionMessage.tpack(group);
        // m = <<group>>
        decisionMessage.tpack(decision);
        // m = <<decision::group>>
        decisionMessage.tpack(new TInteger(ConsensusExecution.CONS_RBCAST));
        // m = <<CONS_BROADCAST::decision::group>>
        decisionMessage.tpack(kObj);
        // m = <<k::CONS_BROADCAST::decision::group>>

        // Broadcast to the f following processes
        int index = group.indexOf(myself);
        int f = (group.size() / 2); // Since group does not contain the initial
                                    // sender of decision
        for (int i = 1; i <= f; i++) {
            PID pi = (PID) group.get((index + i) % group.size());
            // if (!pi.equals(myself)
            // && (dontsend == null || !pi.equals(dontsend))) {
            //   SHOULD BE IMPOSSIBLE
            if (/*!pi.equals(myself) &&*/ !pi.equals(dontsend)) {
                GroupCommEventArgs pt2ptSend = new GroupCommEventArgs();
                pt2ptSend.addLast(decisionMessage.cloneGroupCommMessage());
                pt2ptSend.addLast(pi);
                pt2ptSend.addLast(new TBoolean(false));
                // not promisc
                logger.log(Level.FINE, "Sending Broadcast message {0} to {1}",
                        new Object[] { decisionMessage, pi });
                trigger.trigger(Constants.PT2PTSEND, pt2ptSend);
            }
        }
    }
    
    /**
     * Sends the decision tag again. This is done to simulate the bahaviour of
     * Reliable Broadcast in static environments.
     * 
     * @param kObj
     *            The instance number
     * @param group
     *            The group of processes to which the message has to be sent
     * @param dontsend
     *            A PID to which it is not necessary to send the message
     *            (optimization)
     */
    //ADDED BY ORUTTI
    private void reSendDecisionTag(Transportable kObj,
            TList group, PID dontsend) {
        GroupCommMessage decisionMessage = new GroupCommMessage();
        // m = <<>>
        decisionMessage.tpack(group);
        // m = <<group>>
        decisionMessage.tpack(new TInteger(ConsensusExecution.CONS_DECIDETAG));
        // m = <<CONS_DECIDETAG::group>>
        decisionMessage.tpack(kObj);
        // m = <<k::CONS_DECIDETAG::group>>

        // Broadcast to the f following processes
        int index = group.indexOf(myself);
        int f = (group.size() / 2); // Since group does not contain the initial
                                    // sender of decision
        for (int i = 1; i <= f; i++) {
            PID pi = (PID) group.get((index + i) % group.size());
            // if (!pi.equals(myself)
            // && (dontsend == null || !pi.equals(dontsend))) {
            //   SHOULD BE IMPOSSIBLE
            if (/*!pi.equals(myself) &&*/ !pi.equals(dontsend)) {
                GroupCommEventArgs pt2ptSend = new GroupCommEventArgs();
                pt2ptSend.addLast(decisionMessage.cloneGroupCommMessage());
                pt2ptSend.addLast(pi);
                pt2ptSend.addLast(new TBoolean(false));
                // not promisc
                logger.log(Level.FINE, "Sending Broadcast message {0} to {1}",
                        new Object[] { decisionMessage, pi });
                trigger.trigger(Constants.PT2PTSEND, pt2ptSend);
            }
        }
    }

    /**
     * Updates the monitored map with the process group of a new instance. It
     * also starts monitoring completely new processes.
     * 
     * @param group
     *            The group of process that will take part in current consensus.
     */
    private void addProcesses(Transportable kObj, TList group) {
        logger.entering("ConsensusHandlers", "addProcesses");
        THashSet start = new THashSet();

        // For each p in group
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
            e1.addLast(start); // Start //TODO: clone start? it's used below as
            // well...
            e1.addLast(new THashSet()); // Stop
            trigger.trigger(Constants.STARTSTOPMONITOR, e1);

            GroupCommEventArgs e2 = new GroupCommEventArgs();
            e2.addLast(start); // Join
            e2.addLast(new THashSet()); // Remove
            trigger.trigger(Constants.JOINREMOVELIST, e2);
        }
        logger.exiting("ConsensusHandlers", "addProcesses");
    }

    /**
     * Removes consensus instance number k2 from all processes that appear in
     * map <i>monitored</i> map. If some updated process has no more instances
     * in its mapped list it is removed from the map (since it doesn't take part
     * in any remaining consensus)
     * 
     * @param k2
     *            The consensus instance that has just finished
     */
    private void removeProcesses(Transportable kObj) {
        logger.entering("ConsensusHandlers", "removeProcesses");
        // Sergio - 9 mar 2006 - added for optimization
        Transportable tmp = remove_k;
        remove_k = kObj;
        if(tmp == null) return;
        
        THashSet stop = new THashSet();

        // For each p in group
        TCollection keys = monitored.keySet();
        Iterator i = keys.iterator();
        while (i.hasNext()) {
            PID p = (PID) i.next();
            TSet ins = (TSet) monitored.get(p);
            // ins is the set of instances (long) that p takes part of
            ins.remove(tmp);
            if (ins.isEmpty()) {
                stop.add(p);
            }
        }

        // Stop = processes that don't take part in any of
        // the remaining consensus
        i = stop.iterator();
        while (i.hasNext()) {
            PID p = (PID) i.next();
            monitored.remove(p);
            // Side effect: we're modifying all remaining instances, but
            // p doesn't take part in any of them
            suspected.remove(p);
        }

        if (!stop.isEmpty()) {
            GroupCommEventArgs e1 = new GroupCommEventArgs();
            e1.addLast(new THashSet()); // Start
            e1.addLast((THashSet) stop.clone()); // Stop
            trigger.trigger(Constants.STARTSTOPMONITOR, e1);

            GroupCommEventArgs e2 = new GroupCommEventArgs();
            e2.addLast(new THashSet()); // Join
            e2.addLast(stop); // Remove
            trigger.trigger(Constants.JOINREMOVELIST, e2);

        }
        logger.exiting("ConsensusHandlers", "removeProcesses");
    }

    /**
     * Return the execution object mapped to a given instance number. If there
     * is still no execution mapped to the instance number it maps a fresh one.
     * 
     * @param k
     *            The instance number whose execution will be returned.
     * @return The execution mapped to that instance number.
     */
    private ConsensusExecution getExecution(Transportable o) {
        logger.entering("ConsensusHandlers", "getExecution");
        
        ConsensusExecution exec = (ConsensusExecution) executions.get(o);
        if (exec == null) {
            // need to clone suspected??
            exec = new ConsensusExecution(myself, o, suspected, trigger);
            executions.put(o, exec);
        }
        
        logger.exiting("ConsensusHandlers", "getExecution");
        return exec;
    }

    /**
     * Used for debugging. </br> Undocumented.
     * 
     * @param out
     *            An output stream
     */
    public void dump(OutputStream out) {
        PrintStream err = new PrintStream(out);
        err.println("===== Consensus: dump =====");
        err.println("All executions: " + executions);
        err.println("Decisions arrived: " + decided);
        err.println("Finished executions: " + finished);
        err.println("Processes suspected: " + suspected);
        err.println("Processes monitored: " + monitored);
        err.println("===================================");
    }
}
