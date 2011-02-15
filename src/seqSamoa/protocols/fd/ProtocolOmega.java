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
import java.util.LinkedList;

import seqSamoa.Message;
import seqSamoa.ProtocolModule;
import seqSamoa.ProtocolStack;
import seqSamoa.ServiceCallOrResponse;
import seqSamoa.exceptions.AlreadyExistingProtocolModuleException;
import seqSamoa.services.fd.FD;
import seqSamoa.services.fd.FDCallParameters;
import seqSamoa.services.fd.FDResponseParameters;
import seqSamoa.services.fd.Leader;
import seqSamoa.services.fd.LeaderCallParameters;
import seqSamoa.services.fd.LeaderResponseParameters;
import uka.transport.Transportable;
import framework.Constants;
import framework.GroupCommEventArgs;
import framework.GroupCommException;
import framework.PID;
import framework.libraries.Trigger;
import framework.libraries.serialization.TList;
import framework.libraries.serialization.TSet;
import groupcomm.common.fd.LeaderHandler;

/**
 * This class implement a Protocol that detect distant process failure. It
 * implement the failure detector needed by the consensus protocol described by
 * Chandra and Toueg.
 * 
 * This Protocol need a Protocol that implements FD.
 * 
 * The service implemented is Leader (described in util/Services.java)
 */
public class ProtocolOmega extends ProtocolModule implements Trigger {
    // Service provided
    private Leader leader;

    // Service required
    private FD fd;

    // The object containig the Omega algorithm
    private LeaderHandler handlers = null;

    // The Executer
    // It start to monitor the processes in parameters
    protected Leader.Executer leaderExecuter;

    // The Listener
    // It wait for suspicions of FD
    protected FD.Listener fdListener;

    /**
     * Constructor. <br>
     * 
     * @param name
     *            Name of the layer
     * @param stack
     * 			  The stack in which the module will be
     */
    public ProtocolOmega(String name, ProtocolStack stack, Leader leader, FD fd) throws AlreadyExistingProtocolModuleException {
        super(name, stack);

        handlers = new LeaderHandler(this, stack.getPID());

        this.leader = leader;
        this.fd = fd;

        LinkedList<ServiceCallOrResponse> initiatedLeader = new LinkedList<ServiceCallOrResponse>();
        initiatedLeader.add(ServiceCallOrResponse.createServiceCallOrResponse(fd, true));
        initiatedLeader.add(ServiceCallOrResponse.createServiceCallOrResponse(leader, false));        
        leaderExecuter = leader.new Executer(this, initiatedLeader) {
            public void evaluate(LeaderCallParameters params, Message dmessage) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();

                    ga.add(params.startMonitoring);
                    ga.add(params.stopMonitoring);

                    try {
                        handlers.handleStartStopMonitor(ga);
                    } catch (GroupCommException ex) {
                        throw new RuntimeException("ProtocolOmega: "
                                + "leaderExecuter: " + ex.getMessage());
                    }
                }
            }
        };

        LinkedList<ServiceCallOrResponse> initiatedFd = new LinkedList<ServiceCallOrResponse>();
        initiatedFd.add(ServiceCallOrResponse.createServiceCallOrResponse(leader, false));
        fdListener = fd.new Listener(this, initiatedFd) {
            public void evaluate(FDResponseParameters infos,
                    Transportable response) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();

                    ga.add(infos.suspected);

                    handlers.handleSuspect(ga);
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
        case Constants.STARTSTOPMONITOR:
            FDCallParameters params = new FDCallParameters((TSet) e.remove(0),
                    (TSet) e.remove(0));

            fd.call(params, null);
            break;
        case Constants.NEWLEADER:
            LeaderResponseParameters infos = new LeaderResponseParameters(
                    (PID) e.remove(0), (TList) e.remove(0));

            leader.response(infos, null);
            break;
        default:
            // This shouldn't ever happen
            throw new RuntimeException("ProtocolOmega: trigger: "
                    + "Unexpected event type");
        }
    }
}
