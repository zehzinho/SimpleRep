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
package seqSamoa.api;

import java.util.LinkedList;

import seqSamoa.Callback;
import seqSamoa.Message;
import seqSamoa.ProtocolStack;
import seqSamoa.SamoaFlowControl;
import seqSamoa.SamoaScheduler;
import seqSamoa.Service;
import seqSamoa.ServiceCallOrResponse;
import seqSamoa.exceptions.AlreadyExistingProtocolModuleException;
import seqSamoa.exceptions.AlreadyExistingServiceException;
import seqSamoa.protocols.abcast.ProtocolDynAbcast;
import seqSamoa.protocols.consensus.ProtocolConsensus;
import seqSamoa.protocols.fd.ProtocolPing;
import seqSamoa.protocols.gmp.ProtocolGmp;
import seqSamoa.protocols.monitoring.ProtocolSimpleMonitoring;
import seqSamoa.protocols.replacer.ConsensusReplacer;
import seqSamoa.protocols.replacer.DynAbcastReplacer;
import seqSamoa.services.abcast.DynAbcast;
import seqSamoa.services.abcast.DynAbcastCallParameters;
import seqSamoa.services.abcast.DynAbcastResponseParameters;
import seqSamoa.services.consensus.Consensus;
import seqSamoa.services.fd.FD;
import seqSamoa.services.gms.ManageView;
import seqSamoa.services.gms.ManageViewCallParameters;
import seqSamoa.services.gms.ManageViewResponseParameters;
import seqSamoa.services.replacement.ReplaceProtocol;
import seqSamoa.services.replacement.ReplaceProtocolCallParameters;
import seqSamoa.services.replacement.ReplaceProtocolResponseParameters;
import seqSamoa.services.udp.UDPCallParameters;
import uka.transport.Transportable;
import framework.Constants;
import framework.PID;
import framework.libraries.serialization.TBoolean;
import framework.libraries.serialization.TElement;
import framework.libraries.serialization.TGLinkedList;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TList;
import framework.libraries.serialization.TString;

/**
 * A protocol stack that implements atomic broadcast
 * in a dynamic group for a crash-stop model. Furthermore,
 * this stack allows the atomic broadcast, group membership
 * and consensus protocols to be dynamically updated.
 */
public class ApiSamoaDynAbcastWithReplaceableProtocolStack extends
		ProtocolStack {

	// The different microprotocol
	ProtocolPing pFD;

	ProtocolConsensus pConsensus;

	ProtocolDynAbcast pDynAbcast;

	ProtocolGmp pGmp;

	ProtocolSimpleMonitoring pMonitoring;

	// The different replacer
	ConsensusReplacer rConsensus;

	DynAbcastReplacer rDynAbcast;

	// The different services
	FD fd;

	Consensus consensus;

	DynAbcast dynAbcast;

	ManageView manageView;

	ReplaceProtocol replaceConsensus;

	ReplaceProtocol replaceDynAbcast;

	// The Listeners
	// It listen for DynABcast message
	protected DynAbcast.Listener dynAbcastListener;

	// It listen for view change
	protected ManageView.Listener manageViewListener;

	// They listen for protocol change
	protected ReplaceProtocol.Listener replaceConsensusListener;

	protected ReplaceProtocol.Listener replaceDynAbcastListener;

	/**
	 * Constructor.
	 * 
	 * @param myself
	 *            the Process ID of the stack (should be unique)
	 * @param processes
	 * 			  the list of processes that run the same stack
	 * @param scheduler
	 * 			  the scheduler that manages executions in the stack
	 * @param fc
	 * 			  the flow control dedicated this stack
	 * @param callback
	 * 			  the interface that gets the responses of this stack
	 * @param logFile
	 * 			  name of the file where to log the infos
	 */
	public ApiSamoaDynAbcastWithReplaceableProtocolStack(PID myself,
			TList processes, SamoaScheduler scheduler,
			SamoaFlowControl fc, Callback callback, String logFile) {

		super(myself, processes, scheduler, fc, callback, logFile,
				new String("groupcomm"), true, true);

		// SERVICE CREATION
		try {
			fd = new FD("fd", this);
			consensus = new Consensus("consensus", this);
			dynAbcast = new DynAbcast("dynAbcast", this);
			manageView = new ManageView("manageView", this);
			replaceConsensus = new ReplaceProtocol("replaceConsensus", this);
			replaceDynAbcast = new ReplaceProtocol("replaceDynAbcast", this);
		} catch (AlreadyExistingServiceException aep) {
			throw new RuntimeException(
					"Should not be possible! Bug in conception.");
		}

		replaceConsensusListener = replaceConsensus.new Listener(this, new LinkedList<ServiceCallOrResponse>()) {
			synchronized public void evaluate(
					ReplaceProtocolResponseParameters infos,
					Transportable message) {
				this.parent.getStack().getCallback().serviceCallback(infos, message);
			}
		};

		replaceDynAbcastListener = replaceDynAbcast.new Listener(this, new LinkedList<ServiceCallOrResponse>()) {
			synchronized public void evaluate(
					ReplaceProtocolResponseParameters infos,
					Transportable message) {
				this.parent.getStack().getCallback().serviceCallback(infos, message);
			}
		};

		dynAbcastListener = dynAbcast.new Listener(this, new LinkedList<ServiceCallOrResponse>()) {
			synchronized public void evaluate(
					DynAbcastResponseParameters infos, Transportable message) {
				this.parent.getStack().getCallback().serviceCallback(infos, message);
			}
		};

		manageViewListener = manageView.new Listener(this, new LinkedList<ServiceCallOrResponse>()) {
			synchronized public void evaluate(
					ManageViewResponseParameters infos, Transportable message) {
				this.parent.getStack().getCallback().serviceCallback(infos, message);
				this.parent.getStack().setGroup(infos.view);
			}
		};

		// Protocol Instanciation
		try {
			pFD = new ProtocolPing(
					new String("FD"),
					this,
					1000,
					3,
					fd,
					(Service<? extends UDPCallParameters, ? extends Object>) udp);

			pConsensus = new ProtocolConsensus(new String("Consensus"), this,
					consensus, fd, rpt2pt);

			pDynAbcast = new ProtocolDynAbcast(new String("DynAbcast"), this,
					dynAbcast, consensus, rpt2pt);

			pGmp = new ProtocolGmp(new String("Gmp"), this, manageView,
					dynAbcast, rpt2pt);

			pMonitoring = new ProtocolSimpleMonitoring(
					new String("Monitoring"), this, processSuspicion,
					manageView);

			rConsensus = new ConsensusReplacer(new String("ConsensusReplacer"), this,
					replaceConsensus, consensus, dynAbcast);

			rDynAbcast = new DynAbcastReplacer(new String("DynAbcastReplacer"),
					this, replaceDynAbcast, dynAbcast);
		} catch (AlreadyExistingProtocolModuleException aep) {
			aep.printStackTrace();
			throw new RuntimeException(
					"Should not be possible! Bug in conception.");
		}

	}

	// to abcast a message
	synchronized public void abcastMessage(Transportable message) {
		fc.enter();
		TInteger atype = new TInteger(Constants.AM);
		DynAbcastCallParameters aparams = new DynAbcastCallParameters(atype, null);
		long cid = dynAbcast.externalCall(aparams, new Message(message, dynAbcastListener));
		this.scheduler.waitEnd(cid);
	}
	
	// to join a process to the group
	synchronized public void sendJoin(PID pid) {
		fc.enter();
		ManageViewCallParameters params = new ManageViewCallParameters(pid, new TBoolean(true));
		long cid = manageView.externalCall(params, null);
		this.scheduler.waitEnd(cid);		
	}

	// to remove a process from the group
	synchronized public void sendRemove(PID pid) {
		fc.enter();
		ManageViewCallParameters params = new ManageViewCallParameters(pid, new TBoolean(false));
		long cid = manageView.externalCall(params, null);
		this.scheduler.waitEnd(cid);		
	}

	// to replace the consensus protocol
	synchronized public void sendReplaceConsensus(TString name, TGLinkedList<TElement> newFeatures) {
		fc.enter();
		long cid = replaceConsensus.externalCall(new ReplaceProtocolCallParameters(name, newFeatures), null);
		this.scheduler.waitEnd(cid);
	}

	// to replace the abcast protocol
	synchronized public void sendReplaceAbcast(TString name, TGLinkedList<TElement> newFeatures) {
		fc.enter();
		long cid = replaceDynAbcast.externalCall(new ReplaceProtocolCallParameters(name, newFeatures), null);
		this.scheduler.waitEnd(cid);
	}
}
