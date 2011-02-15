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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import seqSamoa.exceptions.InterruptedSchedulerException;
import seqSamoa.exceptions.NotInAComputationException;
import seqSamoa.exceptions.UndeclaredCallOrResponseException;
import framework.CompressedLongSet;

/**
 * This manager implements the simple version of the algorithm to ensure the
 * module order property.
 */
public class SimpleModuleOrderManager implements ConcurrencyManager {
	
	// This class represents a computation
	private static class Computation {
		/* The computation ID in which the task is executed */
		public long cID;
		
		/* The task currently executed by the computation*/
		public AtomicTask currentTask;

		/* Sorted internal tasks */
		public LinkedList<AtomicTask> readyTasks = new LinkedList<AtomicTask>();

		/* Internal Tasks not yet sorted */
		public LinkedList<AtomicTask> initiatedTasks = new LinkedList<AtomicTask>();

		// The array of events in each critical path
		public HashMap<Long, Integer> nbEventsPerPath = new HashMap<Long, Integer>();

		public Computation(long cID) {
			this.cID = cID;
		}
	}

	// This class represents threads
	private static class TaskThread extends Thread {
		/* The concurrency manager */
		private SimpleModuleOrderManager scheduler;
		
		/* The computation that is currently executed by the Thread */
		public Computation currentComputation;

		public TaskThread(SimpleModuleOrderManager scheduler) {
			this.scheduler = scheduler;
		}

		public void run() {
			try {
				while (true) {
					// Get a computation to be executed
					currentComputation = scheduler.getComputation(this);

					// Execute the computation toExecute
					while (!currentComputation.readyTasks.isEmpty()) {
						currentComputation.currentTask = currentComputation.readyTasks.removeFirst();

						// Is the task ready to be executed according to
						// the policy implemented by the scheduler
						scheduler.waitTaskIsReady(currentComputation.currentTask);

						// Execute the task
						currentComputation.currentTask.execute();

						// Adds the tasks initiated by task in readyTasks
						int size = currentComputation.readyTasks.size();
						int startI = 0;
						while (!currentComputation.initiatedTasks.isEmpty()) {
							AtomicTask initiatedTask = currentComputation.initiatedTasks
									.removeFirst();

							int index = startI;
							if (initiatedTask.triggeringModule != null) {
								for (int i = startI; i < size; i++) {
									AtomicTask sTaskTmp = currentComputation.readyTasks.get(i);

									if (initiatedTask.triggeringModule.equals(sTaskTmp.triggeringModule))
										index = i + 1;
								}
							}

							currentComputation.readyTasks.add(index, initiatedTask);
							startI = index + 1;
						}

						// Indicates to the scheduler that the task
						// has been executed
						scheduler.taskExecuted(currentComputation.currentTask, currentComputation);
					}
					
					scheduler.finishComputation(this, currentComputation);
					currentComputation = null;
				}
			} catch (Exception ie) {
				//ie.printStackTrace();
				System.out.println("Scheduler Thread Closed due to Exception: "
						+ ie.getMessage());
			}
		}
	}

	// HashMap that contains the call or response causally depending
	// on the given call or response
	private HashMap<ServiceCallOrResponse, HashSet<ServiceCallOrResponse>> dependenciesAndInfluences = new HashMap<ServiceCallOrResponse, HashSet<ServiceCallOrResponse>>();

	// All the service calls and responses
	private HashSet<ServiceCallOrResponse> allServicesCOR = new HashSet<ServiceCallOrResponse>();

	// HashMap that contains for each ServiceCallOrResponse the order
	// in which computations call execute the call or response
	private HashMap<ServiceCallOrResponse, LinkedList<Long>> pathAccessRights = new HashMap<ServiceCallOrResponse, LinkedList<Long>>();

	// List of computations that are scheduled
	private LinkedList<Computation> scheduledComputations = new LinkedList<Computation>();

	// List of computations that are finished
	private CompressedLongSet finishedComputations = new CompressedLongSet();

	// The next computation id available
	private long nextComputationID = 1;

	// The next critical path id avaliable
	private long nextCriticalPathID = 1;

	// The maximal number of thread
	private int nbThread;

	// The thread that is executed in sequential mode (in order to execute
	// AtomicTasks)
	private Thread sequentialThread = null;

	// The Thread that runs the sequential Manager
	private HashSet<TaskThread> runners = new HashSet<TaskThread>();

	/**
	 * Constructor
	 * 
	 * @param nbThread
	 *            The maximal number of concurrent thread managed by the
	 *            scheduler
	 */
	public SimpleModuleOrderManager(int nbThread) {
		super();

		this.nbThread = nbThread;
	}

	public void waitEnd(long cID)
			throws InterruptedSchedulerException {
		synchronized (finishedComputations) {
			while (!finishedComputations.contains(cID)) {
				try {
					finishedComputations.wait();
				} catch (InterruptedException ie) {
					throw new InterruptedSchedulerException(cID);
				}
			}
		}
	}

	// Schedule a new computation (i.e., a new external call or response)
	public long addExternalTask(AtomicTask task) {
		synchronized (scheduledComputations) {
			// Create the computation
			Computation c = new Computation(this.nextComputationID);
			c.readyTasks.addLast(task);

			// Increment cID for the following computation
			this.nextComputationID++;

			// 	Add the computation to scheduled computation
			scheduledComputations.addLast(c);

			// Notify since new computations are possibly executable
			scheduledComputations.notifyAll();
			return c.cID;
		}
	}

	// Schedule a new internal call or response
	public void addInternalTask(AtomicTask task)
			throws NotInAComputationException {
		Computation c = currentComputation();

		if (c == null)
			throw new NotInAComputationException("");

		c.initiatedTasks.addLast(task);
		
		// Set the module that initiate the task
		task.triggeringModule = c.currentTask.currentModule;

		// Initiate the critical path if the first event
		// of the computation is critical
		if (task.getCOR().isCritical) {
			if (c.currentTask.pathID == 0) {
				initiateCriticalPath(task, c);
			} else if (c.currentTask.pathID != 0) {
				int counter = c.nbEventsPerPath.get(c.currentTask.pathID) + 1;
				c.nbEventsPerPath.put(c.currentTask.pathID, counter);

				task.pathID = c.currentTask.pathID;
			}
		} else {
			task.pathID = 0;
		}
	}

	// Schedule a new atomic task
	public long scheduleAtomicTask(AtomicTask task) {
		synchronized (scheduledComputations) {
			// Create the computation and add it to scheduled computation
			Computation c = new Computation(this.nextComputationID);
			c.readyTasks.addLast(task);

			// Increment cID for the following computation
			this.nextComputationID++;

			// Add the computation to scheduled computation
			scheduledComputations.addLast(c);

			// Notify since new computations are possibly executable
			scheduledComputations.notifyAll();
			return c.cID;
		}
	}
	
	// Returns the current atomic task
	public AtomicTask currentTask() {
		Computation c = currentComputation();
		
		if (c != null)
			return c.currentTask;
		else
			return null;
	}

	public void start() {
		for (int i = 0; i < nbThread; i++) {
			TaskThread runner = new TaskThread(this);
			runner.start();
				
			this.runners.add(runner);
		}
	}
		
	public void close() {
		Iterator<TaskThread> it = runners.iterator();
		while (it.hasNext())
			it.next().interrupt();
	}

	@SuppressWarnings("unchecked")
	public void stackReconfigured(ProtocolStack stack) {
		synchronized(pathAccessRights) {
			pathAccessRights = new HashMap<ServiceCallOrResponse, LinkedList<Long>>();

			// Remove all services from the stack before reconfiguration
			Iterator<ServiceCallOrResponse> itOldServices = allServicesCOR
					.iterator();
			while (itOldServices.hasNext()) {
				Service s = itOldServices.next().service;

				if (s.stack.equals(stack))
					itOldServices.remove();
			}

			// Add all services in the stack after reconfiguration
			// and create the new table of access rights
			Iterator<Service> itNewServices = stack.allServices.values().iterator();
			while (itNewServices.hasNext()) {
				Service service = itNewServices.next();

				ServiceCallOrResponse call = ServiceCallOrResponse
						.createServiceCallOrResponse(service, true);
				ServiceCallOrResponse resp = ServiceCallOrResponse
						.createServiceCallOrResponse(service, false);

				allServicesCOR.add(call);
				allServicesCOR.add(resp);
			}
			dependenciesAndInfluences = computeServicesInfluencesAndDependencies();

			// Construct the table of access rights
			Iterator<ServiceCallOrResponse> itCOR = allServicesCOR.iterator();
			while (itCOR.hasNext()) {
				ServiceCallOrResponse cor = itCOR.next();

				synchronized(cor) {
					pathAccessRights.put(cor, new LinkedList<Long>());
				}
			}
		}
	}

	private void initiateCriticalPath(AtomicTask task,
			Computation c) {
		synchronized (pathAccessRights) {
			task.pathID = nextCriticalPathID;

			// Take the "version" for each COR that may be executed by the
			// computation
			Iterator<ServiceCallOrResponse> itCOR = this.dependenciesAndInfluences
					.get(task.getCOR()).iterator();
			while (itCOR.hasNext()) {
				ServiceCallOrResponse cor = itCOR.next();
				
				synchronized(cor) {
					this.pathAccessRights.get(cor).addLast(task.pathID);
				}
			}
			
			// Update the path ID for the next critical path
			nextCriticalPathID++;
		}
		// Update the number of event in the critical path
		c.nbEventsPerPath.put(task.pathID, 1);
	}

	// Return the current computation
	private Computation currentComputation() {
		Thread t = Thread.currentThread();
		if (t instanceof TaskThread)
			return ((TaskThread) t).currentComputation;
		else 
			return null;
	}

	// Return the next computation to be executed
	private Computation getComputation(Thread thread) {
		Computation c;

		// Wait that a computation is scheduled
		synchronized (scheduledComputations) {
			while ((scheduledComputations.isEmpty())
					|| ((sequentialThread != null) && (!sequentialThread
							.equals(thread)))) {
				try {
					scheduledComputations.wait();
				} catch (InterruptedException ex) {
					throw new RuntimeException("Scheduler interrupted");
				}
			}

			c = scheduledComputations.removeFirst();
			ServiceCallOrResponse firstTaskCor = c.readyTasks.getFirst()
					.getCOR();

			if ((firstTaskCor.equals(ServiceCallOrResponse.nullCOR))
					|| (firstTaskCor.service.stack.isReconfigured)) {
				// The task is atomic or some stack is reconfigured:
				// the computation has to be executed sequentially with other
				// computations
				// = > wait that no other thread executes a computation
				this.sequentialThread = thread;
				boolean computationsBeingExecuted = true;

				while (computationsBeingExecuted) {
					computationsBeingExecuted = false;
					Iterator<TaskThread> it = runners.iterator();

					while ((it.hasNext()) && (!computationsBeingExecuted))
						if (it.next().currentComputation != null)
							computationsBeingExecuted = true;

					if (computationsBeingExecuted) {
						try {
							scheduledComputations.wait();
						} catch (InterruptedException ex) {
							throw new RuntimeException("Scheduler interrupted");
						}
					}
				}
			} else {
				// Initiate the critical path if the first event
				// of the computation is critical
				AtomicTask firstTask = c.readyTasks.getFirst();
				if (firstTask.getCOR().isCritical)
					initiateCriticalPath(firstTask, c);
			}
		}
		
		return c;
	}

	// Return when the task is ready
	private void waitTaskIsReady(AtomicTask task) {
		ServiceCallOrResponse cor = task.getCOR();

		if (cor.isCritical) {
			synchronized(cor) {
				if (!pathAccessRights.get(cor).contains(task.pathID))
					throw new UndeclaredCallOrResponseException(cor);

				while (pathAccessRights.get(cor).getFirst() != task.pathID) {
					try {
						cor.wait();
					} catch (InterruptedException ex) {
						throw new RuntimeException("Scheduler interrupted");
					}
				}
			}
		}
	}

	// Do what is necessary upon end of a task
	private void taskExecuted(AtomicTask task, Computation c) {
		if (task.getCOR().equals(ServiceCallOrResponse.nullCOR)) {
			synchronized (scheduledComputations) {
				this.sequentialThread = null;
				scheduledComputations.notifyAll();
			}
		} else if (task.pathID != 0) {
			int counter = c.nbEventsPerPath.get(task.pathID) - 1;

			if (counter == 0) {
				// The critical path is finished
				// = > Release access to service call or response
				synchronized (pathAccessRights) {
					Iterator<ServiceCallOrResponse> itAccessRight = pathAccessRights.keySet().iterator();
					while (itAccessRight.hasNext()) {
						ServiceCallOrResponse cor = itAccessRight.next();
						
						synchronized (cor) {
							pathAccessRights.get(cor).remove(task.pathID);

							// Notify for access blocking in waitTaskIsReady
							cor.notifyAll();
						}
					}
				}
			} else {
				c.nbEventsPerPath.put(task.pathID, counter);
			}
		}
	}

	// Finish the computation
	private void finishComputation(Thread thread, Computation c) {
		synchronized (finishedComputations) {
			// The computation is finished
			finishedComputations.add(c.cID);

			// Notify for waitEnd
			finishedComputations.notifyAll();
		}
	}

	// Compute the mixed influences and dependencies between services
	private HashMap<ServiceCallOrResponse, HashSet<ServiceCallOrResponse>> computeServicesInfluencesAndDependencies() {
		HashMap<ServiceCallOrResponse, HashSet<ServiceCallOrResponse>> influences = computeServicesInfluences();
		HashMap<ServiceCallOrResponse, HashSet<ServiceCallOrResponse>> dependencies = computeServicesDependencies();
		HashMap<ServiceCallOrResponse, HashSet<ServiceCallOrResponse>> result = new HashMap<ServiceCallOrResponse, HashSet<ServiceCallOrResponse>>();

		// Construct dependencies and influences for all services
		Iterator<ServiceCallOrResponse> itServiceCallOrResponse = dependencies
				.keySet().iterator();
		while (itServiceCallOrResponse.hasNext()) {
			ServiceCallOrResponse callOrResp = itServiceCallOrResponse.next();
			HashSet<ServiceCallOrResponse> depAndInf = new HashSet<ServiceCallOrResponse>();

			Iterator<ServiceCallOrResponse> itOnDep = dependencies.get(
					callOrResp).iterator();
			while (itOnDep.hasNext()) {
				ServiceCallOrResponse depCOR = itOnDep.next();

				Iterator<ServiceCallOrResponse> itOnInf = influences
						.get(depCOR).iterator();
				while (itOnInf.hasNext())
					depAndInf.add(itOnInf.next());
			}

			result.put(callOrResp, depAndInf);
		}

		return result;
	}

	// Compute the influences between services
	@SuppressWarnings("unchecked")
	private HashMap<ServiceCallOrResponse, HashSet<ServiceCallOrResponse>> computeServicesInfluences() {
		HashMap<ServiceCallOrResponse, HashSet<ServiceCallOrResponse>> result = new HashMap<ServiceCallOrResponse, HashSet<ServiceCallOrResponse>>();

		// Compute dependencies for all the services calls and responses
		Iterator<ServiceCallOrResponse> itAllServices = allServicesCOR
				.iterator();
		while (itAllServices.hasNext()) {
			ServiceCallOrResponse cor = itAllServices.next();
			Service service = cor.service;

			HashSet<ServiceCallOrResponse> influences = new HashSet<ServiceCallOrResponse>();
			if (cor.isCritical) {
				influences.add(cor);

				// Compute all the handlers executed upon callOrResp
				HashSet<Service.Handler> executedHandlers = new HashSet<Service.Handler>();
				executedHandlers.addAll(service.boundInterceptors);
				if ((cor.call) && (service.currentExecuter != null))
					executedHandlers.add(service.currentExecuter);
				else if (!cor.call)
					executedHandlers.addAll(service.allListeners.values());

				// Compute the influences (only critical services are
				// considered)
				Iterator<Service.Handler> itHandlers = executedHandlers
						.iterator();
				while (itHandlers.hasNext()) {
					ProtocolModule pModule = itHandlers.next().parent;

					Iterator<Service.Executer> itExec = pModule.allExecuters
							.iterator();
					while (itExec.hasNext()) {
						Service sExec = itExec.next().getService();
						ServiceCallOrResponse corTmp = ServiceCallOrResponse
								.createServiceCallOrResponse(sExec, true);
						if (corTmp.isCritical)
							influences.add(corTmp);
					}

					Iterator<Service.Interceptor> itInter = pModule.allInterceptors
							.iterator();
					while (itInter.hasNext()) {
						Service sInt = itInter.next().getService();

						ServiceCallOrResponse corTmp = ServiceCallOrResponse
								.createServiceCallOrResponse(sInt, true);
						if (corTmp.isCritical)
							influences.add(corTmp);

						corTmp = ServiceCallOrResponse
								.createServiceCallOrResponse(sInt, false);
						if (corTmp.isCritical)
							influences.add(corTmp);
					}

					Iterator<Service.Listener> itList = pModule.allListeners
							.iterator();
					while (itExec.hasNext()) {
						Service sList = itList.next().getService();
						ServiceCallOrResponse corTmp = ServiceCallOrResponse
								.createServiceCallOrResponse(sList, false);
						if (corTmp.isCritical)
							influences.add(corTmp);
					}
				}
			}

			result.put(cor, influences);
		}

		// Return the hashmap with all services influences
		return result;
	}

	// Compute the dependencies between services
	@SuppressWarnings("unchecked")
	private HashMap<ServiceCallOrResponse, HashSet<ServiceCallOrResponse>> computeServicesDependencies() {
		HashMap<ServiceCallOrResponse, HashSet<ServiceCallOrResponse>> result = new HashMap<ServiceCallOrResponse, HashSet<ServiceCallOrResponse>>();

		// Create a list of influences for all service calls and responses
		Iterator<ServiceCallOrResponse> itAllServices = allServicesCOR
				.iterator();
		while (itAllServices.hasNext()) {
			ServiceCallOrResponse cor = itAllServices.next();

			HashSet<ServiceCallOrResponse> dependencies = new HashSet<ServiceCallOrResponse>();
			if (cor.isCritical)
				dependencies.add(cor);
			result.put(cor, dependencies);
		}

		// Compute dependencies for all the service calls and responses
		Iterator<ServiceCallOrResponse> itCOR = result.keySet().iterator();
		while (itCOR.hasNext()) {
			ServiceCallOrResponse cor = itCOR.next();
			if (cor.isCritical) {
				Service service = cor.service;
				HashSet<ServiceCallOrResponse> dependencies = result.get(cor);

				// Compute all the handlers executed upon callOrResp
				HashSet<Service.Handler> executedHandlers = new HashSet<Service.Handler>();
				executedHandlers.addAll(service.boundInterceptors);
				if ((cor.call) && (service.currentExecuter != null))
					executedHandlers.add(service.currentExecuter);
				else if (!cor.call)
					executedHandlers.addAll(service.allListeners.values());

				// Compute the dependencies
				Iterator<Service.Handler> itHandlers = executedHandlers
						.iterator();
				while (itHandlers.hasNext()) {
					Service.Handler h = itHandlers.next();

					Iterator<ServiceCallOrResponse> itSerCOR = h.initiatedCallsAndResponses
							.iterator();
					while (itSerCOR.hasNext()) {
						ServiceCallOrResponse sCOR = itSerCOR.next();

						Iterator<ServiceCallOrResponse> itToBeAdded = result
								.get(sCOR).iterator();
						while (itToBeAdded.hasNext()) {
							ServiceCallOrResponse corTmp = itToBeAdded.next();

							if (corTmp.isCritical)
								dependencies.add(corTmp);
						}
					}
				}

				// Transitive closure
				Iterator<ServiceCallOrResponse> itResult = result.keySet()
						.iterator();
				while (itResult.hasNext()) {
					ServiceCallOrResponse corTmp = itResult.next();
					HashSet<ServiceCallOrResponse> dependenciesTmp = result
							.get(corTmp);

					if (dependenciesTmp.contains(cor)) {
						Iterator<ServiceCallOrResponse> itToBeAdded = dependencies
								.iterator();
						while (itToBeAdded.hasNext()) {
							dependenciesTmp.add(itToBeAdded.next());
						}
					}
				}
			}
		}

		// Return the hashmap with all dependencies between services
		return result;
	}
}
