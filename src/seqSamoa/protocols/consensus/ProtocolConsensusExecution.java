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

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.NoSuchElementException;

import uka.transport.Transportable;

import framework.Constants;
import framework.GroupCommEventArgs;
import framework.GroupCommException;
import framework.GroupCommMessage;
import framework.PID;
import framework.libraries.DefaultSerialization;
import framework.libraries.Trigger;
import framework.libraries.serialization.TList;
import framework.libraries.serialization.TSet;
import framework.libraries.serialization.TBoolean;
import framework.libraries.serialization.TInteger;

import seqSamoa.ProtocolModule;
import seqSamoa.ProtocolStack;
import seqSamoa.Message;
import seqSamoa.ServiceCallOrResponse;

import seqSamoa.exceptions.AlreadyExistingProtocolModuleException;
import seqSamoa.services.consensus.Consensus;
import seqSamoa.services.consensus.ConsensusCallParameters;
import seqSamoa.services.consensus.ConsensusResponseParameters;
import seqSamoa.services.fd.FD;
import seqSamoa.services.fd.FDResponseParameters;
import seqSamoa.services.rpt2pt.RPT2PT;
import seqSamoa.services.rpt2pt.RPT2PTCallParameters;
import seqSamoa.services.rpt2pt.RPT2PTResponseParameters;

import groupcomm.common.consensus.ConsensusExecution;

/**
 * This class implement the Consensus protocol of Chandra-Toueg. It only
 * achieves one instance of consensus
 * 
 * This Protocol need a Protocol that implements RPT2PT and FD.
 * 
 * The service implemented is consensus (described in util/Services.java)
 */
public class ProtocolConsensusExecution extends ProtocolModule implements Trigger {
    final static int MAX_PROCESSES = 7;

    // Service provided
    private Consensus consensus;

    // Services required
    private RPT2PT rpt2pt;

    protected ConsensusExecution handlers;

    // This instance of Consensus has decided consDecision
    private Transportable consDecision = null;

    // K of the consensus
    private Object consK;

    // The Executer
    // It start a consensus
    protected Consensus.Executer consensusExecuter;

    // The Listeners
    // It listen for process supsicion
    protected FD.Listener fdListener;

    // It listen for rpt2pt messages
    protected RPT2PT.Listener rpt2ptListener;

    /**
     * Constructor. <br>
     * 
     * @param name
     *            Name of the layer
     * @param myself
     *            The local PID
     * @param k
     *            Instance of consensus
     * @param suspected
     *            The group of processes initially suspected
     */
    public ProtocolConsensusExecution(String name, ProtocolStack stack, Transportable k,
            TSet suspected, Consensus consensus, FD fd, RPT2PT rpt2pt) throws AlreadyExistingProtocolModuleException {

        super(name, stack);
        this.consK = k;
        handlers = new ConsensusExecution(stack.getPID(), k, suspected, this);

        this.consensus = consensus;
        this.rpt2pt = rpt2pt;

        LinkedList<ServiceCallOrResponse> initiatedCons = new LinkedList<ServiceCallOrResponse>();
        for (int i=0;i<MAX_PROCESSES;i++)
        	initiatedCons.add(ServiceCallOrResponse.createServiceCallOrResponse(rpt2pt, true));
        consensusExecuter = consensus.new Executer(this, initiatedCons) {
            public void evaluate(ConsensusCallParameters params,
                    Message dmessage) {
                synchronized (this.parent) {
                    if (params.id.equals(consK)) {
                        try {
                            // Has its decision already arrived??
                            if (consDecision != null) {
                                Transportable clone = deepClone(consDecision);
                                reSendDecision(consDecision, params.id,
                                        params.group, null);
                                GroupCommEventArgs e = new GroupCommEventArgs();
                                e.addLast(clone);
                                e.addLast(params.id);
                                trigger(Constants.DECIDE, e);
                            } else
                                handlers.processStart(dmessage
                                        .toGroupCommMessage(), params.group);
                        } catch (GroupCommException ex) {
                            throw new RuntimeException(
                                    "ProtocolConsensusExecution: "
                                            + "Executer: " + ex.getMessage());
                        } catch (IOException ex) {
                            throw new RuntimeException(
                                    "ProtocolConsensusExecution: "
                                            + "Executer: " + ex.getMessage());
                        } catch (ClassNotFoundException ex) {
                            throw new RuntimeException(
                                    "ProtocolConsensusExecution: "
                                            + "Executer: " + ex.getMessage());
                        }
                    }
                }
            }
        };

        LinkedList<ServiceCallOrResponse> initiatedRpt2pt = new LinkedList<ServiceCallOrResponse>();
        for (int i=0;i<MAX_PROCESSES;i++)
        	initiatedRpt2pt.add(ServiceCallOrResponse.createServiceCallOrResponse(rpt2pt, true));
        initiatedRpt2pt.add(ServiceCallOrResponse.createServiceCallOrResponse(consensus, false));
        rpt2ptListener = rpt2pt.new Listener(this, initiatedRpt2pt) {
            public void evaluate(RPT2PTResponseParameters infos,
                    Transportable message) {
                synchronized (this.parent) {
                    GroupCommMessage gm = (GroupCommMessage) message;
                    Transportable kmessObj = (Transportable) gm.tunpack();

                    if (consDecision != null)
                        return;

                    PID source = infos.pid;
                    int type = ((TInteger) gm.tunpack()).intValue();
                    try {
                        // m = <<payload>>
                        switch (type) {
                        case ConsensusExecution.CONS_ESTIMATE:
                            // m = <<r::estimate::lastupdated>>
                            int r = ((TInteger) gm.tunpack()).intValue();
                            handlers.processEstimate(r, gm);
                            break;
                        case ConsensusExecution.CONS_PROPOSE:
                            // m = <<r::propose>>
                            r = ((TInteger) gm.tunpack()).intValue();
                            handlers.processPropose(r, gm);
                            break;
                        case ConsensusExecution.CONS_ACK:
                            // m = <<r::ack>>
                            r = ((TInteger) gm.tunpack()).intValue();
                            handlers.processAck(r, gm);
                            break;
                        case ConsensusExecution.CONS_RBCAST:
                            // m = <<decision::group>>
                            Transportable decision = gm.tunpack();
                            // m = <<group>>
                            if (handlers.hasStarted()) {
                                Transportable clone = deepClone(decision);
                                TList group = (TList) gm.tunpack();
                                reSendDecision(decision, kmessObj, group,
                                            source);

                                GroupCommEventArgs e = new GroupCommEventArgs();
                                e.addLast(clone);
                                e.addLast(kmessObj);
                                trigger(Constants.DECIDE, e);
                            } else {
                                consDecision = decision;
                            }
                            break;
                        default:
                            throw new GroupCommException(
                                    "ProtocolConsensusExecution:"
                                            + " rpt2ptListener: "
                                            + "Unknown message type: " + type);
                        }
                    } catch (NoSuchElementException e) {
                        throw new RuntimeException(
                                "ProtocolConsensusExecution: "
                                        + "rpt2ptListener: "
                                        + "NoSuchElementException: "
                                        + e.getMessage());
                    } catch (GroupCommException e) {
                        throw new RuntimeException(
                                "ProtocolConsensusExecution: "
                                        + "rpt2ptListener: "
                                        + "GroupCommException:"
                                        + e.getMessage());
                    } catch (IOException e) {
                        throw new RuntimeException(
                                "ProtocolConsensusExecution: "
                                        + "rpt2ptListener: " + "IOException:"
                                        + e.getMessage());
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(
                                "ProtocolConsensusExecution: "
                                        + "rpt2ptListener: "
                                        + "ClassNotFoundException:"
                                        + e.getMessage());
                    }
                }
            }
        };

        LinkedList<ServiceCallOrResponse> initiatedFD = new LinkedList<ServiceCallOrResponse>();
        for (int i=0;i<MAX_PROCESSES;i++)
        	initiatedFD.add(ServiceCallOrResponse.createServiceCallOrResponse(rpt2pt, true));
        fdListener = fd.new Listener(this, initiatedFD) {
            public void evaluate(FDResponseParameters infos,
                    Transportable message) {
                synchronized (this.parent) {
                    TSet suspected = infos.suspected;

                    try {
                        handlers.processSuspicion(suspected);
                    } catch (Exception ex) {
                        throw new RuntimeException(
                                "ProtocolConsensusExecution: " + "fdListener: "
                                        + ex.getMessage());
                    }
                }
            }
        };
    }
    
    synchronized public void dump(OutputStream stream) {
        PrintStream err = new PrintStream(stream);
        err.println(handlers);
    }

    /**
     * Manage the triggering of the events
     */
    public void trigger(int type, GroupCommEventArgs l) {
        switch (type) {
        case Constants.DECIDE:
            GroupCommMessage gm = (GroupCommMessage) l.remove(0);
            Message dmessage = new Message(gm);
            ConsensusResponseParameters infos = new ConsensusResponseParameters(
                    l.remove(0));

            this.close();
            consensus.response(infos, dmessage);
            break;

        case Constants.PT2PTSEND:
            Transportable message = l.remove(0);
            PID pid = (PID) l.remove(0);
            TBoolean promisc = (TBoolean) l.remove(0);
            RPT2PTCallParameters rparams = new RPT2PTCallParameters(pid,
                    promisc, new TBoolean(true));

            rpt2pt.call(rparams, new Message(message, rpt2ptListener));
            break;

        default:
            throw new RuntimeException("ProtocolConsensus: trigger: "
                    + "Unexpected event type");
        }
    }

    // Makes a copy in memory of the parameter. If the parameter has references
    // to other objects, they are also cloned.
    // This is necessary to avoid side-effects at higher-lever protocols.
    private Transportable deepClone(Transportable o) throws IOException,
            ClassNotFoundException {
        // TODO: There have to be better ways to do deep-clone!!!
        return DefaultSerialization
                .unmarshall(DefaultSerialization.marshall(o));
    }

    // Send the decision again. This is done to simulate RBcast.
    private void reSendDecision(Transportable decision, Transportable kObj,
            TList group, PID dontsend) {
        if (!this.stack.getPID().equals(dontsend)) {
        	GroupCommMessage decisionMessage = new GroupCommMessage();
        	// 	m = <<>>
        	decisionMessage.tpack(group);
        	// m = <<group>>
        	decisionMessage.tpack(decision);
        	// 	m = <<decision::group>>
        	decisionMessage.tpack(new TInteger(ConsensusExecution.CONS_RBCAST));
        	// m = <<CONS_BROADCAST::decision::group>>
        	decisionMessage.tpack(kObj);
        	// 	m = <<k::CONS_BROADCAST::decision::group>>
        	for (int i = 0; i < group.size(); i++)
        		if (!group.get(i).equals(this.stack.getPID())
        				&& (dontsend == null || !group.get(i).equals(dontsend))) {
        			GroupCommEventArgs pt2ptSend = new GroupCommEventArgs();
        			pt2ptSend.addLast(decisionMessage.cloneGroupCommMessage());
        			pt2ptSend.addLast(group.get(i));
        			pt2ptSend.addLast(new TBoolean(false));
        			trigger(Constants.PT2PTSEND, pt2ptSend);
        		}
        }
    }
}
