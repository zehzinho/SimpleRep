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
package framework.libraries.tcp;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import uka.transport.DeepClone;
import uka.transport.MarshalStream;
import uka.transport.Transportable;
import uka.transport.UnmarshalStream;
import framework.Constants;
import framework.PID;

//import lse.net.net.RSocket;

/**
 * This class implements a TCP connection to a remote process.
 * It has several threads for sending/receiving/connecting. It has
 * been designed to be used by the protocols as a <i>framework 
 * library </i>.
 * 
 * See interfaces <i>NonBlockingTCP</i> and <i>TCPStackInterface</i> 
 * for more details about the way this class interacts with the protocols 
 * in the stack
 */
public class Connection implements Comparable, Transportable {
    //TODO: remove "implements Transportable"
	/**
	 * Reference to the local process. It is passes as a parameter to this 
	 * class's constructor
	 */
	private PID myself;
	/**
	 * Boolean set to <i>true</i> if this is connection towards the local process. 
	 * The behavior of a self-connected Connection is slightly different from 
	 * that of a normal Connection. A self-connected Connection does not have a socket 
	 * (since it is not necessary), and it has special thread for sending and receiving.
	 */
	private boolean selfConnected;
	/*3
	 * The socket used to send/receive data
	 */
	Socket socket;
	/**
	 * PID of the remote process.
	 */
	PID remote = null;
	/**
	 * The socket's OutputStream
	 */
	OutputStream os = null;
	/**
	 * The socket's InputStream
	 */
	InputStream is = null;
	/**
	 * The parent object that will insert the events into the stack. It is 
	 * typically the wrapping micro-protocol of a low level <i>common code</i> 
	 * protocol.
	 */
	TCPStackInterface parent = null;
	/**
	 * Tells whether the active threads must exit
	 */
	boolean exit = false;
	/**
	 * Tells whether the connection protocol has been carried out
	 */
	boolean connected = false;
	/**
	 * The message waiting to be sent by the sending thread
	 */
	byte[] messageToSend = null;
	/**
	 * Tells whether the sending thread is active
	 */
	boolean sender = false;
	/**
	 * Tells whether the receiving thread is active
	 */
	boolean receiver = false;

	/**
	 * Constructor called when the socket has already been created. Normally, 
	 * this constructor will be called by the Server class upon an incomming 
	 * connection from a remote process.
	 * 
	 * @param myself The PID of the local process
	 * @param sock Socket already created 
	 * @param sock parent The object that inserts event into the stack 
	 */
	public Connection(PID myself, Socket sock, TCPStackInterface parent) {
		this.myself = myself;
		this.parent = parent;
		socket = sock;
		selfConnected = false;

		startThread(acceptThread, "acceptThread");
		startThread(senderThread, "senderThread2");
		startThread(receiverThread, "receiverThread2");
	}

	/**
	 * Constructor called when there is no created socket yet. Normally, 
	 * this constructor will be called by a protcol that wants to start 
	 * a client connection with a remote process.
	 * 
	 * @param myself The PID of the local process
	 * @param remote PID of the process to connect to
	 * @param sock parent The object that inserts event into the stack 
	 */
	public Connection(PID myself, PID remote, TCPStackInterface parent) {
		this.myself = myself;
		this.remote = remote;
		this.parent = parent;
		selfConnected = myself.equals(remote);
		if (selfConnected) {
			startThread(selfSendReceiveThread, "selfSendReceiveThread");
		} else {
			startThread(connectThread, "connectThread");
			startThread(senderThread, "senderThread1");
			startThread(receiverThread, "receiverThread1");
		}
	}

	private void startThread(Runnable r, String name) {
		Thread t = new Thread(Constants.THREADGROUP, r, name);
		t.setDaemon(true);
		t.start();
	}

	/**
	 * Disconnect the socket and finish all active threads
	 */
	public synchronized void disconnect() {
		exit = true;
		notifyAll();
	}

	/**
	 * Start the sender thread. From this moment on, the Connection 
	 * notifies <i>parent</i> whenever it is ready to accept a new 
	 * message to send. This is done with the parent's  
	 * <i>readyForNextMessage</i> method.
	 */
	public synchronized void startSender() {
		sender = true;
		notifyAll();
	}

	/**
	 * Stop the sender thread
	 */
	public synchronized void stopSender() {
		sender = false;
	}

	/**
	 * Start the receiver thread. From this moment on, the Connection 
	 * notifies <i>parent</i> whenever a message is received from the 
	 * socket. This is done with the parent's <i>recv</i> method.
	 */
	public synchronized void startReceiver() {
		receiver = true;
		notifyAll();
	}

	/**
	 * Stop the receiver thread
	 */
	public synchronized void stopReceiver() {
		receiver = false;
	}

	/**
	 * Accepts a message to be asynchronously sent by the sending thread. 
	 * The caller returns immedaitely, without waiting for the message 
	 * to be actually written to the socket.
	 * 
	 * @param message The message to be asynchronously sent
	 */
	public synchronized void setMessageToSend(byte[] message) {
		if (messageToSend != null)
			throw new RuntimeException("messageToSend != null!!!!");
		messageToSend = message;
		notifyAll();
	}

	/**
	 * Sends a message synchronously. The called does not return immedaitely. 
	 * It returns after the message is actually written to the socket. If 
	 * the buffers at TCP level are full, this method will block the caller 
	 * until there is again space for the message at the TCP level.
	 * 
	 * This method is normally used by the sender thread, And sometimes by 
	 * protocol that are 100% sure that TCP buffers will not be full by the 
	 * time this method is called.
	 * 
	 * @param message The message to be synchronously sent 
	 */
	public void sendMessage(byte[] b) {
		try {
			if (selfConnected) {
				parent.recv(b, moi);
			} else {
				os.write(intToBytes(b.length));
				os.write(b);
				os.flush();
			}
		} catch (IOException ioe) {
			disconnect();
			parent.broken(this);
		}
	}

	/**
	 * Returns the PID of the remote process
	 */
	public PID getRemotePID() {
		return remote;
	}

	Connection moi = this;

	private Runnable connectThread = new Runnable() {
		public void run() {
				//Protocol for the connection initator
	try {
				socket = new Socket(remote.ip, remote.port);                                
				// Good!! We got a connection... but
				// DON'T notify it with parent.opened() yet 
				is = socket.getInputStream();
				os = socket.getOutputStream();
                
                // Deactivate Nagle algorithm
                socket.setTcpNoDelay(true);
			} catch (IOException ioe) {
				//Connection failed
				parent.connected(remote, null);
				disconnect();
				return;
			}

			try {
				//Wait for the real PID
				PID realPID = readPID();
				if (exit) {
					shutdownOutput();
					return;
				}
				if (realPID == null
					|| //Connection remotely closed
				!realPID.equals(remote)) {
					//Ooops, different incarnation
					disconnect();
					parent.broken(moi);
					//parent.closed(moi);
					//shutdownOutput();
					return;
				}

				//Send local PID
				sendPID();

				connected = true;
				synchronized (moi) {
					moi.notifyAll();
				}
				parent.connected(remote, moi);
			} catch (IOException ioe) {
				disconnect();
				parent.broken(moi);
			}
		}
	};

	private Runnable acceptThread = new Runnable() {
		public void run() {
			try {                
                // Deactivate Nagle algorithm
                socket.setTcpNoDelay(true);

                //Protocol for the connection receiver
			    os = socket.getOutputStream();
				//Send local PID
				sendPID();
				is = socket.getInputStream();

				//Wait for remote PID
				PID remPID = readPID();
				if (remPID == null) {
					//Connection closed
					shutdownOutput();
					return;
				}
				remote = remPID;

				connected = true;
				synchronized (moi) {
					moi.notifyAll();
				}
				parent.accepted(moi);
			} catch (IOException ioe) {
				disconnect();
			}
		}
	};

	private Runnable receiverThread = new Runnable() {
		public void run() {
			try {
				synchronized (moi) {
					while (!connected && !exit) {
						try {
							moi.wait();
						} catch (InterruptedException ie) {
						}
					}
				}
				if (exit)
					return;
				while (true) {
					// Normal message reception
					byte[] m = readMessage();
					// 		    if(cauterized){
					// 			return;
					// 		    }
					if (exit)
						return;

					synchronized (moi) {
						while (!receiver && !exit) {
							try {
								moi.wait();
							} catch (InterruptedException ie) {
							}
						}
						if (exit)
							return;
					}
					//Deliver message to the transport
					parent.recv(m, moi);
				}
			} catch (EOFException eofe) {
				parent.closed(moi);
			} catch (IOException ioe) {
				disconnect();
				parent.broken(moi);
			}
		}
	};

	private Runnable senderThread = new Runnable() {
		public void run() {
			try {
				synchronized (moi) {
					while (!connected && !exit) {
						try {
							moi.wait();
						} catch (InterruptedException ie) {
						}
					}
				}
				if (exit) {
					shutdownOutput();
					return;
				}
				while (true) {
					synchronized (moi) {
						while (!sender && !exit) {
							try {
								moi.wait();
							} catch (InterruptedException ie) {
							}
						}
						if (exit) {
							shutdownOutput();
							return;
						}
					}
					parent.readyForNextMessage(moi);
					synchronized (moi) {
						while (messageToSend == null && !exit) {
							try {
								moi.wait();
							} catch (InterruptedException ie) {
							}
						}
						if (exit) {
							shutdownOutput();
							return;
						}
					}
					sendMessage(messageToSend);
					messageToSend = null;
				}
			} catch (IOException ioe) {
				System.out.println(
					"Connection: IOException while shutting down output");
			}
		}
	};

	private Runnable selfSendReceiveThread = new Runnable() {
		public void run() {
			byte[] m;
			connected = true;
			parent.connected(remote, moi);
			//parent.accepted(moi);

			while (true) {
				synchronized (moi) {
					while (!sender && !exit) {
						try {
							moi.wait();
						} catch (InterruptedException ie) {
						}
					}
					if (exit) {
						return;
					}
				}
				parent.readyForNextMessage(moi);
				synchronized (moi) {
					while ((messageToSend == null || !receiver) && !exit) {
						try {
							moi.wait();
						} catch (InterruptedException ie) {
						}
					}
					if (exit) {
						return;
					}
					m = messageToSend;
					messageToSend = null;
				}
				//Deliver message to the transport
				parent.recv(m, moi);
			}
		}
	};

	/**
	 * Send the local process' PID to the remote process. This is used when 
	 * connecting to verify the incarnation number.
	 */
	void sendPID() throws IOException {
		//PID p = PID.LOCALHOST;
		//send inetAddress
		byte[] inetBytes = myself.ip.getHostName().getBytes();
		os.write(intToBytes(inetBytes.length));
		os.write(inetBytes);
		//send port
		os.write(intToBytes(myself.port));
		//send incarnation
		os.write(intToBytes(myself.incarnation));
		os.flush();
	}

	/**
	 * Attempt to treat a message (of length nextLength). 
	 *
	 * @exception EOFException Throws anexception if th end of the inputstream has
	 *           been reached.
	 */
	byte[] readMessage() throws EOFException, IOException {
		int length = treatInt();
		byte[] t = new byte[length];
		readIS(t);
		return t;
	}

	/**
	 * @return the read {@link GroupComm.common.PID PID} from the inputStream 
	 *  or null if the inputStream has been closed.
	 */
	/*private*/
	PID readPID() throws IOException, EOFException {
		InetAddress inet = null;
		int port = -1;
		int incarnation = -1;

		//Read the inetAddress
		inet = treatInetAddress();

		//Read the port number
		port = treatInt();

		//Read and format the incarnation number
		incarnation = treatInt();

		return new PID(inet, port, incarnation);
	}

	/*private*/
	void shutdownOutput() throws IOException {
		if (socket != null)
			socket.shutdownOutput();
	}

	private InetAddress treatInetAddress() throws EOFException, IOException {
		InetAddress inet = null;

		//Read and format the length of the InetAddress
		int lengthInet = treatInt();

		//Read and format the InetAddress
		byte[] t = new byte[lengthInet];
		readIS(t);

		try {
			inet = InetAddress.getByName(new String(t));
		} catch (UnknownHostException ex) {
			throw new IOException();
		}
		return inet;

	}

	/**
	 * Attempt to rebuild an int from the inputStream.
	 *
	 * @exception EOFException Throws anexception if th end of the inputstream has
	 *           been reached.
	 */
	private int treatInt() throws EOFException, IOException {
		byte[] t = new byte[4];
		readIS(t);
		return bytesToInt(t);
	}

	/**
	 * Reads <b>exactly</b> <i>length</i> bytes from the inputStream, stores it 
	 * into <i>b</i>, and returns the number of read bytes.</br>
	 * If the method returns -1, it means that the inputstream has been closed.
	 */
	private int readIS(byte[] b) throws EOFException, IOException {
		int i = 0, n;
		for (n = 0; n < b.length && i != -1; n += i) {
			i = is.read(b, n, b.length - n);
		}
		if (i == -1)
			throw new EOFException();
		return n;
	}

    //TODO: Migrate this to DataXXput Stream!!!
    /**
     * Transforms an <i>int</i> into a <i>bytearray</i>
     */
    private static byte[] intToBytes(int i) {
        //Creates byte array
        byte[] header = new byte[4];

        header[3] = (byte) (0xff & (i >> 24));
        header[2] = (byte) (0xff & (i >> 16));
        header[1] = (byte) (0xff & (i >> 8));
        header[0] = (byte) (0xff & i);

        return header;
    }

    /**
     * Transfoms a <i>bytearray</i> into an <i>int</i>
     */
    private static int bytesToInt(byte[] b) {
        int i =
            (((b[3] & 0xff) << 24)
                | ((b[2] & 0xff) << 16)
                | ((b[1] & 0xff) << 8)
                | (b[0] & 0xff));

        return i;
    }

	/**
	 * Method for comparing two Connections
	 */
	public int compareTo(Object o) {
		Connection c = (Connection) o;
		if (c == null) {
			System.err.println(
				"Connection: compareTo: remote connectin is null!!");
			return +1;
		}
		if (selfConnected || c.selfConnected)
			return 0;

		IpAndPort[] pairs = {
			//Me Remote
			new IpAndPort(socket.getInetAddress(), socket.getPort()),
			//Me Local
			new IpAndPort(socket.getLocalAddress(), socket.getLocalPort()),
			//Other Remote
			new IpAndPort(c.socket.getInetAddress(), c.socket.getPort()),
			//Other Local
			new IpAndPort(c.socket.getLocalAddress(), c.socket.getLocalPort())};

		int min = 0;
		for (int i = 1; i < pairs.length; i++) {
			if (pairs[i].compareTo(pairs[min]) < 0)
				min = i;
		}

		IpAndPort aux = null;
		if ((min % 2) == 1) {
			aux = pairs[0];
			pairs[0] = pairs[1];
			pairs[1] = aux;
			aux = pairs[2];
			pairs[2] = pairs[3];
			pairs[3] = aux;
		}

		if (pairs[0].compareTo(pairs[2]) < 0) {
			return -1;
		} else if (pairs[0].compareTo(pairs[2]) > 0) {
			return +1;
		} else { //The're the same... let's see the local
			if (pairs[1].compareTo(pairs[3]) < 0) {
				return -1;
			} else if (pairs[1].compareTo(pairs[3]) > 0) {
				return +1;
			} else { //Completely equal
				return 0;
			}
		}
	}

	public String toString() {
			String st = new String(//"ConnectionID:"+
		//super.toString() +
	"PID="
		+ remote
		+ ";  exit="
		+ exit
		+ ";  connected="
		+ connected
		+ ";  sender="
		+ sender
		+ ";  receiver="
		+ receiver);
		if (socket != null)
			st =
				st
					+ "; remote=("
					+ socket.getInetAddress()
					+ ":"
					+ socket.getPort()
					+ "); local=("
					+ socket.getLocalAddress()
					+ ":"
					+ socket.getLocalPort()
					+ ")";
		return st + ")";
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
