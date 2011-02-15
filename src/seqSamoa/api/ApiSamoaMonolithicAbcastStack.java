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
import seqSamoa.protocols.abcast.ProtocolFastAbcast;
import seqSamoa.protocols.fd.ProtocolPing;
import seqSamoa.services.abcast.Abcast;
import seqSamoa.services.abcast.AbcastResponseParameters;
import seqSamoa.services.fd.FD;
import seqSamoa.services.monitoring.ProcessSuspicion;
import seqSamoa.services.monitoring.ProcessSuspicionCallParameters;
import seqSamoa.services.udp.UDPCallParameters;
import uka.transport.Transportable;
import framework.PID;
import framework.libraries.serialization.TList;

/**
 * A protocol stack that implements atomic broadcast
 * in a static group for a crash-stop model 
 */
public class ApiSamoaMonolithicAbcastStack extends ProtocolStack {
    // The different microprotocol
    ProtocolPing pFD;
    ProtocolFastAbcast pFastAbcast;

    // The different services
    FD fd;
    Abcast abcast;

    // The Listeners
    // It listen for DynABcast message
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
     * @param logFile
     * 			  name of the file where to log the infos
     */
    public ApiSamoaMonolithicAbcastStack(PID myself, TList processes,
			SamoaScheduler scheduler, SamoaFlowControl fc,
			Callback callback, String logFile) {

        super(myself, processes, scheduler, fc, callback, logFile, new String("groupcomm"), true, true);

        // SERVICE CREATION
        try {
        	fd = new FD("fd", this);
        	abcast = new Abcast("abcast", this);
        } catch (AlreadyExistingServiceException aep) {
        	throw new RuntimeException("Should not be possible! Bug in conception.");
        }
        
        abcastListener = abcast.new Listener(this, new LinkedList<ServiceCallOrResponse>()) {
            synchronized public void evaluate(AbcastResponseParameters infos, Transportable message) {
                this.parent.getStack().getCallback().serviceCallback(infos, message);
            }
        };

        ProcessSuspicion.Executer unuseful = this.processSuspicion.new Executer(this, new LinkedList<ServiceCallOrResponse>()) {
        	synchronized public void evaluate(ProcessSuspicionCallParameters infos,
        					Message message) {
        		System.err.println("A process is strongly suspected to be crashed!");        		
        	}
        };
        try{
        	unuseful.link();
        } catch (AlreadyBoundServiceException ex){
			throw new RuntimeException("Should not be possible! Bug in conception.");
        }
        
        // Protocol Instanciation
        try {
        	pFD = new ProtocolPing(new String("FD"), this, 1000, 3, fd,
        			(Service<? extends UDPCallParameters, ? extends Object>) this.udp);

        	pFastAbcast = new ProtocolFastAbcast(new String("Abcast"), this, abcast, fd, this.rpt2pt);
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
