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

/**
 * This interface represents an atomic task, i.e., 
 * a task that is executed in isolation with (1) the computation that
 * results from exernal {@link seqSamoa.Service} call/response and
 * (2) other atomic tasks.
 * 
 * Atomic tasks can be scheduled with timeout.
 * 
 * @see seqSamoa.Scheduler
 */
public abstract class AtomicTask {	
    /** 
     * The module that initiates the task (used to ensure extended causal order)
     */
    protected ProtocolModule triggeringModule = null;
    
    /**
     * The module currently executed by the task (used to ensure extended causal order)
     */
    protected ProtocolModule currentModule = null;
	
	/**
	 * The path ID corresponding to the task (used to ensure relaxed-module order)
	 */
	protected long pathID = 0;
	
	/**
	 * Describe the behavior of the task
	 */
	public abstract void execute();
	
	/**
	 * Return the {@link seqSamoa.ServiceCallOrResponse service calls/responses}
	 * that corresponds to the atomic task. By default, the value that is returned
	 * corresponds to all services calls/responses.
	 * 
	 * @return
	 * 		the {@link seqSamoa.ServiceCallOrResponse service calls/responses} 
	 *  	that corresponds to the atomic task
	 */
	protected ServiceCallOrResponse getCOR(){
		return ServiceCallOrResponse.nullCOR;
	}
}
