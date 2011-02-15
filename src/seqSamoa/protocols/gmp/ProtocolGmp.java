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
package seqSamoa.protocols.gmp;

import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedList;

import seqSamoa.Message;
import seqSamoa.ProtocolModule;
import seqSamoa.ProtocolStack;
import seqSamoa.ServiceCallOrResponse;
import seqSamoa.Service.Listener;
import seqSamoa.exceptions.AlreadyExistingProtocolModuleException;
import seqSamoa.services.abcast.DynAbcast;
import seqSamoa.services.abcast.DynAbcastCallParameters;
import seqSamoa.services.abcast.DynAbcastResponseParameters;
import seqSamoa.services.gms.ManageView;
import seqSamoa.services.gms.ManageViewCallParameters;
import seqSamoa.services.gms.ManageViewResponseParameters;
import seqSamoa.services.rpt2pt.RPT2PT;
import seqSamoa.services.rpt2pt.RPT2PTCallParameters;
import seqSamoa.services.rpt2pt.RPT2PTResponseParameters;
import uka.transport.Transportable;
import framework.Constants;
import framework.GroupCommEventArgs;
import framework.GroupCommException;
import framework.PID;
import framework.libraries.Trigger;
import framework.libraries.serialization.TBoolean;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TList;
import framework.libraries.serialization.TSet;
import groupcomm.common.gmp.GroupMembershipImpl;

/**
 * This class implement the Group Membership protocol
 * 
 * This Protocol need a Protocol that implements Abcast and PT2PT
 * 
 * The service implemented is manageView (described in util/Services.java)
 */
public class ProtocolGmp extends ProtocolModule implements Trigger {
    final static int MAX_PROCESSES = 7;

    // Service provided
    private ManageView manageView;

    // Services required
    private DynAbcast dynAbcast;

    private RPT2PT rpt2pt;

    protected GroupMembershipImpl handlers;

    // The Executer
    // It add or remove a processes
    protected ManageView.Executer manageViewExecuter;

    // The Listeners
    // It listen for DynABcast message
    protected DynAbcast.Listener dynAbcastListener;

    // It listen for rpt2pt messages
    protected RPT2PT.Listener rpt2ptListener;

    /**
     * Constructor. <br>
     * 
     * @param name
     *            Name of the layer
     * @param stack
     * 			  The stack in which the module will be
     */
    public ProtocolGmp(String name, ProtocolStack stack,
            ManageView manageView, DynAbcast dynAbcast, RPT2PT rpt2pt) throws AlreadyExistingProtocolModuleException {

        super(name, stack);

        handlers = new GroupMembershipImpl(this, stack.getPID());

        this.manageView = manageView;
        this.dynAbcast = dynAbcast;
        this.rpt2pt = rpt2pt;

        LinkedList<ServiceCallOrResponse> initiatedManView = new LinkedList<ServiceCallOrResponse>();
        initiatedManView.add(ServiceCallOrResponse.createServiceCallOrResponse(dynAbcast, true));
        manageViewExecuter = manageView.new Executer(this, initiatedManView) {
            public void evaluate(ManageViewCallParameters params,
                    Message dmessage) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();
                    ga.addLast(params.pid);

                    if (params.add.booleanValue())
                        handlers.handleJoin(ga);
                    else
                        handlers.handleRemove(ga);
                }
            }
        };

        LinkedList<ServiceCallOrResponse> initiatedRpt2pt = new LinkedList<ServiceCallOrResponse>();
        for (int i=0;i<MAX_PROCESSES;i++)
        	initiatedRpt2pt.add(ServiceCallOrResponse.createServiceCallOrResponse(rpt2pt, true));
        rpt2ptListener = rpt2pt.new Listener(this, initiatedRpt2pt) {
            public void evaluate(RPT2PTResponseParameters infos,
                    Transportable message) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();

                    ga.addLast(message);
                    ga.addLast(infos.pid);

                    try {
                        handlers.handlePt2PtDeliver(ga);
                    } catch (Exception ex) {
                        throw new RuntimeException("ProtocolGmp: "
                                + "rpt2ptListener: " + ex.getMessage());
                    }
                }
            }
        };

        LinkedList<ServiceCallOrResponse> initiatedAbcast = new LinkedList<ServiceCallOrResponse>();
        for (int i=0;i<MAX_PROCESSES;i++)
        	initiatedAbcast.add(ServiceCallOrResponse.createServiceCallOrResponse(rpt2pt, true));
        initiatedAbcast.add(ServiceCallOrResponse.createServiceCallOrResponse(manageView, false));
        dynAbcastListener = dynAbcast.new Listener(this, initiatedAbcast) {
            public void evaluate(DynAbcastResponseParameters infos,
                    Transportable message) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();

                    ga.addLast(infos.type);
                    ga.addLast(message);
                    ga.addLast(infos.pid);

                    try {
                        handlers.handleADeliver(ga);
                    } catch (GroupCommException ex) {
                        throw new RuntimeException("ProtocolGmp: "
                                + "dynAbcastListener: " + ex.getMessage());
                    }
                }
            }
        };
    }

    synchronized public void init() {    	
        try {
            GroupCommEventArgs initev = new GroupCommEventArgs();
            initev.addFirst(this.stack.getGroup());
            handlers.handleInit(initev);
        } catch (GroupCommException ex) {
            throw new RuntimeException("ProtocolGmp: InitException: "
                    + ex.getMessage());
        }
        
    	super.init();
    }

    synchronized public void dump(OutputStream stream) {
        handlers.dump(stream);
    }

    /**
     * Manage the triggering of the events
     */
    @SuppressWarnings("unchecked")
	public void trigger(int type, GroupCommEventArgs l) {
        switch (type) {
        case Constants.JOINREMOVELIST:
            TSet start = (TSet) l.remove(0);
            TSet stop = (TSet) l.remove(0);

            Iterator itStart = start.iterator();
            while (itStart.hasNext()) {
                RPT2PTCallParameters jparams = new RPT2PTCallParameters(
                        (PID) itStart.next(), new TBoolean(true), new TBoolean(
                                false));

                rpt2pt.call(jparams, null);
            }

            Iterator itStop = stop.iterator();
            while (itStop.hasNext()) {
                RPT2PTCallParameters jparams = new RPT2PTCallParameters(
                        (PID) itStop.next(), new TBoolean(false), new TBoolean(
                                false));
                rpt2pt.call(jparams, null);
            }

            break;

        case Constants.PT2PTSEND:
            Transportable message = l.remove(0);
            PID ppid = (PID) l.remove(0);
            TBoolean promisc = (TBoolean) l.remove(0);
            RPT2PTCallParameters rparams = new RPT2PTCallParameters(ppid,
                    promisc, new TBoolean(true));

            rpt2pt.call(rparams, new Message(message,
                    (Listener) rpt2ptListener));
            break;

        case Constants.ABCAST:
            TInteger atype = (TInteger) l.remove(0);
            Transportable amessage = l.remove(0);
            PID apid = (PID) l.remove(0);
            DynAbcastCallParameters aparams = new DynAbcastCallParameters(
                    atype, apid);

            dynAbcast.call(aparams, new Message(amessage,
                    (Listener) dynAbcastListener));
            break;

        case Constants.NEW_VIEW:
            TInteger id = (TInteger) l.remove(0);
            TList view = (TList) l.remove(0);
            ManageViewResponseParameters infos = new ManageViewResponseParameters(
                    id, view);

            manageView.response(infos, null);
            break;
        }
    }
}
