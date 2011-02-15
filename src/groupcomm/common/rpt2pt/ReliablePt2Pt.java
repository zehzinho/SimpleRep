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
package groupcomm.common.rpt2pt;

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
import framework.libraries.Serialize;
import framework.libraries.Timer;
import framework.libraries.Trigger;
import framework.libraries.serialization.TBoolean;
import framework.libraries.serialization.TByteArray;
import framework.libraries.serialization.TCollection;
import framework.libraries.serialization.THashMap;
import framework.libraries.serialization.THashSet;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TLinkedList;
import framework.libraries.serialization.TMap;
import framework.libraries.serialization.TSet;
import framework.libraries.tcp.Connection;
import framework.libraries.tcp.NonBlockingTCP;
import framework.libraries.tcp.Server;

/**
 * <b> This class implements the event handlers for the Reliable Channel layer. </b>
 * <hr>
 * <b> Input Events: </b>
 * <dl>
 * <dt> <i>Init</i>           </dt> <dd> Initializes the Reliable and unreliable layer. </dd>
 * <dt> <i>Pt2PtSend</i>      </dt> <dd> Sends a message to a process reliably.         </dd>
 * <dt> <i>Recv</i>           </dt> <dd> Receives a message from a process unreliably.  </dd>
 * <dt> <i>JoinRemoveList</i> </dt> <dd> Processes to be added or to be removed 
 *                                       into/from the known processes.                 </dd>
 * <b> Output Events: </b>
 * <dl>
 * <dt> <i>Pt2PtDeliver</i>   </dt> <dd> Delivers a message to the upper layer.         </dd>
 * <dt> <i>Send</i>           </dt> <dd> Sends a message using the network transport
 * </dl>
 */
public class ReliablePt2Pt {
	//State Definitions for the state-machine
	private static final int ST_NULL = 0;
	private static final int ST_HIDDEN = 1;
	private static final int ST_CONNECTING = 2;
	private static final int ST_CONNECTED = 3;
	private static final int ST_CLOSED = 4;
	private static final int ST_CROSS = 5;

	private static final int SUSPECT = 1;
	private static final int RETRY = 2;

	private static final int FC_THRESHOLD = 3000;
	private static final int TO_SUSPECT = 60 * 1000; //Miliseconds
	private static final int TO_RETRY_CONNECT = 5 * 1000; //Miliseconds
	private static final int MAX_RETRIES = 200;

	private static final int PROMISC = 1;
	private static final int NORMAL = 2;
	private static final int ACK = 3;
	private static final int CHOSEN = 4;

	private int fc_threshold;
	private int smoothness = 1000;
	private int to_suspect;
	private int to_retry_connect;

	private PID myself;
	private FlowControl flow_control = null;
	private int fc_key;
	private Trigger trigger = null;
	private Timer timer = null;
	private NonBlockingTCP tcp = null;
	private Serialize serialize = null;

	private TMap connections = null;
	private Server server = null;
	private boolean blocked = false;

	private static final Logger logger =
		Logger.getLogger(ReliablePt2Pt.class.getName());

	public static class ConnectionData implements Transportable{
        //TODO: remove implements Transportable

		protected Connection connection;
		protected Connection connection2;
		//TODO: what would happen if these guys were SETS (no order)
		protected TLinkedList bufferOut; //Can't be TLinkedList
		protected TLinkedList bufferIn;
		protected int state;
		protected boolean full;
		protected boolean sendingThreadReady;
		protected int retries;
		protected int joins;

		protected ConnectionData(int state) {
			connection = null;
			connection2 = null;
			bufferOut = new TLinkedList();
			bufferIn = new TLinkedList();
			this.state = state;
			full = false;
			sendingThreadReady = false;
			retries = 0;
			joins = 1;
		}

		public String toString() {
			String st = "ST_NULL";
			switch (this.state) {
				case 1 :
					st = "ST_HIDDEN";
					break;
				case 2 :
					st = "ST_CONNECTING";
					break;
				case 3 :
					st = "ST_CONNECTED";
					break;
				case 4 :
					st = "ST_CLOSED";
					break;
				case 5 :
					st = "ST_CROSS";
			}
			return new String(
				"ConnectionData: "
					+ "\n\tConnection: "
					+ connection
					+ "\n\tConnection2: "
					+ connection2
					+ "\n\tBufferOut (size): "
					+ bufferOut.size()
					+ "\n\tBufferIn: "
					+ bufferIn
					+ "\n\tState: "
					+ st
					+ "\n\tFull: "
					+ full
					+ "\n\tSendingThreadReady: "
					+ sendingThreadReady
					+ "\n\tJoins: "
					+ joins);
		}

		public void marshal(MarshalStream arg0) throws IOException {
            throw new IOException("unimplemented!");
		}
		public void unmarshalReferences(UnmarshalStream arg0) throws IOException, ClassNotFoundException {
            throw new IOException("unimplemented!");
		}
		public Object deepClone(DeepClone arg0) throws CloneNotSupportedException {
            throw new CloneNotSupportedException("unimplemented!");
		}
	}

	/**
	 * Constructor.
	 *
	 */
	public ReliablePt2Pt(
		Trigger trigger,
		FlowControl fc,
		NonBlockingTCP tcp,
		Serialize serialize,
		Timer timer,
		PID myself) {

		logger.entering("ReliablePt2Pt", "<constr>");
		this.trigger = trigger;
		this.flow_control = fc;
		this.tcp = tcp;
		this.serialize = serialize;
		this.timer = timer;
		this.myself = myself;
		connections = new THashMap();
		logger.exiting("ReliablePt2Pt", "<constr>");
	}

	public void handleInit(GroupCommEventArgs arg) throws GroupCommException {
		logger.entering("ReliablePt2Pt", "handleInit");
		int fc_threshold = ((TInteger) arg.removeFirst()).intValue();
		int to_suspect = ((TInteger) arg.removeFirst()).intValue();
		int to_retry_connect = ((TInteger) arg.removeFirst()).intValue();

		if (fc_threshold == 0)
			fc_threshold = FC_THRESHOLD;
		if (to_suspect == 0)
			to_suspect = TO_SUSPECT;
		if (to_retry_connect == 0)
			to_retry_connect = TO_RETRY_CONNECT;

		fc_key = flow_control.getFreshKey();
		//flow_control.setThreshold(fc_key, 1);
		this.fc_threshold = fc_threshold;

		GroupCommMessage suspect = new GroupCommMessage();
		suspect.tpack(new TInteger(SUSPECT));
		timer.schedule(suspect, true, to_suspect);
		this.to_suspect = to_suspect;
		this.to_retry_connect = to_retry_connect;

		this.blocked = false;

		server = tcp.startServer(myself);

		logger.exiting("ReliablePt2Pt", "handleInit");
	}

	public void handleJoinRemoveList(GroupCommEventArgs arg)
		throws GroupCommException {
		logger.entering("ReliablePt2Pt", "handleJoinRemoveList");
		TSet newProcesses = (TSet) arg.removeFirst();
		TSet removeProcesses = (TSet) arg.removeFirst();

		// Join
		Iterator i = newProcesses.iterator();
		while (i.hasNext()) {
			PID pid = (PID) i.next();
			doJoin(pid);
		}

		// Remove
		i = removeProcesses.iterator();
		while (i.hasNext()) {
			PID pid = (PID) i.next();
			doRemove(pid);
		}
		logger.exiting("ReliablePt2Pt", "handleJoinRemoveList");
	}

	/**
	 * Add a new process to already known processes. </br>
	 *
	 * @param sp         The set of processes to add.
	 */
	private void doJoin(PID pid) throws GroupCommException {
		logger.entering("ReliablePt2Pt", "doJoin");
		//		if (pid.equals(myself)) {
		//            selfConnected++;
		//			logger.exiting("ReliablePt2Pt", "doJoin");
		//			return;
		//		}
		switch (getState(pid)) {
			case ST_NULL :
				//Creates a new connection
				tcp.connect(pid);
				setState(pid, ST_CONNECTING);
				//This resets the nomber of retries
				logger.log(
					Level.FINE,
					"Connection to {0} created. State ST_CONNECTING",
					pid);
				break;
			case ST_HIDDEN :
				//Reusing the connection that pid already established
				setState(pid, ST_CONNECTED);
				logger.log(
					Level.FINE,
					"Hidden connection to {0} reused. State ST_CONNECTED",
					pid);
				tcp.startSender(getConnection(pid));
				tcp.startReceiver(getConnection(pid));
				flushInputBuffer(pid);
				break;
			case ST_CONNECTING :
			case ST_CONNECTED :
			case ST_CLOSED :
			case ST_CROSS :
				logger.log(
					Level.FINE,
					"Connecting more than once to {0}.",
					pid);
				incJoins(pid);
				break;
			default :
				throw new GroupCommException(
					"ReliablePt2Pt:doJoin:"
						+ "Hmmm, weird. State of pid unknown");
		}
		logger.exiting("ReliablePt2Pt", "doJoin");
	}

	/**
	 * Remove a set of processes from already known processes.
	 *
	 * @param lp         The set of processes to remove.
	 */
	private void doRemove(PID pid) throws GroupCommException {
		logger.entering("ReliablePt2Pt", "doRemove");
		//		if (pid.equals(myself)) {
		//			if (--selfConnected < 0)
		//				throw new GroupCommException(
		//					"Trying to self-disconnect while not self-connected.");
		//			logger.exiting("ReliablePt2Pt", "doRemove");
		//			return;
		//		}
		switch (getState(pid)) {
			case ST_NULL :
				throw new GroupCommException(
					"Disconnnecting from unknown process " + pid);
			case ST_HIDDEN :
				throw new GroupCommException("Connection to pid doesn't exist (hidden)");
			case ST_CROSS :
			case ST_CONNECTED :
			case ST_CONNECTING :
				if (!decJoins(pid)) {
					if (getState(pid) != ST_CONNECTING)
						tcp.disconnect(getConnection(pid));
					removeState(pid); //We forget the process
					logger.log(
						Level.FINE,
						"Disconnecting from {0}. Forgetting process",
						pid);
					//Check if we can release the application
					// (Application is blocked and no queue is full)
					if (blocked && !fullQueues()) {
						//Release application
						flow_control.release(fc_key);
						logger.fine("Releasing application's flow control");
						blocked = false;
					}
				}
				break;
			case ST_CLOSED :
				if (!decJoins(pid)) {
					//Connection ceased to exist: no need to close
					removeState(pid); //We forget the process
					logger.log(
						Level.FINE,
						"Already disconneced from {0}. Just forgetting it",
						pid);
				}
				break;
			default :
				throw new GroupCommException(
					"ReliablePt2Pt:doRemove:"
						+ "Hmmm, weird. State of pid unknown");
		}
		logger.exiting("ReliablePt2Pt", "doRemove");
	}

	public void handleConnected(GroupCommEventArgs arg)
		throws GroupCommException {
		logger.entering("ReliablePt2Pt", "handleConnected");
		PID pid = (PID) arg.removeFirst();
		Connection c = (Connection) arg.removeFirst();

		if (c == null) {
			if (getState(pid) == ST_NULL) {
				logger.log(
					Level.INFO,
					"Disconneced from {0} when handling connected. Weird",
					pid);
				return;
			}
			ConnectionData cd = (ConnectionData) connections.get(pid);
			cd.retries++;

			GroupCommMessage retry = new GroupCommMessage();
			//msg = RETRY::pid
			retry.tpack(pid);
			retry.tpack(new TInteger(RETRY));
			timer.schedule(retry, false, to_retry_connect);
			logger.log(
				Level.FINE,
				"Connection establishment to {0} failed. Retrying...",
				pid);

			logger.exiting("ReliablePt2Pt", "handleConnected");
			return;
		}

		switch (getState(pid)) {
			case ST_NULL :
				//Maybe I Connected and Disconnected too swiftly
				//We disconnect the socket
				tcp.disconnect(c);
				logger.log(
					Level.INFO,
					"Received connected from NULL-state connection to {0}. Disconnecting",
					pid);
				break;
			case ST_HIDDEN :
				throw new GroupCommException(
					"ReliablePt2Pt:handleConnected:"
						+ "Two incomming (hidden) connections for the same pid??");
			case ST_CONNECTING :
				if (myself.equals(pid)) {
					//I am self-connecting
					setState(pid, ST_CONNECTED);
					mapConnection(pid, c);
					tcp.startSender(c);
				}
			case ST_CROSS :
				//Ok, now we need to wait for the ACK message from the other endpoint
				tcp.startReceiver(c);
				break;
			case ST_CONNECTED :
				throw new GroupCommException(
					"ReliablePt2Pt:handleConnected:"
						+ "Two connections for the same pid??");
			case ST_CLOSED :
				//Maybe I Connected and Disconnected too swiftly
				//  and I was in a crossed connection
				//We ignore it
				tcp.disconnect(c);
				logger.log(
					Level.INFO,
					"Received connected from CLOSED-state connection to {0}. Disconnecting",
					pid);
				break;
			default :
				throw new GroupCommException(
					"ReliablePt2Pt:handleConnected:"
						+ "Hmmm, weird. State of pid unknown");
		}
		logger.exiting("ReliablePt2Pt", "handleConnected");
	}

	public void handleClosed(GroupCommEventArgs arg)
		throws GroupCommException {
		logger.entering("ReliablePt2Pt", "handleClosed");
		Connection c = (Connection) arg.removeFirst();
		PID pid = tcp.getRemotePID(c);

		//Shutdown the sending stream
		tcp.disconnect(c);

		closed_broken(pid, c);
		logger.exiting("ReliablePt2Pt", "handleClosed");
	}

	public void handleBroken(GroupCommEventArgs arg)
		throws GroupCommException {
		logger.entering("ReliablePt2Pt", "handleBroken");
		Connection c = (Connection) arg.removeFirst();
		PID pid = tcp.getRemotePID(c);

		//Same treatment as HandleClosed
		closed_broken(pid, c);
		logger.exiting("ReliablePt2Pt", "handleBroken");
	}

	private void closed_broken(PID pid, Connection c)
		throws GroupCommException {
		logger.entering("ReliablePt2Pt", "closed_broken");
		switch (getState(pid)) {
			case ST_NULL :
				//Crossed closing
				//We ignore it
				logger.log(Level.INFO, "Ignoring crossed closing to: {0}", pid);
				break;
			case ST_HIDDEN :
				//Some process contacted me and then disconnected. But I don't know it
				removeState(pid); //We forget the process
				logger.log(Level.FINE, "Hidden connection closed by: {0}", pid);
				break;
			case ST_CONNECTED :
				ConnectionData cd = (ConnectionData) connections.get(pid);
				if (c != cd.connection) {
					//Closing not-chosen connection
					logger.log(
						Level.FINE,
						"Closing not-chosen connection to: {0}",
						pid);
					break;
				}
			case ST_CROSS :
				//The remote process has closed the connection
				setState(pid, ST_CLOSED);
				unmapConnection(pid);
				logger.log(Level.FINE, "Connection closed by: {0}", pid);
				((ConnectionData) connections.get(pid)).bufferOut.clear();
				//Check if we can release the application
				// (Application is blocked and no queue is full)
				if (blocked && !fullQueues()) {
					//Release application
					flow_control.release(fc_key);
					logger.fine("Releasing application's flow control");
					blocked = false;
				}
				break;
			case ST_CONNECTING :
				//Maybe incarnations didn't match
				logger.log(
					Level.FINE,
					"Connection closed by: {0} (while in ST_CONNECTING)",
					pid);
				setState(pid, ST_CLOSED);
				((ConnectionData) connections.get(pid)).bufferOut.clear();
				//Check if we can release the application
				// (Application is blocked and no queue is full)
				if (blocked && !fullQueues()) {
					//Release application
					flow_control.release(fc_key);
					logger.fine("Releasing application's flow control");
					blocked = false;
				}
				break;
			case ST_CLOSED :
				//Maybe both the reader and the sender threads are reporting
				// the same connection broken. Ignore it
				logger.log(
					Level.FINE,
					"The remote pid {0} can't close a connection twice",
					pid);
				break;
			default :
				throw new GroupCommException(
					"ReliablePt2Pt:handleClosed:"
						+ "Hmmm, weird. State of pid unknown");
		}
		logger.exiting("ReliablePt2Pt", "closed_broken");
	}

	public void handleAccepted(GroupCommEventArgs arg)
		throws GroupCommException {
		logger.entering("ReliablePt2Pt", "handleAccept");
		Connection c = (Connection) arg.removeFirst();
		PID pid = tcp.getRemotePID(c);
		switch (getState(pid)) {
			case ST_NULL :
				//A new process has contacted me
				logger.log(Level.FINE, "Hiding connection from: {0}", pid);
				setState(pid, ST_HIDDEN);
				mapConnection(pid, c);
				tcp.startReceiver(c);
				//Sending ACK message with value TRUE
				GroupCommMessage ackMsg = new GroupCommMessage();
				//ackMsg = ACK::true
				ackMsg.tpack(new TBoolean(true));
				ackMsg.tpack(new TInteger(ACK));
				tcp.sendMessage(serialize.marshall(ackMsg), c);
				break;
			case ST_HIDDEN :
				throw new GroupCommException(
					"ReliablePt2Pt:handleAccept:"
						+ "Two incomming (hidden) connections for the same pid??");
			case ST_CONNECTING :
				//Oh no, crossed connection!!  We will receive an "open" later on
				logger.log(
					Level.FINE,
					"Crossed connection to: {0}. Waiting for 'connected' event",
					pid);
				setState(pid, ST_CROSS);
				mapConnection(pid, c);
				//Sending ACK message with value FALSE... for the other 
				//process to learn about the crossed connection
				ackMsg = new GroupCommMessage();
				//ackMsg = ACK::false
				ackMsg.tpack(new TBoolean(false));
				ackMsg.tpack(new TInteger(ACK));
				tcp.sendMessage(serialize.marshall(ackMsg), c);
				break;
			case ST_CONNECTED :
				throw new GroupCommException(
					"ReliablePt2Pt:handleAccept:"
						+ "Two incomming connections for the same pid??");
			case ST_CLOSED :
				throw new GroupCommException(
					"ReliablePt2Pt:handleAccept:"
						+ "The remote pid can't have closed a not yet created connection");
			case ST_CROSS :
				//Sending ACK message with value FALSE... for the other 
				//process to learn about the crossed connection
				ackMsg = new GroupCommMessage();
				ackMsg.tpack(new TBoolean(false));
				ackMsg.tpack(new TInteger(ACK));
				//ackMsg = ACK::false
				tcp.sendMessage(serialize.marshall(ackMsg), c);

				Connection c2 = getConnection(pid);
				//unmapConnection(pid); not necessary
				//Compare the two connections
				logger.log(
					Level.FINE,
					"Comparing crossed connections to {0}....",
					pid);
				if (c.compareTo(c2) < 0) {
					Connection caux = c2;
					c2 = c;
					c = caux;
				}
				//From here on, c2 has the smallest ID
				//So, I keep c
				logger.log(Level.FINE, "Connection chosen: {0}", c);
				setState(pid, ST_CONNECTED);
				mapConnection(pid, c);
				ConnectionData cd = (ConnectionData) connections.get(pid);
				cd.connection2 = c2;
				//Sending CHOSEN message to be able to  
				//cleanly close the connection that wasn't chosen
				GroupCommMessage chosenMsg = new GroupCommMessage();
				chosenMsg.tpack(new TInteger(CHOSEN));
				//chosenMsg = CHOSEN
				tcp.sendMessage(serialize.marshall(chosenMsg), c);
				//The connection whose Accept I handled, hasn't yet started the receiver...
				//... so it may be the chosen one, it may not
				tcp.startReceiver(c);
				break;
			default :
				throw new GroupCommException(
					"ReliablePt2Pt:handleAccept:"
						+ "Hmmm, weird. State of pid unknown");
		}
		logger.exiting("ReliablePt2Pt", "handleAccept");
	}

	/**
	 * The handler for the <i>Pt2PtSend</i> event. </br>
	 * It sends a message to a process.
	 *
	 * @param l <dl>
	 *              <dt> arg1 : GroupCommMessage  </dt> <dd> Le message.   </dd>
	 *              <dt> arg2 : PID            </dt> <dd> La destination du message. </dd>
	 *              <dt> arg3 : Boolean        </dt> <dd> Attribut utilisé par la destination </dd>
	 *          </dl>
	 */
	public void handlePt2PtSend(GroupCommEventArgs arg)
		throws GroupCommException {
		logger.entering("ReliablePt2Pt", "handlePt2PtSend");
		GroupCommMessage m = (GroupCommMessage) arg.removeFirst();
		PID pid = (PID) arg.removeFirst();
		boolean promisc = ((TBoolean) arg.removeFirst()).booleanValue();

		if (m == null)
			throw new GroupCommException(
				"ReliablePt2Pt:handlePt2PtSend:" + "Message can't be null");

		//		if (pid.equals(myself)) {
		//			//In this case, "promisc" is completely useless
		//			if (selfConnected < 1)
		//				throw new GroupCommException(
		//					"ReliablePt2Pt:handlePt2PtSend:"
		//						+ "Self-connection doesn't exist");
		//            //Even if the message is bound to myself, I serialize it.
		//            //  This way I avoid surprises with later side-effects
		//            GroupCommMessage m2 = tcp.unmarshallMessage(tcp.marshallMessage(m));
		//			GroupCommEventArgs args = new GroupCommEventArgs();
		//			args.addLast(m2);
		//			args.addLast(pid);
		//			trigger.trigger(Constants.PT2PTDELIVER, args);
		//            //YOU CANNOT DO THIS, IT BREAKS ATOMICITY
		//			logger.exiting("ReliablePt2Pt", "handlePt2PtSend");
		//			return;
		//		}

		if (promisc) {
			//m = PROMISC::<payload>
			m.tpack(new TInteger(PROMISC));
		} else {
			//m = NORMAL::<payload>
			m.tpack(new TInteger(NORMAL));
		}

		switch (getState(pid)) {
			case ST_NULL :
				throw new GroupCommException(
					"ReliablePt2Pt:handlePt2PtSend:"
						+ "Connection to pid "
						+ pid
						+ " doesn't exist");
			case ST_HIDDEN :
				throw new GroupCommException(
					"ReliablePt2Pt:handlePt2PtSend:"
						+ "Connection to pid "
						+ pid
						+ " doesn't exist (hidden)");
			case ST_CONNECTING :
			case ST_CROSS :
			case ST_CONNECTED :
				logger.log(
					Level.FINE,
					"Sending message to {0}:\n\tMessage:{1}",
					new Object[] { pid, m });
				byte[] b = serialize.marshall(m);
				ConnectionData cd = (ConnectionData) connections.get(pid);
				if (cd.sendingThreadReady) {
					// The sending thread is blocked on the output buffer, since  it's empty
					// ... thus, the message should be sent immediately
					tcp.setMessageToSend(b, getConnection(pid));
					cd.sendingThreadReady = false;
					cd.full = false;
				} else {
					// The output buffer is not empty
					// ... thus, queue the message for deferred sending
					cd.bufferOut.addLast(new TByteArray(b));
					if (!blocked && cd.bufferOut.size() > fc_threshold) {
						//Block application
						logger.fine("Blocking application's flow control");
						flow_control.block(fc_key);
						blocked = true;
					}
				}
				break;
			case ST_CLOSED :
				//Message discarded
				logger.log(
					Level.FINE,
					"Discarding message to {0}:\n\tMessage:{1}",
					new Object[] { pid, m });
				break;
			default :
				throw new GroupCommException(
					"ReliablePt2Pt:handlePt2PtSend:"
						+ "Hmmm, weird. State of pid unknown");
		}
		logger.exiting("ReliablePt2Pt", "handlePt2PtSend");
	}

	public void handleReadyForNextMessage(GroupCommEventArgs arg) {
		logger.entering("ReliablePt2Pt", "handleReadyForNextMessage");
		Connection c = (Connection) arg.removeFirst();
		PID pid = tcp.getRemotePID(c);
		if (getState(pid) != ST_CONNECTED) {
			logger.log(
				Level.INFO,
				"Discarding readyForNextMessage from {0}",
				pid);
			logger.exiting("ReliablePt2Pt", "handleReadyForNextMessage");
			return;
		}
		ConnectionData cd = (ConnectionData) connections.get(pid);
		if (cd.bufferOut.size() > 0) {
			logger.fine("Sending first message in the output buffer");
			byte[] b = ((TByteArray)cd.bufferOut.removeFirst()).byteValue();
			tcp.setMessageToSend(b, c);
			cd.sendingThreadReady = false;
			if (blocked && !fullQueues()) {
				logger.fine("Releasing application's flow control");
				flow_control.release(fc_key);
				blocked = false;
			}
		} else {
			logger.fine("Output buffer is empty: no message has to be sent");
			cd.sendingThreadReady = true;
		}
		cd.full = false;
		logger.exiting("ReliablePt2Pt", "handleReadyForNextMessage");
	}

	/**
	 * The handler for the <i>Recv</i> event. </br>
	 * It occured when a message is received from a process. </br>
	 * <hr>
	 * If the process is known, the message is send to the upper layer. </br>
	 * If the process is unknown, but the special attribute promisc is set,
	 * then the process is added to the known processes and the message
	 * is delivered. </br>
	 * If the process is unknown, and the special attribute promisc is unset,
	 * then the couple (process, message) is added to the Masked Processes. </br>
	 *
	 * @param l <dl>
	 *              <dt> arg1 : GroupCommMessage  </dt> <dd> Le message.   </dd>
	 *              <dt> arg2 : PID            </dt> <dd> La source du message. </dd>
	 *          </dl>
	 *
	 * @see KnownProcesses  The set of Known Processes.
	 * @see MaskedProcesses The set of Masked Processes.
	 * @see Reliable_Pt2Pt  The class which contains the method to send a message to the
	 *                      upper layer.
	 */
	public void handleRecv(GroupCommEventArgs arg) throws GroupCommException {
		logger.entering("ReliablePt2Pt", "handleRecv");
		byte[] b = ((TByteArray) arg.removeFirst()).byteValue();
		Connection c = (Connection) arg.removeFirst();

		PID pid = tcp.getRemotePID(c);
        GroupCommMessage m = null;
		try {
			m = (GroupCommMessage) serialize.unmarshall(b);
		} catch (Exception e) {
			if (getState(pid) == ST_CONNECTED) {
				logger.log(
					Level.INFO,
					"Corrupted TCP stream from {0} while connected. Closing connection...",
					pid);
				//Shutdown the sending stream
				tcp.disconnect(c);
                //Acxt as though the remote process has closed the connection
				closed_broken(pid, c);
                //Suspect it at once
                GroupCommMessage suspect = new GroupCommMessage();
                suspect.tpack(new TInteger(SUSPECT));
                timer.schedule(suspect, false, 10);
				return;
			} else {
				logger.log(
					Level.INFO,
					"Corrupted TCP stream from {0}. Exiting...",
					pid);
				System.exit(1);
			}
		}
		//m = type::<payload>
		int type = ((TInteger) m.tunpack()).intValue();
		//m = <payload>
		switch (getState(pid)) {
			case ST_NULL :
				//Maybe I Connected and Disconnected too swiftly. Very weird!!
				//Disconnect from it
				logger.log(
					Level.INFO,
					"Receiving data from unknown connection to {0}. WEIRD!! Disconnecting",
					pid);
				tcp.disconnect(c);
				break;
			case ST_CLOSED :
				//				throw new GroupCommException(
				//					"ReliablePt2Pt: handleRecv:"
				//						+ "The remote process can't send messages on a closed connection");

				//It might happen that the sending thread gets a "broken" event, more or less at the 
				// same time that the receiving thread receives a message.
				//If sending thread overtakes receiving thread, we might receive a message 
				//  on a CLOSED connection
				// SO WE DISCARD IT...
				logger.log(
					Level.INFO,
					"Receiving data from closed connection to {0}. WEIRD!! Disconnecting",
					pid);
				tcp.disconnect(c);
				break;
			case ST_CONNECTING :
				//I shouldn't be able to receive normal messages yet
				if (type != ACK)
					throw new GroupCommException(
						"ReliablePt2Pt: handleRecv:"
							+ "The remote process can't send messages on a not yet created connection");
				//We may have a crossed connetion. Let's check it
				if (((TBoolean) m.tunpack()).booleanValue()) {
					//All right, just one connection
					logger.log(
						Level.FINE,
						"Connection to {0} established",
						pid);
					setState(pid, ST_CONNECTED);
					mapConnection(pid, c);
					tcp.startSender(c);
				} else {
					//Oh no, a crossed connection, let's wait for the accept
					logger.log(
						Level.FINE,
						"Crossed connection to {0} waiting for Accept",
						pid);
					setState(pid, ST_CROSS);
					mapConnection(pid, c);
				}
				break;
			case ST_CROSS :
				if (type == ACK) {
					if (((TBoolean) m.tunpack()).booleanValue())
						throw new GroupCommException(
							"ReliablePt2Pt: handleRecv:"
								+ "If I am in CROSS state, the ACK I receive can't be TRUE!!");
					Connection c2 = getConnection(pid);
					//unmapConnection(pid); not necessary
					//Compare the two connections
					logger.log(
						Level.FINE,
						"Comparing crossed connections to {0}....",
						pid);
					if (c.compareTo(c2) < 0) {
						Connection caux = c2;
						c2 = c;
						c = caux;
					}
					//From here on, c2 has the smallest ID
					//So, I keep c
					logger.log(Level.FINE, "Connection chosen: {0}", c);
					setState(pid, ST_CONNECTED);
					mapConnection(pid, c);
					ConnectionData cd = (ConnectionData) connections.get(pid);
					cd.connection2 = c2;
					//Sending CHOSEN message to be able to  
					//cleanly close the connection that wasn't chosen
					GroupCommMessage chosenMsg = new GroupCommMessage();
					chosenMsg.tpack(new TInteger(CHOSEN));
					//chosenMsg = CHOSEN
					tcp.sendMessage(serialize.marshall(chosenMsg), c);
					//The connection whose Accept I handled, hasn't yet started the receiver...
					//... so it may be the chosen one, it may not
					tcp.startReceiver(c);
				} else if (type == CHOSEN) {
					throw new GroupCommException(
						"Received CHOSEN messages while in ST_CROSS."
							+ "Impossible: receiver thread not started");
				} else {
					throw new GroupCommException("Received normal messages while in ST_CROSS!!");
				}
				break;
			case ST_CONNECTED :
				//Message from connected process pid
				if (type == ACK)
					throw new GroupCommException(
						"ReliablePt2Pt: handleRecv: If I am "
							+ "in CONNECTED state, I can't receive ACK messages");
				ConnectionData cd = (ConnectionData) connections.get(pid);
				if (getConnection(pid) != c)
					throw new GroupCommException(
						"ReliablePt2Pt: handleRecv: "
							+ "Received a message from an unmapped connection");
				if (type == CHOSEN) {
					if (cd.connection2 == null)
						throw new GroupCommException(
							"ReliablePt2Pt: handleRecv: "
								+ "Connection2 can't be null when receiving CHOSEN message");

					tcp.startSender(cd.connection);
					tcp.disconnect(cd.connection2);
					cd.connection2 = null;
				} else { //Normal message TODO: add assertion!
					if (cd.connection2 != null)
						throw new GroupCommException(
							"ReliablePt2Pt: handleRecv: "
								+ "Connection2 must be null when receiving a normal message");
					logger.log(
						Level.FINE,
						"Data from: {0}\n\t Message: {1}",
						new Object[] { pid, m });
					GroupCommEventArgs args = new GroupCommEventArgs();
					args.addLast(m);
					args.addLast(pid);
					trigger.trigger(Constants.PT2PTDELIVER, args);
				}
				break;
			case ST_HIDDEN :
				if (type == ACK)
					throw new GroupCommException(
						"ReliablePt2Pt: handleRecv: If I am "
							+ "in HIDDEN state, I can't receive ACK messages");
				if (type == CHOSEN)
					throw new GroupCommException(
						"ReliablePt2Pt: handleRecv: If I am "
							+ "in HIDDEN state, I can't receive CHOSEN messages");
				tcp.stopReceiver(c);
				cd = (ConnectionData) connections.get(pid);
				if (type == NORMAL) { //Not promiscuous
					logger.log(
						Level.FINE,
						"Buffering data from: {0} (hidden)\n\t Message: {1}",
						new Object[] { pid, m });
					//It is a queue because of asynchrony of recv and stopReceiver
					cd.bufferIn.addLast(m);
				} else { //Promiscuous
					//Yes, we change order because this has to be the first message to be delivered
					logger.log(
						Level.FINE,
						"Promicuous message from: {0} (hidden)\n\t Message: {1}",
						new Object[] { pid, m });
					cd.bufferIn.addFirst(m);
					doJoin(pid);
					//Because this join is not a normal one, we shouldn't count it
					decJoins(pid);
				}
				break;
			default :
				throw new GroupCommException(
					"ReliablePt2Pt: handleRecv:"
						+ "Hmmm, weird. State of pid unknown");
		}
		logger.exiting("ReliablePt2Pt", "handleRecv");
	}

	public void handleTimeout(GroupCommEventArgs arg)
		throws GroupCommException {

		logger.entering("ReliablePt2Pt", "handleTimeout");
		GroupCommMessage mclone = (GroupCommMessage) arg.removeFirst();
		GroupCommMessage m = mclone.cloneGroupCommMessage();
		//m = type::<payload>
		int type = ((TInteger) m.tunpack()).intValue();
		//m = <payload>

		switch (type) {
			case RETRY :
				PID pid = (PID) m.tunpack();
				ConnectionData cd = (ConnectionData) connections.get(pid);
				switch (getState(pid)) {
					case ST_NULL :
						//I quickly closed the connection
					case ST_CLOSED :
						//The remote process quickly closed the connection
						break;
					case ST_CONNECTING :
						if (cd.retries > MAX_RETRIES) {
							//We give up
							logger.log(
								Level.FINE,
								"Giving up connection to {0}",
								pid);
							setState(pid, ST_CLOSED);
							cd.bufferOut.clear();
							//Check if we can release the application
							// (Application is blocked and no queue is full)
							if (blocked && !fullQueues()) {
								//Release application
								flow_control.release(fc_key);
								logger.fine(
									"Releasing application's flow control");
								blocked = false;
							}
						} else {
							logger.log(
								Level.FINE,
								"Retrying connection to {0}",
								pid);
							tcp.connect(pid);
						}
						break;
					case ST_CROSS :
						if (cd.retries > MAX_RETRIES) {
							//We give up
							logger.log(
								Level.FINE,
								"Couldn't set up connection to {0}. Giving up",
								pid);
							setState(pid, ST_CLOSED);
							unmapConnection(pid);
							cd.bufferOut.clear();
							//Check if we can release the application
							// (Application is blocked and no queue is full)
							if (blocked && !fullQueues()) {
								//Release application
								flow_control.release(fc_key);
								logger.fine(
									"Releasing application's flow control");
								blocked = false;
							}
						} else {
							logger.log(
								Level.FINE,
								"Retrying connection to {0}",
								pid);
							tcp.connect(pid);
						}
						break;
					case ST_HIDDEN :
					case ST_CONNECTED :
					default :
						throw new GroupCommException(
							"ReliablePt2Pt: handleTimeout: If connection didn't establish,"
								+ " state must be ST_CONNECTING or ST_CROSS");

				}
				break;
			case SUSPECT :
				TSet suspected = new THashSet();
				//Check all queues
				TCollection keys = connections.keySet();
				Iterator i = keys.iterator();
				while (i.hasNext()) {
					boolean suspect = false;
					pid = (PID) i.next();
					switch (getState(pid)) {
						case ST_CONNECTED :
						case ST_CONNECTING :
						case ST_CROSS :
							cd = (ConnectionData) connections.get(pid);
							if (!cd.sendingThreadReady) {
								//The queue is not empty
								suspect = cd.full;
								cd.full = true;
							}
							break;
						case ST_CLOSED :
							suspect = true;
						case ST_HIDDEN :
							//Do nothing, we don't (yet) have an outgoing queue
					}
					if (suspect) {
						logger.log(
							Level.FINE,
							"Sending event Suspect2 for process: {0}",
							pid);
						suspected.add(pid);
					}
				}
				if (suspected.size() > 0) {
					//Trigger Suspect Event
					GroupCommEventArgs args = new GroupCommEventArgs();
					args.addLast(suspected);
					trigger.trigger(Constants.SUSPECT2, args);
				}
		}
		logger.exiting("ReliablePt2Pt", "handleTimeout");
	}

	/**
	  *
	  */
	public void handleShutdown() throws GroupCommException {
		//Close server
		tcp.stopServer(server);

		//Close all open connections
		TCollection keys = connections.keySet();
		Iterator i = keys.iterator();
		logger.fine("Shutting down.");
		while (i.hasNext()) {
			PID p = (PID) i.next();
			if (getState(p) != ST_CLOSED) {
				Connection c = getConnection(p);
				if (c != null) {
					logger.log(Level.FINE, "Disconnecting from {0}", p);
					tcp.disconnect(c);
				}
			}
		}

		// Clear map
		connections.clear();
	}

	private void setState(PID p, int state) {
		logger.log(
			Level.FINE,
			"Setting state of {0} to {1}",
			new Object[] { p, new Integer(state)});
		if (state == ST_NULL) {
			connections.remove(p);
		} else {
			if (!connections.containsKey(p)) {
				ConnectionData c = new ConnectionData(state);
				connections.put(p, c);
			} else {
				((ConnectionData) connections.get(p)).state = state;
			}
		}
	}

	private int getState(PID p) {
		if (!connections.containsKey(p)) {
			logger.log(Level.FINE, "Getting state of {0}: ST_NULL", p);
			return ST_NULL;
		} else {
			int st = ((ConnectionData) connections.get(p)).state;
			logger.log(
				Level.FINE,
				"Getting state of {0}: {1}",
				new Object[] { p, new Integer(st)});
			return st;
		}
	}

	private void removeState(PID p) {
		setState(p, ST_NULL);
	}

	private void mapConnection(PID p, Connection connection)
		throws GroupCommException {
		if (!connections.containsKey(p))
			throw new GroupCommException(
				"ReliablePt2Pt: mapConnection:" + "PID unkonwn");

		ConnectionData c = (ConnectionData) connections.get(p);
		if (c.state == ST_CLOSED || c.state == ST_CONNECTING)
			throw new GroupCommException(
				"ReliablePt2Pt: mapConnection:"
					+ "Bad state, no connection mapped");

		logger.log(
			Level.FINE,
			"Mapping connection {0} to {1}",
			new Object[] { connection, p });
		c.connection = connection;
	}

	private void unmapConnection(PID p) throws GroupCommException {
		if (!connections.containsKey(p))
			throw new GroupCommException(
				"ReliablePt2Pt: unmapConnection:" + "PID unkonwn");

		ConnectionData c = (ConnectionData) connections.get(p);
		if (c.state == ST_CONNECTED
			|| c.state == ST_HIDDEN
			|| c.state == ST_CROSS)
			throw new GroupCommException(
				"ReliablePt2Pt: unmapConnection:"
					+ "Bad state, connection can't be unmapped");

		logger.log(
			Level.FINE,
			"Unmapping connection {0} to {1}",
			new Object[] { c.connection, p });
		c.connection = null;
	}

	private Connection getConnection(PID p) throws GroupCommException {
		if (!connections.containsKey(p))
			throw new GroupCommException(
				"ReliablePt2Pt: getConnection:" + "PID unkonwn");

		ConnectionData c = (ConnectionData) connections.get(p);
		if (c.state == ST_CLOSED)
			throw new GroupCommException(
				"ReliablePt2Pt: getConnection:"
					+ "Bad state, no connection mapped");

		//It may return null, if no connection was mapped
		logger.log(
			Level.FINE,
			"Getting mapped connection {0} to {1}",
			new Object[] { c.connection, p });
		return c.connection;
	}

	private void incJoins(PID p) throws GroupCommException {
		logger.log(Level.FINE, "Incrementing joins of {0}", p);
		if (!connections.containsKey(p))
			throw new GroupCommException(
				"No state for " + p + "when trying to increment joins");
		((ConnectionData) connections.get(p)).joins++;
	}

	/*
	 * Returns true if there are remaining joins to remove 
	 */
	private boolean decJoins(PID p) throws GroupCommException {
		logger.log(Level.FINE, "Decrementing joins of {0}", p);
		if (!connections.containsKey(p))
			throw new GroupCommException(
				"No state for " + p + "when trying to decrement joins");
		ConnectionData cd = (ConnectionData) connections.get(p);
		return (--cd.joins > 0);
	}

	private boolean fullQueues() {
		//Check all queues to know whether they're full
		TCollection keys = connections.keySet();
		Iterator i = keys.iterator();
		boolean full = false;
		while (i.hasNext()) {
			PID p = (PID) i.next();
			if (getState(p) == ST_CONNECTED
				|| getState(p) == ST_CONNECTING
				|| getState(p) == ST_CROSS) {
				ConnectionData c = (ConnectionData) connections.get(p);
				full =
					full
						|| (c.bufferOut.size()
							> (fc_threshold - (fc_threshold / smoothness)));
			}
		}
		logger.log(
			Level.FINE,
			"Checking queues... result {0}",
			new Boolean(full));
		return full;
	}

	private void flushInputBuffer(PID p) {
		logger.entering("ReliablePt2Pt", "flushInputBuffer");

		ConnectionData cd = (ConnectionData) connections.get(p);
		//LinkedList lm = (LinkedList)cd.bufferIn.clone(); why clone????
		TLinkedList lm = cd.bufferIn;
		logger.log(
			Level.FINE,
			"Flushing messages from {0}: \n\t{1}",
			new Object[] { p, lm });
		while (lm.size() > 0) {
			GroupCommEventArgs args = new GroupCommEventArgs();
			args.addLast(lm.removeFirst());
			args.addLast(p);
			trigger.trigger(Constants.PT2PTDELIVER, args);
		}
		logger.exiting("ReliablePt2Pt", "flushInputBuffer");
	}

	/**
	 * Used for debugging. </br>
	 * Undocumented.
	 */
	public void handleDump(OutputStream out) {
		PrintStream err = new PrintStream(out);
		err.println("======== ReliablePt2Pt: dump =======");
		err.println("fc_threshold: " + fc_threshold);
		err.println("to_suspect: " + to_suspect);
		err.println("to_retry_connect: " + to_retry_connect);
		//err.println("self_connected: " + selfConnected);
		err.println("Flow control is blocked: " + blocked);
		err.println("Connections: ");
		Iterator it = connections.keySet().iterator();
		while (it.hasNext()) {
			PID pid = (PID) it.next();
			err.println("   PID:" + pid + "-->" + connections.get(pid));
			err.println("----");
		}
		err.println("================================");
	}
}
