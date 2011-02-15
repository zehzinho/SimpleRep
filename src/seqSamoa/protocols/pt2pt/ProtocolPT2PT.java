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
package seqSamoa.protocols.pt2pt;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import seqSamoa.Message;
import seqSamoa.ProtocolModule;
import seqSamoa.ProtocolStack;
import seqSamoa.Service;
import seqSamoa.ServiceCallOrResponse;
import seqSamoa.AtomicTask;
import seqSamoa.exceptions.AlreadyExistingProtocolModuleException;
import seqSamoa.exceptions.NotScheduledTaskException;
import seqSamoa.services.pt2pt.PT2PT;
import seqSamoa.services.pt2pt.PT2PTCallParameters;
import seqSamoa.services.pt2pt.PT2PTResponseParameters;
import seqSamoa.services.udp.UDP;
import seqSamoa.services.udp.UDPCallParameters;
import static_recovery.common.pt2pt.Pt2ptHandler;
import uka.transport.Transportable;
import framework.Constants;
import framework.GroupCommEventArgs;
import framework.GroupCommException;
import framework.GroupCommMessage;
import framework.PID;
import framework.libraries.Timer;
import framework.libraries.Trigger;
import framework.libraries.serialization.TBoolean;

/**
 * This class implement a Protocol that allows Point to Point communication
 * 
 * The service implemented is pt2pt (described in util/Services.java)
 */
public class ProtocolPT2PT extends ProtocolModule implements Trigger, Timer {
    // Service provided
    private PT2PT pt2pt;

    // Service required
    private UDP udp;

    protected Pt2ptHandler handlers = null;

    // Is the Protocol Closed
    boolean closed;

    /** Timers scheduled */
    Map<Transportable, AtomicTask> timers = null;

    // The Executer
    // It start to monitor the processes in parameters
    protected PT2PT.Executer pt2ptExecuter;

    // The Listener
    // It wait for UDP message
    protected UDP.Listener udpListener;
    
    // The COR for the call to pt2pt
    private ServiceCallOrResponse pt2ptCallCOR;

    /**
     * Constructor
     * 
     * @param name
     *            String identifier of the protocol
     * @param stack
     * 			  The stack in which the module will be
     * @param sendTimeout
     *            The time we wait before sending again a message
     */
    public ProtocolPT2PT(String name, ProtocolStack stack, int sendTimeout, PT2PT pt2pt,
            UDP udp) throws AlreadyExistingProtocolModuleException {
        super(name, stack);

        this.timers = new HashMap<Transportable, AtomicTask>();
        this.closed = false;

        handlers = new Pt2ptHandler(this, this, sendTimeout, stack.getPID());

        this.udp = udp;
        this.pt2pt = pt2pt;
        this.pt2ptCallCOR = ServiceCallOrResponse.createServiceCallOrResponse(this.pt2pt, true);

        LinkedList<ServiceCallOrResponse> initiatedPt2Pt = new LinkedList<ServiceCallOrResponse>();
        initiatedPt2Pt.add(ServiceCallOrResponse.createServiceCallOrResponse(udp, true));
        pt2ptExecuter = pt2pt.new Executer(this, initiatedPt2Pt) {
            public void evaluate(PT2PTCallParameters params, Message dmessage) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();

                    if (dmessage != null) {
                        ga.addLast(dmessage.toGroupCommMessage());
                        ga.addLast(params.pid);
                        ga.addLast(params.key);

                        handlers.handlePt2ptSend(ga);
                    } else {
                        ga.addLast(params.key);

                        try {
                            handlers.handleKillMessage(ga);
                        } catch (Exception e) {
                            throw new RuntimeException("ProtocolPT2PT: "
                                    + "PT2PTExecuter: " + e);
                        }
                    }
                }
            }
        };

        LinkedList<ServiceCallOrResponse> initiatedUdp = new LinkedList<ServiceCallOrResponse>();
        initiatedUdp.add(ServiceCallOrResponse.createServiceCallOrResponse(pt2pt, false));
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

    private synchronized void timeout(Object o) {
        final Transportable fKey = (Transportable) o;

        if (!timers.containsKey(fKey))
            // Timer was canceled
            return;

        handlers.handleTimeout(fKey);
    }

    synchronized public void recovery(boolean recovery) {
        GroupCommEventArgs e = new GroupCommEventArgs();
        e.add(new TBoolean(recovery));

        try {
            handlers.handleRecovery(e);
        } catch (GroupCommException ex) {
            throw new RuntimeException("ProtocolPT2PT: Recovery: "
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
    @SuppressWarnings("unchecked")
	public void trigger(int type, GroupCommEventArgs l) {
        switch (type) {
        case Constants.PT2PTDELIVER:
            Message message = new Message((GroupCommMessage) l.get(0));
            PT2PTResponseParameters infos = new PT2PTResponseParameters(
                    (PID) l.get(1));

            pt2pt.externalResponse(infos, message);
            break;
        case Constants.UDPSEND:
            Transportable umessage = l.get(0);
            UDPCallParameters uparams = new UDPCallParameters((PID) l.get(1));

            udp.call(uparams,
                    new Message(umessage, (Service.Listener) udpListener));
            break;
        default:
            throw new RuntimeException("Error : ProtocolPT2PT : trigger"
                    + "Trying to send an unknown event type: " + type);
        }
    }

    /**
     * <i>Requiered by interface Timer </i>
     */
    synchronized public void schedule(final Transportable key, boolean periodic, int time) {
        if (!periodic)
            throw new RuntimeException("ProtocolPT2PT: schedule: "
                    + "Non-periodic timers not supported!!!");

        if (timers.containsKey(key))
            throw new RuntimeException("ProtocolPT2PT: schedule: "
                    + "Task already scheduled!");

        // There is no entry in the map
        // Create the entry and start the timer
        AtomicTask trigger = new AtomicTask() {
            public void execute() {
                timeout(key);
            }
            
            public ServiceCallOrResponse getCOR(){
            	return pt2ptCallCOR;
            }
        };

        timers.put(key, trigger);
        stack.getScheduler().schedule(trigger, periodic, time);
    }

    /**
     * <i>Requiered by interface Timer</i>
     */
    synchronized public void cancel(Transportable key) {
        try {
        	stack.getScheduler().cancel(timers.remove(key));
        } catch (NotScheduledTaskException ex) {
            throw new RuntimeException("ProtocolPing: cancel: The task is not"
                    + " currently scheduled");
        }
    }

    /**
     * <i>Requiered by interface Timer</i>
     */
    public void reset(Transportable key) {
        throw new RuntimeException("ProtocolPT2PT: reset: not supported!!!");
    }
}
