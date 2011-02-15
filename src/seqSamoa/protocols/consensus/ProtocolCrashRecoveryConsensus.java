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
package seqSamoa.protocols.consensus;

import java.io.OutputStream;
import java.util.LinkedList;

import uka.transport.Transportable;

import framework.Constants;
import framework.GroupCommException;
import framework.GroupCommEventArgs;
import framework.GroupCommMessage;
import framework.PID;
import framework.libraries.Trigger;
import framework.libraries.serialization.TBoolean;
import framework.libraries.serialization.TSet;

import seqSamoa.Message;
import seqSamoa.ProtocolModule;
import seqSamoa.ProtocolStack;
import seqSamoa.ServiceCallOrResponse;

import seqSamoa.exceptions.AlreadyExistingProtocolModuleException;
import seqSamoa.services.consensus.Consensus;
import seqSamoa.services.consensus.ConsensusCallParameters;
import seqSamoa.services.consensus.ConsensusResponseParameters;
import seqSamoa.services.fd.FDSu;
import seqSamoa.services.fd.FDSuCallParameters;
import seqSamoa.services.fd.FDSuResponseParameters;
import seqSamoa.services.pt2pt.PT2PT;
import seqSamoa.services.pt2pt.PT2PTCallParameters;
import seqSamoa.services.pt2pt.PT2PTResponseParameters;

/**
 * This class implement the Consensus protocol of Chandra-Toueg.
 * 
 * This Protocol need a Protocol that implements PT2PT and FDSu.
 * 
 * The service implemented is consensus (described in util/Services.java)
 */
public class ProtocolCrashRecoveryConsensus extends ProtocolModule implements Trigger {
    final static int MAX_PROCESSES = 7;

    // Service provided
    private Consensus consensus;

    // Service required
    private FDSu fdsu;

    private PT2PT pt2pt;

    protected static_recovery.common.consensus.Consensus handlers = null;

    // The Executer
    // It start a consensus
    protected Consensus.Executer consensusExecuter;

    // The Listeners
    // It listen for process supsicion
    protected FDSu.Listener fdSuListener;

    // It listen for rpt2pt messages
    protected PT2PT.Listener pt2ptListener;

    /**
     * Constructor.
     * 
     * @param name
     *            String identifier of the protocol
     * @param stack
     * 			  The stack in which the module will be
     * @param storage
     *            The storage for recovery
     */
    public ProtocolCrashRecoveryConsensus(String name, ProtocolStack stack, Consensus consensus, FDSu fdsu,
            PT2PT pt2pt) throws AlreadyExistingProtocolModuleException {

        super(name, stack);
        handlers = new static_recovery.common.consensus.Consensus(this, stack.getFlowControl(),
                stack.getStorage(), stack.getGroup(), stack.getPID());

        this.consensus = consensus;
        this.pt2pt = pt2pt;
        this.fdsu = fdsu;

        LinkedList<ServiceCallOrResponse> initiatedCons = new LinkedList<ServiceCallOrResponse>();
        for (int i=0;i<MAX_PROCESSES;i++)
        	initiatedCons.add(ServiceCallOrResponse.createServiceCallOrResponse(pt2pt, true));
        initiatedCons.add(ServiceCallOrResponse.createServiceCallOrResponse(consensus, false));
        consensusExecuter = consensus.new Executer(this, initiatedCons) {
            public void evaluate(ConsensusCallParameters params,
                    Message dmessage) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();

                    ga.addLast(params.id);
                    ga.addLast(dmessage.toGroupCommMessage());

                    try {
                        handlers.handlePropose(ga);
                    } catch (Exception ex) {
                        throw new RuntimeException(
                                "ProtocolConsensus: Executer: "
                                        + ex.getMessage());
                    }
                }
            }
        };

        LinkedList<ServiceCallOrResponse> initiatedPt2pt = new LinkedList<ServiceCallOrResponse>();
        for (int i=0;i<MAX_PROCESSES;i++)
        	initiatedPt2pt.add(ServiceCallOrResponse.createServiceCallOrResponse(pt2pt, true));
        initiatedPt2pt.add(ServiceCallOrResponse.createServiceCallOrResponse(consensus, false));
        pt2ptListener = pt2pt.new Listener(this, initiatedPt2pt) {
            public void evaluate(PT2PTResponseParameters infos,
                    Transportable message) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();

                    ga.addLast(message);
                    ga.addLast(infos.pid);

                    try {
                        handlers.handlePt2PtDeliver(ga);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        throw new RuntimeException("ProtocolConsensus: "
                                + "pt2ptListener: " + ex.getMessage());
                    }
                }
            }
        };

        LinkedList<ServiceCallOrResponse> initiatedFDSu = new LinkedList<ServiceCallOrResponse>();
        for (int i=0;i<MAX_PROCESSES;i++)
        	initiatedFDSu.add(ServiceCallOrResponse.createServiceCallOrResponse(pt2pt, true));
        fdSuListener = fdsu.new Listener(this, initiatedFDSu) {
            public void evaluate(FDSuResponseParameters infos,
                    Transportable message) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();

                    ga.addLast(infos.suspected);
                    ga.addLast(infos.epoch);

                    try {
                        handlers.handleTrustSu(ga);
                    } catch (Exception ex) {
                        throw new RuntimeException("ProtocolConsensus: "
                                + "fdSuListener: " + ex.getMessage());
                    }
                }
            }
        };
    }

    synchronized public void commit() {
        handlers.handleCheckpoint();
    }

    synchronized public void recovery(boolean recovery) {
        GroupCommEventArgs e = new GroupCommEventArgs();
        e.add(new TBoolean(recovery));

        try {
            handlers.handleRecovery(e);
        } catch (GroupCommException ex) {
            throw new RuntimeException("ProtocolConsensus: Recovery: "
                    + ex.getMessage());
        }
    }

    synchronized public void dump(OutputStream os) {
        handlers.dump(os);
    }

    /**
     * Manage the triggering of the events
     */
    public void trigger(int type, GroupCommEventArgs l) {
        switch (type) {
        case Constants.DECIDE:
            ConsensusResponseParameters infos = new ConsensusResponseParameters(
                    l.get(0));
            GroupCommMessage gm = (GroupCommMessage) l.get(1);
            Message dmessage = new Message(gm);

            consensus.response(infos, dmessage);
            break;
        case Constants.PT2PTSEND:
            Transportable message = l.get(0);
            PID pid = (PID) l.get(1);
            Transportable key = l.get(2);
            PT2PTCallParameters rparams = new PT2PTCallParameters(pid, key);

            pt2pt.call(rparams, new Message(message, pt2ptListener));
            break;
        case Constants.KILLMESSAGE:
            Transportable kkey = l.get(0);
            PT2PTCallParameters rkparams = new PT2PTCallParameters(null, kkey);

            pt2pt.call(rkparams, null);
            break;
        case Constants.STARTSTOPMONITOR:
            TSet startM = (TSet) l.get(0);
            TSet stopM = (TSet) l.get(1);
            FDSuCallParameters mparams = new FDSuCallParameters(startM, stopM);

            fdsu.call(mparams, null);
            break;
        default:
            throw new RuntimeException("Error : ProtocolConsensus : trigger"
                    + "Trying to send an unknown event type: " + type);
        }
    }
}
