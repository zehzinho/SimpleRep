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
import seqSamoa.exceptions.AlreadyBoundServiceException;
import seqSamoa.exceptions.AlreadyExistingProtocolModuleException;
import seqSamoa.exceptions.AlreadyExistingServiceException;
import seqSamoa.protocols.abcast.ProtocolAbcast;
import seqSamoa.protocols.consensus.ProtocolConsensus;
import seqSamoa.protocols.fd.ProtocolPing;
import seqSamoa.protocols.replacer.AbcastReplacer;
import seqSamoa.protocols.replacer.ConsensusReplacer;
import seqSamoa.services.abcast.Abcast;
import seqSamoa.services.abcast.AbcastResponseParameters;
import seqSamoa.services.consensus.Consensus;
import seqSamoa.services.fd.FD;
import seqSamoa.services.monitoring.ProcessSuspicion;
import seqSamoa.services.monitoring.ProcessSuspicionCallParameters;
import seqSamoa.services.replacement.ReplaceProtocol;
import seqSamoa.services.replacement.ReplaceProtocolCallParameters;
import seqSamoa.services.replacement.ReplaceProtocolResponseParameters;
import seqSamoa.services.udp.UDPCallParameters;
import uka.transport.Transportable;
import framework.PID;
import framework.libraries.serialization.TElement;
import framework.libraries.serialization.TGLinkedList;
import framework.libraries.serialization.TList;
import framework.libraries.serialization.TString;

/**
 * A protocol stack that implements atomic broadcast
 * in a static group for a crash-stop model. Furthermore,
 * this stack allows the atomic broadcast protocol to be
 * dynamically updated.
 */
public class ApiSamoaAbcastWithReplaceableProtocolStack extends ProtocolStack {
	// The different microprotocol
	ProtocolPing pFD;

	ProtocolConsensus pConsensus;

	ProtocolAbcast pAbcast;

	// The ABcast and Consensus Replacer
	int typeReplacer;

	AbcastReplacer rAbcast;

	ConsensusReplacer rConsensus;

	// The different services
	FD fd;

	Consensus consensus;

	Abcast abcast;

	ProcessSuspicion processSuspicion;

	ReplaceProtocol replaceAbcast;

	ReplaceProtocol replaceConsensus;

	// The Listeners
	protected Abcast.Listener abcastListener;

	protected ReplaceProtocol.Listener replaceAbcastListener;

	protected ReplaceProtocol.Listener replaceConsensusListener;

	/**
	 * Constructor.
	 * 
	 * @param myself
	 *            the Process ID of the stack (should be unique)
	 * @param processes
	 *            the list of processes that run the same stack
	 * @param scheduler
	 *            the scheduler that manages executions in the stack
	 * @param fc
	 *            the flow control dedicated this stack
	 * @param callback
	 *            the interface that gets the responses of this stack
	 * @param logFile
	 *            name of the file where to log the infos
	 * @param typeReplacer
	 *            the type of the replacer (0 = Consensus, 1 = Abcast, 2 =
	 *            FIFOAbcast, 3 = FIFOABcast by MR)
	 */
	public ApiSamoaAbcastWithReplaceableProtocolStack(PID myself,
			TList processes, SamoaScheduler scheduler,
			SamoaFlowControl fc, Callback callback, String logFile,
			int typeReplacer) {

		super(myself, processes, scheduler, fc, callback, logFile,
				new String("groupcomm"), true, true);
		this.typeReplacer = typeReplacer;

		// SERVICE CREATION
		try {
			fd = new FD("fd", this);
			consensus = new Consensus("consensus", this);
			abcast = new Abcast("abcast", this);
			replaceAbcast = new ReplaceProtocol("replaceAbcast", this);
			replaceConsensus = new ReplaceProtocol("replaceConsensus", this);
		} catch (AlreadyExistingServiceException aep) {
			throw new RuntimeException(
					"Should not be possible! Bug in conception.");
		}

		abcastListener = abcast.new Listener(this, new LinkedList<ServiceCallOrResponse>()) {
			synchronized public void evaluate(AbcastResponseParameters infos,
					Transportable message) {
				this.parent.getStack().getCallback().serviceCallback(infos, message);
			}
		};

		replaceAbcastListener = replaceAbcast.new Listener(this, new LinkedList<ServiceCallOrResponse>()) {
			synchronized public void evaluate(
					ReplaceProtocolResponseParameters infos,
					Transportable message) {
				this.parent.getStack().getCallback().serviceCallback(infos, message);
			}
		};

		replaceConsensusListener = replaceConsensus.new Listener(this, new LinkedList<ServiceCallOrResponse>()) {
			synchronized public void evaluate(
					ReplaceProtocolResponseParameters infos,
					Transportable message) {
				this.parent.getStack().getCallback().serviceCallback(infos, message);
			}
		};

		ProcessSuspicion.Executer unuseful = ((ProcessSuspicion) getService("processSuspicion")).new Executer(
				this, new LinkedList<ServiceCallOrResponse>()) {
			synchronized public void evaluate(
					ProcessSuspicionCallParameters infos, Message message) {
				throw new RuntimeException(
						"A process is strongly suspected to be crashed!");
			}
		};
		try {
			unuseful.link();
		} catch (AlreadyBoundServiceException ex) {
			throw new RuntimeException(
					"Should not be possible! Bug in conception.");
		}

		// Protocol Instanciation
		try {
			pFD = new ProtocolPing(new String("FD"), this, 1000, 3, fd,
					(Service<? extends UDPCallParameters, ? extends Object>) udp);

			pConsensus = new ProtocolConsensus(new String("Consensus"), this,
					consensus, fd, rpt2pt);
			pAbcast = new ProtocolAbcast(new String("Abcast"), this, abcast,
					consensus, rpt2pt);
			rAbcast = new AbcastReplacer(new String("AbcastReplacer"), this,
					replaceAbcast, abcast);
			rConsensus = new ConsensusReplacer(new String("ConsensusReplacer"),
					this, replaceConsensus, consensus, abcast);
		} catch (AlreadyExistingProtocolModuleException aep) {
			throw new RuntimeException(
					"Should not be possible! Bug in conception.");
		}
	}

	// to abcast a message
	synchronized public void abcastMessage(Transportable message) {
		fc.enter();
		long cid = abcast.externalCall(null, new Message(message, abcastListener));
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
		long cid = replaceAbcast.externalCall(new ReplaceProtocolCallParameters(name, newFeatures), null);
		this.scheduler.waitEnd(cid);
	}

	public void init() throws AlreadyBoundServiceException {
		if (this.typeReplacer != 0) {
			removeProtocolModule(rConsensus);
			rConsensus.close();
		} 
		if (this.typeReplacer != 1) {
			removeProtocolModule(rAbcast);
			rAbcast.close();
		}
		
		super.init();
	}
}
