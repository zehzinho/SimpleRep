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

import framework.PID;

/**
* This interface, together with its companion inteface {@link TCPStackInterface} defines the way 
* <i>common code</i> protocols interact with the TCP library (offered by the framework).
* 
* It is designed so that protocols can send and receive messages in a totally asynchronous way 
* (so that they do not block when sending/receiving messages, since this would break the 
* non-blocking paradigm of the <i>common code</i>).  
* Another design feature is that the code using these two interfaces does not need to spawn any thread,
* i.e., the protocol code that uses TCP can perfectly be monothreaded.
* 
* The commented out methods, are complementary methods defined by {@link TCPStackInterface}. They 
* are usually a response to a call done by the protocol code. 
* 
* @author Sergio MENA
*/
public interface NonBlockingTCP{
    /**
     * <b>Methods to Listen</b>
     */
    
    /**
     * This method creates a Server object which contains a server object and a listener thread for accpeting 
     * connections. Incomming connections are listened on the TCP port contained in the PID object passed as 
     * parameter. Upon an incomming connection, this object calls method <i>accepted</i> of 
     * {@link TCPStackInterface}.
     * 
     * @param myself The identifier of the local process. It is needed to know on which port the server TCP
     * socket should listen.
     */
    public Server startServer(PID myself);
    /**
     * This method stops the server thread. So, from now on, incomming connections will not be dispatched.
     * 
     * @param s The server to stop.
     */
    public void stopServer(Server s);
    //upon accepted(PID p, Connection c)

    /**
     * <b>Connect/disconnect/Link failure</b>
     */
    
    /**
     * With this method, a {@link Connection} (a class containing a TCP socket) is created to connect the local 
     * process and the process passed as parameter. This method returns immediately (i.e., it does not wait for 
     * the connection to fail or be established). 
     * 
     * If the connection is established, the <i>connected</i> method of {@link TCPStackInterface} is called with 
     * the newly created {@link Connection} object as paremter to notify this event. If the connection could not be 
     * established (e.g., a firewall is present, the other endpoint is not listening, etc) the same method is called 
     * but its {@link Connection} parameter is set to <b>null</b>. 
     * 
     * @param p The remote process with which the connection is to be established 
     */
    public void connect(PID p);
    //upon connected(PID p, Connection c)
    /**
     * Use this method to close an already established connection. The remote endpoint will be notified with 
     * the <i>closed</i> method of {@link TCPStackInterface}.
     * 
     * @param c The connection to close
     */
    public void disconnect(Connection c);
    //upon closed(Connection c)
    //upon broken(Connection c)

    /**
     * <b>Receive / Blocking send</b>
     */
    
    /**
     * This is the "blocking" version to send a message. If TCP buffers are full, this method will block until these 
     * buffers become empty and the message fits. If the message fits in the TCP buffers from the beginning, 
     * this method returns immediately. 
     *   
     * @param b The message (already serialized) to be sent
     * @param c The {@link Connection} that will be used
     */
    public void sendMessage(byte[] b, Connection c); /*May block*/
    /**
     * As long as this method is not called (or {@link stopReceiver} was called) the {@link Connection} does not 
     * receive messages from the network (so TCP flow control will eventually block a sender that keeps on sending 
     * messages). At the moment this method is called, a receiver thread is started to received messages from the 
     * network.
     * 
     * The receiver thread notifies the reception of a message with method <i>recv</i> of {@link TCPStackInterface}.
     * 
     * @param c The connection whose receiver thread is to be started.
     */
    public void startReceiver(Connection c);
    /**
     * This method stops the receiving thread. It is not ensured that <b>no</b> message will be received soon after 
     * the call to this method, since it returns immediately.
     * 
     * The receiver thread can be started again later on with method <i>startReceiver</i>
     * 
     * @param c The connection whose receiver thread is to be stopped.
     */
    public void stopReceiver(Connection c);
    //upon recv(byte[] b, Connection c);

    /**
     * <b>Non-blocking send</b>
     */
    
    /**
     * This method starts a {@link Connection}'s sender thread. The sender thread allows the protocol code to send 
     * messages to the network without blocking in the sending call ({@see setMessageToSend}.
     * If the sender thread is not active, asynchronous (non-blocking) sending is not possible.
     * 
     * @param c The connection whose sender thread is to be started.
     */
    public void startSender(Connection c);
    /**
     * This method stops the sender thread.  It is not ensured that <b>no</b> message will be sent soon after 
     * the call to this method, since it returns immediately.
     * 
     * The sender thread can be started again later on with method <i>startSender</i>
     * 
     * @param c The connection whose sender thread is to be stopped.
     */
    public void stopSender(Connection c);
    /**
     * This is the method to use to send messages without the risk of blocking. The mechanism works as follows.
     * After the sender thread is spawned {@see startSender}), the protocol code sets the message to be asynchronously 
     * sent by calling this method, which returns immediately. Then the message is actually sent (if TCP buffers are empty 
     * this is done almost instantly, but if TCP buffers are full it can take a long time), and when the sending is done, method 
     * <i>readyForNextMessage</i> of {@link TCPStackInterface} is called to tell the protocol code that the Connection is 
     * ready to accept the next message.
     * 
     * If this method is called twice before receiving the <i>readyForNextMessage</i> notification, the effects are unpredictable 
     * (usually the previous message will be overwritten by the new one, and thus lost forever).
     * 
     * @param b The message (already serialized) to be sent
     * @param c The connection that will be used
     */
    public void setMessageToSend(byte[] b, Connection c);
    //upon readyForNextMessage(Connection c)

    /**
     * <b>Miscellaneous</b>
     */
    
    /**
     * Return the PID of the process that the Connection is connected to.
     * 
     * @param c The connection to be queried
     */
    public PID getRemotePID(Connection c);
}
