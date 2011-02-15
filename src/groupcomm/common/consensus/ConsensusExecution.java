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
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import uka.transport.DeepClone;
import uka.transport.MarshalStream;
import uka.transport.Transportable;
import uka.transport.UnmarshalStream;
import framework.Constants;
import framework.GroupCommEventArgs;
import framework.GroupCommException;
import framework.GroupCommMessage;
import framework.PID;
import framework.libraries.DefaultSerialization;
import framework.libraries.Trigger;
import framework.libraries.serialization.TArrayList;
import framework.libraries.serialization.TBoolean;
import framework.libraries.serialization.THashSet;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TLinkedList;
import framework.libraries.serialization.TList;
import framework.libraries.serialization.TSet;
import framework.libraries.serialization.TSortedMap;
import framework.libraries.serialization.TTreeMap;

/**
 * This class implements a single instance of consensus<br>
 * It implements Chandra-Toueg algorithm with one execption: The decision is
 * RBcast by the enclosing Consensus class.
 * 
 */
public class ConsensusExecution implements Transportable {
    /**
     * Identifiers of Consensus message types
     */
    public static final int CONS_ESTIMATE = 1;

    public static final int CONS_PROPOSE = 2;

    public static final int CONS_ACK = 3;

    public static final int CONS_RBCAST = 4;
    
    public static final int CONS_DECIDETAG = 5;

    public static final int CONS_ABORT = 6;

    /**
     * PID of the local process
     */
    private PID myself;

    /**
     * Number of this consensus instance
     */
    private Transportable k;

    /**
     * The serial number of the current round.<br>
     * -1 means that the algorithm has not started yet.<br>
     * +infinity means that the algorithm has finished (with a decision).
     */
    private int round = -1;

    /**
     * Number of current phase. Can be 1, 2, 3 and 4.
     */
    private int phase = -1;

    /**
     * The current estimation.
     */
    private Transportable estimate;
    
    /**
     * The first Estimate that we received
     * NB: Optimization to avoid resending the complete decision
     * Added by ORUTTI
     */
    private Transportable firstEstimate = null;

    /**
     * The round number in which the estimate was last updated.
     */
    private int lastUpdated = -1;

    /**
     * Number of estimates received in this round (only valid if the process is
     * the coordinator in the current round and is executing Phase 2).
     */
    private int numEstimate = 0;

    /**
     * Number of ACKs received in the current round (only valid if the process
     * is the coordinator in the current round and is executing Phase 4)
     */
    private int nbAck = 0;

    /**
     * Number of NACKs received in the current round (only valid if the process
     * is the coordinator in the current round and is executing Phase 4)
     */
    private int nbNack = 0;

    /**
     * True if we have already send a acknoledgement to the coordinator! (avoid
     * that both an ack and a nack are send to the coordinator
     */
    private boolean alreadySendAck = false;

    /**
     * Array containing the group processes that take part into this consensus
     * instance.
     */
    private TList group;

    /**
     * Array containing all processes in the group but <i>myself</i>. It is
     * used to send <b>propose</b> and <b>decision</b> messages in rounds
     * where the local process is the coordinator.
     */
    private TList others;

    /**
     * The coordinatorof the current round.
     */
    private PID coordinator;

    /**
     * The minimum number of processes needed to have a majority in the group.
     */
    private int majority;

    /**
     * Set of suspected processes. Its initial value is given in the
     * constructor. It is updated every time a <i>suspect</i> event is
     * triggered by the failure detector.
     */
    private TSet suspected = new THashSet();

    /**
     * Interface to trigger events to the outside. It has to be implemented by
     * the wrapping framework component.
     */
    private Trigger trigger;

    /**
     * This set contains all messages that came too early and thus they can't be
     * treeated yet. Messages are ordered by round number, then by phase number.
     */
    private TSortedMap pushedBack = new TTreeMap();

    /**
     * It logs debugging messages.
     */
    private static final Logger logger = Logger
            .getLogger(ConsensusExecution.class.getName());

    /**
     * Constructor. <br>
     * It initalises all data, and copies references to libraries used.
     * 
     * @param myself
     *            The local process.
     * @param k
     *            The instance object
     * @param suspected
     *            The initial set of suspected processes.
     * @param trigger
     *            The interface to trigger events.
     */
    public ConsensusExecution(PID myself, Transportable k, TSet suspected,
            Trigger trigger) {

        logger.entering("ConsensusExecution", "<constr>");
        this.myself = myself;
        this.k = k;
        this.suspected = suspected;
        this.trigger = trigger;
        logger.exiting("ConsensusExecution", "<constr>");
    }

    /**
     * Starts consenssus execution.
     * 
     * @param proposal
     *            The value proposed
     * @param group
     *            The group of processes taking part of this consensus
     * @throws GroupCommException
     */
    public void processStart(Transportable proposal, TList group)
            throws GroupCommException, IOException, ClassNotFoundException {
        logger.entering("ConsensusExecution", "processStart");
        estimate = proposal;
        // List of all processes in the group
        // this.group = new PID[group.size()];
        this.group = new TArrayList();
        // List of all processes in the group but myself
        this.others = new TArrayList();
        for (int i = 0; i < group.size(); i++) { // TODO: Optimize!
            PID p = (PID) group.get(i);
            this.group.add(p);
            if (!p.equals(myself)) {
                this.others.add(p);
            }
        }
        // Limit for ack and estimate messages (excluding myself)
        majority = this.group.size() / 2;
        if (round != -1)
            throw new GroupCommException(
                    "ConsensusExecution: Calling propose on consensus" + k
                            + "while round != -1!!");
        logger
                .log(
                        Level.FINE,
                        "Starting ConsensusExecution. k = {0}. Group = {1}. Majority = {2}",
                        new Object[] { k, group, new Integer(majority) });
        nextRound();
        logger.exiting("ConsensusExecution", "processStart");
    }

    /**
     * A new estimation has just arrived. This method processes it.
     * 
     * @param r
     *            The round number.
     * @param m
     *            The message containing the estimation.
     * @throws GroupCommException
     */
    public void processEstimate(int r, GroupCommMessage m)
            throws GroupCommException, ClassNotFoundException, IOException {
        logger.entering("ConsensusExecution", "processEstimate");
        if (r > round) {
            // The message is for a future round
            // We keep it for later handling
            pushback(r, CONS_ESTIMATE, m);
            logger
                    .log(
                            Level.FINE,
                            "An ESTIMATE was pushed back. Current pushed-back messages: {0}",
                            pushedBack);
        }
        if (r != round) {
            // The message is not for this round
            logger.exiting("ConsensusExecution", "processEstimate");
            return;
        }

        // I am not the coordinator => BAD!
        if (!myself.equals(coordinator)) {
            throw new GroupCommException(
                    "Unexpected message received in round " + r
                            + "by non-coordinator: " + m);
        }

        // We are not in phase 2, this estimate is late
        if (phase != 2) {
            // ignore
            logger
                    .log(
                            Level.FINE,
                            "Discarding late ESTIMATE (phase != 2): {0}. Current round {1}",
                            new Object[] { m, new Integer(round) });
            logger.exiting("ConsensusExecution", "processEstimate");
            return;
        }

        // m = <<estimate::lastUpdated>>
        Transportable estim = m.tunpack();
        int lastUpd = ((TInteger) m.tunpack()).intValue();

        // update the estimate if necessary
        if (lastUpd > lastUpdated) {
            logger.log(Level.FINE,
                    "Updating ESTIMATE with {0}. Timestamp: {1}", new Object[] {
                            estim, new Integer(lastUpd) });
            lastUpdated = lastUpd;
            estimate = estim;
        }

        numEstimate++;
        if (numEstimate == majority) {
            // We received enough estimations: we can propose a value to all
            // other processes.
            // Proceed to phase 4
            logger
                    .fine("Got majority of ESTIMATEs. Changing phase to 4. Sending proposal");
            lastUpdated = round;// BUG FIXED BY RACHELLE FUZZATI
            phase = 4;
            sendPropose();
            processAck(round, null);
            // just to check if a NACK has already arrived
        }
        logger.exiting("ConsensusExecution", "processEstimate");
    }

    /**
     * A proposal has just arrived from the coordinator.
     * 
     * @param r
     *            The round number.
     * @param m
     *            The message containing the proposal.
     * @throws GroupCommException
     */
    public void processPropose(int r, GroupCommMessage m)
            throws GroupCommException, ClassNotFoundException, IOException {
        logger.entering("ConsensusExecution", "processPropose");
        if (r > round) {
            // The message is for a future round
            // We keep it for later handling
            pushback(r, CONS_PROPOSE, m);
            logger
                    .log(
                            Level.FINE,
                            "A PROPOSE was pushed back. Currrent pushed-back messages: {0}",
                            pushedBack);
        }
        if (r != round) {
            // START: ADDED BY ORUTTI
            if (r == 0){
            	if(m.size() == 1){// Sergio: Included due to a bug in static-appia-abcast, when the coordinator is catching up
            		firstEstimate = deepClone(m.tpeek(0)); 
            	} else {
            		firstEstimate = deepClone(m.tpeek(1)); // 1 instead 0 because CONS_PROPOSE was pushed
                                                       // in method pushback
            	}
            }
            // END: ADDED BY ORUTTI
            // The message is not for this round
            logger.exiting("ConsensusExecution", "processPropose");
            return;
        }

        // I am the coordinator => BAD!
        if (myself.equals(coordinator)) {
            throw new GroupCommException(
                    "Unexpected message received in round " + r
                            + "by coordinator: " + m);
        }

        estimate = m.tunpack();
        if (round == 0){ 
            firstEstimate = deepClone(estimate);
        }
        lastUpdated = round;
        logger
                .log(
                        Level.FINE,
                        "Received a PROPOSE: {0} in round {1}. Setting it as estimate.",
                        new Object[] { estimate, new Integer(round) });

        // Send ack to the coordinator
        sendAck(true);
        alreadySendAck = true;
        // Nothing to do this round, wait decision, abort or coordinator
        // suspicion
        logger.exiting("ConsensusExecution", "processPropose");
    }

    /**
     * An acknowledgement message (Ack or Nack) has just arrived.
     * 
     * @param r
     *            The round number.
     * @param m
     *            The message containing the acknowledgement.
     * @throws GroupCommException
     */
    public void processAck(int r, GroupCommMessage m) throws GroupCommException, IOException, ClassNotFoundException {
        logger.entering("ConsensusExecution", "processAck");
        if (r > round) {
            // The message is for a future round
            // We keep it for later handling
            pushback(r, CONS_ACK, m);
            logger
                    .log(
                            Level.FINE,
                            "An ACK was pushed back. Currrent pushed-back messages: {0}",
                            pushedBack);
        }
        if (r != round) {
            // The message is not for this round
            logger.exiting("ConsensusExecution", "processAck");
            return;
        }
        // I am not the coordinator => BAD!
        if (!myself.equals(coordinator)) {
            throw new GroupCommException(
                    "Unexpected message received in round " + r
                            + "by non-coordinator: " + m);
        }

        // Increment number of {ack,nack} received.
        logger
                .log(
                        Level.FINE,
                        "About to process an ACK: {0} in round {1}. Received acks/nacks {2}/{3}",
                        new Object[] { m, new Integer(round),
                                new Integer(nbAck), new Integer(nbNack) });
        if (m != null) {
            boolean ack = ((TBoolean) m.tunpack()).booleanValue();
            if (ack) {
                nbAck++;
            } else {
                nbNack++;
            }
        }

        // We take into account messages that arrive in Phase 2 (NACKs).
        // However, the process should not pass on to the next round
        // until it completes Phase 2.
        if (phase == 2) {
            logger.fine("Still in phase 2");
            logger.exiting("ConsensusExecution", "processAck");
            return;
        }

        if (nbNack == 0) {
            if (nbAck == majority) {
                // We got a majority of positive Ack's. Thefore we can decide
                logger
                        .fine("Got a majority of positive Ack's. Broadcasting decision");
                broadcastDecision();
                round = Integer.MAX_VALUE;                
            }
        } else {
            // Somebody suspected us and sent us a negative acknowledgement.
            // We might not be able to decide in this round
            // We proceed to next round
            logger.fine("Got a NACK. Going to next round");
            nbNack = 0;
            nbAck = 0;
            sendAbort();
            nextRound();
        }
        logger.exiting("ConsensusExecution", "processAck");
    }

    /**
     * An abort message has just arrived.
     * 
     * @param r
     *            The round number.
     * @throws GroupCommException
     */
    public void processAbort(int r) throws GroupCommException, IOException, ClassNotFoundException {
        logger.entering("ConsensusExecution", "processAbort");
        if (r > round) {
            // The message is for a future round
            // We keep it for later handling
            pushback(r, CONS_ABORT, new GroupCommMessage());
            logger
                    .log(
                            Level.FINE,
                            "An Abort Message was pushed back. Currrent pushed-back messages: {0}",
                            pushedBack);
        }
        if (r != round) {
            // The message is not for this round
            logger.exiting("ConsensusExecution", "processAbort");
            return;
        }

        // I am the coordinator => BAD!
        if (myself.equals(coordinator)) {
            throw new GroupCommException(
                    "Unexpected message received in round " + r
                            + "by coordinator: " + " Abort");
        }

        nextRound();
        logger.exiting("ConsensusExecution", "processAbort");
    }

    /**
     * The list of suspected processes has just changed. To process the new
     * list, fisrt update the suspected list with the one received. Then, send a
     * Nack to the coordinator if it is now suspected (and proceed to the next
     * round).
     * 
     * @param suspected
     *            The updated suspect list
     * @throws GroupCommException
     */
    public void processSuspicion(TSet suspected) throws GroupCommException, IOException, ClassNotFoundException {
        logger.entering("ConsensusExecution", "processSuspicion");
        this.suspected = suspected;

        // Consensus is not running (either not started yet or already finished)
        if (round == Integer.MAX_VALUE || round == -1) {
            logger.exiting("ConsensusExecution", "processSuspicion");
            return;
        }

        if (!myself.equals(coordinator) && suspected.contains(coordinator)) {
            // Send a NACK only if no ack were already send
            if (!alreadySendAck)
                sendAck(false);
            // Proceed to next round
            nextRound();
        }
        logger.exiting("ConsensusExecution", "processSuspicion");
    }

    /**
     * Returns true iff this instance has already started (i.e., a value was
     * proposed by the local host).
     * 
     * @return
     */
    public boolean hasStarted() {
        return round > -1;
    }
    
    /** 
     * Returns the estimate of the process first round
     */
    public Transportable firstEstimate() {
        return firstEstimate;
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
     * This method manages the transition to the next round. It updates several
     * attributes and executes a different code dependng on whether the local
     * process will be the coordinator or not in the new round.
     * 
     * @throws GroupCommException
     */
    private void nextRound() throws GroupCommException, IOException, ClassNotFoundException {
        logger.entering("ConsensusExecution", "nextRound");
        // Increments the round
        round++;
        alreadySendAck = false;
        if (round > 1000)
            System.err
                    .println("WARNING: Consensus is taking too many rounds!!!");
        // New coordinator
        coordinator = (PID) group.get(round % group.size());
        logger.log(Level.FINE, "New round: {0}. New coordinator: {1}",
                new Object[] { new Integer(round), coordinator });

        if (!myself.equals(coordinator)) {
            // I am not the coordinator

            if (round > 0) { // Optimization for round #1
                // PHASE 1
                // If this is not round #1, send estimate to the coordinator
                sendEstimate();
            }

            if (suspected.contains(coordinator)) {
                // PHASE 3
                // The new coordinator is suspected
                // Send it a Nack
                sendAck(false);
                // Nothing left to do in this round
                // Proceed to next round
                nextRound();
            }

        } else {
            // I am the coordinator

            if (round > 0) { // Optimization for round #1
                // If this is not round #1, wait for estimates
                numEstimate = 0;
                phase = 2;
            } else {
                // If this is round #1, directly send proposal to the others
                firstEstimate = deepClone(estimate);
                sendPropose();
                // Proceed to phase 4
                phase = 4;
            }
        }

        // In any case,
        // Pushed back messages must be treated if current round is theirs
        while (!pushedBack.isEmpty()) {
            TInteger rObj = (TInteger) pushedBack.firstKey();
            int r = rObj.intValue();
            if (r > round) {
                // Messages for future round, we keep it and quit the loop
                logger.exiting("ConsensusExecution", "nextRound");
                return;
            }
            // We remove all messages of round r from the pushed back queue
            TLinkedList l = (TLinkedList) pushedBack.remove(rObj);
            if (r < round) {
                logger
                        .log(
                                Level.FINE,
                                "Discarding old messages {0} in pushedBack for round {1}",
                                new Object[] { l, rObj });
            } else { // r == round
                logger.log(Level.FINE,
                        "Processing messages {0} in pushedBack for round {1}",
                        new Object[] { l, rObj });
                Iterator it = l.iterator();
                while (it.hasNext()) {
                    GroupCommMessage m = (GroupCommMessage) it.next();
                    int type = ((TInteger) m.tunpack()).intValue();
                    switch (type) {
                    case CONS_ESTIMATE:
                        processEstimate(r, m);
                        break;
                    case CONS_PROPOSE:
                        processPropose(r, m);
                        break;
                    case CONS_ACK:
                        processAck(r, m);
                        break;
                    case CONS_ABORT:
                        processAbort(r);
                        break;
                    default:
                        throw new GroupCommException("Weird message type "
                                + type + " in pushed back set!");
                    }
                }
            }
        }
        logger.exiting("ConsensusExecution", "nextRound");
    }

    /**
     * This method inserts a message whose processing has to be delayed into map
     * <i>pushedBack</i>. The map is ordered by increasing round numbers.
     * 
     * @param r
     *            The message's round number
     * @param type
     *            The message's type
     * @param m
     *            The message payload
     */
    private void pushback(int r, int type, GroupCommMessage m) {
        logger.entering("ConsensusExecution", "pushback");
        TInteger rObj = new TInteger(r);
        TLinkedList l = (TLinkedList) pushedBack.get(rObj);
        if (l == null) {
            l = new TLinkedList();
            pushedBack.put(rObj, l);
        }
        m.tpack(new TInteger(type));
        l.addLast(m);
        logger.exiting("ConsensusExecution", "pushback");
    }

    /**
     * Send a message with the current estimate to the current coordinator (the
     * local process mustn't be the coordinator)
     * 
     */
    private void sendEstimate() {
        GroupCommMessage estimateMessage = new GroupCommMessage();
        // m = <<>>
        estimateMessage.tpack(new TInteger(lastUpdated));
        // m = <<lastUpdated>>
        estimateMessage.tpack(estimate);
        // m = <<estimate::lastUpdated>>
        estimateMessage.tpack(new TInteger(round));
        // m = <<round::estimate::lastUpdated>>
        estimateMessage.tpack(new TInteger(CONS_ESTIMATE));
        // m = <<CONS_ESTIMATE::round::estimate::lastUpdated>>
        estimateMessage.tpack(k);
        // m = <<k::CONS_ESTIMATE::round::estimate::lastUpdated>>
        triggerSend(estimateMessage, coordinator);
    }

    /**
     * Send a message with the current estimate as the coordinator's proposal
     * (the local process must be the coordinator)
     * 
     */
    private void sendPropose() {
        GroupCommMessage proposeMessage = new GroupCommMessage();
        // m = <<>>
        proposeMessage.tpack(estimate);
        // m = <<estimate>>
        proposeMessage.tpack(new TInteger(round));
        // m = <<round::estimate>>
        proposeMessage.tpack(new TInteger(CONS_PROPOSE));
        // m = <<CONS_PROPOSE::round::estimate>>
        proposeMessage.tpack(k);
        // m = <<k::CONS_PROPOSE::round::estimate>>
        triggerSend(proposeMessage, others);
    }

    /**
     * Send a message with the current estimate as the coordinator's proposal
     * (the local process must be the coordinator)
     * 
     */
    private void sendAbort() {
        GroupCommMessage abortMessage = new GroupCommMessage();
        // m = <<>>
        abortMessage.tpack(new TInteger(round));
        // m = <<round>>
        abortMessage.tpack(new TInteger(CONS_ABORT));
        // m = <<CONS_ABORT::round>>
        abortMessage.tpack(k);
        // m = <<k::CONS_ABORT::round>>
        triggerSend(abortMessage, others);
    }

    /**
     * Send an acknowledgement message to the current coordinator. It will be an
     * Ack or a Nack depending on the parameter
     * 
     * @param ack
     *            If it is true, an Ack is sent. Otherwise, a Nack is sent.
     */
    private void sendAck(boolean ack) {
        GroupCommMessage ackMessage = new GroupCommMessage();
        // m = <<>>
        ackMessage.tpack(new TBoolean(ack));
        // m = <<NACK>>
        ackMessage.tpack(new TInteger(round));
        // m = <<round::NACK>>
        ackMessage.tpack(new TInteger(CONS_ACK));
        // m = <<CONS_ACK::round::NACK>>
        ackMessage.tpack(k);
        // m = <<k::CONS_ACK::round::NACK>>
        triggerSend(ackMessage, coordinator);
    }

    /**
     * Sends a decision message to all processes
     * 
     */
    private void broadcastDecision() {
        GroupCommMessage decisionMessage = new GroupCommMessage();
        // m = <<>>
        decisionMessage.tpack(others);
        // m = <<group>> group does not have to contain myself
        // Because decision do not have to be resend to the process that decide
        if (round == 0){
            decisionMessage.tpack(new TInteger(CONS_DECIDETAG));
        } else {
            decisionMessage.tpack(estimate);        
            // m = <<decision::group>>
            decisionMessage.tpack(new TInteger(CONS_RBCAST));
        }
        // m = <<(CONS_BROADCAST::decision || CONS_DECIDETAG)::group>>
        decisionMessage.tpack(k);
        // m = <<k::(CONS_BROADCAST::decision || CONS_DECIDETAG)::group>>
        // Broadcast decision to others
        triggerSend(decisionMessage, group);
    }

    /**
     * Triggers a <i>PointToPointSend</i> event for each process in the second
     * parameter.
     * 
     * @param m
     *            The message to be sent.
     * @param g
     *            The processes that the message is to be sent to.
     */
    private void triggerSend(GroupCommMessage m, TList g) {
        for (int i = 0; i < g.size(); i++) {
            triggerSend(m.cloneGroupCommMessage(), (PID) g.get(i));
        }
    }

    /**
     * Triggers a single <i>PointToPointSend</i> event.
     * 
     * @param m
     *            The message to be sent.
     * @param p
     *            The destination process.
     */
    private void triggerSend(GroupCommMessage m, PID p) {
        GroupCommEventArgs pt2ptSend = new GroupCommEventArgs();
        pt2ptSend.addLast(m);
        pt2ptSend.addLast(p);
        pt2ptSend.addLast(new TBoolean(false)); // not promisc
        logger.log(Level.FINE, "Sending Pt2Pt message {0} to {1}",
                new Object[] { m, p });
        trigger.trigger(Constants.PT2PTSEND, pt2ptSend);
    }

    /**
     * Used for debugging. Undocumented.
     */
    public String toString() {
        return new String("(** k: " + k + " r: " + round + " phase: " + phase
                + " coord: " + coordinator + " nbAck: " + nbAck + " nbNack: "
                + nbNack + " nbEstimate: " + numEstimate + " lastUpd: "
                + lastUpdated + " estimate: " + estimate + " suspected: "
                + suspected + " pushedBack: " + pushedBack + "**)");
    }

    // TODO: Remove
    public void marshal(MarshalStream arg0) throws IOException {
        throw new IOException("Unimplemented marshall");
    }

    public void unmarshalReferences(UnmarshalStream arg0) throws IOException,
            ClassNotFoundException {
        throw new IOException("Unimplemented unmarshallReferences");
    }

    public Object deepClone(DeepClone arg0) throws CloneNotSupportedException {
        throw new CloneNotSupportedException("Unimplemented deepClone");
    }
}
