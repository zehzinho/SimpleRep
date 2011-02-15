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
import java.util.Iterator;
import java.util.LinkedList;

import uka.transport.Transportable;

import framework.Constants;
import framework.GroupCommEventArgs;
import framework.GroupCommMessage;
import framework.GroupCommException;
import framework.PID;
import framework.libraries.Trigger;
import framework.libraries.serialization.TList;
import framework.libraries.serialization.TSet;
import framework.libraries.serialization.TBoolean;
import groupcomm.common.consensus.ConsensusPaxos;

import seqSamoa.ProtocolModule;
import seqSamoa.ProtocolStack;
import seqSamoa.Message;
import seqSamoa.ServiceCallOrResponse;
import seqSamoa.exceptions.AlreadyExistingProtocolModuleException;
import seqSamoa.services.consensus.Consensus;
import seqSamoa.services.consensus.ConsensusCallParameters;
import seqSamoa.services.consensus.ConsensusResponseParameters;
import seqSamoa.services.fd.Leader;
import seqSamoa.services.fd.LeaderCallParameters;
import seqSamoa.services.fd.LeaderResponseParameters;
import seqSamoa.services.rpt2pt.RPT2PT;
import seqSamoa.services.rpt2pt.RPT2PTCallParameters;
import seqSamoa.services.rpt2pt.RPT2PTResponseParameters;

/**
 * This class implement the Consensus protocol of Chandra-Toueg.
 * 
 * This Protocol need a Protocol that implements RPT2PT and FD.
 * 
 * The service implemented is consensus (described in util/Services.java)
 */
public class ProtocolConsensusPaxos extends ProtocolModule implements Trigger {
    final static int MAX_PROCESSES = 7;

    // Service provided
    private Consensus consensus;

    // Services required
    private RPT2PT rpt2pt;

    private Leader leader;

    protected ConsensusPaxos handlers;

    // The Executer
    // It start a consensus
    protected Consensus.Executer consensusExecuter;

    // The Listeners
    // It listen for process supsicion
    protected Leader.Listener leaderListener;

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
    public ProtocolConsensusPaxos(String name, ProtocolStack stack,
            Consensus consensus, Leader leader, RPT2PT rpt2pt) throws AlreadyExistingProtocolModuleException {

        super(name, stack);
        handlers = new ConsensusPaxos(this, stack.getFlowControl(), stack.getPID());

        this.consensus = consensus;
        this.rpt2pt = rpt2pt;
        this.leader = leader;

        LinkedList<ServiceCallOrResponse> initiatedCons = new LinkedList<ServiceCallOrResponse>();
        for (int i=0;i<MAX_PROCESSES;i++)
        	initiatedCons.add(ServiceCallOrResponse.createServiceCallOrResponse(rpt2pt, true));
        initiatedCons.add(ServiceCallOrResponse.createServiceCallOrResponse(leader, true));
        initiatedCons.add(ServiceCallOrResponse.createServiceCallOrResponse(consensus, false));
        consensusExecuter = consensus.new Executer(this, initiatedCons) {
            public void evaluate(ConsensusCallParameters params,
                    Message dmessage) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();

                    ga.addLast(params.group);
                    ga.addLast(dmessage.toGroupCommMessage());
                    ga.addLast(params.id);

                    try {
                        handlers.handleRun(ga);
                    } catch (GroupCommException ex) {
                        throw new RuntimeException("ProtocolConsensusPaxos: "
                                + "Executer: " + ex.getMessage());
                    } catch (IOException ex) {
                        throw new RuntimeException("ProtocolConsensusPaxos: "
                                + "Executer: " + ex.getMessage());
                    } catch (ClassNotFoundException ex) {
                        throw new RuntimeException("ProtocolConsensusPaxos: "
                                + "Executer: " + ex.getMessage());
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
                    GroupCommEventArgs ga = new GroupCommEventArgs();

                    ga.addLast(message);
                    ga.addLast(infos.pid);

                    try {
                        handlers.handlePt2PtDeliver(ga);
                    } catch (Exception ex) {
                        throw new RuntimeException("ProtocolConsensusPaxos: "
                                + "rpt2ptListener: " + ex.getMessage());
                    }
                }
            }
        };

        LinkedList<ServiceCallOrResponse> initiatedLeader = new LinkedList<ServiceCallOrResponse>();
        for (int i=0;i<MAX_PROCESSES;i++)
        	initiatedLeader.add(ServiceCallOrResponse.createServiceCallOrResponse(rpt2pt, true));
        leaderListener = leader.new Listener(this, initiatedLeader) {
            public void evaluate(LeaderResponseParameters infos,
                    Transportable message) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();

                    ga.addLast(infos.leader);
                    ga.addLast(infos.group);

                    try {
                        handlers.handleNewLeader(ga);
                    } catch (Exception ex) {
                    	ex.printStackTrace();
                        throw new RuntimeException("ProtocolConsensusPaxos: "
                                + "fdListener: " + ex.getMessage());
                    }
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

        case Constants.DECIDE:
            GroupCommMessage gm = (GroupCommMessage) l.remove(0);
            Message dmessage = new Message(gm);
            ConsensusResponseParameters infos = new ConsensusResponseParameters(
                    l.remove(0));

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

        case Constants.STARTSTOPMONITOR:
            TList startM = (TList) l.remove(0);
            TList stopM = (TList) l.remove(0);
            LeaderCallParameters mparams = new LeaderCallParameters(startM,
                    stopM);

            leader.call(mparams, null);
            break;

        default:
            throw new RuntimeException("ProtocolConsensusPaxos: trigger: "
                    + "Unexpected event type");
        }
    }
}
