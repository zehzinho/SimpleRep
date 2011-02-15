/**
 *  Fortika - Robust Group Communication
 *  Copyright (C) 2002-2006  Sergio Mena de la Cruz (EPFL) (sergio.mena@@epfl.ch)
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
package groupcomm.common.order;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.logging.Logger;

import uka.transport.Transportable;
import framework.Constants;
import framework.GroupCommEventArgs;
import framework.GroupCommException;
import framework.GroupCommMessage;
import framework.PID;
import framework.libraries.DefaultSerialization;
import framework.libraries.FlowControl;
import framework.libraries.Trigger;
import framework.libraries.serialization.TBoolean;
import framework.libraries.serialization.THashMap;
import framework.libraries.serialization.THashSet;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TLinkedList;
import framework.libraries.serialization.TList;
import framework.libraries.serialization.TMap;

public class CausalOrderImpl {
	// Identifier of Causal Ordering Messages
	public static final int CAUSAL_ORDER_SEND = 1;

	public static final int CAUSAL_ORDER_ACK = 3;

    // ID of the next messages to be sent
    private CausalOrderMessageID causalID;
    
	// Messages currently sent
	private TMap buffer;

	// Messages not received but acknowledged
	private TMap bufferAck;

	// Messages to be sent
	private TLinkedList outqueue;

	// Trigger class for events routing
	private Trigger trigger;

	// Local ID
	private PID myself;

	// FlowControl
	private FlowControl fc;

	private int fc_key;

	// Flow Control Settings
	private static int MAX_PENDING_MESSAGES = 100;

	private static final Logger logger = Logger.getLogger(CausalOrderImpl.class
			.getName());
    
    private static class TriggerItem {
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
	 * @@param CausalOrder
	 *            object of a framework protocol based class, which ensure event
	 *            routing for this protocol.
	 */
	public CausalOrderImpl(Trigger CausalOrder, FlowControl fc, PID myself) {
		logger.entering("CausalOrderImpl", "<constr>");
		this.trigger = CausalOrder;
		this.fc = fc;
		this.myself = myself;
        this.causalID = new CausalOrderMessageID(myself, 0);
		this.buffer = new THashMap();
		this.bufferAck = new THashMap();
		this.outqueue = new TLinkedList();
		logger.exiting("CausalOrderImpl", "<constr>");
	}

	/**
	 * Handler for the <i>Init</i> event. </br> It sends the list of known
	 * processes to the lower layer allowing them to communicate with us
	 * 
	 * @@param ev
	 *            <dl>
	 *            <dt> arg1 : Set[PID] </dt>
	 *            <dd> List of processes for broadcasting </dd>
	 *            </dl>
	 * 
	 * @@throws GroupCommException
	 * @@throws IOException
	 * @@throws ClassNotFoundException
	 */
	public void handleInit(GroupCommEventArgs ev) throws GroupCommException,
			IOException, ClassNotFoundException {
		logger.entering("CausalOrderImpl", "handleInit");
		TList p = (TList) ev.removeFirst();

		// Get a kex for the FlowControl
		this.fc_key = fc.getFreshKey();

		// Look for duplicate processes in the group
		for (int i = 0; i < p.size(); i++) {
			for (int j = i + 1; j < p.size(); j++)
				if (p.get(i).equals(p.get(j)))
					throw new GroupCommException("Process" + p.get(i)
							+ " appears more than once in the group.");
		}

		// join-remove
		GroupCommEventArgs jrl = new GroupCommEventArgs();
		jrl.addLast(new THashSet(p)); // join
		jrl.addLast(new THashSet()); // remove
		trigger.trigger(Constants.JOINREMOVELIST, jrl);

		logger.exiting("CausalOrderImpl", "handleInit");
	}

	/**
	 * The handler for the <i>CausalOrderSend</i> event. <br/> It send the message to
	 * the specificied process while respecting CausalOrder order. The message are
	 * delivered in the rder they were sent. <br/>
	 * 
	 * @@param ev
	 *            <dl>
	 *            <dt> arg1: GroupCommMessage </dt>
	 *            <dd> The message </dd>
	 *            <dt> arg2: PID </dt>
	 *            <dd> The destination </dd>
	 *            </dl>
	 * @@throws GroupCommException
	 * @@throws IOException
	 * @@throws ClassNotFoundException
	 */
	public void handleCausalOrderSend(GroupCommEventArgs ev)
			throws GroupCommException {
		logger.entering("CausalOrderImpl", "handleCausalOrdersend");
        
        LinkedList toTrigger = new LinkedList();

		if (buffer.isEmpty()) {
			proceedSendMessage(toTrigger, ev);
            proceedWithTrigger(toTrigger);
		} else {
			outqueue.addLast(ev);
            if ((outqueue.size()+buffer.size()) > MAX_PENDING_MESSAGES)
                fc.block(fc_key);
		}        
		logger.exiting("CausalOrderImpl", "handleCausalOrdersend");
	}

	/**
	 * The handler for the <i>Pt2PtDeliver</i> event. <br/> When we recieve a
	 * message from the Reliable communication layer, we have to resent the
	 * message to all the receipents, if it's the first time it arrives. That's
	 * the R-Broadcast part of the protocol. It launch a consensus too.
	 * 
	 * @@param ev
	 *            <dl>
	 *            <dt> arg1: GroupCommMessage (id::m) </dt>
	 *            <dd> The message, with an id </dd>
	 *            <dt> arg2: PID </dt>
	 *            <dd> Source PID </dd>
	 *            </dl>
	 */
	public void handlePt2PtDeliver(GroupCommEventArgs ev) throws GroupCommException {
		logger.entering("CausalOrderImpl", "handlePt2PtDeliver");
		// msg = id::m
		// msgClone = id::m
		GroupCommMessage msg = (GroupCommMessage) ev.get(0);		
		PID source = (PID) ev.get(1);
		int type = ((TInteger) msg.removeFirst()).intValue();
        CausalOrderMessageID cID = (CausalOrderMessageID) msg.removeFirst();

        LinkedList toTrigger = new LinkedList();
        
		switch (type) {
		case CAUSAL_ORDER_SEND:
			proceedMessage(toTrigger, msg, cID, source);
			break;
		case CAUSAL_ORDER_ACK:
			proceedAck(toTrigger, cID, source);
			break;
		default:
			System.err.println("SHOULD NEVER HAPPEN");
		}

        proceedWithTrigger(toTrigger);
		logger.exiting("CausalOrderImpl", "handlePt2PtDeliver");
	}

    // Execute all the trigger in list of TriggerItem toTrigger
    private void proceedWithTrigger(LinkedList toTrigger) {
        while (!toTrigger.isEmpty()) {
            TriggerItem tItem = (TriggerItem) toTrigger.removeFirst();
            trigger.trigger(tItem.type, tItem.args);
        }
    }
    
	/**
	 * Send the message contains in GroupCommEventArgs
	 */
	private void proceedSendMessage(LinkedList toTrigger, GroupCommEventArgs ev) 
		throws GroupCommException {
		GroupCommMessage gm = (GroupCommMessage) ev.get(0);
		TList destTmp = new TLinkedList((TList) ev.get(1));
        TList destList = new TLinkedList((TList) ev.get(1));
		CausalOrderMessageID cID = causalID.nextId();

		buffer.put(cID, destList);
        gm.addFirst(ev.remove(1));
		gm.addFirst(cID);
        gm.addFirst(new TInteger(CAUSAL_ORDER_SEND));

		while (!destTmp.isEmpty()) {
			GroupCommEventArgs evSent = new GroupCommEventArgs();
			GroupCommMessage sent; 

			PID dest = (PID) destTmp.remove(0);
            
            if (dest.equals(myself)){
                destList.remove(myself);
                
                try {
                	sent = (GroupCommMessage) deepClone(gm);
                } catch (Exception ex) {
                	throw new GroupCommException("Unable to send "+gm.toString());
                }
                
                // Deliver the message
                GroupCommEventArgs evDel = new GroupCommEventArgs();
                // Remove type::cID::dest from message sent
                sent.tunpack();
                sent.tunpack();
                sent.tunpack();
                evDel.addLast(sent);        
                evDel.addLast(myself);
                toTrigger.addLast(new TriggerItem(Constants.CODELIVER, evDel));
                
                // Send an ack to all processes in dest
                int sizeDestList = destList.size();
                for (int i=0; i<sizeDestList; i++){
                    GroupCommEventArgs evAck = new GroupCommEventArgs();
                    GroupCommMessage ack = new GroupCommMessage();
                    ack.addFirst(new TInteger(CAUSAL_ORDER_ACK));
                    ack.addLast(cID);
                    evAck.addLast(ack);
                    evAck.addLast(destList.get(i));
                    evAck.addLast(new TBoolean(false));
                    toTrigger.addLast(new TriggerItem(Constants.PT2PTSEND, evAck));
                }
            } else {
                // msg
            	sent = gm.cloneGroupCommMessage();
            	evSent.addLast(sent);
                evSent.addLast(dest);
                evSent.addLast(new TBoolean(false));
                toTrigger.addLast(new TriggerItem(Constants.PT2PTSEND, evSent));
            }
		}
	}

	/**
	 * Proceed acknolegdement
	 */
	private void proceedAck(LinkedList toTrigger, CausalOrderMessageID cID, PID source) throws GroupCommException {
		TLinkedList dst = (TLinkedList) buffer.get(cID);
        
        // If the message is already known
        // remove the source of the ack and if 
        // we received an ack from all p in dest
        // proceed with the next message
		if (dst!=null) {
			dst.remove(source);
			if (dst.isEmpty()) {
				buffer.remove(cID);
				if ((buffer.isEmpty()) && (!outqueue.isEmpty())) {
					GroupCommEventArgs ev = (GroupCommEventArgs) outqueue
							.removeFirst();
					proceedSendMessage(toTrigger, ev);
				}
			}
		} else { 
            // If the message is not already known
            // store the ack in bufferAck
            // the ack will be processed on reception
            // of the message
		    TLinkedList acked = (TLinkedList) bufferAck.get(cID);
            
            if (acked == null){
                acked = new TLinkedList();
                bufferAck.put(cID, acked);
            }
            
            acked.add(source);
		}
	}

	/**
	 * Proceed acknolegdement
	 */
	private void proceedMessage(LinkedList toTrigger, GroupCommMessage msg, CausalOrderMessageID cID,
			PID source) {
	    TLinkedList destList = (TLinkedList) msg.removeFirst();
        TLinkedList ackList = (TLinkedList) bufferAck.get(cID);
       
        // Deliver the message
        GroupCommEventArgs evDel = new GroupCommEventArgs();
        evDel.addLast(msg);        
        evDel.addLast(source);
        toTrigger.addLast(new TriggerItem(Constants.CODELIVER, evDel));
        
        // Send an ack to all processes in dest
        int sizeDestList = destList.size();
        for (int i=0; i<sizeDestList; i++){
            GroupCommEventArgs evAck = new GroupCommEventArgs();
            GroupCommMessage ack = new GroupCommMessage();
            ack.addFirst(new TInteger(CAUSAL_ORDER_ACK));
            ack.addLast(cID);
            evAck.addLast(ack);
            evAck.addLast(destList.get(i));
            evAck.addLast(new TBoolean(false));
            toTrigger.addLast(new TriggerItem(Constants.PT2PTSEND, evAck));
        }
        
        // Send an ack to the the source if the source is not in the dest
        if (!destList.contains(source)){
            GroupCommEventArgs evAck = new GroupCommEventArgs();
            GroupCommMessage ack = new GroupCommMessage();
            ack.addFirst(new TInteger(CAUSAL_ORDER_ACK));
            ack.addLast(cID);
            evAck.addLast(ack);
            evAck.addLast(source);
            evAck.addLast(new TBoolean(false));
            toTrigger.addLast(new TriggerItem(Constants.PT2PTSEND, evAck));
        }
        
        // Update the set of processes that has acknoledged 
        destList.remove(myself);
        if (ackList != null)
            while (!ackList.isEmpty())
                destList.remove(ackList.removeFirst());

        buffer.put(cID, destList);
	}

    private Transportable deepClone(Transportable o) throws IOException,
			ClassNotFoundException {
		// TODO: There have to be better ways to do deep-clone!!!
		return DefaultSerialization
				.unmarshall(DefaultSerialization.marshall(o));
	}	
	/**
	 * Used for debug
	 * 
	 * @@param out
	 *            The output stream used for showing infos
	 */

	public void dump(OutputStream out) {
		PrintStream err = new PrintStream(out);
		err.println("======== CausalOrderImpl: dump =======");
		err.println(" Buffered Messages: " + String.valueOf(buffer));
		err.println(" Pending Messages: " + String.valueOf(outqueue));
        err.println(" Already received ack: " + String.valueOf(bufferAck));
		err.println("==================================");
	}
}
