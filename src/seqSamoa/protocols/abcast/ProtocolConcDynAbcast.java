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
package seqSamoa.protocols.abcast;

import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedList;

import seqSamoa.Message;
import seqSamoa.ProtocolModule;
import seqSamoa.ProtocolStack;
import seqSamoa.ServiceCallOrResponse;
import seqSamoa.exceptions.AlreadyBoundServiceException;
import seqSamoa.exceptions.AlreadyExistingProtocolModuleException;
import seqSamoa.protocols.consensus.ProtocolConsensusExecution;
import seqSamoa.services.abcast.DynAbcast;
import seqSamoa.services.abcast.DynAbcastCallParameters;
import seqSamoa.services.abcast.DynAbcastResponseParameters;
import seqSamoa.services.consensus.Consensus;
import seqSamoa.services.consensus.ConsensusCallParameters;
import seqSamoa.services.consensus.ConsensusResponseParameters;
import seqSamoa.services.fd.FD;
import seqSamoa.services.fd.FDCallParameters;
import seqSamoa.services.fd.FDResponseParameters;
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
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TList;
import framework.libraries.serialization.TSet;
import groupcomm.common.abcast.ConcDynAbcastImpl;

/**
 * This class implement the ABcast with dynamic set of processes (view
 * synchrony). It manage many consensus instances in parallel in contrary to
 * classical Chandra-Toueg ABcast algorithms.
 * 
 * This Protocol need a Protocol that implements RPT2PT and Consensus.
 * 
 * The service implemented is dynAbcast (described in util/Services.java)
 */
public class ProtocolConcDynAbcast extends ProtocolModule implements Trigger {
    final private static int MAX_PROCESSES = 7;
    final private static int MAX_MESSAGES = 45;
    // Service provided
    private DynAbcast dynAbcast;

    // Service required
    private RPT2PT rpt2pt;

    private Consensus consensus;

    private FD fd;

    // Common Code
    protected ConcDynAbcastImpl handlers;

    // The Executer
    // It ABcasts a message
    protected DynAbcast.Executer dynAbcastExecuter;

    // The Listeners
    // It listen for consensus decision
    protected Consensus.Listener consensusListener;

    // It listen for rpt2pt messages
    protected RPT2PT.Listener rpt2ptListener;

    // It listen for process supsicion
    protected FD.Listener fdListener;

    /**
     * Constructor. <br>
     * 
     * @param name
     *            Name of the layer
     * @param stack
     * 			  The stack in which the module will be
     */
    public ProtocolConcDynAbcast(String name, ProtocolStack stack, DynAbcast dynAbcast, Consensus consensus, FD fd,
            RPT2PT rpt2pt) throws AlreadyExistingProtocolModuleException {

        super(name, stack);
        handlers = new ConcDynAbcastImpl(this, stack.getFlowControl(), stack.getPID());

        this.dynAbcast = dynAbcast;
        this.consensus = consensus;
        this.fd = fd;
        this.rpt2pt = rpt2pt;

        LinkedList<ServiceCallOrResponse> initiatedAbcast = new LinkedList<ServiceCallOrResponse>();
        for (int i=0; i<MAX_PROCESSES; i++)
        	initiatedAbcast.add(ServiceCallOrResponse.createServiceCallOrResponse(rpt2pt, true));
        dynAbcastExecuter = dynAbcast.new Executer(this, initiatedAbcast) {
            public void evaluate(DynAbcastCallParameters params,
                    Message dmessage) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();

                    ga.addLast(params.type);
                    ga.addLast(dmessage.toGroupCommMessage());
                    ga.addLast(params.pid);

                    handlers.handleAbcast(ga);
                }
            }
        };

        LinkedList<ServiceCallOrResponse> initiatedRpt2pt = new LinkedList<ServiceCallOrResponse>();
        for (int i=0; i<MAX_PROCESSES; i++)
        	initiatedRpt2pt.add(ServiceCallOrResponse.createServiceCallOrResponse(rpt2pt, true));
        initiatedRpt2pt.add(ServiceCallOrResponse.createServiceCallOrResponse(consensus, true));
        initiatedRpt2pt.add(ServiceCallOrResponse.createServiceCallOrResponse(fd, true));
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
                        throw new RuntimeException("ProtocolConcDynAbcast: "
                                + "rpt2ptListener: " + ex.getMessage());
                    }
                }
            }
        };

        LinkedList<ServiceCallOrResponse> initiatedCons = new LinkedList<ServiceCallOrResponse>();
        for (int i=0; i<MAX_PROCESSES; i++)
        	initiatedCons.add(ServiceCallOrResponse.createServiceCallOrResponse(rpt2pt, true));
        initiatedCons.add(ServiceCallOrResponse.createServiceCallOrResponse(consensus, true));
        initiatedCons.add(ServiceCallOrResponse.createServiceCallOrResponse(fd, true));
        for (int i=0; i<MAX_MESSAGES; i++)
        	initiatedCons.add(ServiceCallOrResponse.createServiceCallOrResponse(dynAbcast, false));
        consensusListener = consensus.new Listener(this, initiatedCons) {
            public void evaluate(ConsensusResponseParameters infos,
                    Transportable message) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();

                    ga.addLast(message);
                    ga.addLast(infos.id);

                    try {
                        handlers.handleDecide(ga);
                    } catch (GroupCommException ex) {
                        throw new RuntimeException("ProtocolConcDynAbcast: "
                                + "handleDecide: " + ex.getMessage());
                    }
                }
            }
        };

        LinkedList<ServiceCallOrResponse> initiatedFd = new LinkedList<ServiceCallOrResponse>();
        fdListener = fd.new Listener(this, initiatedFd) {
            public void evaluate(FDResponseParameters infos,
                    Transportable message) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();

                    ga.addLast(infos.suspected);

                    try {
                        handlers.handleSuspect(ga);
                    } catch (Exception ex) {
                        throw new RuntimeException("ProtocolConcDynAbcast: "
                                + "fdListener: " + ex.getMessage());
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
        } catch (GroupCommException ex) {
            throw new RuntimeException("ProtocolConcDynAbcast: handleInit: "
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

            rpt2pt.call(rparams, new Message(message, rpt2ptListener));
            break;

        case Constants.ADELIVER:
            TInteger atype = (TInteger) l.remove(0);
            GroupCommMessage gm = (GroupCommMessage) l.remove(0);
            Message dmessage = new Message(gm);
            PID apid = (PID) l.remove(0);
            DynAbcastResponseParameters infos = new DynAbcastResponseParameters(
                    atype, apid);

            dynAbcast.response(infos, dmessage);
            break;

        case Constants.PROPOSE:
            l.remove(0);
            Transportable id = l.remove(0);
            TSet suspected = (TSet) l.remove(0);
            Transportable cmessage = l.remove(0);
            TList group = (TList) l.remove(0);

            try {
            	ProtocolConsensusExecution pConsEx = new ProtocolConsensusExecution(
                    new String(name + id), this.stack, id, suspected, consensus,
                    fd, rpt2pt);

                pConsEx.linkToService();
                pConsEx.init();

                ConsensusCallParameters cparams = new ConsensusCallParameters(
                		group, id);

                consensus.call(cparams, new Message(cmessage, consensusListener));

                pConsEx.unlinkFromService();
            } catch (AlreadyBoundServiceException ex) {
                throw new RuntimeException("ProtocolConcDynAbcast: Service "
                        + "CONSENSUS is already bound");
            } catch (AlreadyExistingProtocolModuleException aem) {
            	throw new RuntimeException("ProtocolConcDynAbcast: Protocol module"
            			+ name + id + " already existing!");
            }
            break;

        case Constants.STARTSTOPMONITOR:
            TSet startM = (TSet) l.remove(0);
            TSet stopM = (TSet) l.remove(0);
            FDCallParameters mparams = new FDCallParameters(startM, stopM);

            fd.call(mparams, null);
            break;
        default:
            throw new RuntimeException("Unknow event triggered : " + type);
        }
    }
}
