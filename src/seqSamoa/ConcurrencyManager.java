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
package seqSamoa;

import seqSamoa.exceptions.InterruptedSchedulerException;
import seqSamoa.exceptions.NotInAComputationException;

/**
 * This interface represents a concurrency manager. Samoa provides
 * five different concurrency manager: one ensure sequential runs,
 * three ensure the module order property and finally one ensure
 * the event order property.
 * 
 * @see seqSamoa.Scheduler
 * @see seqSamoa.SequentialManager
 * @see seqSamoa.SimpleModuleOrderManager
 * @see seqSamoa.BoundModuleOrderManager
 * @see seqSamoa.RouteModuleOrderManager
 * @see seqSamoa.EventOrderManager
 * 
 */
public interface ConcurrencyManager {
	
    /**
     * This method returns only when all the {@link seqSamoa.Service service} calls and response
     * that causally depends on the external {@link seqSamoa.Service service} 
     * call or response identified by cid are executed.
     * 
     * @param cID
     * 		the id of the corresponding external {@link seqSamoa.AtomicTask task} 
     * 	    we want to wait the end
     * 
     */
    public void waitEnd(long cID) throws InterruptedSchedulerException;
    
    /**
     * This method allows to schedule a new {@link AtomicTask task}
     * that corresponds to an external {@link seqSamoa.Service service} call
     * or response. 
     * 
     * @param task  
     * 			The {@link seqSamoa.AtomicTask task} to be scheduled
     * @return
     * 			The identifier of the computation that results
     * 		    from this {@link seqSamoa.AtomicTask task}
     */
    public long addExternalTask(AtomicTask task);
    
    /**
     * This method allows to schedule a new {@link AtomicTask task}
     * that corresponds to an internal {@link seqSamoa.Service service} call
     * or response. 
     * 
     * @param task
     * 			The {@link seqSamoa.AtomicTask task} to be scheduled
     * @throws {@link seqSamoa.exceptions.NotInAComputationException NotInAComputationException}
     */
    public void addInternalTask(AtomicTask task) throws NotInAComputationException;
    
    /**
     * This method returns the {@link seqSamoa.AtomicTask task} that is currently executed by
     * the concurrency manager.
     * 
     * @return the {@link seqSamoa.AtomicTask task} currently executed
     */
    public AtomicTask currentTask();
    
    /**
     * This method allows to schedule a new {@link seqSamoa.AtomicTask atomic task}
     * 
     * @param task
     * 			The {{@link seqSamoa.AtomicTask atomic task} to be scheduled
     * @return
     * 			The identifier of the computation that results
     * 		    from this {@link seqSamoa.AtomicTask atomic task}
     */    
    public long scheduleAtomicTask(AtomicTask task);
    
    /**
     * This method informs the manager that a stack was reconfigured, i.e.,
     *  some {@link seqSamoa.Service.Executer executer}, 
     *  {@link seqSamoa.Service.Interceptor interceptor} or
     *  {@link seqSamoa.Service.Listener listener} has been bound, unbound
     *  removed or added to the {@link seqSamoa.ProtocolStack stack}
     *  given in parameter. 
     *  
     * @param stack
     * 		The {@link seqSamoa.ProtocolStack stack} that is reconfigured 
     */
    public void stackReconfigured(ProtocolStack stack);
    
    /**
     * Start the concurrency manager
     */
    public void start();
    
    /**
     * Close the concurrency manager
     */
    public void close();
}
