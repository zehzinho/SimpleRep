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
import seqSamoa.ServiceCallOrResponse;
import seqSamoa.exceptions.AlreadyBoundServiceException;
import seqSamoa.exceptions.AlreadyExistingProtocolModuleException;
import seqSamoa.exceptions.AlreadyExistingServiceException;
import seqSamoa.protocols.abcast.ProtocolCrashRecoveryAbcast;
import seqSamoa.protocols.consensus.ProtocolCrashRecoveryConsensus;
import seqSamoa.protocols.fd.ProtocolFDSe;
import seqSamoa.protocols.fd.ProtocolFDSu;
import seqSamoa.services.abcast.Abcast;
import seqSamoa.services.abcast.AbcastResponseParameters;
import seqSamoa.services.commit.UpdateState;
import seqSamoa.services.commit.UpdateStateCallParameters;
import seqSamoa.services.consensus.Consensus;
import seqSamoa.services.fd.FDSe;
import seqSamoa.services.fd.FDSu;
import uka.transport.Transportable;
import framework.PID;
import framework.libraries.serialization.TList;

/**
 * A protocol stack that implements atomic broadcast
 * in a static group for a crash-recovery model. The 
 * atomic broadcast algorithm is inspired by the 
 * Rodriguez and Raynal algorithm. 
 */
public class ApiSamoaCrashRecoveryAbcastStack extends ProtocolStack {
	// The different microprotocols
	ProtocolFDSe pFDSe;

	ProtocolFDSu pFDSu;

	ProtocolCrashRecoveryConsensus pConsensus;

	ProtocolCrashRecoveryAbcast pAbcast;

	// The different services
	UpdateState updateState;

	Abcast abcast;

	Consensus consensus;

	FDSe fdse;

	FDSu fdsu;

	// The Executers
	protected UpdateState.Executer updateStateExecuter;

	// The Listener
	// It listen for ABcast message
	protected Abcast.Listener abcastListener;

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
	 * @param log
	 * 			  the log file
	 */
	public ApiSamoaCrashRecoveryAbcastStack(PID myself, TList processes,
			SamoaScheduler scheduler, SamoaFlowControl fc,
			Callback callback, String logFile) {

		super(myself, processes, scheduler, fc, callback, logFile,
				new String("static_recovery"), new String("tmp/CRabcast_"
						+ myself.ip.getHostName() + "_" + myself.port + "_"
						+ myself.incarnation + ".log"), new String(
						"tmp/CRabcast_" + myself.ip.getHostName() + "_"
								+ myself.port + "_" + myself.incarnation
								+ ".recovery"), true, true);

		// SERVICES CREATION
		try {
			updateState = new UpdateState("updateState", this);
			abcast = new Abcast("abcast", this);
			consensus = new Consensus("consensus", this);
			fdsu = new FDSu("fdsu", this);
			fdse = new FDSe("fdse", this);
		} catch (AlreadyExistingServiceException aep) {
			throw new RuntimeException(
					"Should not be possible! Bug in conception.");
		}

		updateStateExecuter = updateState.new Executer(this, new LinkedList<ServiceCallOrResponse>()) {
			synchronized public void evaluate(UpdateStateCallParameters params,
					Message dmessage) {
				this.parent.getStack().getCallback().serviceCallback(params,
						dmessage);
			}
		};
        try{
        	updateStateExecuter.link();
        } catch (AlreadyBoundServiceException ex){
			throw new RuntimeException("Should not be possible! Bug in conception.");
        }

		abcastListener = abcast.new Listener(this, new LinkedList<ServiceCallOrResponse>()) {
			synchronized public void evaluate(AbcastResponseParameters infos,
					Transportable message) {
				this.parent.getStack().getCallback().serviceCallback(infos,
						message);
			}
		};

		// Protocol Instanciation
		try {
			pFDSu = new ProtocolFDSu(new String("FDSu"), this, fdsu, fdse, udp);

			pFDSe = new ProtocolFDSe(new String("FDSe"), this, 1000, 3, fdse,
					udp);

			pConsensus = new ProtocolCrashRecoveryConsensus(new String("Consensus"), this,
					consensus, fdsu, pt2pt);

			pAbcast = new ProtocolCrashRecoveryAbcast(new String("Abcast"), this, abcast,
					consensus, pt2pt, updateState);
		} catch (AlreadyExistingProtocolModuleException aep) {
			throw new RuntimeException(
					"Should not be possible! Bug in conception.");
		}
	}

	public void checkpoint(Transportable state) {
		pConsensus.commit();
		pAbcast.commit(state);
	}

	// to abcast a message
	synchronized public void abcastMessage(Transportable message) {
		fc.enter();
		long cid = abcast.externalCall(null, new Message(message, abcastListener));
		this.scheduler.waitEnd(cid);
	}
}
