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
package seqSamoa.test;

import java.net.InetAddress;
import java.net.UnknownHostException;
//import java.util.HashMap;
//import java.util.HashSet;
import java.util.LinkedList;

import seqSamoa.BoundModuleOrderManager;
import seqSamoa.ConcurrencyManager;
import seqSamoa.Message;
import seqSamoa.ProtocolModule;
import seqSamoa.ProtocolStack;
import seqSamoa.RouteModuleOrderManager;
import seqSamoa.SamoaFlowControl;
import seqSamoa.SamoaScheduler;
import seqSamoa.SequentialManager;
import seqSamoa.Service;
import seqSamoa.ServiceCallOrResponse;
import seqSamoa.SimpleModuleOrderManager;
import seqSamoa.AtomicTask;
import seqSamoa.exceptions.AlreadyBoundServiceException;
import seqSamoa.exceptions.AlreadyExistingProtocolModuleException;
import seqSamoa.exceptions.AlreadyExistingServiceException;
import uka.transport.Transportable;
import framework.PID;
import framework.libraries.serialization.TLinkedList;

public class Samoa {
	static long errorTotal = 0;
	
	public static class TestStack extends ProtocolStack {
		
		public Service<Long, Long> s1;
		public Service<Long, Long> s2;
		public Service<Long, Long> s3;
		public Service<Long, Long> s4;
		public Service<Long, Long> s5;
		public Service<Long, Long> s6;
		public Service<Long, Long> s7;
		public Service<Long, Long> s8;
		public Service<Long, Long> s10;
		
		@SuppressWarnings("unchecked")
		public TestStack(PID myself, TLinkedList processes, ConcurrencyManager manager)  throws AlreadyExistingServiceException, AlreadyExistingProtocolModuleException {
			super(myself, processes, new SamoaScheduler(manager), new SamoaFlowControl(100),
					null, null, null, false, false);
			
			// Some services
			s1 = new Service("S1", this);
			s2 = new Service("S2", this);
			s3 = new Service("S3", this);
			s4 = new Service("S4", this);
			s5 = new Service("S5", this);
			s6 = new Service("S6", this);
			s7 = new Service("S7", this);
			s8 = new Service("S8", this);

			// Some protocols
			ProtocolModule p1 = new ProtocolModule("P1", this);
			ProtocolModule p2 = new ProtocolModule("P2", this);
			ProtocolModule p3 = new ProtocolModule("P3", this);
			ProtocolModule p4 = new ProtocolModule("P4", this);
			ProtocolModule p5 = new ProtocolModule("P5", this);
			ProtocolModule p6 = new ProtocolModule("P6", this);
			ProtocolModule p7 = new ProtocolModule("P7", this);

			// Definition of Executers
			LinkedList<ServiceCallOrResponse> s1Initiated = new LinkedList<ServiceCallOrResponse>();
			s1Initiated.add(ServiceCallOrResponse.createServiceCallOrResponse(s5, true));
			s1Initiated.add(ServiceCallOrResponse.createServiceCallOrResponse(s3, false));			
			s1.new Executer(p1, s1Initiated) {
				@SuppressWarnings("unchecked")
				public void evaluate(Long params, Message dm) {
					long l = params.longValue();

					try {
						Thread.sleep(1000);
					} catch (InterruptedException ex) {
						System.out.println("S1E reveillé!");
					}
					if (l == 1) {
						System.out.print("A");
						s3.response(null, null);
					} else {
						System.out.print("D");
						s5.call(new Long(2), null);
					}
				}
			};

			LinkedList<ServiceCallOrResponse> s3Initiated = new LinkedList<ServiceCallOrResponse>();
			s3Initiated.add(ServiceCallOrResponse.createServiceCallOrResponse(s4, true));
			s3.new Listener(p2, s3Initiated) {
				@SuppressWarnings("unchecked")
				public void evaluate(Long params, Transportable dm) {
					System.out.print("B");
					s4.call(null, null);
				}
			};
			
			LinkedList<ServiceCallOrResponse> s4Initiated = new LinkedList<ServiceCallOrResponse>();
			s4.new Executer(p3, s4Initiated) {
				public void evaluate(Long params, Message dm) {
					try {
						Thread.sleep(2000);
					} catch (InterruptedException ex) {
						System.out.println("S4E reveillé!");
					}
					System.out.print("C");
				}
			};
			
			LinkedList<ServiceCallOrResponse> s5Initiated = new LinkedList<ServiceCallOrResponse>();
			s5Initiated.add(ServiceCallOrResponse.createServiceCallOrResponse(s6, false));			
			s5.new Executer(p4, s5Initiated) {
				@SuppressWarnings("unchecked")
				public void evaluate(Long params, Message dm) {
					long l = params.longValue();

					if (l == 2) {
						System.out.print("E");
						s6.response(null, null);
					} else
						System.out.print("J");
				}
			};

			LinkedList<ServiceCallOrResponse> s8Initiated = new LinkedList<ServiceCallOrResponse>();
			s8Initiated.add(ServiceCallOrResponse.createServiceCallOrResponse(s7, true));
			s8.new Listener(p4, s8Initiated) {
				@SuppressWarnings("unchecked")
				public void evaluate(Long params, Transportable dm) {
					System.out.print("G");
					s7.call(null, null);
				}
			};

			LinkedList<ServiceCallOrResponse> s6Initiated = new LinkedList<ServiceCallOrResponse>();
			s6Initiated.add(ServiceCallOrResponse.createServiceCallOrResponse(s8, false));			
			s6.new Listener(p5, s6Initiated) {
				@SuppressWarnings("unchecked")
				public void evaluate(Long params, Transportable dm) {
					System.out.print("F");
					s8.response(null, null);
				}
			};

			LinkedList<ServiceCallOrResponse> s7Initiated = new LinkedList<ServiceCallOrResponse>();
			s7.new Executer(p6, s7Initiated) {
				public void evaluate(Long params, Message dm) {
					try {
						Thread.sleep(2000);
					} catch (InterruptedException ex) {
						System.out.println("S7E reveillé!");
					}
					System.out.print("H");
				}
			};

			LinkedList<ServiceCallOrResponse> s2Initiated = new LinkedList<ServiceCallOrResponse>();
			s2Initiated.add(ServiceCallOrResponse.createServiceCallOrResponse(s5, true));
			s2.new Executer(p7, s2Initiated) {
				@SuppressWarnings("unchecked")
				public void evaluate(Long params, Message dm) {
					System.out.print("I");
					s5.call(new Long(5), null);
				}
			};

			// Some service and some protocol
			s10 = new Service("S10", this);
			ProtocolModule p10 = new ProtocolModule("P10", this);

			LinkedList<ServiceCallOrResponse> s10Initiated = new LinkedList<ServiceCallOrResponse>();
			s10Initiated.add(ServiceCallOrResponse.createServiceCallOrResponse(s10, true));
			s10.new Executer(p10, s10Initiated) {
				@SuppressWarnings("unchecked")
				public void evaluate(Long params, Message dm) {
					long value = params.longValue();

					System.out.print(params);

					if (value != 3 && value != 6 && value != 9)
						s10.call(new Long(value + 1), null);
					if (value == 1)
						s10.call(new Long(4), null);
				}
			};			
		}		
	}

	@SuppressWarnings("unchecked")
	public static void main(String args[]) {
		try {

			if ((args.length != 1) || (Integer.parseInt(args[0]) < 1)
					|| (Integer.parseInt(args[0]) > 5)) {
				System.err.println("Usage:: main <concurrency_manager>");
				System.err
						.println("        concurrency_manager = 1  ====> SequentialManager");
				System.err
						.println("        concurrency_manager = 2  ====> SimpleModuleOrderManager");
				System.err
						.println("        concurrency_manager = 3  ====> BoundModuleOrderManager");
				System.err
						.println("        concurrency_manager = 4  ====> RouteModuleOrderManager");
				System.exit(1);
			}

			int managerID = Integer.parseInt(args[0]);

			// Initialize myself and processes
			PID myself;

			myself = new PID(InetAddress.getLocalHost(), 2765, 0);
			TLinkedList processes = new TLinkedList();
			processes.addLast(myself);

			ConcurrencyManager manager;
			switch (managerID) {
			case 1:
				manager = new SequentialManager();
				break;
			case 2:
				manager = new SimpleModuleOrderManager(3);
				break;
			case 3:
				manager = new BoundModuleOrderManager(3);
				break;
			case 4:
				manager = new RouteModuleOrderManager(3);
				break;
			default:
				manager = new SequentialManager();
				break;
			}
			
			TestStack stack = new TestStack(myself, processes, manager);

		
			stack.init();

			// Three computation
			long c1 = stack.s1.externalCall(new Long(1), null);
			Thread.sleep(100);
			long c2 = stack.s1.externalCall(new Long(2), null);
			Thread.sleep(100);
			long c3 = stack.s2.externalCall(null, null);

			stack.getScheduler().waitEnd(c1);
			stack.getScheduler().waitEnd(c2);
			stack.getScheduler().waitEnd(c3);

			System.out.println();

			// Test EXTENDED CAUSAL ORDER

			stack.s10.externalCall(new Long(1), null);
			Thread.sleep(500);
			stack.s10.externalCall(new Long(7), null);

			Thread.sleep(2000);
			System.out.println();

			// Test SAMOATIMER
			final long timeStart = System.currentTimeMillis();
			for (int i = 1000; i > 0; i--) {
				final int timeWait = 1000 + 1 * i;
				AtomicTask cs1 = new AtomicTask() {
					public void execute() {
						long finish = System.currentTimeMillis();
						errorTotal = errorTotal
								+ Math.abs(timeWait - finish + timeStart);
					}
				};
				stack.getScheduler().schedule(cs1, false, timeWait);
			}
			Thread.sleep(15000);

			System.out.println("Timer Error Mean: " + errorTotal / 1000);

			System.out.println();
			System.out.println();
			System.out.println("TEST FINISHED");
			System.out.println();

			// RESULT SHOULD BE
			// 1: ABCDEFGHIJ, 2: IABCDEFGHJ, 3: IABDCEFGJH, 4 and 5: IABDEFGJCH
			// 124356789 (if corrected extended causal order)
			// Timer Error Mean: + errorMean
			stack.close();
		} catch (UnknownHostException e) {
			throw new RuntimeException("IP address for local host not found!");
		} catch (AlreadyBoundServiceException absex) {
			System.out.println("Exception!");
		} catch (InterruptedException iex) {
			System.out.println("One computation was interrupted!!");
		} catch (AlreadyExistingServiceException aesex) {
			System.out.println("Exception!");
		} catch (AlreadyExistingProtocolModuleException aesex) {
			System.out.println("Exception!");
		}
	}
}
