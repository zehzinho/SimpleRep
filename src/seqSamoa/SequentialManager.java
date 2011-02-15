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

import java.io.File;
import java.util.LinkedList;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import seqSamoa.exceptions.InterruptedSchedulerException;
import seqSamoa.exceptions.NotInAComputationException;
import framework.CompressedLongSet;

/**
 * This manager allows only sequential runs.
 */
public class SequentialManager implements ConcurrencyManager, Runnable {	    
	// This class represents a computation
	private static class Computation {
		/* The computation ID in which the task is executed*/
		public long cID;
		
		/* The task currently executed by the computation*/
		public AtomicTask currentTask;
		
		/* Sorted internal tasks */
		public LinkedList<AtomicTask> readyTasks = new LinkedList<AtomicTask>();  		
		
		/* Internal Tasks not yet sorted */
		public LinkedList<AtomicTask> initiatedTasks = new LinkedList<AtomicTask>();  
		
		public Computation(long cID) {
			this.cID = cID;
		}
	}

	// List of tasks scheduled
	private LinkedList<Computation> scheduledComputations = new LinkedList<Computation>();
	
	// List of tasks handled
	private CompressedLongSet finishedComputations = new CompressedLongSet();

	// Has the scheduler to be closed
	private boolean toBeClosed = false;

	// The computation currently being executed
	protected Computation currentComputation;

	// The next computation id available
	private long nextComputationID = 0;

	// The Thread that runs the sequential Manager
	private Thread runner;

	public SequentialManager() {
		super();
	}
	
	/**
	 * This method returns only when all the {@link seqSamoa.Service service} calls and response
	 * that causally depends on the external {@link seqSamoa.Service service} 
	 * call or response identified by cid are executed.
	 * 
	 * @param cID
	 * 		the id of the corresponding external {@link seqSamoa.Service service} 
	 * 		call or response we want to wait the end
	 * 
	 */
	synchronized public void waitEnd(long cID)
			throws InterruptedSchedulerException {
		while (!finishedComputations.contains(cID)) {
			try {
				wait();
			} catch (InterruptedException ie) {
				throw new InterruptedSchedulerException(cID);
			}
		}
	}

	// Schedule a new computation (i.e., a new external call or response)
	synchronized public long addExternalTask(AtomicTask task) {
		// Create the computation and add it to scheduled computation
		Computation c = new Computation(this.nextComputationID);
		c.readyTasks.addLast(task);
		this.scheduledComputations.addLast(c);
		
		// Increment cID for the following computation
		this.nextComputationID++;
		notifyAll();
		
		return c.cID;
	}

	// Schedule a new internal call or response
	synchronized public void addInternalTask(AtomicTask task) throws NotInAComputationException {
		if (!Thread.currentThread().equals(this.runner))
			throw new NotInAComputationException("");
		currentComputation.initiatedTasks.addLast(task);
		
		// Set the module that initiate the task
		task.triggeringModule = currentComputation.currentTask.currentModule;
	}
	
	// Returns the current atomic task
	public AtomicTask currentTask() {
		if (this.currentComputation != null)
			return this.currentComputation.currentTask;
		else
			return null;
	}
	
	// Schedule a new atomic task
	synchronized public long scheduleAtomicTask(AtomicTask task) {
		// Create the computation and add it to scheduled computation
		Computation c = new Computation(this.nextComputationID);
		c.readyTasks.addLast(task);
		this.scheduledComputations.addLast(c);
		
		// Increment cID for the following computation
		this.nextComputationID++;
		notifyAll();
		
		return c.cID;
	}

	public void start() {
		this.runner = new Thread(this);
		runner.start();
	}

	synchronized public void close() {
		toBeClosed = true;
		notifyAll();
	}

	public void run() {
		while (true) {
			synchronized (this) {
				while ((this.scheduledComputations.isEmpty()) && (!this.toBeClosed)) {
					try {
						wait();
					} catch (InterruptedException ex) {
						throw new RuntimeException("Scheduler interrupted");
					}
				}

				if (this.toBeClosed)
					return;

				this.currentComputation = this.scheduledComputations.removeFirst();
			}
						
			while (!this.currentComputation.readyTasks.isEmpty()) {
				this.currentComputation.currentTask = this.currentComputation.readyTasks.removeFirst();
				this.currentComputation.currentTask.execute();
				
				// Sort the initiated Tasks after finishing executing of current tasks
				// The sort is done according to the extended causal order property
				int size = this.currentComputation.readyTasks.size();
				int startI = 0;				
				while (!this.currentComputation.initiatedTasks.isEmpty()) {
					AtomicTask initiatedTask = this.currentComputation.initiatedTasks.removeFirst();
								
					int index = startI;	
					if (initiatedTask.triggeringModule != null) {
						for (int i = startI; i < size; i++) {
							AtomicTask sTaskTmp = this.currentComputation.readyTasks.get(i);

							if (initiatedTask.triggeringModule.equals(sTaskTmp.triggeringModule)) 
								index = i + 1;
						}
					}
					
					this.currentComputation.readyTasks.add(index, initiatedTask);
					startI = index + 1;
				}
			}

			synchronized(this) {
				this.finishedComputations.add(currentComputation.cID);
				this.currentComputation = null;
				this.notifyAll();
			}
		}
	}

	public void stackReconfigured(ProtocolStack stack) {
		// Nothing has to be done
	}
}
