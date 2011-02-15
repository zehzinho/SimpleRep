/**
 *  SAMOA - PROTOCOL FRAMEWORK
 *  Copyright (C) 2005  Olivier Rütti (EPFL) (olivier.rutti@a3.epfl.ch)
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
package seqSamoa.protocols.rpt2pt;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import seqSamoa.AtomicTask;
import seqSamoa.Message;
import seqSamoa.ProtocolModule;
import seqSamoa.ProtocolStack;
import seqSamoa.ServiceCallOrResponse;
import seqSamoa.exceptions.AlreadyExistingProtocolModuleException;
import seqSamoa.exceptions.AlreadyExistingServiceException;
import seqSamoa.services.monitoring.ProcessSuspicion;
import seqSamoa.services.monitoring.ProcessSuspicionCallParameters;
import seqSamoa.services.network.Network;
import seqSamoa.services.network.NetworkResponseParameters;
import seqSamoa.services.rpt2pt.RPT2PT;
import seqSamoa.services.rpt2pt.RPT2PTCallParameters;
import seqSamoa.services.rpt2pt.RPT2PTResponseParameters;
import uka.transport.Transportable;
import framework.Constants;
import framework.GroupCommEventArgs;
import framework.GroupCommException;
import framework.GroupCommMessage;
import framework.PID;
import framework.libraries.DefaultSerialization;
import framework.libraries.Serialize;
import framework.libraries.Timer;
import framework.libraries.Trigger;
import framework.libraries.serialization.TByteArray;
import framework.libraries.serialization.THashSet;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TSet;
import framework.libraries.tcp.Connection;
import framework.libraries.tcp.NonBlockingTCP;
import framework.libraries.tcp.Server;
import framework.libraries.tcp.TCPStackInterface;
import groupcomm.common.rpt2pt.ReliablePt2Pt;

/**
 * This class implement a Protocol that allows Reliable Point to Point
 * communication
 * 
 * The service implemented is rpt2pt (described in util/Services.java)
 */
public class ProtocolRPT2PT extends ProtocolModule implements Trigger,
		NonBlockingTCP, TCPStackInterface, Serialize, Timer {
    final private static int MAX_PROCESSES = 7;
    final private static int MAX_MESSAGES = 45;

	// Service Provided
	private RPT2PT rpt2pt;

	private ServiceCallOrResponse rpt2ptCOR;
	
	// Service required
	private ProcessSuspicion processSuspicion;
	
	// Internal Service for connections
	private Network net;

	// Object that describe behaviour of the Handlers
	protected ReliablePt2Pt handlers;

	// Timers scheduled
	Map<Transportable, AtomicTask> timers = null;

	// TCP Suspicion parameters
	int fc_threshold;

	int to_suspect;

	int to_retry_connect;

	// Is the Protocol Closed
	boolean layerClosed;

	// The Executers
	// It send reliably a message
	protected RPT2PT.Executer rpt2ptExecuter;

	// The listener
	// It processs the events from the connections
	protected Network.Listener netListener;
	
	/**
	 * Constructor. <br>
	 * 
	 * @param name
	 *            Name of the layer
	 * @param stack
	 * 			  The stack in which the module will be
	 * @param fc_threshold
	 *            The max number of messages treated by TCP
	 * @param to_suspect
	 *            The time before suspecting TCP connection to be broken
	 * @param to_retry_connect
	 *            The time before trying to reconnect
	 */
	public ProtocolRPT2PT(String name, ProtocolStack stack, int fc_threshold,
			int to_suspect, int to_retry_connect, RPT2PT rpt2pt,
			ProcessSuspicion processSuspicion)
			throws AlreadyExistingProtocolModuleException, AlreadyExistingServiceException {

		super(name, stack);

		this.rpt2pt = rpt2pt;
		this.rpt2ptCOR = ServiceCallOrResponse.createServiceCallOrResponse(rpt2pt, true);
		this.processSuspicion = processSuspicion;
		this.net = new Network(name+"-Net", stack);
		
		// Init the fields
		handlers = new ReliablePt2Pt(this, stack.getFlowControl(), this, this,
				this, stack.getPID());

		this.fc_threshold = fc_threshold;
		this.to_suspect = to_suspect;
		this.to_retry_connect = to_retry_connect;

		this.layerClosed = false;
		timers = new HashMap<Transportable, AtomicTask>();

		LinkedList<ServiceCallOrResponse> initiatedRpt2pt = new LinkedList<ServiceCallOrResponse>();
		rpt2ptExecuter = rpt2pt.new Executer(this, initiatedRpt2pt) {
			public void evaluate(RPT2PTCallParameters params, Message dmessage) {
				synchronized (ProtocolRPT2PT.this) {
					GroupCommEventArgs ga = new GroupCommEventArgs();
					if (params.send.booleanValue()) {
						ga.addLast(dmessage.toGroupCommMessage());
						ga.addLast(params.pid);
						ga.addLast(params.added);

						try {
							handlers.handlePt2PtSend(ga);
						} catch (GroupCommException ex) {
							ex.printStackTrace();
							throw new RuntimeException(
									"ProtocolRPT2PT: handlePt2PtSend: "
											+ ex.getMessage());
						}
					} else {
						THashSet singleton = new THashSet();
						singleton.add(params.pid);
						if (params.added.booleanValue()) {
							ga.addLast(singleton);
							ga.addLast(new THashSet());
						} else {
							ga.addLast(new THashSet());
							ga.addLast(singleton);
						}

						try {
							handlers.handleJoinRemoveList(ga);
						} catch (GroupCommException ex) {
							throw new RuntimeException(
									"ProtocolRPT2PT: handleJoinRemoveList: "
											+ ex.getMessage());
						}
					}
				}
			}
		};
		
		LinkedList<ServiceCallOrResponse> initiatedNet = new LinkedList<ServiceCallOrResponse>();
        for (int i=0; i<(MAX_PROCESSES*MAX_MESSAGES); i++)
        	initiatedNet.add(ServiceCallOrResponse.createServiceCallOrResponse(rpt2pt, false));
		netListener = net.new Listener(this, initiatedNet) {
			public void evaluate(NetworkResponseParameters params, Transportable message) {
				synchronized (ProtocolRPT2PT.this) {
					final GroupCommEventArgs m = new GroupCommEventArgs();
					try {
						switch (params.code) {
						case 1: // Accept
							m.addLast(params.connection);
							handlers.handleAccepted(m);
							break;
						case 2: // Connected
							m.addLast(params.pid);
							m.addLast(params.connection);
							handlers.handleConnected(m);
							break;
						case 3: // Closed
							m.addLast(params.connection);
							handlers.handleClosed(m);
							break;
						case 4: // Broken
							m.addLast(params.connection);
							handlers.handleBroken(m);
							break;
						case 5: // Recv
							m.addLast(message);
							m.addLast(params.connection);

							if (!layerClosed) 
								handlers.handleRecv(m);
							break;
						case 6: // ReadyForNextMessage
							m.addLast(params.connection);
							handlers.handleReadyForNextMessage(m);
							break;
						default:
						}
					} catch (GroupCommException ex) {
						throw new RuntimeException(
								"ProtocolRPT2PT: net: "
										+ ex.getMessage());
					}

				}
			}
		};
	}

	public void dump(OutputStream stream) {
		handlers.handleDump(stream);
	}

	public void init() {
		GroupCommEventArgs ga = new GroupCommEventArgs();

		ga.addLast(new TInteger(fc_threshold));
		ga.addLast(new TInteger(to_suspect));
		ga.addLast(new TInteger(to_retry_connect));

		try {
			handlers.handleInit(ga);
		} catch (GroupCommException ex) {
			throw new RuntimeException("ProtocolRPT2PT: init: "
					+ ex.getMessage());
		}

		super.init();
	}

	public void close() {
		layerClosed = true;
		super.close();
	}

	/**
	 * Manage the triggering of the events
	 */
	public void trigger(int type, GroupCommEventArgs l) {
		switch (type) {
		case Constants.PT2PTDELIVER:
			Message message = new Message((GroupCommMessage) l.remove(0));
			RPT2PTResponseParameters infos = new RPT2PTResponseParameters(
					(PID) l.remove(0));

			if (!layerClosed)
				rpt2pt.response(infos, message);
			break;

		case Constants.SUSPECT2:
			TSet suspected = (TSet) l.remove(0);
			ProcessSuspicionCallParameters p = new ProcessSuspicionCallParameters(
					suspected);

			if (!layerClosed)
				processSuspicion.externalCall(p, null);
			break;

		default:
			throw new RuntimeException("ProtocolRPT2PT: trigger: "
					+ "Unexpected event type");
		}

	}

	// Interface for the timers
	public void schedule(final Transportable key, boolean periodic, int time) {
		AtomicTask trigger = new AtomicTask() {
			public void execute() {
				synchronized (ProtocolRPT2PT.this) {
					if (!timers.containsKey(key))
						// Timer already canceled
						return;

					GroupCommEventArgs ga = new GroupCommEventArgs();
					ga.addLast(key);

					try {
						handlers.handleTimeout(ga);
					} catch (GroupCommException gce) {
						throw new RuntimeException("ProtocolRP2PT: Timeout: "
								+ gce.getMessage());
					}
				}
			}
			
			public ServiceCallOrResponse getCOR() {
				return ProtocolRPT2PT.this.rpt2ptCOR;
			}
		};

		timers.put(key, trigger);
		stack.getScheduler().schedule(trigger, periodic, time);
	}

	public void cancel(Transportable key) {
		throw new RuntimeException("ProtocolRPt2Pt: Weird: "
				+ "cancel was not expected to be used");
	}

	public void reset(Transportable key) {
		throw new RuntimeException("ProtocolRPt2Pt: Weird: "
				+ "cancel was not expected to be used");
	}

	// Interface for NonBlockingTCP and TCPStackInterface
	/**
	 * Listen
	 */
	public Server startServer(PID myself) {
		Server s = null;
		try {
			s = new Server(myself, this);
		} catch (IOException ioe) {
			ioe.printStackTrace();
			System.exit(1);
		}
		return s;
	}

	public void stopServer(Server s) {
		s.close();
	}

	public void accepted(Connection c) {
		NetworkResponseParameters params = new NetworkResponseParameters(1,c,null);
		net.externalResponse(params, null);
	}

	/**
	 * Connect/disconnect/Link failure
	 */
	public void connect(PID p) {
		new Connection(this.stack.getPID(), p, this);
	}

	public void disconnect(Connection c) {
		c.disconnect();
	}

	public void connected(PID p, Connection c) {
		NetworkResponseParameters params = new NetworkResponseParameters(2,c,p);
		net.externalResponse(params, null);
	}

	public void closed(Connection c) {
		NetworkResponseParameters params = new NetworkResponseParameters(3,c,null);
		net.externalResponse(params, null);
	}

	public void broken(Connection c) {
		NetworkResponseParameters params = new NetworkResponseParameters(4,c,null);
		net.externalResponse(params, null);
	}

	/**
	 * Receive / Blocking send
	 */
	public void sendMessage(byte[] b, Connection c) {
		/* May block */
		c.sendMessage(b);
	}

	public void startReceiver(Connection c) {
		c.startReceiver();
	}

	public void stopReceiver(Connection c) {
		c.stopReceiver();
	}

	public void recv(byte[] b, Connection c) {
		NetworkResponseParameters params = new NetworkResponseParameters(5,c,null);
		net.externalResponse(params, new Message(new TByteArray(b), null));
	}

	/**
	 * Non-blocking send
	 */
	public void startSender(Connection c) {
		c.startSender();
	}

	public void stopSender(Connection c) {
		c.stopSender();
	}

	public void setMessageToSend(byte[] b, Connection c) {
		c.setMessageToSend(b);
	}

	public void readyForNextMessage(Connection c) {
		NetworkResponseParameters params = new NetworkResponseParameters(6,c,null);
		net.externalResponse(params, null);
	}

	/**
	 * Marshalling/unmarshalling
	 */
	public byte[] marshall(Transportable m) {
		byte[] b = null;
		try {
			b = DefaultSerialization.marshall(m);
		} catch (IOException ioe) {
			ioe.printStackTrace();
			System.exit(1);
		}

		return b;
	}

	public Transportable unmarshall(byte[] b) {
		Transportable m = null;
		try {
			m = DefaultSerialization.unmarshall(b);
		} catch (ClassNotFoundException cnfe) {
			cnfe.printStackTrace();
			System.exit(1);
		} catch (IOException ioe) {
			ioe.printStackTrace();
			System.exit(1);
		}
		return m;
	}

	/**
	 * Miscellaneous
	 */
	public PID getRemotePID(Connection c) {
		return c.getRemotePID();
	}
}
