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
// ###############################
// Projet de semestre    I&C - LSR
// Jean Vaucher      D�cembre 2002
// ###############################

package groupcomm.common.abcast;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import uka.transport.Transportable;
import framework.Constants;
import framework.GroupCommEventArgs;
import framework.GroupCommException;
import framework.GroupCommMessage;
import framework.PID;
import framework.libraries.FlowControl;
import framework.libraries.Timer;
import framework.libraries.Trigger;
import framework.libraries.serialization.TArrayList;
import framework.libraries.serialization.TBoolean;
import framework.libraries.serialization.TCollection;
import framework.libraries.serialization.THashMap;
import framework.libraries.serialization.THashSet;
import framework.libraries.serialization.TLinkedHashMap;
import framework.libraries.serialization.TList;
import framework.libraries.serialization.TLong;
import framework.libraries.serialization.TMap;
import framework.libraries.serialization.TSet;

/**
 * <b> This class implements the common code for algorithm abcast. </b>
 * <hr>
 * <b> Events: 
 * <dt> <i>Init</i>           </dt> <dd> Initializes the abcast layer </dd>
 * <dt> <i>Abcast</i>         </dt> <dd> Send a Broadcast message, with the abcast algorithm </dd>
 * <dt> <i>Pt2PtDeliver</i>   </dt> <dd> Happend when a message is received by the lower layers </dd>
 * <dt> <i>Decide</i>         </dt> <dd> Happend when consensus has decided </dd>
 * </dl>
 */
public class AbcastImpl {
    private PID myself;
	// Initialized ?
    private boolean initialized = false;
    // Trigger class for events routing
    private Trigger abcast;

    // Variables for flow control
    private FlowControl flow_control;
    private int fc_key;
    private int nbMsgsSent = 0;

    // Variables for the timer (Sergio - 8 mar 2006)
    private Timer timer;
    private boolean timerOn = false;
    private final int INACTIVITY_TIMEOUT = 5 * 1000;//10 * 1000; // seconds

    // Known processes, to send the broadcast messages : Contains PID
    private TArrayList known;
    // A-delivered messages : Set (AbcastMessageID)
    private TSet aDelivered;
    // A-Undelivered messages : FIFO-order Map (AbcastMessageID -> GroupCommMessage m)
    private TLinkedHashMap aUndelivered;
    // Bound for old A-delivered messages : Table (PID -> Integer) 
    private TMap maxIdProProc;
    // id for consensus requests
    private long k;
    // id for highest consensus ever heard from
    private long gossipK;
    // Abcast message current id
    private AbcastMessageID abcastId;
    // Is a consensus running ?
    private boolean consensusStarted;

    public static final int MIN_LOCALLY_ABCAST = 1;
    //public static final int MAX_UNDELIVERED = 8;
    //public static final int MAX_PROPOSE = 4;
    public static final int MSGS_PER_CONSENSUS = 4;
    private int max_locally_abcast = MSGS_PER_CONSENSUS;

    private static final Logger logger =
	Logger.getLogger(AbcastImpl.class.getName());

    public static class TriggerItem{
        public int type;
        public GroupCommEventArgs args;

        public TriggerItem(int type, GroupCommEventArgs args){
            this.type = type;
            this.args = args;
	}
    }

    /**
     * Constructor.
     *
     * @param abcast  object of a framework protocol based class, which ensure event routing for this protocol.
     */
    public AbcastImpl(Trigger abcast, FlowControl fc, Timer t, PID myself) {
	logger.entering("AbcastImpl","<constr>");
	this.abcast = abcast;
	this.flow_control = fc;
    this.myself = myself;
    this.timer = t;
	aDelivered = new THashSet();
	aUndelivered = new TLinkedHashMap();
	maxIdProProc = new THashMap();
	abcastId = new AbcastMessageID(myself, 0);
	logger.exiting("AbcastImpl","<constr>");
    }

    /**
     * Handler for the <i>Init</i> event. </br>
     * It sends the list of known processes to the lower layer allowing them to communicate with us
     *
     * @param ev <dl>
     *              <dt> arg1 : Set[PID] </dt> <dd> List of processes for broadcasting </dd>
     *           </dl>
     *
     * @exception InitException An init event has already been received.
     */
    public void handleInit(GroupCommEventArgs ev) throws GroupCommException{
	logger.entering("AbcastImpl","handleInit");
	TList p = (TList)ev.removeFirst(); 
	
	if (initialized)
	    throw new GroupCommException("AbcastImpl already initialized.");
	initialized = true;
    fc_key = flow_control.getFreshKey();
	k = 1;
    gossipK = 1;
	consensusStarted = false;
    // timer
    //timer.schedule(new TLong(k), false, INACTIVITY_TIMEOUT);
    //timerOn = true;

	known = new TArrayList(p);
	//Look for duplicate processes in the group
	for(int i = 0; i < known.size() ; i++)
	    for(int j = i+1; j < known.size(); j++)
		if( known.get(i).equals(known.get(j)) )
		    throw new GroupCommException("Process" + known.get(i) +
					    " appears more than once in the group.");
	
	// init maximum id of Adelivered message
	Iterator it = known.iterator();
	PID pid;
	while (it.hasNext()) {
	    pid = (PID)it.next();
	    maxIdProProc.put(pid, new TLong(-1));
	}

	// join-remove
	GroupCommEventArgs jrl = new GroupCommEventArgs();
	jrl.addLast(new THashSet(p)); // join
	jrl.addLast(new THashSet()); // remove
	abcast.trigger(Constants.JOINREMOVELIST, jrl);
	logger.exiting("AbcastImpl","handleInit");
    }

    /**
     * The handler for the <i>Abcast</i> event. <br/>
     * It broadcasts the message to all the processes described by the init event. <br/>
     * It adds an Abcast-Id to the message.
     *
     * @param ev <dl>
     *               <dt> arg1: GroupCommMessage </dt> <dd> The message </dd>
     *           </dl>
     */
    public void handleAbcast(GroupCommEventArgs ev) {
	logger.entering("AbcastImpl","handleAbcast");
	// msg
	GroupCommMessage msg = (GroupCommMessage)ev.removeFirst();

    // Sergio - 8 mar 2006 - added for optimization
    AbcastMessageID id = abcastId.nextId();
    GroupCommMessage cloneM = msg.cloneGroupCommMessage();
    aUndelivered.put(id, cloneM);

    //Flow control
    //flow_control.alloc(fc_key, 1);
    nbMsgsSent++; 
    if (nbMsgsSent >= max_locally_abcast)
        flow_control.block(fc_key);
    
    TriggerItem propose = testAndConsensus();
   
	msg.tpack(id);
	// id::m
	Iterator it = ((TList)known.clone()).iterator();
	while (it.hasNext()) {
	    PID pid = (PID)it.next();
        if(!pid.equals(myself)){ // Sergio - 8 mar 2006 - added for optimization
            GroupCommMessage newMess = msg.cloneGroupCommMessage();
            GroupCommEventArgs  pt2ptSend = new GroupCommEventArgs();
            pt2ptSend.addLast(newMess);
            pt2ptSend.addLast(pid);
            pt2ptSend.addLast(new TBoolean(false)); // not promisc
            logger.log(Level.FINE,
                   "Sending Pt2Pt message id: {0} to {1}\n\tMessage: {2}",
                   new Object[]{id, pid,newMess});
            abcast.trigger (Constants.PT2PTSEND, pt2ptSend);
        }
	}

    // Sergio - 8 mar 2006 - added for optimization
    if (propose != null)
    abcast.trigger (propose.type, propose.args);

	logger.exiting("AbcastImpl","handleAbcast");
    }
    
    /**
     * The handler for the <i>Pt2PtDeliver</i> event. <br/>
     * When we recieve a message from the Reliable communication layer, we have 
     * to resent the message to all the receipents, if it's the first time it arrives.
     * That's the R-Broadcast part of the protocol. It launch a consensus too.
     * 
     * @param ev <dl>
     *               <dt> arg1: GroupCommMessage (id::m) </dt> <dd> The message, with an id </dd>
     *               <dt> arg2: PID                   </dt> <dd> Source PID </dd>
     *           </dl>
     */
    public void handlePt2PtDeliver(GroupCommEventArgs ev) {
	logger.entering("AbcastImpl","handlePt2PtDeliver");
	// msg = id::m
	// msgClone = id::m
	GroupCommMessage msg = (GroupCommMessage)ev.removeFirst();
	//GroupCommMessage msgClone = msg.cloneGroupCommMessage(); Sergio - 8 mar 2006
	PID source = (PID)ev.removeFirst();
    Transportable t = (Transportable)msg.tunpack();
    if(t instanceof TLong){ //somebody has sent us its k value
        long rem_k = ((TLong) t).longValue();
        gossipK = Math.max(gossipK, rem_k);
        TriggerItem propose = testAndConsensus();
        if (propose!=null) abcast.trigger(propose.type, propose.args);
        logger.exiting("AbcastImpl","handlePt2PtDeliver");
        return;
    }
    AbcastMessageID id = (AbcastMessageID) t; 
	// msg = m
	logger.log(Level.FINE,
		   "Receiving message id: {0} from {1}\n\tMessage: {2}", 
		   new Object[]{id, source, msg});
	if (! aUndelivered.containsKey(id) &&
	    ! aDelivered.contains(id) &&
	    ! (id.id <= ((TLong)maxIdProProc.get(id.proc)).longValue())) {
	    aUndelivered.put(id, msg);
        TriggerItem propose = testAndConsensus();

        // Sergio - 8 mar 2006 - removed for optimization
        /*
	    Iterator it = known.iterator();
	    while (it.hasNext()) {
		PID pid = (PID)it.next();
		GroupCommMessage newMess = msgClone.cloneGroupCommMessage();
		// newMess = id::m
		logger.log(Level.FINE,
			 "Resending message id: {0} to {1}\n\tMessage: {2}",
			 new Object[]{id, pid, newMess});
		GroupCommEventArgs pt2ptSend = new GroupCommEventArgs();
		pt2ptSend.addLast(newMess);
		pt2ptSend.addLast(pid);
		pt2ptSend.addLast(new TBoolean(false)); // not promisc
		abcast.trigger (Constants.PT2PTSEND, pt2ptSend);
	    }
        */
        //Flow control
        if (nbMsgsSent >= max_locally_abcast)
            flow_control.block(fc_key);       

	    if (propose != null)
		abcast.trigger (propose.type, propose.args);
	}
	logger.exiting("AbcastImpl","handlePt2PtDeliver");
    }
    
    /**
     * The handler for the <i>Decide</i> event. <br/>
     * It happends when consensus has decided an order to ADeliver messages
     * We are sure that it's the same for everybody, but we test
     * if the message isn't already delivered.
     *
     * @param ev <dl>
     *               <dt> arg1: GroupCommMessage (k::Decision) </dt> <dd> The decision </dd>
     *           </dl>
     *
     * @exception DecideException Thrown when an unknown DecideEvent happends
     */
    public void handleDecide(GroupCommEventArgs ev) throws GroupCommException {
	logger.entering("AbcastImpl","handleDecide");
	TLinkedHashMap undelivered = (TLinkedHashMap)ev.removeFirst();
	long kdecision = ((TLong)(ev.removeFirst())).longValue();
	if (kdecision + 1 != k) 
	    throw new GroupCommException("AbcastImpl: handleDecide: Unordered decide event: incoming = "
				  +kdecision+", expected = "+(k-1));

    //Feed-back for flow-control
    if(undelivered.size() < MSGS_PER_CONSENSUS) max_locally_abcast = Math.min(MSGS_PER_CONSENSUS * 2, max_locally_abcast + 1);
    if(undelivered.size() > MSGS_PER_CONSENSUS) max_locally_abcast = Math.max(MIN_LOCALLY_ABCAST, max_locally_abcast - 1);

	GroupCommMessage msg, delivered;
	AbcastMessageID id;
	TLinkedHashMap toTrigger = new TLinkedHashMap();
	while (! undelivered.isEmpty()) {
	    id = (AbcastMessageID)undelivered.keySet().iterator().next(); //firstKey();
	    msg = (GroupCommMessage)undelivered.remove(id);
	    delivered = msg.cloneGroupCommMessage();
	    long maxId = ((TLong)maxIdProProc.get(id.proc)).longValue();
	    if (!aDelivered.contains(id) && id.id > maxId) {
		// Remove the id from aUndelivered
		aUndelivered.remove(id);
		// add it in aDelivered
		aDelivered.add (id);
		// Book for adeliver later
		toTrigger.put(id, delivered);
		//Flow control
		if(id.proc.equals(myself)){
            //flow_control.free(fc_key, 1);
            nbMsgsSent--; 
		}
		// update the highest aDelivered table 
		AbcastMessageID newID = new AbcastMessageID(id.proc, maxId+1);
		while (aDelivered.contains(newID)) {
		    aDelivered.remove(newID);
		    maxId++;
		    newID.id++;
		}
		maxIdProProc.put(id.proc, new TLong(maxId));
	    }
	}
    
    //Flow control
    if (nbMsgsSent < max_locally_abcast)
        flow_control.release(fc_key);

	consensusStarted = false;
    // timer
    timer.schedule(new TLong(k), false, INACTIVITY_TIMEOUT);
    timerOn = true;

	TriggerItem propose = testAndConsensus();

	//Now, it's time to adeliver all messages
	while (! toTrigger.isEmpty()) {
	    id = (AbcastMessageID)toTrigger.keySet().iterator().next(); //firstKey();
	    msg = (GroupCommMessage)toTrigger.remove(id);
	    // ADeliver message
	    GroupCommEventArgs adeliver = new GroupCommEventArgs();
	    adeliver.addLast(msg);
        adeliver.addLast(id.proc);
	    abcast.trigger(Constants.ADELIVER, adeliver);
	}
	
	if (propose!=null)
	    abcast.trigger(propose.type, propose.args);
	
	logger.exiting("AbcastImpl","handleDecide");
    }

    /**
     * The handler for the <i>Timeout</i> event. <br/>
     * It happends when consensus has started and hasnot decided for a long time
     * This is an optimization to save messages tha are sent
     *
     * @param ev <dl>
     *               <dt> arg: GroupCommMessage (timerKey) </dt> <dd> Timer key. Discarded </dd>
     *           </dl>
     *
     * @exception None
     */
    public void handleTimeout(GroupCommEventArgs arg){
        logger.entering("AbcastImpl", "handleTimeout");
        timerOn = false;

        Iterator it = ((TList)known.clone()).iterator();
        TLong kObj = new TLong(k);
        while (it.hasNext()) {
            PID pid = (PID)it.next();
            if(!pid.equals(myself)){
                GroupCommMessage myK = new GroupCommMessage();
                myK.tpack(kObj);
                GroupCommEventArgs  pt2ptSend = new GroupCommEventArgs();
                pt2ptSend.addLast(myK);
                pt2ptSend.addLast(pid);
                pt2ptSend.addLast(new TBoolean(false)); // not promisc
                logger.log(Level.FINE,
                       "Sending special Pt2Pt message myK to {0}\n\tMessage: {1}",
                       new Object[]{pid, myK});
                abcast.trigger (Constants.PT2PTSEND, pt2ptSend);
            }
        }
        logger.exiting("AbcastImpl", "handleTimeout");
    }

    private TriggerItem testAndConsensus() {
	logger.entering("AbcastImpl","testAndConsensus");
	if ( !consensusStarted && (!aUndelivered.isEmpty() || gossipK > k) ) {
	    //I only take maximum of message IDs for the consensus
	    TMap propose;
        int sizePropose = Math.max(MSGS_PER_CONSENSUS/2, aUndelivered.size()/2);
	    if(aUndelivered.size() > sizePropose){
        // Prevents glitches with tons of messages
		TCollection ids = aUndelivered.keySet();
		propose = new TLinkedHashMap();		
		Iterator it = ids.iterator();
		for  (int tune=0;
		      tune < sizePropose && it.hasNext();
		      tune++){
		    AbcastMessageID id = (AbcastMessageID)it.next();
		    propose.put(id, aUndelivered.get(id));
		}
	    }else{
		propose = (TLinkedHashMap)aUndelivered.clone();
	    }
	    TLong kObj = new TLong(k);
        logger.log(Level.FINE,
               "Launching consensus#{1}:\n\tValue: {0}", 
               new Object[]{propose , kObj});
	    k++;
	    GroupCommEventArgs run = new GroupCommEventArgs();
	    run.addLast(known); //clone not necessary since group is static
	    run.addLast(propose);
	    run.addLast(kObj);
	    consensusStarted = true;
        // timer
        if(timerOn){
            timer.cancel(new TLong(k-1));
            timerOn = false;
        }

	    logger.exiting("AbcastImpl","testAndConsensus");
	    return new TriggerItem(Constants.PROPOSE, run);	    
	}

	logger.exiting("AbcastImpl","testAndConsensus");
	return null;
    }

    /**
     * Used for debug
     *
     * @param out The output stream used for showing infos
     */
     
    public void dump(OutputStream out) {
	PrintStream err = new PrintStream(out);
	err.println("======== AbcastImpl: dump =======");
	err.println(" Initialized: "+String.valueOf(initialized));
	err.println(" ConsensusRunning: "+String.valueOf(consensusStarted));
	err.println(" Next consensus id: "+k);
	err.println(" Last AbcastMessage id used:\n\t"+abcastId);
	err.println(" Flow Control threshold: "+MIN_LOCALLY_ABCAST);
	err.println("\t used: "+nbMsgsSent);
	err.println(" Known processes: size: "+known.size());
	Iterator it = known.iterator();
	PID pid;
	while (it.hasNext()) {
	    pid = (PID)it.next();
	    err.println("\t"+pid.toString());
	}
	err.println(" A-Undelivered messages:");
	err.println("   "+aUndelivered.toString());
	err.println(" A-Delivered messages IDs:");
	it = aDelivered.iterator();
	AbcastMessageID id;
	while (it.hasNext()) {
	    id = (AbcastMessageID)it.next();
	    err.println("\t"+id);
	}
	err.println("   and all message with id <= ");
	err.println("\t"+maxIdProProc.toString());
	err.println("==================================");
    }
}
