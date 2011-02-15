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
package seqSamoa.protocols.fd;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import uka.transport.Transportable;

import framework.PID;
import framework.Constants;
import framework.GroupCommEventArgs;
import framework.GroupCommException;
import framework.libraries.Timer;
import framework.libraries.Trigger;
import framework.libraries.serialization.TSet;
import framework.libraries.serialization.TBoolean;
import framework.libraries.serialization.TMap;

import seqSamoa.AtomicTask;
import seqSamoa.ProtocolModule;
import seqSamoa.ProtocolStack;
import seqSamoa.Message;
import seqSamoa.ServiceCallOrResponse;

import seqSamoa.exceptions.AlreadyExistingProtocolModuleException;
import seqSamoa.exceptions.NotScheduledTaskException;
import seqSamoa.services.fd.FDSe;
import seqSamoa.services.fd.FDSeCallParameters;
import seqSamoa.services.fd.FDSeResponseParameters;
import seqSamoa.services.udp.UDP;
import seqSamoa.services.udp.UDPCallParameters;

/**
 * This class implement a Protocol that detect distant process failure. It
 * implement the failure detector needed by the consensus protocol described by
 * Chandra and Toueg.
 * 
 * This Protocol need a Protocol that implements UDP transport.
 * 
 * The service implemented is FDSe (described in util/Services.java)
 */
public class ProtocolFDSe extends ProtocolModule implements Trigger, Timer {
    final static int MAX_PROCESSES = 7;

    // Service provided
    private FDSe fdse;

    // Service required
    private UDP udp;

    // The object containing the FDSe algorithm
    static_recovery.common.fd.FDSe handlers = null;

    // Is the Protocol Closed
    boolean closed;

    // Timers scheduled
    Map<Transportable, AtomicTask> timers = null;

    // The Executer
    // It start to monitor the processes in parameters
    protected FDSe.Executer fdSeExecuter;

    // The Listener
    // It wait for UDP message
    protected UDP.Listener udpListener;
    
    // The COR for call to fdse
    private ServiceCallOrResponse fdSeCallCOR;

    /**
     * Constructor.
     * 
     * @param name
     *            String identifier of the protocol
     * @param stack
     * 			  The stack in which the module will be
     * @param sendTimeOut
     *            The default sendTimeOut in ms.
     * @param suspectRetries
     *            The number of timeout before suspecting a process
     * @param storage
     *            The storage for recovery
     * @param fdse
     *            Instance of service FDSe
     * @param udp
     *            Instance of service UDP
     */
    public ProtocolFDSe(String name, ProtocolStack stack, int sendTimeOut, int suspectRetries, FDSe fdse, UDP udp) throws AlreadyExistingProtocolModuleException {

        super(name, stack);

        this.fdse = fdse;
        this.udp = udp;
        
        this.fdSeCallCOR = ServiceCallOrResponse.createServiceCallOrResponse(this.fdse, true);

        handlers = new static_recovery.common.fd.FDSe(this, this, stack.getStorage(),
                sendTimeOut, suspectRetries, stack.getPID());
        timers = new HashMap<Transportable, AtomicTask>();

        LinkedList<ServiceCallOrResponse> initiatedFDSe = new LinkedList<ServiceCallOrResponse>();
        for (int i=0;i<MAX_PROCESSES;i++)
        	initiatedFDSe.add(ServiceCallOrResponse.createServiceCallOrResponse(udp, true));
        initiatedFDSe.add(ServiceCallOrResponse.createServiceCallOrResponse(fdse, false));
        fdSeExecuter = fdse.new Executer(this, initiatedFDSe) {
            public void evaluate(FDSeCallParameters params, Message dmessage) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();

                    ga.addLast(params.startMonitoring);
                    ga.addLast(params.stopMonitoring);

                    try {
                        handlers.handleStartStopMonitor(ga);
                    } catch (GroupCommException ex) {
                        throw new RuntimeException("ProtocolFDSe: fdExecuter: "
                                + ex.getMessage());
                    }
                }
            }
        };

        LinkedList<ServiceCallOrResponse> initiatedUdp = new LinkedList<ServiceCallOrResponse>();
        initiatedUdp.add(ServiceCallOrResponse.createServiceCallOrResponse(udp, true));
        initiatedUdp.add(ServiceCallOrResponse.createServiceCallOrResponse(fdse, false));
        udpListener = udp.new Listener(this, initiatedUdp) {
            public void evaluate(Object infos, Transportable response) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();
                    ga.addLast(response);

                    handlers.handleUDPReceive(ga);
                }
            }
        };
    }

    synchronized public void recovery(boolean recovery) {
        GroupCommEventArgs e = new GroupCommEventArgs();
        e.add(new TBoolean(recovery));

        try {
            handlers.handleRecovery(e);
        } catch (GroupCommException ex) {
            throw new RuntimeException("ProtocolFDSe: Recovery: "
                    + ex.getMessage());
        }
    }

    synchronized public void close() {
        closed = true;
        super.close();
    }

    synchronized public void dump(OutputStream os) {
        handlers.dump(os);
    }

    /**
     * Manage the triggering of the events
     */
    public void trigger(int type, GroupCommEventArgs l) {
        switch (type) {
        case Constants.TRUST_SE:
            FDSeResponseParameters infos = new FDSeResponseParameters((TSet) l
                    .remove(0), (TMap) l.remove(0));

            fdse.response(infos, null);
            break;
        case Constants.UDPSEND:
            Transportable message = l.remove(0);
            UDPCallParameters params = new UDPCallParameters((PID) l.remove(0));

            udp.call(params, new Message(message, udpListener));
            break;
        default:
            throw new RuntimeException("ProtocolFDSe: trigger: "
                    + "Trying to send an unknown event type: " + type);
        }
    }

    private synchronized void timeout(Object o) {
        GroupCommEventArgs args = new GroupCommEventArgs();
        final Transportable key = (Transportable) o;

        if (!timers.containsKey(key))
            // Timer was canceled
            return;

        args.add(key);
        handlers.handleTimeOut(args);
    }

    // Interface for the timers
    synchronized public void schedule(final Transportable key,
            boolean periodic, int time) {
        if (!periodic) {
            throw new RuntimeException("ProtocolFDSe: schedule: "
                    + "Non-periodic timers not supported!!!");
        }

        if (timers.containsKey(key))
            throw new RuntimeException(
                    "ProtocolFDSe: schedule: Task already scheduled!");

        // There is no entry in the map
        // Create the entry and start the timer
        AtomicTask trigger = new AtomicTask() {
            public void execute() {
                timeout(key);
            }
            
            public ServiceCallOrResponse getCOR(){
            	return fdSeCallCOR;
            }
        };

        timers.put(key, trigger);
        stack.getScheduler().schedule(trigger, periodic, time);
    }

    synchronized public void cancel(Transportable key) {
        try {
        	stack.getScheduler().cancel(timers.remove(key));
        } catch (NotScheduledTaskException ex) {
            throw new RuntimeException("ProtocolPing: cancel: The task is not"
                    + " currently scheduled");
        }
    }

    synchronized public void reset(Transportable key) {
        try {
        	stack.getScheduler().reset(timers.get(key));
        } catch (NotScheduledTaskException ex) {
            throw new RuntimeException("ProtocolPing: reset: The task is not"
                    + " currently scheduled");
        }
    }
}
