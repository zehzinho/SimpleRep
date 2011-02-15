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

import seqSamoa.Message;
import seqSamoa.ProtocolModule;
import seqSamoa.ProtocolStack;
import seqSamoa.Service;
import seqSamoa.ServiceCallOrResponse;
import seqSamoa.AtomicTask;
import seqSamoa.exceptions.AlreadyExistingProtocolModuleException;
import seqSamoa.exceptions.NotScheduledTaskException;
import seqSamoa.services.fd.FD;
import seqSamoa.services.fd.FDCallParameters;
import seqSamoa.services.fd.FDResponseParameters;
import seqSamoa.services.udp.UDP;
import seqSamoa.services.udp.UDPCallParameters;
import uka.transport.Transportable;
import framework.Constants;
import framework.GroupCommEventArgs;
import framework.GroupCommException;
import framework.PID;
import framework.libraries.Timer;
import framework.libraries.Trigger;
import framework.libraries.serialization.TSet;
import groupcomm.common.fd.HBHandler;

/**
 * This class implement a Protocol that detect distant process failure. It
 * implement the failure detector needed by the consensus protocol described by
 * Chandra and Toueg.
 * 
 * This Protocol need a Protocol that implements UDP transport.
 * 
 * The service implemented is FD (described in util/Services.java)
 */
public class ProtocolHB extends ProtocolModule implements Trigger, Timer {
    final static int MAX_PROCESSES = 7;

    // Service provided
    private FD fd;

    // Service required
    private Service<UDPCallParameters, Object> udp;

    // The object containig the FD algorithm
    private HBHandler handlers = null;

    // Timers scheduled
    private Map<Transportable, AtomicTask> timers = null;

    // The Executer
    // It start to monitor the processes in parameters
    protected FD.Executer fdExecuter;

    // The Listener
    // It wait for UDP message
    protected UDP.Listener udpListener;
    
    // The fd call COR
    private ServiceCallOrResponse fdCallCOR;

    /**
     * Constructor. <br>
     * 
     * @param name
     *            Name of the layer
     * @param stack
     * 			  The stack in which the module will be
     * @param timeout
     *            The timeout for ping messages
     * @param retries
     *            The number of retries before suspecting
     */
    @SuppressWarnings("unchecked")
	public ProtocolHB(String name, ProtocolStack stack, int timeout, int retries, FD fd,
            Service<? extends UDPCallParameters, ? extends Object> udp) throws AlreadyExistingProtocolModuleException {
        super(name, stack);

        handlers = new HBHandler(this, this, stack.getPID(), timeout, retries, retries);
        timers = new HashMap<Transportable, AtomicTask>();

        this.fd = fd;
        this.udp = (Service<UDPCallParameters, Object>) udp;
        
        this.fdCallCOR = ServiceCallOrResponse.createServiceCallOrResponse(this.fd, true);

        LinkedList<ServiceCallOrResponse> initiatedFd = new LinkedList<ServiceCallOrResponse>();
        for (int i=0;i<MAX_PROCESSES;i++)
        	initiatedFd.add(ServiceCallOrResponse.createServiceCallOrResponse(udp, true));
        initiatedFd.add(ServiceCallOrResponse.createServiceCallOrResponse(fd, false));
        fdExecuter = fd.new Executer(this, initiatedFd) {
            public void evaluate(FDCallParameters params, Message dmessage) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();

                    ga.addLast(params.startMonitoring);
                    ga.addLast(params.stopMonitoring);

                    try {
                        handlers.handleStartStopMonitor(ga);
                    } catch (GroupCommException ex) {
                        throw new RuntimeException("ProtocolHB: fdExecuter: "
                                + ex.getMessage());
                    }
                }
            }
        };

        LinkedList<ServiceCallOrResponse> initiatedUdp = new LinkedList<ServiceCallOrResponse>();
        initiatedUdp.add(ServiceCallOrResponse.createServiceCallOrResponse(udp, true));
        initiatedUdp.add(ServiceCallOrResponse.createServiceCallOrResponse(fd, false));
        udpListener = this.udp.new Listener(this, initiatedUdp) {
            public void evaluate(Object infos, Transportable response) {
                synchronized (this.parent) {

                    GroupCommEventArgs ga = new GroupCommEventArgs();
                    ga.addLast(response);

                    handlers.handleUDPReceive(ga);
                }
            }
        };
    }

    synchronized public void dump(OutputStream stream) {
        handlers.dump(stream);
    }

    /**
     * Manage the triggering of the events
     */
    public void trigger(int type, GroupCommEventArgs e) {
        switch (type) {
        case Constants.ALIVE:
            Transportable message = e.remove(0);
            UDPCallParameters params = new UDPCallParameters((PID) e.remove(0));

            udp.call(params, new Message(message, udpListener));
            break;
        case Constants.SUSPECT:
            FDResponseParameters infos = new FDResponseParameters((TSet) e
                    .remove(0));

            fd.response(infos, null);
            break;
        default:
            // This shouldn't ever happen
            throw new RuntimeException("ProtocolHB: trigger: "
                    + "Unexpected event type");
        }
    }

    // Interface for the timers
    synchronized public void schedule(final Transportable key, boolean periodic,
            int time) {
        if (!periodic)
            throw new RuntimeException("ProtocolHB: schedule: Non-periodic "
                    + "timers not supported. Discarding it.");
        if (!timers.containsKey(key)) {
            // There is no entry in the map
            // Create the entry and start the timer
            AtomicTask trigger = new AtomicTask() {
                public void execute() {
                    timeout(key);
                }
                  
                public ServiceCallOrResponse getCOR() {
                	return fdCallCOR;
                }
            };

            timers.put(key, trigger);
            stack.getScheduler().schedule(trigger, periodic, time);
        } else {
            throw new RuntimeException("ProtocolHB:schedule: Suspect "
                    + "Task already scheduled!");
        }
    }

    synchronized public void cancel(Transportable key) {
        try{
        	stack.getScheduler().cancel(timers.remove(key));            
        } catch (NotScheduledTaskException ex){
            throw new RuntimeException("ProtocolPing: cancel: The task is not"
                    + " currently scheduled");
        }
    }

    synchronized public void reset(Transportable key) {
        try{
        	stack.getScheduler().reset(timers.get(key));            
        } catch (NotScheduledTaskException ex){
            throw new RuntimeException("ProtocolPing: reset: The task is not"
                    + " currently scheduled");
        }
    }

    synchronized private void timeout(Object o) {
        GroupCommEventArgs ga = new GroupCommEventArgs();
        final Transportable key = (Transportable) o;

        if (!timers.containsKey(key))
            // Timer already canceled
            return;

        ga.add(key);
        handlers.handleTimeOut(ga);
    }
}
