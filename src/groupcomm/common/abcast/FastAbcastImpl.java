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
/*
 * Author: Olivier Rütti
 */
package groupcomm.common.abcast;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import uka.transport.Transportable;
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
import framework.libraries.serialization.THashMap;
import framework.libraries.serialization.THashSet;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TLinkedHashMap;
import framework.libraries.serialization.TLinkedList;
import framework.libraries.serialization.TList;
import framework.libraries.serialization.TLong;
import framework.libraries.serialization.TMap;
import framework.libraries.serialization.TSet;
import framework.libraries.serialization.TSortedMap;
import framework.libraries.serialization.TTreeMap;

/**
 * <b> This class implements the common code for algorithm abcast. </b>
 * <hr>
 * <b> Events:
 * <dt> <i>Init</i> </dt>
 * <dd> Initializes the abcast layer </dd>
 * <dt> <i>Abcast</i> </dt>
 * <dd> Send a Broadcast message, with the abcast algorithm </dd>
 * <dt> <i>Pt2PtDeliver</i> </dt>
 * <dd> Happend when a message is received by the lower layers </dd>
 * <dt> <i>Decide</i> </dt>
 * <dd> Happend when consensus has decided </dd>
 * </dl>
 */
public class FastAbcastImpl {
    //REMOVE
    long cStart = 0;
    
	/**
	 * Identifiers of Consensus message types
	 */
	public static final int ABCAST_ESTIMATE = 1;

	public static final int ABCAST_PROPOSE = 2;

	public static final int ABCAST_ACK = 3;

	public static final int ABCAST_RBCAST = 4;

	public static final int ABCAST_ABORT = 5;

	public static final int ABCAST_COORDNEEDSESTIMATE = 6;
    
    public static final int ABCAST_UPDATEK = 7;

	private PID myself;

	// Initialized ?
	private boolean initialized = false;

	// Trigger class for events routing
	private Trigger trigger;

	// Variables for flow control
	private FlowControl flow_control;

	private int fc_key;

	private int nbMsgsSent;

	// Known processes, to send the broadcast messages : Contains PID
	private TArrayList known;

	// Others processes, to send the estimate, propose: Does Not Contains MYSELF
	private TArrayList others;

	// A-delivered messages : Set (AbcastMessageID)
	private TSet aDelivered;

	// A-Undelivered messages : FIFO-Order Map (AbcastMessageID -> GroupCommMessage
	// m)
	private TLinkedHashMap aUndelivered;

	// Bound for old A-delivered messages : Table (PID -> Integer)
	private TMap maxIdProProc;

	// Decision that are RBCast but not yet acknowledged
	private TMap decisionToBroadcast;

	// Smallest know decision for each processes
	private TMap processesCurrentK;
    
    // Messages sent to other processes
    private TMap messagesSendToProc;

	// id for consensus requests
	private long k = -1;

	// the coordinator
	private PID coordinator;

	// round
	private int round = -1;

	// phase
	private int phase = -1;

	// current Estimate
	private TLinkedHashMap estimate;

	// Abcast message current id
	private AbcastMessageID abcastId;

	// IS the coordinator requested estimate
	private boolean coordNeedsEstimate = false;

	// Is tzhe first rond optimized
	private boolean optimizeFirstRound = false;

	// Timestamp
	private int timeStamp;

	// nb Estimate received
	private int numEstimate;

	// nb Ack Received
	private int nbAck;

	// nb nack received
	private int nbNack;

	// majority
	private int majority;

	/**
	 * Set of suspected processes. Its initial value is given in the
	 * constructor. It is updated every time a <i>suspect</i> event is
	 * triggered by the failure detector.
	 */
	private TSet suspected = new THashSet();

	// This set contains all messages that came too early and thus they can't be
	// treeated yet. Messages are ordered by round number, then by phase number.
	private TSortedMap pushedBack = new TTreeMap();

	public static final int MIN_LOCALLY_ABCAST = 1;
	//public static final int MAX_UNDELIVERED = 8;
	//public static final int MAX_PROPOSE = 4;
    public static final int MSGS_PER_CONSENSUS = 4;
    private int max_locally_abcast = MSGS_PER_CONSENSUS;
    
    public static final int MAX_MESSAGES_PER_ACK = 2;
    
    public static final int UPDATEK_PERIOD = 100;

	private static final Logger logger = Logger.getLogger(FastAbcastImpl.class
			.getName());

	public static class TriggerItem {
		public int type;

		public GroupCommEventArgs args;

		public TriggerItem(int type, GroupCommEventArgs args) {
			this.type = type;
			this.args = args;
		}
	}

	/**
	 * Constructor.
	 * 
	 * @param abcast
	 *            object of a framework protocol based class, which ensure event
	 *            routing for this protocol.
	 */
	public FastAbcastImpl(Trigger abcast, FlowControl fc, PID myself) {
		logger.entering("FastAbcastImpl", "<constr>");
		this.trigger = abcast;
		this.flow_control = fc;
		this.myself = myself;
		aDelivered = new THashSet();
		aUndelivered = new TLinkedHashMap();
		maxIdProProc = new THashMap();
		abcastId = new AbcastMessageID(myself, 0);

		decisionToBroadcast = new TLinkedHashMap();
		processesCurrentK = new THashMap();
        messagesSendToProc = new THashMap();

		logger.exiting("FastAbcastImpl", "<constr>");
	}

	/**
	 * Handler for the <i>Init</i> event. </br> It sends the list of known
	 * processes to the lower layer allowing them to communicate with us
	 * 
	 * @param ev
	 *            <dl>
	 *            <dt> arg1 : Set[PID] </dt>
	 *            <dd> List of processes for broadcasting </dd>
	 *            </dl>
	 * 
	 * @throws GroupCommException          
	 * @throws IOException
	 * @throws ClassNotFoundException            
	 */
	public void handleInit(GroupCommEventArgs ev) throws GroupCommException,
			IOException, ClassNotFoundException {
		logger.entering("FastAbcastImpl", "handleInit");
		TList p = (TList) ev.removeFirst();

		LinkedList toTrigger = new LinkedList();

		if (initialized)
			throw new GroupCommException("FastAbcastImpl already initialized.");
		initialized = true;
		fc_key = flow_control.getFreshKey();
		optimizeFirstRound = false;
		coordNeedsEstimate = false;

		known = new TArrayList(p);
		others = new TArrayList();
		// Look for duplicate processes in the group
		for (int i = 0; i < known.size(); i++) {
			if (!myself.equals(known.get(i))){
				others.add(known.get(i));
                messagesSendToProc.put(known.get(i), new CompressedSet());
            }
			for (int j = i + 1; j < known.size(); j++)
				if (known.get(i).equals(known.get(j)))
					throw new GroupCommException("Process" + known.get(i)
							+ " appears more than once in the group.");
            
            // add entries to processCurrentK           
            processesCurrentK.put(known.get(i), new TLong(-1));
		}        
        
		// calculate majority excluding myself
		majority = this.known.size() / 2;

		// init maximum id of Adelivered message
		Iterator it = known.iterator();
		PID pid;
		while (it.hasNext()) {
			pid = (PID) it.next();
			maxIdProProc.put(pid, new TLong(-1));
		}

		// Init the coordinator
		coordinator = (PID) known.get(0);

		// start FD
		GroupCommEventArgs jrl = new GroupCommEventArgs();
		GroupCommEventArgs e1 = new GroupCommEventArgs();
		e1.addLast(new THashSet(p)); // Start
		e1.addLast(new THashSet()); // Stop
		toTrigger.addLast(new TriggerItem(Constants.STARTSTOPMONITOR, e1));

		// join-remove        
		jrl.addLast(new THashSet(p)); // join
		jrl.addLast(new THashSet()); // remove
		toTrigger.addLast(new TriggerItem(Constants.JOINREMOVELIST, jrl));
        
		// start the internal consensus
		if (known.size() > 1)
			this.incK(toTrigger, new TLinkedHashMap());
        
		proceedWithTrigger(toTrigger);
		logger.exiting("FastAbcastImpl", "handleInit");
	}

	/**
	 * The handler for the <i>Abcast</i> event. <br/> It broadcasts the message
	 * to all the processes described by the init event. <br/> It adds an
	 * Abcast-Id to the message.
	 * 
	 * @param ev
	 *            <dl>
	 *            <dt> arg1: GroupCommMessage </dt>
	 *            <dd> The message </dd>
	 *            </dl>
	 * @throws GroupCommException
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public void handleAbcast(GroupCommEventArgs ev) throws GroupCommException,
			IOException, ClassNotFoundException {
		logger.entering("FastAbcastImpl", "handleAbcast");

		LinkedList toTrigger = new LinkedList();

		// msg
		GroupCommMessage msg = (GroupCommMessage) ev.removeFirst();

		AbcastMessageID id = abcastId.nextId();

		// if we are the only process deliver m immediately
		if (known.size() == 1) {
			GroupCommEventArgs adeliver = new GroupCommEventArgs();
			adeliver.addLast(msg);
			adeliver.addLast(id.proc);
			trigger.trigger(Constants.ADELIVER, adeliver);

			maxIdProProc.put(id.proc, new TLong(id.id));
			return;
		}
      
		aUndelivered.put(id, msg);

		//Flow control
		//flow_control.alloc(fc_key, 1);
		nbMsgsSent++;
		if (nbMsgsSent >= max_locally_abcast)
			flow_control.block(fc_key);

		// Sends a new estimate to coordinator, if it explicetely asks for it
        // Insert a message in estimate (since it is empty) before sending a message
		if (coordNeedsEstimate && (!myself.equals(coordinator))) {
			coordNeedsEstimate = false;
            estimate.put(id, msg);
			sendEstimate(toTrigger);
		} else if (coordNeedsEstimate) { // If I am the coordinator and I wait for
			// additionnal Estimate. I take this new
			// message to abcast as a proposal
            estimate.put(id, msg);
			sendPropose(toTrigger);

			coordNeedsEstimate = false;
			processAck(toTrigger, k, round, null);
		}

		proceedWithTrigger(toTrigger);        

		logger.exiting("FastAbcastImpl", "handleAbcast");
	}

	/**
	 * The handler for the <i>Pt2PtDeliver</i> event. <br/> When we recieve a
	 * message from the Reliable communication layer, we have to resent the
	 * message to all the receipents, if it's the first time it arrives. That's
	 * the R-Broadcast part of the protocol. It launch a consensus too.
	 * 
	 * @param ev
	 *            <dl>
	 *            <dt> arg1: GroupCommMessage (id::m) </dt>
	 *            <dd> The message, with an id </dd>
	 *            <dt> arg2: PID </dt>
	 *            <dd> Source PID </dd>
	 *            </dl>
	 * @throws GroupCommException          
	 * @throws IOException
	 * @throws ClassNotFoundException            
	 */
	public void handlePt2PtDeliver(GroupCommEventArgs ev)
			throws GroupCommException, IOException, ClassNotFoundException {
		logger.entering("FastAbcastImpl", "handlePt2PtDeliver");

		LinkedList toTrigger = new LinkedList();
		GroupCommMessage m = (GroupCommMessage) ev.get(0);
		// m = <<k::type::payload>>
		long kmess = ((TLong) m.tunpack()).longValue();
		// m = <<type::payload>>

		PID source = (PID) ev.get(1);

		// Update table of known decision that have been taken by others
        updateOldDecisions(kmess, source);

		int type = ((TInteger) m.tunpack()).intValue();
		// m = <<payload>>
		switch (type) {
		case ABCAST_ESTIMATE:
			// m = <<r::estimate::lastupdated>>
			int rmess = ((TInteger) m.tunpack()).intValue();
			processEstimate(toTrigger, kmess, rmess, m);
			break;
		case ABCAST_PROPOSE:
			// m = <<r::propose>>
			rmess = ((TInteger) m.tunpack()).intValue();
			processPropose(toTrigger, kmess, rmess, m);
			break;
		case ABCAST_ACK:
			// m = <<r::ack>>
			rmess = ((TInteger) m.tunpack()).intValue();
			processAck(toTrigger, kmess, rmess, m);
			break;
		case ABCAST_ABORT:
			// m = <<r>>
			rmess = ((TInteger) m.tunpack()).intValue();
			processAbort(toTrigger, kmess, rmess);
			break;
		case ABCAST_COORDNEEDSESTIMATE:
			// m= <<r>>
			rmess = ((TInteger) m.tunpack()).intValue();
			processCoordNeedsEstimate(toTrigger, kmess, rmess);
			break;
		case ABCAST_RBCAST:
			processRBcast(toTrigger, kmess, m, source);
			break;
        case ABCAST_UPDATEK:
            break;
		default:
			throw new GroupCommException("FastAbcastIMpl : handlePt2Ptdeliver : "
					+ "Unknown message type: " + type);
		}

		proceedWithTrigger(toTrigger);
		logger.exiting("FastAbcastImpl", "handlePt2PtDeliver");
	}

	/**
	 * The handler for the <i>Suspect</i> event. <br/> When a 
	 * suspicion arrives, we send a nack to coordinator if 
	 * necessary (i.e., if we do not have already sent a ack) and
	 * go to the next round
	 * 
	 * @param ev
	 *            <dl>
	 *            <dt> arg1: TSet of supsected processes </dt>
	 *            </dl>
	 * @throws GroupCommException          
	 * @throws IOException
	 * @throws ClassNotFoundException            
	 */
	public void handleSuspect(GroupCommEventArgs ev) throws GroupCommException,
			IOException, ClassNotFoundException {
		logger.entering("FastAbcastImpl", "handleSuspect");
		LinkedList toTrigger = new LinkedList();
		suspected = (TSet) ev.get(0);

		processSuspicion(toTrigger, suspected);

		proceedWithTrigger(toTrigger);
		logger.exiting("FastAbcastImpl", "handleSuspect");
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
		return DefaultSerialization
				.unmarshall(DefaultSerialization.marshall(o));
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
	private void pushback(long k, int r, int type, GroupCommMessage m) {
		logger.entering("FastAbcastImpl", "pushback");
		TLong kObj = new TLong(k);
		TInteger rObj = new TInteger(r);
		TTreeMap mapK = (TTreeMap) pushedBack.get(kObj);
		if (mapK == null) {
			mapK = new TTreeMap();
			pushedBack.put(kObj, mapK);
		}

		// We already pushed back a decision for this
		// internal consensus => no more messages different
		// from a decision needs to be pushedback!
		if ((mapK.get(new TInteger(-1)) != null) && (r > -1))
			return;

		TLinkedList listR = (TLinkedList) mapK.get(rObj);
		if (listR == null) {
			listR = new TLinkedList();
			mapK.put(rObj, listR);
		}
		m.tpack(new TInteger(type));
		listR.addLast(m);

		// If we add a decision, delete all pushed back
		// messages for the same k
		if ((mapK.keySet().size() > 1) && (r == -1)) {
			mapK = new TTreeMap();
			mapK.put(rObj, listR);
			pushedBack.put(kObj, mapK);
		}
		logger.exiting("FastAbcastImpl", "pushback");
	}

	// Execute all the trigger in list of TriggerItem toTrigger
	private void proceedWithTrigger(LinkedList toTrigger) {
		while (!toTrigger.isEmpty()) {
			TriggerItem tItem = (TriggerItem) toTrigger.removeFirst();
			trigger.trigger(tItem.type, tItem.args);
		}
	}

	// Update Old decisions set
	private void updateOldDecisions(long kmess, PID source) {
        long initK = ((TLong) processesCurrentK.get(source)).longValue(); 
        
        if (kmess > initK) {
            processesCurrentK.put(source, new TLong(kmess));       
        
            // Retrieve the smallest K for which we need to Keep a decision
            long smallestK = Long.MAX_VALUE;
            Iterator it = processesCurrentK.keySet().iterator();
            while (it.hasNext()) {
                PID pid = (PID) it.next();
                long kPid = ((TLong) processesCurrentK.get(pid)).longValue();

                if (kPid < smallestK)
                    smallestK = kPid;
            }
            
            // Discard all decisions that comes earlier
            for (long kDecision=initK; kDecision<smallestK; kDecision++){
                decisionToBroadcast.remove(new TLong(kDecision));
            }
        }   
	}

	/**
	 * Increase K, i.e. start a new internal consensus 
	 * @throws GroupCommException          
	 * @throws IOException
	 * @throws ClassNotFoundException            
	 */
	private void incK(LinkedList toTrigger, TLinkedHashMap newEstimate)
			throws GroupCommException, IOException, ClassNotFoundException {
		logger.entering("FastAbcastImpl", "incK");
                
        // Update local variables
		this.k++;
		this.round = -1;
		this.timeStamp = -1;
		this.estimate = newEstimate;
        
        if ((this.k % UPDATEK_PERIOD) == 0)
            sendCurrentK(toTrigger);         

		this.nextRound(toTrigger);
		logger.exiting("FastAbcastImpl", "incK");
	}

	/**
	 * Start the new round of the consensus
	 * @throws GroupCommException          
	 * @throws IOException
	 * @throws ClassNotFoundException            
	 */
	private void nextRound(LinkedList toTrigger) throws GroupCommException,
			IOException, ClassNotFoundException {
		logger.entering("FastAbcastImpl", "nextRound");
		round++;
		if (round > 1000)
			System.err
					.println("WARNING: Consensus is taking too many rounds!!!");

		coordinator = (PID) known.get(round % known.size());
		coordNeedsEstimate = false;
		logger.log(Level.FINE,
				"New round: {0}. New coordinator: {1} for {2}th Consensus",
				new Object[] { new Integer(round), coordinator, new Long(k) });

		nbAck = 0;
		nbNack = 0;
		numEstimate = 0;

  		if (round == 0) {
            if (optimizeFirstRound){
			    // This is the first round and it is optimized!!
                if (!myself.equals(coordinator)) {
                    timeStamp = 0;
                    sendAck(toTrigger, true);
                    this.phase = 5;
                } else { // I am the coordinator
                    this.phase = 4;
                    processAck(toTrigger, k, round, null);
                }
            } else {
                // This is the first round and not optimized. 
                if (myself.equals(coordinator)) {
                    if (estimate.size() == 0) {
                        // If coordinator has empty estimate, it asks for estimate
                        logger.fine("No message in estimate, send coordNeedsEstimate");
                        coordNeedsEstimate = true;
                        numEstimate = majority;
                        this.phase = 2;
                        sendCoordNeedsEstimate(toTrigger);
                    } else {
                        // Coordinator has messages to propose, it takes them as proposal
                        timeStamp = round;
                        phase = 4;

                        sendPropose(toTrigger);
                        coordNeedsEstimate = false;
                        processAck(toTrigger, k, round, null);            
                    }
                } else {
                    // Other processes are in phase 3 as if they have sent an estimate
                    this.phase = 3;
                    if (suspected.contains(coordinator)) {
                        sendAck(toTrigger, false);
                        nextRound(toTrigger);
                    }
                }
            }           
		} else {            
			if (!myself.equals(coordinator)) {
				sendEstimate(toTrigger);
				this.phase = 3;

				if (suspected.contains(coordinator)) {
					sendAck(toTrigger, false);
					nextRound(toTrigger);
				}
			} else { // I am the coordinator
				this.phase = 2;
			}
		}

		// Treat pushedback messages
		while (!pushedBack.isEmpty()) {
			// Take message for phase kpushed
			long kpushed = ((TLong) pushedBack.firstKey()).longValue();
			if (kpushed > k) {
				// Messages for futur internal consensus, we keep it and quit
				// the loop
				logger.exiting("FastAbcastImpl", "nextRound");
				return;
			}

			// Remove all possible messages from consensus k
			TTreeMap mapK = (TTreeMap) pushedBack.remove(new TLong(kpushed));
			if (kpushed == k) {
				while (!mapK.isEmpty()) {
					int r = ((TInteger) mapK.firstKey()).intValue();
					if (r > round) {
						// Messages for future round, we keep it (thus, we have
						// to put the map
						// again in pushedBack) and quit the loop
						pushedBack.put(new TLong(k), mapK);
						logger.exiting("ConsensusExecution", "nextRound");
						return;
					}
					// We remove all messages of round r from the pushed back
					// queue
					TLinkedList l = (TLinkedList) mapK.remove(new TInteger(r));
					if ((r < round) && (r != -1)) { // Do not discard decision
						// messages
						logger
								.log(
										Level.FINE,
										"Discarding old messages {0} in pushedBack for round {1} and consensus {2}",
										new Object[] { l, new Integer(r),
												new Long(k) });
					} else { // r == round or r == -1
						logger
								.log(
										Level.FINE,
										"Processing messages {0} in pushedBack for round {1} and consensus {2}",
										new Object[] { l, new Integer(r),
												new Long(k) });
						Iterator it = l.iterator();
						while (it.hasNext()) {
							GroupCommMessage m = (GroupCommMessage) it.next();
							int type = ((TInteger) m.tunpack()).intValue();
							switch (type) {
							case ABCAST_ESTIMATE:
								processEstimate(toTrigger, kpushed, r, m);
								break;
							case ABCAST_PROPOSE:
								processPropose(toTrigger, kpushed, r, m);
								break;
							case ABCAST_ACK:
								processAck(toTrigger, kpushed, r, m);
								break;
							case ABCAST_ABORT:
								processAbort(toTrigger, kpushed, r);
								break;
							case ABCAST_COORDNEEDSESTIMATE:
								processCoordNeedsEstimate(toTrigger, kpushed, r);
								break;
							case ABCAST_RBCAST:
								PID source = (PID) m.removeFirst();
								processRBcast(toTrigger, kpushed, m, source);
								break;
							default:
								throw new GroupCommException(
										"Weird message type " + type
												+ " in pushed back set!");
							}
						}
					}
				}
			}
		}
 		logger.exiting("FastAbcastImpl", "nextRound");
	}

	/**
	 * A new estimation has just arrived. This method processes it.
	 * 
	 * @param kmess
	 *            The consensus of the estimate
	 * @param rmess
	 *            The round of the estimate
	 * @param m
	 *            The message containing the estimation.
	 * @throws GroupCommException 
	 * @throws IOException
	 * @throws ClassNotFoundException            
	 */
	private void processEstimate(LinkedList toTrigger, long kmess, int rmess,
			GroupCommMessage m) throws GroupCommException, IOException,
			ClassNotFoundException {
		logger.entering("FastAbcastImpl", "processEstimate");
		
		// Push back message for future
		if (((rmess > round) && (kmess == k)) || (kmess > k))
			pushback(kmess, rmess, ABCAST_ESTIMATE, m.cloneGroupCommMessage()); 

		// m = <<estimate::lastUpdated>>
		TLinkedHashMap estim = (TLinkedHashMap) m.tunpack();
		int lastUpd = ((TInteger) m.tunpack()).intValue();
		// Add the unknown message to unordered 
		// ... even if it is a past or future message
		Iterator i = estim.keySet().iterator();
		while (i.hasNext()) {
			AbcastMessageID t = (AbcastMessageID) i.next();

			long maxId = ((TLong) maxIdProProc.get(t.proc)).longValue();
			if ((!aUndelivered.containsKey(t)) && (!aDelivered.contains(t))
					&& (maxId < t.id))
				aUndelivered.put(t, estim.get(t));
		}

		//Flow control
        if (nbMsgsSent >= max_locally_abcast)
			flow_control.block(fc_key);

		if ((rmess != round) || (kmess != k))
			return; // discard message for past and future

		// I am not the coordinator => BAD!
		if (!myself.equals(coordinator))
			throw new GroupCommException(
					"Unexpected message received in round " + this.round
							+ "by non-coordinator: " + m);

		if (phase != 2) {
			// ignore
			logger
					.log(
							Level.FINE,
							"Discarding late ESTIMATE (phase != 2): {0}. Current round {1}, consensus {2}",
							new Object[] { m, new Integer(this.round),
									new Long(this.k) });                        
			logger.exiting("ConsensusExecution", "processEstimate");
			return;
		}

		// update the estimate if necessary
		// either the timestamp is bigger than the last one
		// either the timestamp is equal but the new estimate is not empty!!
		if ((lastUpd > timeStamp)
				|| ((lastUpd == timeStamp) && (estimate.keySet().size() == 0) && (estim
						.keySet().size() != 0))) {
			logger.log(Level.FINE,
					"Updating ESTIMATE with {0}. Timestamp: {1}", new Object[] {
							estim, new Integer(lastUpd) });
			timeStamp = lastUpd;
			estimate = estim;
		}

		numEstimate++;
		if (numEstimate >= majority) {
			// If estimate is empty, then take aUndelivered as an estimate
			if (estimate.size() == 0) {
				estimate = new TLinkedHashMap();
				Iterator it = aUndelivered.keySet().iterator();
                int sizeEstimate = Math.max(MSGS_PER_CONSENSUS/2, aUndelivered.size()/2);
				while (it.hasNext() && (estimate.size() < sizeEstimate)) {
					AbcastMessageID id = (AbcastMessageID) it.next();
					estimate.put(id, aUndelivered.get(id));
				}
			}

			// If estimate contains messages, send a proposal else
			// send coordNeedsEstimate
			if (estimate.size() > 0) {
				// We received enough estimations: we can propose a value to all
				// other processes.
				// Proceed to phase 4
				logger
						.fine("Got majority of ESTIMATEs. Changing phase to 4. Sending proposal");
				timeStamp = round;// BUG FIXED BY RACHELLE FUZZATI
				phase = 4;

				sendPropose(toTrigger);
				coordNeedsEstimate = false;
				processAck(toTrigger, k, round, null);
				// just to check if a NACK has already arrived
			} else {
				if (nbNack == 0) {
				    if (!coordNeedsEstimate) {
                        // ASSERT: aUndelivered is empty and no coordNeedsEtimate already sent
                        logger
                                .fine("No message in estimate, send coordNeedsEstimate");
                        coordNeedsEstimate = true;
                        sendCoordNeedsEstimate(toTrigger);
                    }
                } else {
					logger.fine("Got a NACK. Going to next round");
					sendAbort(toTrigger);
					nextRound(toTrigger);
				}
			}
		}

		logger.exiting("FastAbcastImpl", "processEstimate");
	}

	/**
	 * A proposal has just arrived from the coordinator.
	 * 
	 * @param kmess
	 *            The consensus of the estimate
	 * @param rmess
	 *            The round of the estimate
	 * @param m
	 *            The message containing the proposal.
	 * @throws GroupCommException
	 */
	private void processPropose(LinkedList toTrigger, long kmess, int rmess,
			GroupCommMessage m) throws GroupCommException {
		logger.entering("FastAbcastImpl", "processPropose");
        
		if (((rmess > round) && (kmess == k)) || (kmess > k))
			pushback(kmess, rmess, ABCAST_PROPOSE, m); // Push back message for
		// future
		if ((rmess != round) || (kmess != k))
			return; // discard message for past and future

		// I am the coordinator => BAD!
		if (myself.equals(coordinator)) {
			throw new GroupCommException(
					"Unexpected message received in round " + round
							+ "by coordinator: " + m);
		}

		estimate = (TLinkedHashMap) m.tunpack();
		timeStamp = round;
		logger
				.log(
						Level.FINE,
						"Received a PROPOSE: {0} in round {1} in consensus {2}. Setting it as estimate.",
						new Object[] { estimate, new Integer(round),
								new Long(k) });
		coordNeedsEstimate = false;
		// Send ack to the coordinator
		sendAck(toTrigger, true);
		this.phase = 5;
		// Nothing to do this round, wait decision, abort or coordinator
		// suspicion

		logger.exiting("FastAbcastImpl", "processPropose");
	}

	/**
	 * An acknowledgement message (Ack or Nack) has just arrived.
	 * 
	 * @param kmess
	 *            The consensus of the estimate
	 * @param rmess
	 *            The round of the estimate
	 * @param m
	 *            The message containing the acknowledgement.
	 * @throws GroupCommException          
	 * @throws IOException
	 * @throws ClassNotFoundException            
	 */
	private void processAck(LinkedList toTrigger, long kmess, int rmess,
			GroupCommMessage m) throws GroupCommException, IOException,
			ClassNotFoundException {
		logger.entering("FastAbcastImpl", "processAck");
        
		if (((rmess > round) && (kmess == k)) || (kmess > k)) {
			pushback(kmess, rmess, ABCAST_ACK, m); // Push back message for
			return;                                // future
		} 
        
		logger.log(Level.FINE,
						"About to process an ACK: {0} in round {1} consensus {2}. Received acks/nacks {3}/{4}",
						new Object[] { m, new Integer(round), new Long(k),
								new Integer(nbAck), new Integer(nbNack) });
		if (m != null) {
			boolean ack = ((TBoolean) m.tunpack()).booleanValue();
			if ((rmess == round) && (kmess == k)) {
				// Do not take into account ack from the past!
				// I am not the coordinator => BAD!
				if (!myself.equals(coordinator)) {
					throw new GroupCommException(
							"Unexpected message received in round " + round
									+ "by non-coordinator: " + m);
				}

				// Increment number of {ack,nack} received.
				if (ack) {
					nbAck++;
				} else {
					nbNack++;
				}
			}

			// Add messages that are not already ordered in unordered
			TLinkedHashMap msgs = (TLinkedHashMap) m.tunpack();
            Iterator it = msgs.keySet().iterator();
            while (it.hasNext()) {
                AbcastMessageID msgID = (AbcastMessageID) it.next();
                long maxId = ((TLong) maxIdProProc.get(msgID.proc)).longValue();
                if ((!aUndelivered.containsKey(msgID))
                        && (!aDelivered.contains(msgID)) && (msgID.id > maxId)) {
                    aUndelivered.put(msgID, msgs.get(msgID));
                }
            }

			// Flow Control
			//Flow control
            if (nbMsgsSent >= max_locally_abcast)
				flow_control.block(fc_key);
		}

		if ((rmess == round) && (kmess == k)) {
			// Again, do not take into account ack from the past

			// If coordNeedsEstimate is true, the other condition can be true only
			// if we deliver a nack. If we deliver a nack and coordNeedsEstimate is true
			// we have to abort current round

			// We take into account messages that arrive in Phase 2 (NACKs).
			// However, the process should not pass on to the next round
			// until it completes Phase 2.
			if ((phase == 2) && (numEstimate < majority)) {
				logger.fine("Still in phase 2 and no proposal sent");
				logger.exiting("FastAbcastImpl", "processAck");
				return;
			}

			if (nbNack == 0) {

				if (nbAck == majority) {
					// We got a majority of positive Ack's. Thefore we can
					// decide
					logger
							.fine("Got a majority of positive Ack's. Broadcasting decision");
					broadcastDecision(toTrigger);
				}
			} else {
				// Somebody suspected us and sent us a negative acknowledgement.
				// We might not be able to decide in this round
				// We proceed to next round
				logger.fine("Got a NACK. Going to next round");
				sendAbort(toTrigger);
				nextRound(toTrigger);
			}
		} else {
		    
        }
        
		logger.exiting("FastAbcastImpl", "processAck");
	}

	/**
	 * An abort message has just arrived.
	 * 
	 * @param kmess
	 *            The consensus of the estimate
	 * @param rmess
	 *            The round of the estimate
	 * @throws GroupCommException          
	 * @throws IOException
	 * @throws ClassNotFoundException            
	 */
	private void processAbort(LinkedList toTrigger, long kmess, int rmess)
			throws GroupCommException, IOException, ClassNotFoundException {
		logger.entering("FastAbcastImpl", "processAbort");

		if (((rmess > round) && (kmess == k)) || (kmess > k))
			pushback(kmess, rmess, ABCAST_ABORT, new GroupCommMessage()); // Push                                                             // message
		// for
		// future
		if ((rmess != round) || (kmess != k))
			return; // discard message for past and future

		// I am the coordinator => BAD!
		if (myself.equals(coordinator)) {
			throw new GroupCommException(
					"Unexpected message received in round " + round
							+ " in consensus " + k + " by coordinator: "
							+ " Abort");
		}

		nextRound(toTrigger);
		logger.exiting("FastAbcastImpl", "processAbort");
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
	 * @throws IOException
	 * @throws ClassNotFoundException            
	 */
	private void processSuspicion(LinkedList toTrigger, TSet suspected)
			throws GroupCommException, IOException, ClassNotFoundException {
		logger.entering("FastAbcastImpl", "processSuspicion");
		this.suspected = suspected;

		// Re-Send Old Decision if the sender is suspected
		Iterator it = decisionToBroadcast.keySet().iterator();
		while (it.hasNext()) {
			TLong kObj = (TLong) it.next();
			GroupCommMessage decisionK = (GroupCommMessage) decisionToBroadcast
					.get(kObj);

			if (suspected.contains(decisionK.getFirst())) {
				PID source = (PID) decisionK.tunpack();
				// decisionK = <<decision::newPropose::processCurrentK>>
				decisionK.tpack(new TInteger(ABCAST_RBCAST));
				// m = <<CONS_BROADCAST::decision::newPropose::processCurrentK>>
				decisionK.tpack(kObj);
				// m = <<k::CONS_BROADCAST::decision::newPropose::processCurrentK>>

				//Broadcast decision to others except source
				for (int i = 0; i < others.size(); i++) {
					if (!source.equals(others.get(i)))
						triggerSend(toTrigger, decisionK
								.cloneGroupCommMessage(), (PID) others.get(i));
				}

				decisionToBroadcast.remove(kObj);
			}
		}

		if (!myself.equals(coordinator) && suspected.contains(coordinator)) {
			// Send a NACK only if no ack were already send
			if (this.phase != 5)
				sendAck(toTrigger, false);
			// Proceed to next round
			nextRound(toTrigger);
		}
		logger.exiting("FastAbcastImpl", "processSuspicion");
	}

	/**
	 *  A message CoordNeedsEstimate has just arrived
	 *  
	 * @param kmess
	 *            The consensus of the estimate
	 * @param rmess
	 *            The round of the estimate
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	private void processCoordNeedsEstimate(LinkedList toTrigger, long kmess,
			int rmess) throws IOException, ClassNotFoundException {
		if (((rmess > round) && (kmess == k)) || (kmess > k))
			pushback(kmess, rmess, ABCAST_COORDNEEDSESTIMATE,
					new GroupCommMessage());
		else {
			if (!aUndelivered.isEmpty()) {
				estimate = new TLinkedHashMap();
				Iterator it = aUndelivered.keySet().iterator();
                int sizeEstimate = Math.max(MSGS_PER_CONSENSUS/2, aUndelivered.size()/2);
				while (it.hasNext() && (estimate.size() < sizeEstimate)) {
					AbcastMessageID id = (AbcastMessageID) it.next();
					estimate.put(id, aUndelivered.get(id));
				}

				sendEstimate(toTrigger);
			} else if (this.phase != 5) {
				coordNeedsEstimate = true;
			}
		}
	}

	/**
	 * A decision has just arrived.
	 * 
	 * @param kmess
	 *            The consensus of the estimate
	 * @param m
	 *            The message containing the decision.
	 * @throws GroupCommException
	 */
	private void processRBcast(LinkedList toTrigger, long kmess,
			GroupCommMessage m, PID source) throws GroupCommException,
			IOException, ClassNotFoundException {
		logger.entering("FastAbcastImpl", "processRBcast");
        
		if (kmess > k) {
			m.addFirst(source);
			pushback(kmess, -1, ABCAST_RBCAST, m); // Push back message for
			return; // future
		} else if (kmess < k) {
			if ((kmess == k - 1) && (round == 0) && (this.phase != 5)
					&& (!optimizeFirstRound)) {
				// It is possible that we deliver a decision without newEstimate
				// while we should. If this is the case and we are in 1st round, we
				// may block!!!
				// Thus, send an ack to coordinator
				m.tunpack();
				// this messages are already delivered
				TLinkedHashMap newEstimate = (TLinkedHashMap) m.tunpack();
				if (newEstimate.keySet().size() != 0) {
					// I am the coordinator => BAD!
					if (myself.equals(coordinator)) {
						throw new GroupCommException(
								"Unexpected message received in round "
										+ round
										+ " in consensus "
										+ k
										+ " by coordinator: "
										+ " Second Decision with newEtimate != null");
					}

					optimizeFirstRound = true;
					estimate = newEstimate;
					timeStamp = 0;

					// Send a ack to coordinator
					sendAck(toTrigger, true);
					this.phase = 5;
				}
			}
			return;
		}
        
		// Keep the decision in the memory if it needs to be resend
		// If I am the process who take the decision, just Send decision
		// to everybody
		if ((!source.equals(myself)) && (!suspected.contains(source))) {
            GroupCommMessage decisionMessage = (GroupCommMessage) deepClone(m);
			decisionMessage.tpack(source);
			decisionToBroadcast.put(new TLong(k), decisionMessage);
		} else {
			//Send Message to others
			m.tpack(new TInteger(ABCAST_RBCAST));
			// m = <<CONS_BROADCAST::decision::newPropose:>>
			m.tpack(new TLong(k));
			// m = <<k::CONS_BROADCAST::decision::newPropose>>
			//Broadcast decision to others
			for (int i = 0; i < others.size(); i++) {
				if (!source.equals(others.get(i)))
					triggerSend(toTrigger, m.cloneGroupCommMessage(),
                            (PID) others.get(i));
			}
            m.tunpack();
            m.tunpack();
        }
		
        //Feed-back for flow-control
        TLinkedHashMap toBeDelivered = (TLinkedHashMap) deepClone(m.tunpack());
        if(toBeDelivered.size() < MSGS_PER_CONSENSUS) max_locally_abcast = Math.min(MSGS_PER_CONSENSUS * 2, max_locally_abcast + 1);
        if(toBeDelivered.size() > MSGS_PER_CONSENSUS) max_locally_abcast = Math.max(MIN_LOCALLY_ABCAST, max_locally_abcast - 1);
         
        // Adeliver messages contained in decision
		logger.log(
				Level.FINE,
				"Consensus {0}:\n\t size of decision {1}; aUndelivered size = {2}; nbMsgsSent = {3}",
				new Object[] { new Long(k), 
						new Integer(toBeDelivered.size()),
						new Integer(aUndelivered.size()),
						new Integer(nbMsgsSent)});
		while (!toBeDelivered.isEmpty()) {
			AbcastMessageID id = (AbcastMessageID) toBeDelivered.keySet().iterator().next(); //firstKey();
			GroupCommMessage msg = (GroupCommMessage) toBeDelivered.remove(id);
			// delivered = msg.cloneGroupCommMessage();
			long maxId = ((TLong) maxIdProProc.get(id.proc)).longValue();
			if (!aDelivered.contains(id) && id.id > maxId) {
				// Remove the id from aUndelivered
				aUndelivered.remove(id);
				// add it in aDelivered
				aDelivered.add(id);
				// Adeliver message
				GroupCommEventArgs adeliver = new GroupCommEventArgs();
				adeliver.addLast(msg);
				adeliver.addLast(id.proc);
				toTrigger.addLast(new TriggerItem(Constants.ADELIVER, adeliver));

				//Flow control
				if (id.proc.equals(myself)) {
					//flow_control.free(fc_key, 1);
					nbMsgsSent--;
                    
                    // Add the messages to the messages sent
                    int sizeOthers = others.size();
                    for (int i = 0; i<sizeOthers; i++)
                        ((CompressedSet) messagesSendToProc.get(others.get(i))).add(id);
				}

				// update the highest aDelivered table
				AbcastMessageID newID = new AbcastMessageID(id.proc, maxId + 1);
				while (aDelivered.contains(newID)) {
					aDelivered.remove(newID);
					maxId++;
					newID.id++;
				}
				maxIdProProc.put(id.proc, new TLong(maxId));
			}
		}
       
		// Flow Control
		// Flow control
        if (nbMsgsSent < max_locally_abcast)
			flow_control.release(fc_key);

		// Start next internal consensus
		TLinkedHashMap newEstimate = (TLinkedHashMap) m.tunpack();
		logger.log(
				Level.FINE,
				"New proposal length = {0} \t aUndel = {1} \t nbMsgsSent = {2}",
				new Object[] { new Integer(newEstimate.size()),
						new Integer(aUndelivered.size()),
						new Integer(nbMsgsSent)});

		if (newEstimate.keySet().size() != 0) {
			optimizeFirstRound = true;
		} else {
			optimizeFirstRound = false;
			newEstimate = new TLinkedHashMap();
			Iterator it = aUndelivered.keySet().iterator();
            int sizeEstimate = Math.max(MSGS_PER_CONSENSUS/2, aUndelivered.size()/2);
			while (it.hasNext() && (newEstimate.size() < sizeEstimate)) {
				AbcastMessageID id = (AbcastMessageID) it.next();
				newEstimate.put(id, aUndelivered.get(id));
			}
		}

		// Update the current K
        processesCurrentK.put(myself, new TLong(this.k));
        incK(toTrigger, newEstimate);
         
		logger.exiting("FastAbcastImpl", "processRBcast");
	}

	/**
	 * Send a message with the current estimate to the current coordinator (the
	 * local process mustn't be the coordinator)
	 * 
	 */
	private void sendEstimate(LinkedList toTrigger) throws IOException,
			ClassNotFoundException {

		GroupCommMessage estimateMessage = new GroupCommMessage();
		// m = <<>>
        
        // Remember which messages where sent to the coordinator
        // To avoid sending them twice
        Iterator it = estimate.keySet().iterator();
        while (it.hasNext()) {
            AbcastMessageID aID = (AbcastMessageID) it.next();
            ((CompressedSet) messagesSendToProc.get(coordinator)).add(aID);
        }
        
		estimateMessage.tpack(new TInteger(timeStamp));
		// m = <<timeStamp>>
		estimateMessage.tpack(deepClone(estimate));
		// m = <<estimate::lastUpdated>>
		estimateMessage.tpack(new TInteger(round));
		// m = <<round::estimate::lastUpdated>>
		estimateMessage.tpack(new TInteger(ABCAST_ESTIMATE));
		// m = <<CONS_ESTIMATE::round::estimate::lastUpdated>>
		estimateMessage.tpack(new TLong(k));
		// m = <<k::CONS_ESTIMATE::round::estimate::lastUpdated>>
		triggerSend(toTrigger, estimateMessage, coordinator);
	}

	/**
	 * Send a message with the current estimate as the coordinator's proposal
	 * (the local process must be the coordinator)
	 * 
	 */
	private void sendPropose(LinkedList toTrigger) throws IOException,
			ClassNotFoundException {
		GroupCommMessage proposeMessage = new GroupCommMessage();
		// m = <<>>
		proposeMessage.tpack(deepClone(estimate));
		// m = <<estimate>>
		proposeMessage.tpack(new TInteger(round));
		// m = <<round::estimate>>
		proposeMessage.tpack(new TInteger(ABCAST_PROPOSE));
		// m = <<CONS_PROPOSE::round::estimate>>
		proposeMessage.tpack(new TLong(k));
		// m = <<k::CONS_PROPOSE::round::estimate>>
		triggerSend(toTrigger, proposeMessage, others);
	}

	/**
	 * Send a message to make other processes abort current round 
	 */
	private void sendAbort(LinkedList toTrigger) {
		GroupCommMessage abortMessage = new GroupCommMessage();
		// m = <<>>
		abortMessage.tpack(new TInteger(round));
		// m = <<round>>
		abortMessage.tpack(new TInteger(ABCAST_ABORT));
		// m = <<CONS_ABORT::round>>
		abortMessage.tpack(new TLong(k));
		// m = <<k::CONS_ABORT::round>>
		triggerSend(toTrigger, abortMessage, others);
	}

    /**
     * Send a message indicating current K
     */
    private void sendCurrentK(LinkedList toTrigger) {
        GroupCommMessage updateKMessage = new GroupCommMessage();
        // m = <<>>
        updateKMessage.tpack(new TInteger(ABCAST_UPDATEK));
        // m = <<ABCAST_UPDATEK>>
        updateKMessage.tpack(new TLong(k));
        // m = <<k::ABCAST_UPDATEK>>
        triggerSend(toTrigger, updateKMessage, others);
    }
    
	/**
	 * Send a message with the current estimate as the coordinator's proposal
	 * (the local process must be the coordinator)
	 * 
	 */
	private void sendCoordNeedsEstimate(LinkedList toTrigger) {
		GroupCommMessage abortMessage = new GroupCommMessage();
		// m = <<>>
		abortMessage.tpack(new TInteger(round));
		// m = <<round>>
		abortMessage.tpack(new TInteger(ABCAST_COORDNEEDSESTIMATE));
		// m = <<CONS_ABORT::round>>
		abortMessage.tpack(new TLong(k));
		// m = <<k::CONS_ABORT::round>>
		triggerSend(toTrigger, abortMessage, others);
	}

	/**
	 * Send an acknowledgement message to the current coordinator. It will be an
	 * Ack or a Nack depending on the parameter
	 * 
	 * @param ack
	 *            If it is true, an Ack is sent. Otherwise, a Nack is sent.
	 */
	private void sendAck(LinkedList toTrigger, boolean ack) {
		GroupCommMessage ackMessage = new GroupCommMessage();
        TLinkedHashMap messagesToTransmit = new TLinkedHashMap();
        
        int sizeAck = Math.max(MAX_MESSAGES_PER_ACK, (aUndelivered.size())/2);
        int nbMess = 0;
        
        // If we send an ack, piggyback all messages in aUndelivered
        // that are not already known by the coordinator        
        Iterator i = aUndelivered.keySet().iterator();
        while ((i.hasNext()) && (nbMess < sizeAck)){
            AbcastMessageID key = (AbcastMessageID) i.next();
            
            if ((ack)
                    && (key.proc.equals(myself))
                    && (!estimate.containsKey(key))
                    && (!aDelivered.contains(key))
                    && (!((CompressedSet) messagesSendToProc.get(coordinator))
                            .contains(key))) {
                messagesToTransmit.put(key, aUndelivered.get(key));
                ((CompressedSet) messagesSendToProc.get(coordinator)).add(key);
                //nbMess++;
            }
        }

        // m = <<>>
		ackMessage.tpack(messagesToTransmit);
		// m = <<unordered/estimate>>
		ackMessage.tpack(new TBoolean(ack));
		// m = <<NACK::unordered/estimate>>
		ackMessage.tpack(new TInteger(round));
		// m = <<round::NACK::unordered/estimate>>
		ackMessage.tpack(new TInteger(ABCAST_ACK));
		// m = <<CONS_ACK::round::NACK::unordered/estimate>>
		ackMessage.tpack(new TLong(k));
        
		// m = <<k::CONS_ACK::round::NACK::unordered/estimate>>
		triggerSend(toTrigger, ackMessage, coordinator);
	}

	/**
	 * Sends a decision message to all processes
	 * @throws GroupCommException          
	 * @throws IOException
	 * @throws ClassNotFoundException            
	 */
	private void broadcastDecision(LinkedList toTrigger)
			throws GroupCommException, IOException, ClassNotFoundException {
		GroupCommMessage decisionMessage = new GroupCommMessage();

		// m = <<>>
		TLinkedHashMap newPropose = new TLinkedHashMap();
		if (coordinator.equals(known.get(0))) {
			Iterator i = aUndelivered.keySet().iterator();
            int sizeEstimate = Math.max(MSGS_PER_CONSENSUS/2, aUndelivered.size()/2);
			while (i.hasNext() && (newPropose.size() < sizeEstimate)) {
				AbcastMessageID id = (AbcastMessageID) i.next();
				long maxId = ((TLong) maxIdProProc.get(id.proc)).longValue();
				if ((!estimate.containsKey(id)) && (!aDelivered.contains(id))
						&& (id.id > maxId))
					newPropose.put(id, aUndelivered.get(id));
			}
		}

		decisionMessage.tpack(newPropose);
		// m = <<newPropose>>
		decisionMessage.tpack(estimate);
		// m = <<decision::newPropose>>

		// Deliver decision to myself
		// and send it to others
		processRBcast(toTrigger, k, decisionMessage, myself);
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
	private void triggerSend(LinkedList toTrigger, GroupCommMessage m, TList g) {
		for (int i = 0; i < g.size(); i++) {
			triggerSend(toTrigger, m.cloneGroupCommMessage(), (PID) g.get(i));
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
	private void triggerSend(LinkedList toTrigger, GroupCommMessage m, PID p) {
		GroupCommEventArgs pt2ptSend = new GroupCommEventArgs();
		pt2ptSend.addLast(m);
		pt2ptSend.addLast(p);
		pt2ptSend.addLast(new TBoolean(false)); // not promisc
		logger.log(Level.FINE, "Sending Pt2Pt message {0} to {1}",
				new Object[] { m, p });
		toTrigger.addLast(new TriggerItem(Constants.PT2PTSEND, pt2ptSend));
	}

	/**
	 * Used for debug
	 *
	 * @param out The output stream used for showing infos
	 */

	public void dump(OutputStream out) {
		PrintStream err = new PrintStream(out);
		err.println("======== FastAbcastImpl: dump =======");
		err.println(" Initialized: " + String.valueOf(initialized));
		err.println(" Current internal consensus: " + k);
        err.println(" Current estimate: "+ estimate);
		err.println(" Last AbcastMessage id used:\n\t" + abcastId);
		err.println(" Number of known undelivered messages: "
				+ aUndelivered.size());
		err.println(" Known processes: size: " + known.size());
		Iterator it = known.iterator();
		PID pid;
		while (it.hasNext()) {
			pid = (PID) it.next();
			err.println("\t" + pid.toString());
		}
		err.println(" A-Undelivered messages:");
		err.println("   " + aUndelivered.toString());
		err.println(" A-Delivered messages IDs:");
		it = aDelivered.iterator();
		AbcastMessageID id;
		while (it.hasNext()) {
			id = (AbcastMessageID) it.next();
			err.println("\t" + id);
		}
		err.println("   and all message with id <= ");
		err.println("\t" + maxIdProProc.toString());
        err.println(" Decision that are not surely delivered by everyone: " );
        err.println("  " + decisionToBroadcast);
        err.println(" Last decision taken by processes: ");
        err.println("  " + processesCurrentK );
        err.println(" Messages Pushback: ");
        err.println("  " + pushedBack);
		err.println("==================================");
	}
}
