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
package seqSamoa.protocols.udp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.LinkedList;

import seqSamoa.Message;
import seqSamoa.ProtocolModule;
import seqSamoa.ProtocolStack;
import seqSamoa.Service;
import seqSamoa.ServiceCallOrResponse;
import seqSamoa.exceptions.AlreadyExistingProtocolModuleException;
import seqSamoa.services.udp.UDP;
import seqSamoa.services.udp.UDPCallParameters;
import framework.libraries.DefaultSerialization;

/**
 * This class implement a Protocol that allow to send Object through UDP
 * 
 * The service implemented is udp (described in util/Services.java)
 */
public class ProtocolUDP extends ProtocolModule implements Runnable {
    // Service provided
    private UDP udp;

    // Max length for a datagram packet
    private static final int MAX_PACKET_LENGTH = 1024 * 30;

    // UDP socket to send/receive
    private DatagramSocket dsock = null;

    // True if the thread is running
    private boolean open = true;

    // A thread to listen on the UDP socket
    private Thread thread;

    // The Executer
    // It send a message with udp
    protected Service<UDPCallParameters, Object>.Executer udpExecuter;

    /**
     * Constructor. <br>
     * 
     * @param name
     *            Name of the layer
     * @param port
     *            The port to listen
     */
    public ProtocolUDP(String name, ProtocolStack stack, UDP udp) throws AlreadyExistingProtocolModuleException {

        // Initialises the microprotocol
        super(name, stack);

        this.udp = udp;

        // Initialize the Executer
        LinkedList<ServiceCallOrResponse> initiatedUdp = new LinkedList<ServiceCallOrResponse>();
        udpExecuter = this.udp.new Executer(this, initiatedUdp) {
            public void evaluate(UDPCallParameters params, Message dmessage) {
                synchronized (this.parent) {
                    try {
                        // Writes and sends the object
                        byte[] b = DefaultSerialization.marshall(dmessage);
                        if (b.length > MAX_PACKET_LENGTH) {
                            throw new RuntimeException("ProtocolUDP : Trying"
                                    + " to send a packet too" + " long."
                                    + dmessage);
                        }

                        DatagramPacket pack = new DatagramPacket(b, b.length,
                                params.pid.ip, params.pid.port);
                        dsock.send(pack);
                    } catch (IOException e) {
                        throw new RuntimeException("ProtocolUDP: udpExecuter: "
                                + "IOException: " + e.getMessage());
                    }
                }
            }
        };

        // Creates a new datagram socket.
        try {
            dsock = new DatagramSocket(stack.getPID().port);
        } catch (IOException ex) {
            throw new RuntimeException(
                    "ProtocolUDP  : IOException : Failed to create a datagram socket.");
        }
    }

    /**
     * Overload the close function of Protocol
     */
    synchronized public void close() {
        open = false;
        super.close();
    }

    synchronized public void dump(OutputStream stream) {
    }

    /**
     * Enable reception of message in starting a dedicated thread
     */
    synchronized public void startListener() {
        thread = new Thread(this);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Thread that will listen to the network and detect reception of UDP
     * messages
     */
    public void run() {
        try {
            while (open) {
                // Creates a new bytes array
                byte[] b = new byte[MAX_PACKET_LENGTH];
                // Creates a UDP packet for receiving a packet.
                // Max size: MAX_PACKET_LENGTH
                DatagramPacket pack = new DatagramPacket(b, b.length);

                // Reads a packet.
                dsock.receive(pack);

                // Reads the object
                Message message = (Message) DefaultSerialization
                        .unmarshall(b);
 
                if (open)
                	udp.externalResponse(null, message);
            } // while (open)
            dsock.close();

        } catch (IOException ex) {
            throw new RuntimeException("MicroUDP : run : IOException");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                    "MicroUDP : run : ClassNotFoundException : Class read from the socket was not found");
        }
    }

}
