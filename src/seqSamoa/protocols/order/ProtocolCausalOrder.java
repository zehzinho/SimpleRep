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
import seqSamoa.services.order.CausalOrder;
import seqSamoa.services.order.CausalOrderCallParameters;
import seqSamoa.services.order.CausalOrderResponseParameters;
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
import groupcomm.common.order.CausalOrderImpl;

public class ProtocolCausalOrder extends ProtocolModule implements Trigger {
    final static int MAX_PROCESSES = 7;

    private CausalOrder causalOrder;

    // Services required
    private RPT2PT rpt2pt;

    protected CausalOrderImpl handlers;

    // The Executer
    // It start a CausalOrderOrder
    protected CausalOrder.Executer CausalOrderExecuter;

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
    public ProtocolCausalOrder(String name, ProtocolStack stack,
            CausalOrder causalOrder, RPT2PT rpt2pt) throws AlreadyExistingProtocolModuleException {

        super(name, stack);
        handlers = new CausalOrderImpl(this, stack.getFlowControl(), stack.getPID());

        this.causalOrder = causalOrder;
        this.rpt2pt = rpt2pt;
        
        LinkedList<ServiceCallOrResponse> initiatedCO = new LinkedList<ServiceCallOrResponse>();
        for (int i=0; i<MAX_PROCESSES; i++)           
        	initiatedCO.add(ServiceCallOrResponse.createServiceCallOrResponse(rpt2pt, true));
        initiatedCO.add(ServiceCallOrResponse.createServiceCallOrResponse(causalOrder, false));
        CausalOrderExecuter = causalOrder.new Executer(this, initiatedCO) {
            public void evaluate(CausalOrderCallParameters params,
                    Message dmessage) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();

                    ga.addLast(dmessage.toGroupCommMessage());
                    ga.addLast(params.pids);

                    try {
                        handlers.handleCausalOrderSend(ga);
                    } catch (GroupCommException ex) {
                        throw new RuntimeException(
                                "ProtocolCausalOrderOrder: Executer: "
                                        + ex.getMessage());
                    } 
                }
            }
        };

        LinkedList<ServiceCallOrResponse> initiatedRpt2pt = new LinkedList<ServiceCallOrResponse>();
        for (int i=0; i<MAX_PROCESSES; i++)           
        	initiatedRpt2pt.add(ServiceCallOrResponse.createServiceCallOrResponse(rpt2pt, true));
        initiatedRpt2pt.add(ServiceCallOrResponse.createServiceCallOrResponse(causalOrder, false));
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
                        throw new RuntimeException("ProtocolCausalOrderOrder: "
                                + "rpt2ptListener: " + ex.getMessage());
                    }
                }
            }
        };
    }

    synchronized public void init() {    	
        try {
            GroupCommEventArgs initev = new GroupCommEventArgs();
            initev.addLast(stack.getGroup());
            handlers.handleInit(initev);
        } catch (Exception ex) {
            throw new RuntimeException("ProtocolCausalOrder: handleInit: "
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

        case Constants.CODELIVER:
            GroupCommMessage gm = (GroupCommMessage) l.remove(0);
            Message fmessage = new Message(gm);
            PID source = (PID) l.remove(0);
            CausalOrderResponseParameters fparams = new CausalOrderResponseParameters(source);

            causalOrder.response(fparams, fmessage);
            break;

        default:
            throw new RuntimeException("ProtocolCausalOrderOrder: trigger: "
                    + "Unexpected event type");
        }
    }
}
