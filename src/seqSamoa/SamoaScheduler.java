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

import java.util.HashSet;
import java.util.Iterator;

import seqSamoa.exceptions.InterruptedSchedulerException;
import seqSamoa.exceptions.NotInAComputationException;
import seqSamoa.exceptions.NotScheduledTaskException;

/**
 * A <CODE>scheduler</CODE> manages the execution of {@link seqSamoa.Service service}
 * calls and responses. The scheduler to ensure properties
 * such as Extended Causal Order and the different flavor of the isolation properties.
 * Furthermore, the <CODE>scheduler</CODE> manages the execution of the tasks that can
 * be delayed, executed periodically.
 * 
 * In order to ensure this properties, a concurrency manager has to be passed 
 * in parameters upon the creation of the scheduler. The concurrency manager 
 * describes the behavior of the scheduler.
 */
public class SamoaScheduler {
    // This class represents a task that is delayed (i.e., that have to wait before being scheduled)
    static private class DelayedTask {
        public long period;

        public long start;

        public boolean periodic;

        public AtomicTask task;
        
        public SamoaScheduler scheduler;

        protected DelayedTask(AtomicTask task, boolean periodic, long period, SamoaScheduler scheduler) {
            this.start = System.currentTimeMillis();
            this.task = task;
            this.period = period;
            this.periodic = periodic;
            this.scheduler = scheduler;
        }

        protected long updateAndRun() {
            long now = System.currentTimeMillis();
            if ((now - this.start - period + scheduler.epsilon) >= 0) {
                scheduler.schedule(task);
                
                if (this.periodic)
                    this.start = now;
                else {
                    this.start = -1;                    
                }
            }

            return (this.start+period-now-scheduler.epsilon);
        }
        
        public int hashCode(){
        	return task.hashCode();
        }
        
        public boolean equals(Object o){
            if (!(o instanceof DelayedTask))
                return false;
            DelayedTask dt = (DelayedTask) o;
                       
            return (dt.task.equals(this.task));
        }
        
        public String toString() {
        	return this.task.toString();
        }
    }
    	
	// Execute the computation if nextStart is smaller than EPSILON
    private int epsilon = 0;
    static private int DEFAULT_EPSILON = 10;

    // Time to execute the while loop with an addition
    static private int TIME_WHILE = 5;
    static private int STEP_WHILE = 100;
    
    // The period to wait before executing the next delayed task
    private long timeWait = 0;

    // The period already waited
    private long timeWaited = 0;

    // The time when starts the last sleep period
    private long lastSleepPeriod = 0;

    // List of tasks delayed
    private HashSet<DelayedTask> delayedTasks;
    	
    // Has the scheduler to be closed
    private boolean toBeClosed = false;

    // Thread delaying the tasks 
    private Thread delayer;
    
    // The concurrency manager
    private ConcurrencyManager manager;

    /**
     * Constructor with a default precision set to 100ms (see the other constructor for more details).
     */
    public SamoaScheduler(ConcurrencyManager manager) {
    	this.manager = manager;
    	this.delayedTasks = new HashSet<DelayedTask>();
    	this.epsilon = DEFAULT_EPSILON;
    }
    
    /**
     * Constructor
     * 
     * @param epsilon
     *            determine the precision of the timer. More precisely if a task t is scheduled in
     *            n milliseconds then the <CODE>scheduler</CODE> ensures that t is not executed before t-epsilon
     *            milliseconds. Note that a too small value for epsilon may result in bigger delays than
     *            expected for delayed {@link seqSamoa.AtomicTask tasks}.
     */
    public SamoaScheduler(int epsilon, ConcurrencyManager manager) {
    	this.manager = manager;
    	this.delayedTasks = new HashSet<DelayedTask>();
        this.epsilon = epsilon;
    }
   
    /**
     * Start the scheduler. Note that the {@link seqSamoa.ProtocolStack stacks}
     * managed by the scheduler do nothing before the scheduler is started.
     */
    public void start() {
        toBeClosed = false;
        
       this.manager.start();
        delayer = new Thread() {
        	public void run() {
        	        try {
        	            while (true) {
        	                // If there is no task to be executed, wait!!!
        	                synchronized (this) {
        	                    timeWaited = 0;

        	                    while ((timeWait == 0)
        	                            && (!toBeClosed))
        	                        wait();
        	                }
    
        	                // wait the necessary time
        	                while ((timeWaited + STEP_WHILE < timeWait)) {
        	                    lastSleepPeriod = System.currentTimeMillis();
        	                    Thread.sleep(STEP_WHILE - TIME_WHILE);

        	                    synchronized (this) {
        	                        timeWaited = timeWaited + STEP_WHILE;
        	                    }
        	                }
        	                                
        	                if (timeWait > timeWaited) 
        	                    Thread.sleep(timeWait - timeWaited);                    

        	                if (toBeClosed) 
        	                    return;
        	                
        	                long minTime = Long.MAX_VALUE;        	                
        	                // Execute the delayed tasks that can be executed
        	                synchronized(this) {
        	                	Iterator<DelayedTask> it = delayedTasks.iterator();
        	                	while (it.hasNext()) {
        	                		DelayedTask dTask = it.next();
        	                		long t = dTask.updateAndRun();
        	                		
        	                		if (t <= 0) 
        	                			it.remove();
        	                		else if (t < minTime)
        	                			minTime = t;
        	                	}
        	                }
        	                
        	                // Compute the next waiting timer
        	                if (minTime == Long.MAX_VALUE)
        	                    timeWait = 0;
        	                else
        	                    timeWait = minTime;
        	            }
        	        } catch (InterruptedException ex) {
        	            throw new RuntimeException("SamoaScheduler.delayer: unrespected delay!");
        	        }
        	    }
        };
        delayer.start();        
    }

    /**
     * Close the scheduler. As a result, all {@link seqSamoa.ProtocolStack stacks} managed
     * by this scheduler stop their execution.
     */
    public void close() {
        toBeClosed = true;
                
        manager.close();
        
        synchronized (this.delayer) {
        	this.delayer.notifyAll();
        }
    }
    
    /**
     * This method returns the {@link seqSamoa.AtomicTask task} that is currently
     * executed by the scheduler.
     * 
     * @return the {@link seqSamoa.AtomicTask task} currently executed
     */
    public AtomicTask currentTask() {
    	return this.manager.currentTask();
    }

    
    /**
     * This method returns when the computation or the task identified by the
     * cID finishes. More precisely, if cID identifies a computation,
     * the method returns when all the {@link seqSamoa.Service service} calls and response
     * that causally depends on the external {@link seqSamoa.Service service} 
     * are executed. Otherwise, it returns when the task an the resulting calls
     * and response terminate.
     * 
     * @param cID
     * 		the id of the task or the corresponding external
     * 	    {@link seqSamoa.Service service} call or response
     *      we want to wait the end
     * 
     */
    public void waitEnd(long cID) throws InterruptedSchedulerException {
    	manager.waitEnd(cID);
    }
        
    // Schedule a new computation (i.e., a new external call or response)
    protected long addExternalTask(AtomicTask task){
    	return manager.addExternalTask(task);
    }
    
    // Schedule a new internal call or response
    protected void addInternalTask(AtomicTask task) throws NotInAComputationException {
    	manager.addInternalTask(task);
    }
    
    /**
     * This method informs the scheduler that a stack was reconfigured, i.e.,
     *  some {@link seqSamoa.Service.Executer executer}, 
     *  {@link seqSamoa.Service.Interceptor interceptor} or
     *  {@link seqSamoa.Service.Listener listener} has been bound, unbound
     *  removed or added to the {@link seqSamoa.ProtocolStack stack}
     *  given in parameter. 
     *  
     * @param stack
     * 		The {@link seqSamoa.ProtocolStack stack} that is reconfigured 
     */
    public void stackReconfigured(ProtocolStack stack) {
    	this.manager.stackReconfigured(stack);
    }
 
    /**
	 * Schedules a {@link seqSamoa.AtomicTask task}. The task is executed in
	 * isolation with computations. The method returns an identifier
	 * that identifies the task.
	 *
	 * @param t
	 *            The task to be executed
	 *            
	 * @return 
	 * 			  The computation id attached to this task and -1 if
	 * 			  the task is scheduled part of a computation.
	 */
     public long schedule(AtomicTask t) {
    	return manager.scheduleAtomicTask(t);
     }

    /**
     * Schedules a {@link seqSamoa.AtomicTask task} for the amount of time passed in the
     * third parameter. When this period of time has passed by, the {@link seqSamoa.AtomicTask task}
     * is executed in isolation with computations.
     * 
     * Timers can be non-periodic: the {@link seqSamoa.AtomicTask task} is triggered at most once.
     * Then, it is immediately canceled. Timers can also be periodic: they are
     * re-scheduled every time they expire, until its cancellation.
     * 
     * There is no guarantee in the maximum amount of time between the
     * scheduling operation and the actual execution of the {@link seqSamoa.AtomicTask task}.
     * The only guarantee is in the minimum amount of time.
     * 
     * @param t
     *            The task to be executed
     * @param periodic
     *            Indicates whether this timer is periodic or non-periodic
     * @param time
     *            The lapse, in milliseconds, before executing the task
     */
     public void schedule(AtomicTask t, boolean periodic, long time) {
        DelayedTask dt = new DelayedTask(t, periodic, time, this);
               
        synchronized(this.delayer) {
        	delayedTasks.add(dt);
        
        	if (timeWait == 0) {
        		timeWait = time;
        		this.delayer.notifyAll();
        	} else if (((timeWait - timeWaited) > STEP_WHILE)
        			&& ((time + timeWaited + System.currentTimeMillis() - lastSleepPeriod) < timeWait)) {
        		timeWait = time;
        	}
        }
    }

    /**
     * Cancels a previously scheduled {@link seqSamoa.AtomicTask task}. If the {@link seqSamoa.AtomicTask task}
     * is currently already executed, this method has no effect.
     * 
     * @param t
     *            The {@link seqSamoa.AtomicTask task} previously scheduled.
     */
    public void cancel(AtomicTask t)
            throws NotScheduledTaskException {
    
    	synchronized(this.delayer) {
    		Iterator<DelayedTask> it = delayedTasks.iterator();
    		while (it.hasNext()){
    			DelayedTask dt = it.next();
        	
    			if (dt.task.equals(t)) {
    				it.remove();
    				return;
    			}
    		}
    	}
        	        
    	throw new NotScheduledTaskException();
    }

    /**
     * Resets the delay of {@link seqSamoa.AtomicTask task} execution to its initial value.
     * The achieved effect is as though the timer had just been scheduled
     * for the first time.
     * 
     * @param t
     *            The {@link seqSamoa.AtomicTask task} previously scheduled.
     */
    public void reset(AtomicTask t)
            throws NotScheduledTaskException {
    	synchronized(delayer) {
    		Iterator<DelayedTask> it = delayedTasks.iterator();
    		while (it.hasNext()){
    			DelayedTask dt = it.next();
        	
    			if (dt.task.equals(t)) {
    				dt.start = System.currentTimeMillis();
    				return;
    			}
    		}
    	}
        	        
    	throw new NotScheduledTaskException();
    }
}
