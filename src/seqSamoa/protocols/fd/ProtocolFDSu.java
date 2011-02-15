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

import framework.Constants;
import framework.GroupCommException;
import framework.GroupCommEventArgs;
import framework.PID;
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
import seqSamoa.services.fd.FDSu;
import seqSamoa.services.fd.FDSuCallParameters;
import seqSamoa.services.fd.FDSuResponseParameters;
import seqSamoa.services.udp.UDP;
import seqSamoa.services.udp.UDPCallParameters;

/**
 * This class implement a Protocol that detect distant process failure. It
 * implement the failure detector needed by the consensus protocol described by
 * Chandra and Toueg.
 * 
 * This Protocol need a Protocol that implements UDP transport and a protocol
 * that implements FDSe.
 * 
 * The service implemented is FDSu (described in util/Services.java)
 */
public class ProtocolFDSu extends ProtocolModule implements Trigger, Timer {
    final static int MAX_PROCESSES = 7;

    // Service provided
    private FDSu fdsu;

    // Service required
    private UDP udp;

    private FDSe fdse;

    // The object containing the FDSu algorithm
    static_recovery.common.fd.FDSu handlers = null;

    // Is the Protocol Closed
    boolean closed;

    // Timers scheduled
    Map<Transportable, AtomicTask> timers = null;

    // The Executer
    // It start to monitor the processes in parameters
    protected FDSu.Executer fdSuExecuter;

    // The Listener
    // It wait for UDP message
    protected UDP.Listener udpListener;

    // It wait for FDSe suspicions
    protected FDSe.Listener fdSeListener;

    // The COR for the fdSe response
    private ServiceCallOrResponse fdSeResponseCOR;
    
    /**
     * Constructor
     * 
     * @param name
     *            String identifier of the protocol
     * @param stack
     * 			  The stack in which the module will be
     * @param storage
     *            The storage for recovery
     */
    public ProtocolFDSu(String name, ProtocolStack stack,
            FDSu fdsu, FDSe fdse, UDP udp) throws AlreadyExistingProtocolModuleException {

        super(name, stack);

        this.fdsu = fdsu;
        this.fdse = fdse;
        this.udp = udp;
        
        this.fdSeResponseCOR = ServiceCallOrResponse.createServiceCallOrResponse(fdse, false);

        handlers = new static_recovery.common.fd.FDSu(this, this, stack.getStorage(),
                stack.getPID());
        timers = new HashMap<Transportable, AtomicTask>();

        LinkedList<ServiceCallOrResponse> initiatedFDSu = new LinkedList<ServiceCallOrResponse>();
        initiatedFDSu.add(ServiceCallOrResponse.createServiceCallOrResponse(fdse, true));
        initiatedFDSu.add(ServiceCallOrResponse.createServiceCallOrResponse(fdsu, false));
        fdSuExecuter = fdsu.new Executer(this, initiatedFDSu) {
            public void evaluate(FDSuCallParameters params, Message dmessage) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();

                    ga.addLast(params.startMonitoring);
                    ga.addLast(params.stopMonitoring);

                    try {
                        handlers.handleStartStopMonitor(ga);
                    } catch (GroupCommException ex) {
                        throw new RuntimeException("ProtocolFDSu: fdExecuter: "
                                + ex.getMessage());
                    }
                }
            }
        };

        LinkedList<ServiceCallOrResponse> initiatedUDP = new LinkedList<ServiceCallOrResponse>();
        initiatedUDP.add(ServiceCallOrResponse.createServiceCallOrResponse(fdsu, false));
        udpListener = udp.new Listener(this, initiatedUDP) {
            public void evaluate(Object infos, Transportable response) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();
                    ga.addLast(response);

                    handlers.handleUDPReceive(ga);
                }
            }
        };

        LinkedList<ServiceCallOrResponse> initiatedFDSe = new LinkedList<ServiceCallOrResponse>();
        for (int i=0;i<MAX_PROCESSES;i++)
        	initiatedFDSe.add(ServiceCallOrResponse.createServiceCallOrResponse(udp, true));
         fdSeListener = fdse.new Listener(this, initiatedFDSe) {
            public void evaluate(FDSeResponseParameters infos,
                    Transportable response) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();
                    ga.addLast(infos.suspected);
                    ga.addLast(infos.epoch);

                    handlers.handleTrustSe(ga);
                }
            }
        };
    }

    synchronized public void recovery(boolean recovery) {
        GroupCommEventArgs e = new GroupCommEventArgs();
        e.add(new TBoolean(recovery));

        handlers.handleRecovery(e);
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
        case Constants.TRUST_SU:
            FDSuResponseParameters infos = new FDSuResponseParameters((TSet) l
                    .remove(0), (TMap) l.remove(0));

            fdsu.response(infos, null);
            break;
        case Constants.UDPSEND:
            Transportable message = l.remove(0);
            UDPCallParameters params = new UDPCallParameters((PID) l.remove(0));

            udp.call(params, new Message(message, udpListener));
            break;
        case Constants.STARTSTOPMONITOR:
            FDSeCallParameters fparams = new FDSeCallParameters((TSet) l
                    .remove(0), (TSet) l.remove(0));
            fdse.call(fparams, null);
            break;
        default:
            throw new RuntimeException("ProtocolFDSu: trigger: "
                    + "Trying to send an unknown event type: " + type);
        }
    }

    private synchronized void timeout(Object o) {
        final Transportable key = (Transportable) o;
        GroupCommEventArgs args = new GroupCommEventArgs();

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
            throw new RuntimeException("ProtocolFDSu: schedule: "
                    + "Non-periodic timers not supported!!!");
        }

        if (timers.containsKey(key))
            throw new RuntimeException(
                    "ProtocolFDSu: schedule: Task already scheduled!");

        // There is no entry in the map
        // Create the entry and start the timer
        AtomicTask trigger = new AtomicTask() {
            public void execute() {
                timeout(key);
            }
            
            public ServiceCallOrResponse getCOR() {
            	return fdSeResponseCOR;
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
