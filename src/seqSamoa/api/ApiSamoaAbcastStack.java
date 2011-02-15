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

import org.apache.log4j.Logger;

import br.ufms.dct.simplerep.SimpleRepConfiguration;
import br.ufms.dct.simplerep.kernels.SamoaKernel;

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
import seqSamoa.services.abcast.Abcast;
import seqSamoa.services.abcast.AbcastResponseParameters;
import seqSamoa.services.consensus.Consensus;
import seqSamoa.services.fd.FD;
import seqSamoa.services.fd.FDResponseParameters;
import seqSamoa.services.monitoring.ProcessSuspicion;
import seqSamoa.services.monitoring.ProcessSuspicionCallParameters;
import seqSamoa.services.udp.UDP;
import seqSamoa.services.udp.UDPCallParameters;
import uka.transport.Transportable;
import framework.PID;
import framework.libraries.serialization.TList;

/**
 * A protocol stack that implements atomic broadcast
 * in a static group for a crash-stop model 
 */
public class ApiSamoaAbcastStack extends ProtocolStack {
	static Logger logger = Logger.getLogger(ApiSamoaAbcastStack.class.getName());
	
    // The different microprotocol
    ProtocolPing pFD;
    ProtocolAbcast pAbcast;
    ProtocolConsensus pConsensus;

    // The different services
    FD fd;
    Abcast abcast;
    Consensus consensus;

    // The Listeners
    // It listen for DynABcast message
    protected Abcast.Listener abcastListener;
    
    protected FD.Listener fdListener;
    
    // @JRS 
    protected UDP.Listener udpListener;

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
    public ApiSamoaAbcastStack(PID myself, TList processes,
			SamoaScheduler scheduler, SamoaFlowControl fc,
			Callback callback, final Callback udpCallback, String logFile, SimpleRepConfiguration conf) {

        super(myself, processes, scheduler, fc, callback, logFile, new String("groupcomm"), true, true);

        // SERVICE CREATION
        try {
        	fd = new FD("fd", this);
        	consensus = new Consensus("consensus", this);
        	abcast = new Abcast("abcast", this);
        } catch (AlreadyExistingServiceException aep) {
        	throw new RuntimeException("Should not be possible! Bug in conception.");
        }
        
        abcastListener = abcast.new Listener(this, new LinkedList<ServiceCallOrResponse>()) {
            synchronized public void evaluate(AbcastResponseParameters infos, Transportable message) {
                this.parent.getStack().getCallback().serviceCallback(infos, message);
            }
        };
        
        fdListener = fd.new Listener(this, new LinkedList<ServiceCallOrResponse>())
        {
            synchronized public void evaluate(FDResponseParameters params, Transportable response)
            {
                 // escrevendo na tela a lista de processos suspeitos
                logger.debug("Suspected: " +  params.suspected.toString() + "\n");
            }
        };
        
        udpListener = udp.new Listener(this, new LinkedList<ServiceCallOrResponse>())
        {
			public void evaluate(Object params, Transportable response) {
				udpCallback.serviceCallback(params, response);
			}
        };

        ProcessSuspicion.Executer unuseful = this.processSuspicion.new Executer(this, new LinkedList<ServiceCallOrResponse>()) {
        	synchronized public void evaluate(ProcessSuspicionCallParameters infos,
        					Message message) {
        		logger.debug("A process is strongly suspected to be crashed!");        		
        	}
        };
        try{
        	unuseful.link();
        } catch (AlreadyBoundServiceException ex){
			throw new RuntimeException("Should not be possible! Bug in conception.");
        }
        
        // Protocol Instanciation
        try {
        	pFD = new ProtocolPing(new String("FD"), this, conf.getFrameworkTimeout(), 3, fd,
        			(Service<? extends UDPCallParameters, ? extends Object>) this.udp);
        	

        	pConsensus = new ProtocolConsensus(new String("Consensus"), this, consensus, fd, this.rpt2pt);
        	pAbcast = new ProtocolAbcast(new String("Abcast"), this, abcast, consensus, this.rpt2pt);
        } catch (AlreadyExistingProtocolModuleException aep) {
        	throw new RuntimeException("Should not be possible! Bug in conception.");
        }
     }

	// to abcast a message
	synchronized public void abcastMessage(Transportable message) {
		fc.enter();
		long cid = abcast.externalCall(null, new Message(message, abcastListener));
		this.scheduler.waitEnd(cid);
	}
}
