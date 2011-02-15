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
package groupcomm.common.abcast;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
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
import framework.libraries.FlowControl;
import framework.libraries.Timer;
import framework.libraries.Trigger;
import framework.libraries.serialization.TArrayList;
import framework.libraries.serialization.TBoolean;
import framework.libraries.serialization.TCollection;
import framework.libraries.serialization.THashMap;
import framework.libraries.serialization.THashSet;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TLinkedHashMap;
import framework.libraries.serialization.TLinkedList;
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
public class DynAbcastImpl {
    private PID myself;
	// Initialized ?
    private boolean initialized = false;
    // Were we given a set of processes
    // (when we initialized)?
    private boolean initData = false;
    // Trigger class for events routing
    private Trigger abcast;

    // Variables for flow control
    private FlowControl flow_control;
    private int fc_key;
    private int nbMsgsSent = 0;

    // Variables for the timer (Sergio - 8 mar 2006)
    private Timer timer;
    private boolean timerOn = false;
    private final int INACTIVITY_TIMEOUT = 5000;//10 * 1000; // milliseconds

    // Known processes, to send the broadcast messages : Contains PID
    private TArrayList known = null;
    // private LinkedList known;
    // A-delivered messages : Set (AbcastMessageID)
    private THashSet aDelivered = null;
    // A-Undelivered messages : FIFO-order Map (AbcastMessageID -> GroupCommMessage m)
    private TLinkedHashMap aUndelivered = null;
    // Bound for old A-delivered messages : Table (PID -> Integer)
    private THashMap maxIdProProc = null;
    // id for consensus requests
    private long k;
    // id for highest consensus ever heard from
    private long gossipK = 0;
    // Abcast message current id
    private AbcastMessageID abcastId;
    // Has a consensus started ?
    private boolean consensusStarted;

    public static final int MIN_LOCALLY_ABCAST = 1;
    //public static final int MAX_UNDELIVERED = 8;
    //public static final int MAX_PROPOSE = 4;
    public static final int MSGS_PER_CONSENSUS = 4;
    private int max_locally_abcast = MSGS_PER_CONSENSUS;
    //private boolean nullDecision = false;

    private TLinkedList whenAbcast;
    private TLinkedList whenPt2PtDeliver;
    private boolean whenWarning = false;

    //private boolean imDead = false;

    // Logging
    private static final Logger logger =
	Logger.getLogger(DynAbcastImpl.class.getName());

    public static class TriggerItem implements Transportable{
        public int type;
        public GroupCommEventArgs args;

        public TriggerItem(int type, GroupCommEventArgs args){
            this.type = type;
            this.args = args;
	}
		public void marshal(MarshalStream arg0) throws IOException {
            throw new IOException("unimplemented");
		}
		public void unmarshalReferences(UnmarshalStream arg0) throws IOException, ClassNotFoundException {
            throw new IOException("unimplemented");
		}
		public Object deepClone(DeepClone arg0) throws CloneNotSupportedException {
            throw new CloneNotSupportedException("unimplemented");
		}
    }

    /**
     * Constructor.
     *
     * @param abcast  object of a framework protocol based class, which ensure event routing for this protocol.
     */
    public DynAbcastImpl(Trigger abcast, FlowControl fc, Timer t, PID myself) {
	logger.entering("DynAbcastImpl","<constr>");    
	this.abcast = abcast;
	this.flow_control = fc;
    this.myself = myself;
    this.timer = t;
	aUndelivered = new TLinkedHashMap();
	abcastId = new AbcastMessageID(myself, 0);
	// Lists to store events when
	// initData=false
	whenAbcast = new TLinkedList();
	whenPt2PtDeliver = new TLinkedList();
	logger.exiting("DynAbcastImpl","<constr>");    
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
    public void handleInit(GroupCommEventArgs ev) throws GroupCommException {
	logger.entering("DynAbcastImpl","handleInit");    
	TList p = (TList)ev.removeFirst(); 
	
	if (initialized)
	    throw new GroupCommException("AbcastImpl already initialized.");
	initialized = true;
    fc_key = flow_control.getFreshKey();
	k = 1;
	consensusStarted = false;
        if(p.isEmpty()){
	    initData=false;
	    // flow control
        } else {
	    initData=true;
	    known = new TArrayList(p);
	    //Look for duplicate processes in the group
	    for(int i = 0; i < known.size() ; i++)
		for(int j = i+1; j < known.size(); j++)
		    if( known.get(i).equals(known.get(j)) )
			throw new GroupCommException("Process" + known.get(i) +
						" appears more than once in the group.");
   	    aDelivered = new THashSet();
	    // init maximum id of Adelivered message
	    maxIdProProc = new THashMap();
	    Iterator it = known.iterator();
	    while (it.hasNext()) {
		PID pid = (PID)it.next();
		maxIdProProc.put (pid, new TLong(-1));
	    }

        // timer
        //timer.schedule(new TLong(k), false, INACTIVITY_TIMEOUT);
        //timerOn = true;

        // join-remove
	    GroupCommEventArgs jrl = new GroupCommEventArgs();
	    jrl.addLast (new THashSet(p)); // join
	    jrl.addLast (new THashSet()); // remove
	    abcast.trigger (Constants.JOINREMOVELIST, jrl);	   
	}
	logger.exiting("DynAbcastImpl","handleInit");
    }

    /**
     * The handler for the <i>Abcast</i> event. <br/>
     * It broadcasts the message to all the processes described by the init event. <br/>
     * It adds an Abcast-Id to the message.
     *
     * @param ev <dl>
     *               <dt> arg1: Integer </dt> <dd> The type </dd>
	*			 <dt> arg2: GroupCommMessage </dt> <dd> The message </dd>
     *           </dl>
     */
    public void handleAbcast(GroupCommEventArgs ev) {   
	logger.entering("DynAbcastImpl","handleAbcast");    
//	if(imDead){
//	    logger.exiting("DynAbcastImpl","handleAbcast");    
//	    return;
//	}
	//When (initData)
	if(!initData){
	    whenAbcast.addLast(ev);
	    logger.exiting("DynAbcastImpl","handleAbcast");    
	    return;
	}
	TInteger type = (TInteger)ev.removeFirst();
	GroupCommMessage payload = (GroupCommMessage)ev.removeFirst();
	// payload = m  
	if(type.intValue() != Constants.AM){
	    payload.tpack((PID) ev.removeFirst());
	}
	// payload = (pid)::m
	payload.tpack(type);
	// payload = type::(pid)::m

    // Sergio - 8 mar 2006 - added for optimization
    TLinkedList toTrigger = new TLinkedList();
    // This list will contain all events to be triggered, and we'll 
    // trigger them at the end of this method
    AbcastMessageID id = abcastId.nextId();
    GroupCommMessage cloneM = payload.cloneGroupCommMessage();
    aUndelivered.put(id, cloneM);
    
    //Flow control
    //flow_control.alloc(fc_key, 1);
    nbMsgsSent++; 
    if (nbMsgsSent >= max_locally_abcast)
        flow_control.block(fc_key);
   
    testAndConsensus(toTrigger);
    
    

	payload.tpack(id);
	// payload = id::type::(pid)::m
	payload.tpack(new TBoolean(false));
	// payload = isinit::id::type::(pid)::m
    
	Iterator it = ((TList)known.clone()).iterator();
	while (it.hasNext()) {
	    PID pid = (PID)it.next();
        if(!pid.equals(myself)){ // Sergio - 8 mar 2006 - added for optimization
            GroupCommMessage newMess = payload.cloneGroupCommMessage();
            GroupCommEventArgs pt2ptSend = new GroupCommEventArgs();
            pt2ptSend.addLast(newMess);
            pt2ptSend.addLast(pid);
            pt2ptSend.addLast(new TBoolean(false)); // not promisc
            logger.log(Level.FINE,
                   "Sending Pt2Pt message id: {0} to {1}\n\tMessage: {2}",
                   new Object[]{id, pid,newMess});
            toTrigger.addLast(new TriggerItem(Constants.PT2PTSEND, pt2ptSend));
        }
    }

    //Finally, we trigger all events
    while(!toTrigger.isEmpty()){
        TriggerItem item = (TriggerItem)toTrigger.removeFirst();
        abcast.trigger(item.type, item.args);
    }

	logger.exiting("DynAbcastImpl","handleAbcast");    
    }

    /**
     * The handler for the <i>Pt2PtDeliver</i> event. <br/>
     * When we recieve a message from the Reliable communication layer, we have
     * to resend the message to all the recipients, if it's the first time it arrives.
     * That's the R-Broadcast part of the protocol. It launches a consensus too.
     *
     * @param ev <dl>
     *               <dt> arg1: GroupCommMessage (id::m) </dt> <dd> The message, with an id </dd>
     *               <dt> arg2: PID                   </dt> <dd> Source PID </dd>
     *           </dl>
     */
    public void handlePt2PtDeliver(GroupCommEventArgs ev) throws GroupCommException{
//	if(imDead){
//	    logger.exiting("DynAbcastImpl","handlePt2PtDeliver");
//	    return;
//	}	    
	logger.entering("DynAbcastImpl","handlePt2PtDeliver");    
	GroupCommMessage msg = (GroupCommMessage)ev.get(0);
	//msg = isinit::<payload>
	PID source = (PID)ev.get(1);
	boolean isinit = ((TBoolean)msg.tunpack()).booleanValue();
	//msg = <payload>

	//When (initData OR isinit)
	if(!initData && !isinit){
	    msg.tpack(new TBoolean(false));
	    //msg = false::<payload>
	    whenPt2PtDeliver.addLast(ev);
	    logger.log(Level.FINE,
		       "Added event to whenPt2PtDeliver.\n\tArguments:{0}",
		       ev);
	    logger.exiting("DynAbcastImpl","handlePt2PtDeliver");    
	    return;
	}

	if(isinit) {
	    if(!initData){
		// msg = maxIdProProc::aDelivered::k::known::newProcesses
		GroupCommMessage mClone = msg.cloneGroupCommMessage();
		// mClone = msg
		maxIdProProc = (THashMap)msg.tunpack();
		// msg = aDelivered::k::known::newProcesses
		aDelivered = (THashSet)msg.tunpack();
		// msg = k::known::newProcesses
		k = ((TLong)msg.tunpack()).longValue();
		// msg = known::newProcesses
		known = (TArrayList)msg.tunpack();
		//Look for duplicate processes in the group
		for(int i = 0; i < known.size() ; i++)
		    for(int j = i+1; j < known.size(); j++)
			if( known.get(i).equals(known.get(j)) )
			    throw new GroupCommException("Process" + known.get(i) +
						    " appears more than once in the group.");
		// msg = newProcesses
		TSet newProcesses = (TSet)msg.tunpack();
		
		if (!known.containsAll((TSet)newProcesses))
		    throw new GroupCommException("Known doesn't contain all new processes in handlePt2PtDeliver()");

		initData = true;

        // timer
        //timer.schedule(new TLong(k), false, INACTIVITY_TIMEOUT);
        //timerOn = true;

		// Join_Remove_List event
		GroupCommEventArgs jrl = new GroupCommEventArgs();
//		HashSet joinP = new HashSet();
//		Iterator it = known.iterator();
//		while (it.hasNext()) {
//		    PID p = (PID)it.next();
//		    if(!p.equals(source)){
//			joinP.add(p);
//		    }
//		}
		jrl.addLast (new THashSet(known));  // join
		jrl.addLast (new THashSet()); // remove
		abcast.trigger (Constants.JOINREMOVELIST, jrl);

        //I remove myself from the new processes
        newProcesses.remove(myself);
		Iterator it = newProcesses.iterator();
		GroupCommMessage sentMess;
		while (it.hasNext()) {
		    PID pid = (PID)it.next();
		    sentMess = mClone.cloneGroupCommMessage();
		    // sentMess = aDelivered::k::known::newProcesses
		    sentMess.tpack(new TBoolean(true));
		    // sentMess = isinit::m
    		    GroupCommEventArgs pt2ptSend = new GroupCommEventArgs();
		    pt2ptSend.addLast(sentMess);
		    pt2ptSend.addLast(pid);
		    pt2ptSend.addLast(new TBoolean(true)); // promisc !!!
		    abcast.trigger (Constants.PT2PTSEND, pt2ptSend);
		}
		// Flush the events waiting at a "when" guard
		// Reason: initData is now true
		if(whenWarning){
		    throw new GroupCommException("Trying to flush 'when' while flushing!!");
		} else {
		    whenWarning = true;
		}
		it = whenPt2PtDeliver.iterator();
		while (it.hasNext()) {
		    GroupCommEventArgs e = (GroupCommEventArgs)it.next();
		    handlePt2PtDeliver(e);
		}
		whenPt2PtDeliver.clear(); //Just in case
		it = whenAbcast.iterator();
		while (it.hasNext()) {
		    GroupCommEventArgs e = (GroupCommEventArgs)it.next();
		    //To prevent flow control from allocating the same message twice
		    //flow_control.free(fc_key, 1);
            nbMsgsSent--;
		    handleAbcast(e);
		}
		whenAbcast.clear(); //Just in case
	    }//End of if(!initData)
	} else { // !isinit
	    if(!initData) 
		throw new GroupCommException("DyAbcastImpl: Error: Trying to manage a " +
				   "Pt2Pt message while InitData=false!");
	    // msg = id::type::(pid)::payload
	    //GroupCommMessage mClone = msg.cloneGroupCommMessage(); Sergio - 8 mar 2006
	    // mClone = msg
        Transportable t = (Transportable)msg.tunpack();
        if(t instanceof TLong){ //somebody has sent us its k value
            long rem_k = ((TLong) t).longValue();
            // This list will contain all events to be triggered, and we'll 
            // trigger them at the end of this method
            gossipK = Math.max(gossipK, rem_k);
            TLinkedList toTrigger = new TLinkedList();
            testAndConsensus(toTrigger);
            //Finally, we trigger all events
            while(!toTrigger.isEmpty()){
                TriggerItem item = (TriggerItem)toTrigger.removeFirst();
                abcast.trigger(item.type, item.args);
            }
            logger.exiting("DynAbcastImpl","handlePt2PtDeliver");
            return;
        }
	    AbcastMessageID id = (AbcastMessageID) t;
	    // msg = type::(pid)::payload

        logger.log(Level.FINE,
               "Receiving message id: {0} from {1}\n\tMessage: {2}", 
               new Object[]{id, source, msg});

	    if( known.contains(id.proc) &&
		(id.id > ((TLong)maxIdProProc.get(id.proc)).longValue()) &&
	        ! aDelivered.contains(id) &&
		! aUndelivered.containsKey(id) ){

            //To deliver all events in the end
            TLinkedList toTrigger = new TLinkedList();

            aUndelivered.put(id, msg);//Doesn't need to be cloned
            if (nbMsgsSent >= max_locally_abcast)
                flow_control.block(fc_key);

            // Sergio - 8 mar 2006 - removed for optimization
            /*
            Iterator it = known.iterator();
            mClone.tpack(new TBoolean(false));
            // msg = false::id::type::(pid)::payload
            while (it.hasNext()) {
                PID pid = (PID)it.next();
                GroupCommEventArgs pt2ptSend = new GroupCommEventArgs();
                pt2ptSend.addLast(mClone.cloneGroupCommMessage());
                pt2ptSend.addLast(pid);
                pt2ptSend.addLast(new TBoolean(false)); // not promisc
                toTrigger.addLast(new TriggerItem(Constants.PT2PTSEND, pt2ptSend));
            } // End of WHILE
            */

            testAndConsensus(toTrigger);

            //Now we trigger all events scheduled
            while(!toTrigger.isEmpty()){
                TriggerItem item = (TriggerItem)toTrigger.removeFirst();
                abcast.trigger(item.type, item.args);
		}
	    } //End of if (! aUndelivered
	} // End of else
	logger.exiting("DynAbcastImpl","handlePt2PtDeliver");    
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
	logger.entering("DynAbcastImpl","handleDecide");
//	if(imDead)
//	    throw new GroupCommException("I am dead!! Consensus can't decide");
	TLinkedHashMap undelivered = (TLinkedHashMap)ev.removeFirst();
	long kdecision = ((TLong)ev.removeFirst()).longValue();

	if (!initData) 
	    throw new GroupCommException("DynAbcastImpl: handleDecide: InitData = false");

	if (kdecision + 1 != k)
	    throw new GroupCommException("DynAbcastImpl: handleDecide: Unordered decide event:" +
				      "incoming = "+kdecision+", expected = "+(k-1));

    //Feed-back for flow-control
    if(undelivered.size() < MSGS_PER_CONSENSUS) max_locally_abcast = Math.min(MSGS_PER_CONSENSUS * 2, max_locally_abcast + 1);
    if(undelivered.size() > MSGS_PER_CONSENSUS) max_locally_abcast = Math.max(MIN_LOCALLY_ABCAST, max_locally_abcast - 1);

//    nullDecision = undelivered.isEmpty();
//    if(nullDecision) logger.info("Warning: A null decision has been taken");

	// This list will contain all events to be triggered, and we'll 
	// trigger them at the end of this method
	TLinkedList toTrigger = new TLinkedList();
        // First we adeliver all messages of type AM in undelivered
        // unless we've already done so (see condition in next "if")
	TLinkedHashMap special = new TLinkedHashMap();
	while ( !undelivered.isEmpty()) {
        AbcastMessageID id = (AbcastMessageID) undelivered.keySet().iterator().next(); //.firstKey();
	    GroupCommMessage msg = (GroupCommMessage)undelivered.remove(id);
	    // msg = type::(pid)::payload

	    if( known.contains(id.proc) &&
		(id.id > ((TLong)maxIdProProc.get(id.proc)).longValue()) &&
	        ! aDelivered.contains(id) ){

		TInteger type=(TInteger) msg.tunpack();
		// msg = (pid)::payload
		if(type.intValue() != Constants.AM){ // type is ADD or REMOVE
		    msg.tpack(type);
		    // msg = type::pid::payload
		    special.put(id, msg);//I'll deliver it later
		} else {
		    // msg = payload
		    triggerAdeliver(id, type, msg, id.proc, toTrigger);
		}
	    }
	}

	TSet newProc = new THashSet();

	//Now we adeliver all messages that change the process set
	while (! special.isEmpty()) {
	    AbcastMessageID id = (AbcastMessageID)special.keySet().iterator().next(); //.firstKey();
	    GroupCommMessage msg = (GroupCommMessage)special.remove(id);
	    // msg = type::pid::payload
	    TInteger type=(TInteger)msg.tunpack();
	    // msg = pid::payload
	    PID pid = (PID)msg.tunpack();
	    // msg = payload


	    GroupCommEventArgs jrl = new GroupCommEventArgs();
	    switch (type.intValue()){
	    case Constants.ADD:
		if(!known.contains(pid)){
		    known.add(pid); //Add new process to known
		    newProc.add(pid); //Add it to newProc, too
		    // init maximum id of Adelivered message for newP
		    maxIdProProc.put(pid, new TLong(-1));
           
            // join-remove
            TSet singleton = new THashSet();
            singleton.add(pid);
		    jrl.addLast (singleton); // join
		    jrl.addLast (new THashSet()); // remove
		    toTrigger.addLast(new TriggerItem(Constants.JOINREMOVELIST, jrl));
		    triggerAdeliver(id, type, msg, pid, toTrigger);
		} else {
		    //Remove ignored message from aUndelivered
		    aUndelivered.remove(id);
		    //System.err.println("Warning: trying to join a process twice. "
		    //	       + "Second <join> will be ignored.");
		}
		break;
	    case Constants.REM:
		if(known.contains(pid)){
		    known.remove(pid);//Remove PID from known
		    // remove maximum id of Adelivered message for pid
		    maxIdProProc.remove(pid);

            // remove all msgs that came from pid in aUndelivered
		    TLinkedHashMap temp = new TLinkedHashMap();
		    TCollection ids = aUndelivered.keySet();
		    Iterator it = ids.iterator();
		    while(it.hasNext()){
			AbcastMessageID rid = (AbcastMessageID)it.next();
			if(!rid.proc.equals(pid)){
			    temp.put(rid, aUndelivered.get(rid));
			}
		    }
		    aUndelivered = temp;

		    // remove also msgs that came from pid in ADelivered
		    THashSet tmp2 = new THashSet();
		    it = aDelivered.iterator();
		    while(it.hasNext()){
			AbcastMessageID rid = (AbcastMessageID)it.next();
			if(!rid.proc.equals(pid)){
			    tmp2.add(rid);
			}
		    }
		    aDelivered = tmp2;

		    //remove also msgs that came from pid in "special"
		    //TODO: to test it, consensus has to run very slow
            // and two processes trying to remove each other
		    TLinkedHashMap tmp3 = new TLinkedHashMap();
		    ids = special.keySet();
		    it = ids.iterator();
		    while(it.hasNext()){
			AbcastMessageID rid = (AbcastMessageID)it.next();
			if(!rid.proc.equals(pid)){
			    tmp3.put(rid, special.get(rid));
			}
		    }
		    special = tmp3;

		    // join-remove
            TSet singleton = new THashSet();
            singleton.add(pid);
		    jrl.addLast (new THashSet()); // join
		    jrl.addLast (singleton); // remove
		    toTrigger.addLast(new TriggerItem(Constants.JOINREMOVELIST, jrl));
		    //Is somebody removing itself??
		    if(pid.equals(id.proc)){
			// ADeliver message
			GroupCommEventArgs selfremove = new GroupCommEventArgs();
			selfremove.addLast(type);
			selfremove.addLast(msg);
			selfremove.addLast(pid);
			toTrigger.addLast(new TriggerItem(Constants.ADELIVER, selfremove));
		    } else {
			triggerAdeliver(id, type, msg, pid, toTrigger);
		    }
 		} else {
		    //Remove ignored message from aUndelivered
		    aUndelivered.remove(id);
 		    //System.err.println("Warning: trying to remove an unknown process. "
		    //	       + "<Remove> will be ignored.");
		}
		break;
	    default:
        throw new GroupCommException("DynAbcastImpl:Handle deliver:Unknown message type");
	    }
	}

    if (nbMsgsSent < max_locally_abcast)
        flow_control.release(fc_key);
    
	consensusStarted = false;
    // timer
    timer.schedule(new TLong(k), false, INACTIVITY_TIMEOUT);
    timerOn = true;

	if(!newProc.isEmpty()){
	    // Now we send aDelivered::k::known::newKnown = msgInit
	    // to all the new processes (the ones in newKnown)
	    GroupCommMessage msgInit = new GroupCommMessage();
	    msgInit.tpack(newProc); //Don't need to clone: local variable
	    // msgInit = newProc
	    msgInit.tpack((TList)known.clone());
	    // msgInit = known::newProc
	    msgInit.tpack(new TLong(k));
	    // msgInit = k::known::newProc
	    msgInit.tpack((THashSet)aDelivered.clone());
	    // msgInit = aDelivered::k::known::newProc
	    msgInit.tpack((TMap)maxIdProProc.clone());
	    // msgInit = maxIdProProc::aDelivered::k::known::newProc
	    msgInit.tpack(new TBoolean(true));
	    // msgInit = true::maxIdProProc::aDelivered::k::known::newProc
	    Iterator it = newProc.iterator();
	    while (it.hasNext()) {
		PID pid = (PID)it.next();
		GroupCommEventArgs pt2ptSend = new GroupCommEventArgs();
		pt2ptSend.addLast(msgInit.cloneGroupCommMessage());
		pt2ptSend.addLast(pid);
		pt2ptSend.addLast(new TBoolean(true)); // promisc!!!
		toTrigger.addLast(new TriggerItem(Constants.PT2PTSEND, pt2ptSend));
	    }
	}

	if(known.contains(myself)) {
	    if(!newProc.isEmpty()){//Introduced for performance reasons
		// Next: sending the messages in aUndelivered if any.
		Iterator itUndelivered = (aUndelivered.keySet()).iterator();
		while (itUndelivered.hasNext()){
		    AbcastMessageID id = (AbcastMessageID)itUndelivered.next();
		    GroupCommMessage msg = (GroupCommMessage)aUndelivered.get(id);
		    // msg = type::m
		    GroupCommMessage mClone = msg.cloneGroupCommMessage();
		    // mClone = type::m
		    mClone.tpack(id);
		    // mClone = id::type::m
		    mClone.tpack(new TBoolean(false));
		    Iterator itProcesses = newProc.iterator();
		    while (itProcesses.hasNext()) {
			PID pid = (PID)itProcesses.next();
			GroupCommEventArgs pt2ptSend = new GroupCommEventArgs();
			// We get the message from aUndelivered
			pt2ptSend.addLast(mClone.cloneGroupCommMessage());
			pt2ptSend.addLast(pid);
			pt2ptSend.addLast(new TBoolean(false)); // not promisc
			toTrigger.addLast(new TriggerItem(Constants.PT2PTSEND, pt2ptSend));
		    }
		}
	    }

	    testAndConsensus(toTrigger);
        } else {
            throw new GroupCommException("The local process has been excluded from the view");
	    //imDead = true;
	}

	//Finally, we trigger all events
	while(!toTrigger.isEmpty()){
	    TriggerItem item = (TriggerItem)toTrigger.removeFirst();
	    abcast.trigger(item.type, item.args);
	}
	logger.exiting("DynAbcastImpl","handleDecide");
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
        logger.entering("DynAbcastImpl", "handleTimeout");
        timerOn = false;

        TLong kObj = new TLong(k);
        TBoolean isinit = new TBoolean(false);
        Iterator it = ((TList)known.clone()).iterator();
        while (it.hasNext()) {
            PID pid = (PID)it.next();
            if(!pid.equals(myself)){
                GroupCommMessage myK = new GroupCommMessage();
                myK.tpack(kObj);
                myK.tpack(isinit);
                GroupCommEventArgs pt2ptSend = new GroupCommEventArgs();
                pt2ptSend.addLast(myK);
                pt2ptSend.addLast(pid);
                pt2ptSend.addLast(new TBoolean(false)); // not promisc
                logger.log(Level.FINE,
                       "Sending special Pt2Pt message to {0}\n\tMessage: {1}",
                       new Object[]{pid, myK});
                abcast.trigger(Constants.PT2PTSEND, pt2ptSend);
            }
        }
        logger.exiting("DynAbcastImpl", "handleTimeout");
    }

    private void triggerAdeliver(AbcastMessageID id, 
				 TInteger type,
				 GroupCommMessage msg,
				 PID pid,
				 TLinkedList toTrigger){
	logger.entering("DynAbcastImpl","trigerAdeliver");
	// Remove the id from aUndelivered
	aUndelivered.remove(id);
	//Is it an id from an "unknown process?"
	if(known.contains(id.proc)){
	    // add it in aDelivered
	    aDelivered.add(id);
	    //Flow control
        if(id.proc.equals(myself)){
            //flow_control.free(fc_key, 1);
            nbMsgsSent--; 
        }
	    // update the highest aDelivered table
	    long maxId = ((TLong)maxIdProProc.get(id.proc)).longValue();
	    AbcastMessageID newID = new AbcastMessageID(id.proc, maxId + 1);
	    while (aDelivered.contains(newID)) {
		aDelivered.remove(newID);
		maxId++;
		newID.id++;
	    }
	    maxIdProProc.put(id.proc, new TLong(maxId));

	    // ADeliver message
	    GroupCommEventArgs adeliver = new GroupCommEventArgs();
	    adeliver.addLast(type);
	    adeliver.addLast(msg);
	    adeliver.addLast(pid);
	    toTrigger.addLast(new TriggerItem(Constants.ADELIVER, adeliver));
	} else {
	    System.err.println("DynabcastImpl:trigerAdeliver: Dind't" +
			       " adeliver a msg from an unknown source!!!");
	}
	logger.exiting("DynAbcastImpl","trigerAdeliver");
    }

    private void testAndConsensus(TLinkedList toTrigger) {
	logger.entering("DynAbcastImpl","testAndConsensus");
	if ( !consensusStarted && (!aUndelivered.isEmpty() || gossipK > k) ) {
	    //I only take maximum of message IDs for the consensus
	    TMap propose;
        int sizePropose = Math.max(MSGS_PER_CONSENSUS/2, aUndelivered.size()/2);
        if(aUndelivered.size() > sizePropose){
        //Prevents glitches with tons of messages
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
		       "Launching consensus#{1}:\n\tValue: {0}\n\tProcesses:{2}", 
		       new Object[]{propose , kObj, known});
	    k++;
	    GroupCommEventArgs run = new GroupCommEventArgs();
	    run.addLast( (TArrayList) known.clone());
	    run.addLast(propose);
	    run.addLast(kObj);
	    consensusStarted = true;
        // timer
        if(timerOn){
            timer.cancel(new TLong(k-1));
            timerOn = false;
        }

	    toTrigger.addLast(new TriggerItem(Constants.PROPOSE, run));
	}
	logger.exiting("DynAbcastImpl","testAndConsensus");
    }

    /**
     * Used for debug
     *
     * @param out The output stream used for showing infos
     */

    public void dump(OutputStream out) {
	PrintStream err = new PrintStream(out);
	err.println("===== DynAbcastImpl: dump ====");
	err.println(" Initialized: " + initialized);
	err.println(" When clauses.\n\tWhenAbcast " + whenAbcast);
	err.println("\tWhenPt2PtDeliver " + whenPt2PtDeliver);
	err.println(" ConsensusRunning: " + consensusStarted);
	err.println(" Next consensus id: " + k);
	err.println(" Next AbcastMessage id:" + abcastId);
	err.println(" Flow Control threshold: "+MIN_LOCALLY_ABCAST);
	err.println("\t used: "+nbMsgsSent);
	err.println(" initData: " + initData);
	if(initData){
	    err.println(" Known processes: size: " + known.size());
	    Iterator it = known.iterator();
	    PID pid;
	    while (it.hasNext()) {
		pid = (PID)it.next();
		err.println("\t" + pid);
	    }
	    err.println(" A-Undelivered messages:");
	    err.println("   " + aUndelivered);
        err.println(" A-Undelivered Size: " + aUndelivered.size() + " TIME: "+System.currentTimeMillis());
	    err.println(" A-Delivered messages ids:");
	    it = aDelivered.iterator();
	    AbcastMessageID id;
	    while (it.hasNext()) {
		id = (AbcastMessageID)it.next();
		err.println("\t" + id);
	    }
	    err.println("   and all message where id less or equal than:");
	    err.println("\t"+maxIdProProc);
	} 
	err.println("==============================");
    }
}
