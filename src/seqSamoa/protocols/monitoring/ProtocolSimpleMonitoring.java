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
package seqSamoa.protocols.monitoring;

import java.io.OutputStream;
import java.util.LinkedList;

import seqSamoa.Message;
import seqSamoa.ProtocolModule;
import seqSamoa.ProtocolStack;
import seqSamoa.ServiceCallOrResponse;
import seqSamoa.exceptions.AlreadyExistingProtocolModuleException;
import seqSamoa.services.gms.ManageView;
import seqSamoa.services.gms.ManageViewCallParameters;
import seqSamoa.services.monitoring.ProcessSuspicion;
import seqSamoa.services.monitoring.ProcessSuspicionCallParameters;
import framework.Constants;
import framework.GroupCommEventArgs;
import framework.PID;
import framework.libraries.Trigger;
import framework.libraries.serialization.TBoolean;
import groupcomm.common.monitoring.SimpleMonitoringImpl;

/**
 * This class implement a Protocol that remove processes highly suspected form
 * the current view.
 * 
 * This Protocol need a Protocol that implements GM.
 * 
 * The service implemented is Process_Suspicion (described in
 * util/Services.java)
 */
public class ProtocolSimpleMonitoring extends ProtocolModule implements Trigger {
    final static int MAX_PROCESSES = 7;

    // Service required
    private ManageView manageView;

    protected SimpleMonitoringImpl handlers;

    protected ProcessSuspicion.Executer monitorExecuter;

    /**
     * Constructor. <br>
     * 
     * @param name
     *            Name of the layer
     * @param stack
     * 			  The stack in which the module will be
     */
    public ProtocolSimpleMonitoring(String name, ProtocolStack stack,
            ProcessSuspicion processSuspicion, ManageView manageView) throws AlreadyExistingProtocolModuleException {

        super(name, stack);
        handlers = new SimpleMonitoringImpl(this);

        this.manageView = manageView;

        LinkedList<ServiceCallOrResponse> initiatedMonitor = new LinkedList<ServiceCallOrResponse>();
        for (int i=0;i<MAX_PROCESSES;i++)
        	initiatedMonitor.add(ServiceCallOrResponse.createServiceCallOrResponse(manageView, true));
        monitorExecuter = processSuspicion.new Executer(this, initiatedMonitor) {
            public void evaluate(ProcessSuspicionCallParameters params,
                    Message dmessage) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();

                    ga.add(params.suspected);
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
    public void trigger(int type, GroupCommEventArgs l) {
        switch (type) {
        case Constants.REMOVE:
            PID pid = (PID) l.remove(0);
            ManageViewCallParameters params = new ManageViewCallParameters(pid,
                    new TBoolean(false));

            manageView.call(params, null);
            break;
        default:
            throw new RuntimeException("ProtocolSimpleMonitoring: trigger: "
                    + "Unexpected event type");
        }
    }
}
