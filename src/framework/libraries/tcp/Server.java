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

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import framework.Constants;
import framework.PID;

//import lse.net.net.RServerSocket;
//import lse.net.net.RSocket;

/**
* This class implements a Server which listens for new connections.
* When a connection has been asked, the server adds it to a set of
* connections.
*/
public class Server implements Runnable {
	/**
	 * Reference to the local process. It is passes as a parameter to this 
	 * class's constructor
	 */
	private PID myself;
	/**
	 * The socket that will listen for new TCP-Connection.
	 */
	private ServerSocket servSocket;
	/**
	 * Tells whether the socket is ready to accept new connections. 
	 */
	private boolean open;
	/**
	 * Thread that listens for new connections
	 */
	private Thread listen;
	/**
	 * The parent object that will insert the events into the stack. It is 
	 * typically the wrapping micro-protocol of a low level <i>common code</i> 
	 * protocol.
	 * 
	 * Objects of this class use <i>parent</i> to notify of accepted connections.  
	 */
	private TCPStackInterface parent;

	/**
	* Create a new Server. </br>
	* A new Server thread is created and started.
	*/
	public Server(PID myself, /*int port,*/ TCPStackInterface parent) throws IOException {

		servSocket = new ServerSocket(myself.port);
		open = true;
        
        this.myself = myself;

		// Initializes the parent
		this.parent = parent;

		// Launch the thread (listen) which listens for new connections.
		listen = new Thread(Constants.THREADGROUP, this, "TCPAcceptorThread");
		listen.setDaemon(true);
		listen.start();
	}

	/**
	 * Thread that listens for new connections. </br>
	 * When a new connection arrives from a remote process, it tries to add it to
	 * the set of connections.
	 */
	public void run() {
		try {
			servSocket.setSoTimeout(10000);
		} catch (SocketException e) {
			System.err.println(
				"Server : run : There is an error "
					+ "in the underlying protocol, such as "
					+ "a TCP error.");
			e.printStackTrace();
			System.exit(1);
		}
		while (open) {
			try {
				Socket s = (Socket) servSocket.accept();
				if (open)
					new Connection(myself, s, parent);
			} catch (InterruptedIOException e) {
				/*System.out.println("Server : run : Timeout! Cool!");*/
			} catch (IOException e) {
				System.err.println("Server : run : I/O error");
				e.printStackTrace();
				System.exit(1);
			}
		}
	}

	/**
	 * Finish the listener thread.
	 */
	public void close() {
		open = false;
	}
}
