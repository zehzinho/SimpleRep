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
package seqSamoa.protocols.order;

import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedList;

import seqSamoa.Message;
import seqSamoa.ProtocolModule;
import seqSamoa.ProtocolStack;
import seqSamoa.ServiceCallOrResponse;
import seqSamoa.Service.Listener;
import seqSamoa.exceptions.AlreadyExistingProtocolModuleException;
import seqSamoa.services.order.FIFO;
import seqSamoa.services.order.FIFOCallParameters;
import seqSamoa.services.order.FIFOResponseParameters;
import seqSamoa.services.rpt2pt.RPT2PT;
import seqSamoa.services.rpt2pt.RPT2PTCallParameters;
import seqSamoa.services.rpt2pt.RPT2PTResponseParameters;
import uka.transport.Transportable;
import framework.Constants;
import framework.GroupCommEventArgs;
import framework.GroupCommException;
import framework.GroupCommMessage;
import framework.PID;
import framework.libraries.Trigger;
import framework.libraries.serialization.TBoolean;
import framework.libraries.serialization.TSet;
import groupcomm.common.order.FIFOImpl;

public class ProtocolFIFOOrder extends ProtocolModule implements Trigger {
    final static int MAX_PROCESSES = 7;
    final static int MAX_MESSAGES = 45;

    private FIFO fifo;

    // Services required
    private RPT2PT rpt2pt;

    protected FIFOImpl handlers;

    // The Executer
    // It start a FIFOOrder
    protected FIFO.Executer FIFOExecuter;

    // The Listeners
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
    public ProtocolFIFOOrder(String name, ProtocolStack stack,
            FIFO fifo, RPT2PT rpt2pt) throws AlreadyExistingProtocolModuleException {

        super(name, stack);
        handlers = new FIFOImpl(this);

        this.fifo = fifo;
        this.rpt2pt = rpt2pt;
        
        LinkedList<ServiceCallOrResponse> initiatedFIFO = new LinkedList<ServiceCallOrResponse>();
        for (int i=0; i<MAX_PROCESSES; i++)           
        	initiatedFIFO.add(ServiceCallOrResponse.createServiceCallOrResponse(rpt2pt, true));
        initiatedFIFO.add(ServiceCallOrResponse.createServiceCallOrResponse(fifo, false));
        FIFOExecuter = fifo.new Executer(this, initiatedFIFO) {
            public void evaluate(FIFOCallParameters params,
                    Message dmessage) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();

                    ga.addLast(dmessage.toGroupCommMessage());
                    ga.addLast(params.pids);

                    try {
                        handlers.handleFIFOSend(ga);
                    } catch (GroupCommException ex) {
                        throw new RuntimeException(
                                "ProtocolFIFOOrder: Executer: "
                                        + ex.getMessage());
                    } 
                }
            }
        };

        LinkedList<ServiceCallOrResponse> initiatedRpt2pt = new LinkedList<ServiceCallOrResponse>();
        for (int i=0; i<MAX_MESSAGES; i++)
        	initiatedRpt2pt.add(ServiceCallOrResponse.createServiceCallOrResponse(fifo, false));
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
                        ex.printStackTrace();
                        throw new RuntimeException("ProtocolFIFOOrder: "
                                + "rpt2ptListener: " + ex.getMessage());
                    }
                }
            }
        };
    }

    synchronized public void init() {    	
        try {
            GroupCommEventArgs initev = new GroupCommEventArgs();
            initev.addLast(this.stack.getGroup());
            handlers.handleInit(initev);
        } catch (Exception ex) {
            throw new RuntimeException("ProtocolFIFOOrder: handleInit: "
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
            PID pid = (PID) l.remove(0);
            TBoolean promisc = (TBoolean) l.remove(0);
            RPT2PTCallParameters rparams = new RPT2PTCallParameters(pid,
                    promisc, new TBoolean(true));

            rpt2pt.call(rparams, new Message(message,
                    (Listener) rpt2ptListener));
            break;

        case Constants.FIFODELIVER:
            GroupCommMessage gm = (GroupCommMessage) l.remove(0);
            Message fmessage = new Message(gm);
            PID source = (PID) l.remove(0);
            FIFOResponseParameters fparams = new FIFOResponseParameters(source);

            fifo.response(fparams, fmessage);
            break;

        default:
            throw new RuntimeException("ProtocolFIFOOrder: trigger: "
                    + "Unexpected event type");
        }
    }
}
