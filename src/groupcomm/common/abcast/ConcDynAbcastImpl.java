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
import framework.libraries.Trigger;
import framework.libraries.serialization.TArrayList;
import framework.libraries.serialization.TBoolean;
import framework.libraries.serialization.TCollection;
import framework.libraries.serialization.THashMap;
import framework.libraries.serialization.THashSet;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TLinkedList;
import framework.libraries.serialization.TList;
import framework.libraries.serialization.TLong;
import framework.libraries.serialization.TMap;
import framework.libraries.serialization.TSet;
import framework.libraries.serialization.TSortedMap;
import framework.libraries.serialization.TTreeMap;

/**
 * <b> This class implements the common code for algorithm abcast. It runs several consensus
 * concurrently. </b>
 * <hr>
 * <b> Events:
 * <dt> <i>Init</i>           </dt> <dd> Initializes the abcast layer </dd>
 * <dt> <i>Abcast</i>         </dt> <dd> Send a Broadcast message, with the abcast algorithm </dd>
 * <dt> <i>Pt2PtDeliver</i>   </dt> <dd> Happend when a message is received by the lower layers </dd>
 * <dt> <i>Decide</i>         </dt> <dd> Happend when consensus has decided </dd>
 * </dl>
 */
public class ConcDynAbcastImpl {
    private PID myself;
    // Initialized ?
    private boolean initialized = false;
    // Were we given a set of processes
    // (when we initialized)?
    private boolean initData = false;
    // Trigger class for events routing
    private Trigger abcast;
    
    private FlowControl flow_control;
    private int fc_key;
    private int fc_threshold;
    private int fc_used = 0;

    // Known processes, to send the broadcast messages : Contains PID
    private TArrayList known = null;
    // private LinkedList known;
    // A-delivered messages : Set (AbcastMessageID)
    private THashSet aDelivered = null;
    // A-Undelivered messages : Ordered Maps (AbcastMessageID -> GroupCommMessage m)
    //    1) Messages proposed in a running consensus
    //    2) Other messages
    private TTreeMap proposed;
    private TTreeMap unproposed;
    // A Map between consensus ID and Messages proposed
    private TTreeMap KtoID;
    // Bound for old A-delivered messages : Table (PID -> Integer)
    private THashMap maxIdProProc = null;
    // id for consensus requests
    private long k;
    // id of the next consensus to be decided
    private long nextKToBeDecided;
    // Map of consensus decided too early
    private THashMap alreadyDecided;
    // Abcast message current id
    private AbcastMessageID abcastId;
    // How many consensus started ?
    private int consensusStarted;
 
    // The minimal number of undelivered messages 
    // allowing to start a new concurrent consensus
    private int Lmin = 4;   
    public static final int MAX_PROPOSE = 4;
    
    private TLinkedList whenAbcast;
    private TLinkedList whenPt2PtDeliver;
    private boolean whenWarning = false;

    /**
     * Set of suspected processes. It is handed over to the newly created consensus instances. 
     * It is updated every time a <i>suspect</i> event is handled.
     */
    private TSet suspected = new THashSet();
    /**
     * It contains the instances of all ongoing consensus
     */
    private TSet consensusInstances = new THashSet();
    
    // Logging
    private static final Logger logger =
        Logger.getLogger(ConcDynAbcastImpl.class.getName());
    
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
    public ConcDynAbcastImpl(Trigger abcast, FlowControl fc, PID myself) {
        logger.entering("ConcDynAbcastImpl","<constr>");    
        this.abcast = abcast;
        this.flow_control = fc;
        this.myself = myself;
        proposed = new TTreeMap();
        unproposed = new TTreeMap();
        KtoID = new TTreeMap();
        alreadyDecided = new THashMap();
        abcastId = new AbcastMessageID(myself, 0);
        // Lists to store events when
        // initData=false
        whenAbcast = new TLinkedList();
        whenPt2PtDeliver = new TLinkedList();
        logger.exiting("ConcDynAbcastImpl","<constr>");    
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
        logger.entering("ConcDynAbcastImpl","handleInit");    
        TList p = (TList)ev.removeFirst(); 
        
        if (initialized)
            throw new GroupCommException("AbcastImpl already initialized.");
        initialized = true;
        fc_key = flow_control.getFreshKey();
        k = 1;
        nextKToBeDecided = 1;
        consensusStarted = 0;
        if(p.isEmpty()){
            initData=false;
            // flow control
            //flow_control.setThreshold(fc_key, 2*MAX_PROPOSE);
            fc_threshold = 2*MAX_PROPOSE;
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
            // flow control
            //flow_control.setThreshold(fc_key, Math.max(MAX_PROPOSE / known.size(), 1));
            fc_threshold = Math.max(MAX_PROPOSE / known.size(), 1);
            // join-remove
            GroupCommEventArgs jrl = new GroupCommEventArgs();
            jrl.addLast (new THashSet(p)); // join
            jrl.addLast (new THashSet()); // remove
            abcast.trigger (Constants.JOINREMOVELIST, jrl);
        }
        logger.exiting("ConcDynAbcastImpl","handleInit");
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
        logger.entering("ConcDynAbcastImpl","handleAbcast");    

        //Flow control
        //flow_control.alloc(fc_key, 1);
        fc_used++; if(fc_used >= fc_threshold) flow_control.block(fc_key);
        //When (initData)
        if(!initData){
            whenAbcast.addLast(ev);
            logger.exiting("ConcDynAbcastImpl","handleAbcast");    
            return;
        }
        TInteger type = (TInteger)ev.removeFirst();
        GroupCommMessage payload = (GroupCommMessage)ev.removeFirst();
        // payload = m  
        if(type.intValue() != Constants.AM){
            payload.tpack(ev.removeFirst());
        }
        // payload = (pid)::m
        payload.tpack(type);
        // payload = type::(pid)::m
        payload.tpack(abcastId.nextId());
        // payload = id::type::(pid)::m
        payload.tpack(new TBoolean(false));
        // payload = isinit::id::type::(pid)::m
        Iterator it = ((TList)known.clone()).iterator();
        while (it.hasNext()) {
            PID pid = (PID)it.next();
            GroupCommMessage newMess = payload.cloneGroupCommMessage();
            GroupCommEventArgs pt2ptSend = new GroupCommEventArgs();
            pt2ptSend.addLast(newMess);
            pt2ptSend.addLast(pid);
            pt2ptSend.addLast(new TBoolean(false)); // not promisc
            abcast.trigger (Constants.PT2PTSEND, pt2ptSend);
        }	
        logger.exiting("ConcDynAbcastImpl","handleAbcast");    
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
        logger.entering("ConcDynAbcastImpl","handlePt2PtDeliver");    
        GroupCommMessage msg = (GroupCommMessage)ev.get(0);
        
        boolean isinit = ((TBoolean)msg.tunpack()).booleanValue();
        
        if(!initData && !isinit){
            msg.tpack(new TBoolean(false));
            //msg = false::<payload>
            whenPt2PtDeliver.addLast(ev);
            logger.log(Level.FINE,
                    "Added event to whenPt2PtDeliver.\n\tArguments:{0}",
                    ev);
            logger.exiting("ConcDynAbcastImpl","handlePt2PtDeliver");    
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
                nextKToBeDecided = k;
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
                
                // flow control
                //flow_control.setThreshold(fc_key, Math.max(MAX_PROPOSE / known.size(), 1));
                fc_threshold = Math.max(MAX_PROPOSE / known.size(), 1);
                
                initData = true;
                
                // Join_Remove_List event
                GroupCommEventArgs jrl = new GroupCommEventArgs();
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
                    fc_used--;
                    handleAbcast(e);
                }
                whenAbcast.clear(); //Just in case
            }//End of if(!initData)
        } else { // !isinit
            if(!initData) 
                throw new GroupCommException("DyAbcastImpl: Error: Trying to manage a " +
                "Pt2Pt message while InitData=false!");
            // msg = id::type::(pid)::payload
            GroupCommMessage mClone = msg.cloneGroupCommMessage();
            // mClone = msg
            AbcastMessageID id = (AbcastMessageID)msg.tunpack();
            // msg = type::(pid)::payload
            if( known.contains(id.proc) &&
                    (id.id > ((TLong)maxIdProProc.get(id.proc)).longValue()) &&
                    ! aDelivered.contains(id) &&
                    ! proposed.containsKey(id) &&
                    ! unproposed.containsKey(id)){
                unproposed.put(id, msg);//Doesn't need to be cloned
                Iterator it = known.iterator();
                mClone.tpack(new TBoolean(false));
                // msg = false::id::type::(pid)::payload
                
                //To deliver all events in the end
                TLinkedList toTrigger = new TLinkedList();
                
                while (it.hasNext()) {
                    PID pid = (PID)it.next();
                    GroupCommEventArgs pt2ptSend = new GroupCommEventArgs();
                    pt2ptSend.addLast(mClone.cloneGroupCommMessage());
                    pt2ptSend.addLast(pid);
                    pt2ptSend.addLast(new TBoolean(false)); // not promisc
                    toTrigger.addLast(new TriggerItem(Constants.PT2PTSEND, pt2ptSend));
                } // End of WHILE
                
                testAndConsensus(toTrigger);
                
                //Now we trigger all events scheduled
                while(!toTrigger.isEmpty()){
                    TriggerItem item = (TriggerItem)toTrigger.removeFirst();
                    abcast.trigger(item.type, item.args);
                }
            } //End of if (! aUndelivered
        } // End of else
        logger.exiting("ConcDynAbcastImpl","handlePt2PtDeliver");    
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
     * @exception DecideException Thrown when an unknow DecideEvent happends
     */
    public void handleDecide(GroupCommEventArgs ev) throws GroupCommException {
        logger.entering("ConcDynAbcastImpl","handleDecide");
        TTreeMap undelivered = (TTreeMap)ev.removeFirst();
        long kdecision = ((TLong)ev.removeFirst()).longValue();
        
        if (!initData) 
            throw new GroupCommException("ConcDynAbcastImpl: handleDecide: InitData = false");
        
        // Messages proposed and not decided are put in "unproposed"
        // Messages to be delivered are removed from "unproposed" and "proposed"      
        TLinkedList IDList = (TLinkedList) KtoID.remove(new TLong(kdecision));
	TCollection ids = undelivered.keySet();
	Iterator it = ids.iterator();
	while (it.hasNext()){
            AbcastMessageID id = (AbcastMessageID) it.next();
            IDList.remove(id);
            proposed.remove(id);
            unproposed.remove(id);
        }
        
        while (!IDList.isEmpty()){
            AbcastMessageID id = (AbcastMessageID) IDList.removeFirst();
            GroupCommMessage msg = (GroupCommMessage) proposed.remove(id);
            
            if (msg!=null)
                unproposed.put(id, msg);
        }
        
        if (kdecision != nextKToBeDecided){
            alreadyDecided.put(new TLong(kdecision), undelivered);
        } else{            
            // This list will contain all events to be triggered, and we'll 
            // trigger them at the end of this method
            TLinkedList toTrigger = new TLinkedList();

            // Deliver all messages possibles
            while (undelivered!=null){
                // First we adeliver all messages of type AM in undelivered
                // unless we've already done so (see condition in next "if")
                TSortedMap special = new TTreeMap();
                while ( !undelivered.isEmpty()) {
                    AbcastMessageID id = (AbcastMessageID)undelivered.firstKey();
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
                    AbcastMessageID id = (AbcastMessageID)special.firstKey();
                    GroupCommMessage msg = (GroupCommMessage)special.remove(id);
                    // msg = type::pid::payload
                    TInteger type=(TInteger)msg.tunpack();
                    // msg = pid::payload
                    PID pid = (PID)msg.tunpack();
                    // msg = payload
                    
                    
                    GroupCommEventArgs jrl = new GroupCommEventArgs();
		    GroupCommEventArgs startstop1 = new GroupCommEventArgs();
                    switch (type.intValue()){
                    case Constants.ADD:
                        if(!known.contains(pid)){
                            known.add(pid); //Add new process to known
                            newProc.add(pid); //Add it to newProc, too
                            // init maximum id of Adelivered message for newP
                            maxIdProProc.put(pid, new TLong(-1));
                            // flow control
                            //flow_control.setThreshold(fc_key, Math.max(MAX_PROPOSE / known.size(), 1));
                            fc_threshold = Math.max(MAX_PROPOSE / known.size(), 1);
                            if(fc_used >= fc_threshold) {
                                flow_control.block(fc_key);
                            }else{
                                flow_control.release(fc_key);
                            }
                            // join-remove
			    TSet singleton = new THashSet();
			    singleton.add(pid);
			    jrl.addLast (singleton); // join
                            jrl.addLast (new THashSet()); // remove
                            toTrigger.addLast(new TriggerItem(Constants.JOINREMOVELIST, jrl));
			    startstop1.addLast(singleton); //Start
			    startstop1.addLast(new THashSet()); //Stop
			    toTrigger.addLast(new TriggerItem(Constants.STARTSTOPMONITOR, startstop1));
                            triggerAdeliver(id, type, msg, pid, toTrigger);
                        } else {
                            //Remove ignored message from aUndelivered
                            proposed.remove(id);
                            unproposed.remove(id);
                        }
                    break;
                    case Constants.REM:
                        if(known.contains(pid)){
                            known.remove(pid);//Remove PID from known
                            // remove maximum id of Adelivered message for pid
                            maxIdProProc.remove(pid);
                            // flow control
                            //flow_control.setThreshold(fc_key, Math.max(MAX_PROPOSE / known.size(), 1));
                            fc_threshold = Math.max(MAX_PROPOSE / known.size(), 1);
                            if(fc_used >= fc_threshold) {
                                flow_control.block(fc_key);
                            }else{
                                flow_control.release(fc_key);
                            }            
                            // remove all msgs that came from pid in aUndelivered
                            // firstly for messages proposed
                            TTreeMap temp = new TTreeMap();
			    ids = undelivered.keySet();
			    it = ids.iterator();
                            while(it.hasNext()){
                                AbcastMessageID rid = (AbcastMessageID)it.next();
                                if(!rid.proc.equals(pid)){
                                    temp.put(rid, proposed.get(rid));
                                }
                            }
                            proposed = temp;
                            // then for the other messages
                            temp = new TTreeMap();
                            ids = unproposed.keySet();
                            it = ids.iterator();
                            while(it.hasNext()){
                                AbcastMessageID rid = (AbcastMessageID)it.next();
                                if(!rid.proc.equals(pid)){
                                    temp.put(rid, unproposed.get(rid));
                                }
                            }
                            unproposed = temp;                    
                            
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
                            TSortedMap tmp3 = new TTreeMap();
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
			    startstop1.addLast(new THashSet()); //Start
			    startstop1.addLast(singleton); //Stop
			    toTrigger.addLast(new TriggerItem(Constants.STARTSTOPMONITOR, startstop1));
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
                            proposed.remove(id);
                            unproposed.remove(id);
                        }
                    break;
                    default:
                        System.err.println("ConcDynAbcastImpl:Handle deliver:Unknown message type");
                    }
                }
                
                consensusStarted--;
                
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
                    it = newProc.iterator();
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
                        // firstly messages proposed
                        Iterator itUndelivered = (proposed.keySet()).iterator();
                        while (itUndelivered.hasNext()){
                            AbcastMessageID id = (AbcastMessageID)itUndelivered.next();
                            GroupCommMessage msg = (GroupCommMessage)proposed.get(id);
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
                        
                        // then other messages 
                        itUndelivered = (unproposed.keySet()).iterator();
                        while (itUndelivered.hasNext()){
                            AbcastMessageID id = (AbcastMessageID)itUndelivered.next();
                            GroupCommMessage msg = (GroupCommMessage)unproposed.get(id);
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
                }
                
                // Increase the Id of the next consensus to be decided
                nextKToBeDecided++;
                // Check if it is already decided
                undelivered = (TTreeMap) alreadyDecided.remove(new TLong(nextKToBeDecided));
            }
            
            //Finally, we trigger all events
            while(!toTrigger.isEmpty()){
                TriggerItem item = (TriggerItem)toTrigger.removeFirst();
                abcast.trigger(item.type, item.args);
            }
        }
        logger.exiting("ConcDynAbcastImpl","handleDecide");
    }

    /**
     * Handler for event <i>Suspect</i>.
     * Every time the Failure Detector changes its suspect list, it triggers an event that
     * this handler is bound to.
     *
     * @param e <dl>
     *              <dt> arg1 : Set[PID] </dt> <dd> The updated suspect list. </dd>
     *          </dl>
     */
    public void handleSuspect(GroupCommEventArgs e) {
	logger.entering("ConsensusHandlers", "handleSuspect");
	suspected = (TSet) e.get(0);
	logger.exiting("ConsensusHandlers", "handleSuspect");
    }
    
    private void triggerAdeliver(AbcastMessageID id, 
            TInteger type,
            GroupCommMessage msg,
            PID pid,
            TLinkedList toTrigger){
        logger.entering("ConcDynAbcastImpl","trigerAdeliver");
        // Remove the id from aUndelivered
        proposed.remove(id);
        unproposed.remove(id);
        //Is it an id from an "unknown process?"
        if(known.contains(id.proc)){
            // add it in aDelivered
            aDelivered.add(id);
            //Flow control
            if(id.proc.equals(myself)){
                //flow_control.free(fc_key, 1);
                fc_used--; if(fc_used < fc_threshold) flow_control.release(fc_key);
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
            System.err.println("ConcDynAbcastImpl:trigerAdeliver: Dind't" +
            " adeliver a msg from an unknown source!!!");
        }
        logger.exiting("ConcDynAbcastImpl","trigerAdeliver");
    }
    
    private void testAndConsensus(TLinkedList toTrigger) throws GroupCommException{
        logger.entering("ConcDynAbcastImpl","testAndConsensus");
        if (((consensusStarted == 0) && !unproposed.isEmpty()) ||
            (unproposed.size() > 0)) {
            
            //I only take maximum of message IDs for the consensus
            //Prevents glitches with tons of messages
            TCollection ids = unproposed.keySet();
            TSortedMap propose = new TTreeMap();
            TLinkedList IDList = new TLinkedList();
            Iterator it = ids.iterator();
            for  (int tune=0;
            tune < MAX_PROPOSE && it.hasNext();
            tune++){                
                AbcastMessageID id = (AbcastMessageID) it.next();
                propose.put(id, unproposed.get(id));
                proposed.put(id, unproposed.remove(id));
                IDList.add(id);
            }
            TLong kObj = new TLong(k);
            KtoID.put(kObj, IDList);
            logger.log(Level.FINE,
                    "Launching consensus#{1}:\n\tValue: {0}\n\tProcesses:{2}", 
                    new Object[]{propose, kObj, known});
            k++;

	    // Check if the localhost is in the group 
	    boolean found = false;
	    for (int i = 0; i < known.size(); i++)
		found = found || myself.equals(known.get(i));
	    if (!found)
		throw new GroupCommException("Consensus: The localhost "
					     + myself
					     + " is not in the group passed as parameter: "
					     + known);

	    if (known.size() == 1) {
		GroupCommEventArgs gaDecide = new GroupCommEventArgs();
		gaDecide.addLast(propose);
		gaDecide.addLast(kObj);
		handleDecide(gaDecide);
		logger.exiting("ConcDynAbcastImpl","testAndConsensus");
		return;
	    }

            consensusStarted++;
	    consensusInstances.add(kObj);

	    GroupCommEventArgs ga = new GroupCommEventArgs();
	    ga.add(myself);
	    ga.add(kObj);
	    ga.add(suspected);
	    ga.add(propose);
	    ga.add(known);
	    toTrigger.addLast(new TriggerItem(Constants.PROPOSE, ga));
        }
        logger.exiting("ConcDynAbcastImpl","testAndConsensus");
    }

    /**
     * Used for debug
     *
     * @param out The output stream used for showing infos
     */
    
    public void dump(OutputStream out) {
        PrintStream err = new PrintStream(out);
        err.println("===== ConcDynAbcastImpl: dump ====");
        err.println(" Initialized: " + initialized);
        err.println(" When clauses.\n\tWhenAbcast " + whenAbcast);
        err.println("\tWhenPt2PtDeliver " + whenPt2PtDeliver);
        err.println(" ConsensusRunning: " + consensusStarted);
        err.println(" Next consensus id: " + k);
        err.println(" Next AbcastMessage id:" + abcastId);
        err.println(" Flow Control threshold: "+fc_threshold);
        err.println("\t used: "+fc_used);
        err.println(" initData: " + initData);
        if(initData){
            err.println(" Known processes: size: " + known.size());
            Iterator it = known.iterator();
            PID pid;
            while (it.hasNext()) {
                pid = (PID)it.next();
                err.println("\t" + pid);
            }
            err.println(" A-Undelivered proposed messages:");
            err.println("   " + proposed);
            err.println(" A-Undelivered other messages:");
            err.println("   " + unproposed);            
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
