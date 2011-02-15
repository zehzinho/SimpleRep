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
import java.util.logging.Logger;

import framework.Constants;
import framework.GroupCommEventArgs;
import framework.GroupCommException;
import framework.GroupCommMessage;
import framework.PID;
import framework.libraries.Trigger;
import framework.libraries.serialization.TBoolean;
import framework.libraries.serialization.THashMap;
import framework.libraries.serialization.THashSet;
import framework.libraries.serialization.TList;
import framework.libraries.serialization.TLong;
import framework.libraries.serialization.TMap;

/**
 * <b> This class implements the common code that ensures FIFO ordered channels.
 * </b>
 * <hr>
 * <b> Events:
 * <dt> <i>Init</i> </dt>
 * <dd> Initializes the fifo layer </dd>
 * <dt> <i>FIFOSend</i> </dt>
 * <dd> Send a Broadcast message, with the abcast algorithm </dd>
 * <dt> <i>Pt2PtDeliver</i> </dt>
 * <dd> Happend when a message is received by the lower layers </dd>
 * </dl>
 */
public class FIFOImpl {

	private TMap messagesSentToProc;

	private TMap messagesRecvByProc;

	private TMap messagesToDeliver;

	// Trigger class for events routing
	private Trigger trigger;

	private static final Logger logger = Logger.getLogger(FIFOImpl.class
			.getName());

	/**
	 * Constructor.
	 * 
	 * @@param fifo
	 *            object of a framework protocol based class, which ensure event
	 *            routing for this protocol.
	 */
	public FIFOImpl(Trigger fifo) {
		logger.entering("FIFOImpl", "<constr>");
		this.trigger = fifo;
		messagesSentToProc = new THashMap();
		messagesRecvByProc = new THashMap();
		messagesToDeliver = new THashMap();
		logger.exiting("FIFOImpl", "<constr>");
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
		logger.entering("FIFOImpl", "handleInit");
		TList p = (TList) ev.removeFirst();

		// Look for duplicate processes in the group
		for (int i = 0; i < p.size(); i++) {
			messagesSentToProc.put(p.get(i), new TLong(0));
			messagesRecvByProc.put(p.get(i), new TLong(0));
			messagesToDeliver.put(p.get(i), new THashMap());
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

		logger.exiting("FIFOImpl", "handleInit");
	}

	/**
	 * The handler for the <i>FIFOSend</i> event. <br/> It send the message to
	 * the specificied process while respecting FIFO order. The message are
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
	public void handleFIFOSend(GroupCommEventArgs ev)
			throws GroupCommException {
		logger.entering("FIFOImpl", "handleFIFOsend");

		GroupCommMessage gm = (GroupCommMessage) ev.remove(0);
		TList destList = (TList) ev.remove(0);

		while (!destList.isEmpty()) {
			GroupCommMessage gmSend = gm.cloneGroupCommMessage();
			GroupCommEventArgs evSend = new GroupCommEventArgs();
			PID dest = (PID) destList.remove(0);
			TLong sn = (TLong) messagesSentToProc.get(dest);

			if (sn == null)
				throw new GroupCommException("Process " + dest
						+ " not in the group");

			// msg
			gmSend.addFirst(sn);
			evSend.addLast(gmSend);
			evSend.addLast(dest);
			evSend.addLast(new TBoolean(false));

			messagesSentToProc.put(dest, new TLong(sn.longValue() + 1));

			trigger.trigger(Constants.PT2PTSEND, evSend);
		}
		logger.exiting("FIFOImpl", "handleFIFOsend");
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
	public void handlePt2PtDeliver(GroupCommEventArgs ev) {
		logger.entering("FIFOImpl", "handlePt2PtDeliver");
		// msg = id::m
		// msgClone = id::m
		GroupCommMessage msg = (GroupCommMessage) ev.get(0);
		PID source = (PID) ev.get(1);
		long sn = ((TLong) msg.removeFirst()).longValue();
		long current = ((TLong) messagesRecvByProc.get(source)).longValue();
		THashMap msgs = (THashMap) messagesToDeliver.get(source);

		if (sn == current) {
			// Deliver the message sn and all the following ones that
			// are already received.
			while (ev != null) {
				current++;
				trigger.trigger(Constants.FIFODELIVER, ev);
				ev = (GroupCommEventArgs) msgs.remove(new TLong(current));
			}

			messagesRecvByProc.put(source, new TLong(current));
		} else {
			msgs.put(new TLong(sn), ev);
		}

		logger.exiting("FIFOImpl", "handlePt2PtDeliver");
	}

	/**
	 * Used for debug
	 * 
	 * @@param out
	 *            The output stream used for showing infos
	 */

	public void dump(OutputStream out) {
		PrintStream err = new PrintStream(out);
		err.println("======== FIFOImpl: dump =======");
		err.println(" Buffered Messages: " + String.valueOf(messagesToDeliver));
		err.println(" Number of Messages Sent: "
				+ String.valueOf(messagesSentToProc));
		err.println(" Number of Messages Recv: "
				+ String.valueOf(messagesRecvByProc));
		err.println("==================================");
	}
}
