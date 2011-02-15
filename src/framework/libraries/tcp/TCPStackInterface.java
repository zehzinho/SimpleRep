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
* This interface, together with its companion inteface {@link NonBlockingTCP} defines the way 
* <i>common code</i> protocols interact with the TCP library (offered by the framework).
* 
* It is designed so that protocols can send and receive messages in a totally asynchronous way 
* (so that they do not block when sending/receiving messages, since this would break the 
* non-blocking paradigm of the <i>common code</i>).  
* Another design feature is that the code using these two interfaces does not need to spawn any thread,
* i.e., the protocol code that uses TCP can perfectly be monothreaded.
* 
* The commented out methods, are complementary methods defined by {@link NonBlockingTCP}. They 
* are usually requests done by the protocol code that have to be replied back using the method defined in 
* this class. 
* 
* @author Sergio MENA
*/
public interface TCPStackInterface{
    /**
     * <b>Listen</b>
     */


    /**
     * This method notifies of an incomming connection.
     * 
     * @param c The incomming connection  
     */
    public void accepted(Connection c);
    //public Server startServer(PID myself);
    //public void stopServer(Server s);

    /**
     * <b>Connect/disconnect/Link failure</b>
     */
    
    /**
     * This method is used to notify that the establishment of a connection was sucessful.
     * 
     * @param p The remote PID with which the connection has been established
     * @param c A newly created {@link Connection} object that represents the new connection  
     */
     public void connected(PID p, Connection c);
    //public void connect(PID p);
    /**
     * This method is used to notify that the remote process has closed the connection
     * 
     * @param c Connection that has just been closed
     */
     public void closed(Connection c);
    //public void disconnect(Connection c);
     /**
      * This method notifies that a connection has been broken. It usually comes from the TCP socket level.  
      * I can be due to many reasons: network failure, process failure, etc.
      * 
      * @param c The broken connection
      */
     public void broken(Connection c);

    /**
     * <b>Receive / Blocking send</b>
     */

    /**
     * This method notifies of a message reception from the network
     * 
     * @param b The message (yet to be unmarshalled)
     * @param c The Connection that received the message
     */
    public void recv(byte[] b, Connection c);
     //public void sendMessage(byte[] b, Connection c); /*May block*/
     //public void startReceiver(Connection c);
     //public void stopReceiver(Connection c);

    /**
     * <b>Non-blocking send</b>
     */

    /**
     * This method notifies that a message that was to be sent has been successfully sent, 
     * and thus, the connection is ready to accept the next message.
     * 
     * @param c The Connection that is ready for the next message
     */
    public void readyForNextMessage(Connection c);
    //public void startSender(Connection c);
    //public void stopSender(Connection c);
    //public void setMessageToSend(byte[] b, Connection c);


    /**
     * <b>Miscellaneous</b>
     */
     //public PID getRemotePID(Connection c);
}
