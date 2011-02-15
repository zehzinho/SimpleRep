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
package seqSamoa.test.staticRecovery;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;

import seqSamoa.BoundModuleOrderManager;
import seqSamoa.Callback;
import seqSamoa.ConcurrencyManager;
import seqSamoa.RouteModuleOrderManager;
import seqSamoa.SamoaFlowControl;
import seqSamoa.SamoaScheduler;
import seqSamoa.SequentialManager;
import seqSamoa.SimpleModuleOrderManager;
import seqSamoa.api.ApiSamoaCrashRecoveryAbcastStackCommit;
import seqSamoa.exceptions.AlreadyBoundServiceException;
import seqSamoa.services.abcast.AbcastResponseParameters;
import seqSamoa.services.commit.NbCommitsCallParameters;
import uka.transport.Transportable;
import framework.PID;
import framework.libraries.BinaryStableStorage;
import framework.libraries.serialization.THashMap;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TLinkedList;
import framework.libraries.serialization.TLong;
import framework.libraries.serialization.TMap;
import framework.libraries.serialization.TString;

public class AbcastMainCommit implements Callback {
	private static long start = 0;

	static ApiSamoaCrashRecoveryAbcastStackCommit stack;

	private static long applState = 0;

	private static long nb_checks = 0;

	private static TMap checkpoints = null;

	private static BinaryStableStorage storage = null;

	// Buffer for the standard input
	private static BufferedReader in = new BufferedReader(
			new InputStreamReader(System.in));

	public static void main(String args[]) {
		try {
			if ((args.length != 3) || (Integer.parseInt(args[2]) < 1)
					|| (Integer.parseInt(args[2]) > 5)) {
				System.err
						.println("Usage:: main <num_port> <num_incarnation> <concurrency_manager>");
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

			int port = Integer.parseInt(args[0]);
			int inc = Integer.parseInt(args[1]);
			int managerID = Integer.parseInt(args[2]);
			PID myself;
			try {
				// Initialize myself
				myself = new PID(InetAddress.getLocalHost(), port, inc);
			} catch (UnknownHostException e) {
				throw new RuntimeException(
						"IP address for local host not found!");
			}

			// 1. Initialisation
			BufferedReader in = new BufferedReader(new InputStreamReader(
					System.in));
			String line;
			boolean debugMode = false;

			// 2. Welcome
			System.out
					.println("+-----------------------------------------------+");
			System.out
					.println("|                   CACTUS TEST                 |");
			System.out
					.println("+-----------------------------------------------+");
			System.out
					.println("|       Abcast layer test (Crash recovery with commit)      |");
			System.out
					.println("+-----------------------------------------------+");
			System.out
					.println("LOCALHOST.ip   = " + InetAddress.getLocalHost());
			System.out.println("LOCALHOST.port = " + port);
			System.out.println("LOCALHOST.inc = " + inc);

			// 3. Get processes
			TLinkedList processes = new TLinkedList();

			do {
				PID pid;

				System.out.println("PID to add.");
				pid = readPID();
				processes.addLast(pid);
				System.out.println("Added process: " + pid.toString());
				System.out
						.print("Press 'y' to add more. Press ENTER if you are done: ");
				line = readLine();
			} while (!line.equals("") && !line.equals("OR")
					&& !line.equals("KC"));
			if (line.equals("OR")) {
				PID pid;

				processes.clear();
				pid = readPID("lsepc32.epfl.ch", "2765", "0");
				processes.addLast(pid);
				pid = readPID("lsepc32.epfl.ch", "2766", "0");
				processes.addLast(pid);
				pid = readPID("lsepc32.epfl.ch", "2777", "0");
				processes.addLast(pid);
			} else if (line.equals("KC")) {
				PID pid;

				processes.clear();
				pid = readPID("lsec1.epfl.ch", "2765", "0");
				processes.addLast(pid);
				pid = readPID("lsec2.epfl.ch", "2765", "0");
				processes.addLast(pid);
				pid = readPID("lsec8.epfl.ch", "2765", "0");
				processes.addLast(pid);
			}

			System.out
					.println("\nDo you want the protocol to be UNIFORM (Y/n)?");
			line = in.readLine();
			boolean uniform = !(line.equals("n") || line.equals("N"));

			System.out.println("There are " + processes.size()
					+ " processes registered");

			// 4. Create and couple composite
			String path = "tmp/CRabcastCommit_"
					+ InetAddress.getLocalHost().getHostName() + "_" + port
					+ "_" + inc + ".application";
			storage = new BinaryStableStorage(path);
			Object temp = storage.retrieve(0, 0);
			if (temp == null) { // We are not recovering
				checkpoints = new THashMap();
				// each item = (nb_checkpoints, state)
				checkpoints.put(new TLong(0), new TLong(0));
				storage.store(0, 0, checkpoints, true);
			} else { // We are recovering
				checkpoints = (TMap) temp;
			}
			System.out.println("Creating Cactus composite...");

			ConcurrencyManager manager;
			switch (managerID) {
			case 1:
				manager = new SequentialManager();
				break;
			case 2:
				manager = new SimpleModuleOrderManager(2);
				break;
			case 3:
				manager = new BoundModuleOrderManager(2);
				break;
			case 4:
				manager = new RouteModuleOrderManager(2);
				break;
			default:
				manager = new SequentialManager();
				break;
			}

			stack = new ApiSamoaCrashRecoveryAbcastStackCommit(myself,
					processes, new SamoaScheduler(manager),
					new SamoaFlowControl(100), new AbcastMainCommit(), uniform,
					null);
			try {
				stack.init();
			} catch (AlreadyBoundServiceException abse) {
				System.err.println("Can load the stack!");
				System.exit(1);
			}

			// 5. Main loop
			line = "";
			System.out.println("\nType messages to be sent after the prompt.");
			help();

			while (!line.equals("exit") && !line.equals("kill")) {
				if (line.equals("dump")) {
					stack.sendDump(System.err);
				} else if (line.equals("help")) {
					help();
				} else if (line.equals("debug")) {
					debugMode = !debugMode;
					System.out.println("Debug mode: " + debugMode);
					stack.sendDebug(debugMode);
				} else if (line.equals("cp")) {
					// We need to pass a reference!!!
					Runnable r = new Runnable() {
						@SuppressWarnings("unchecked")
						public void run() {
							nb_checks++;
							TMap cp2 = new THashMap();
							Iterator it = checkpoints.keySet().iterator();
							while (it.hasNext()) {
								TLong i = (TLong) it.next();
								if (i.longValue() > nb_checks - 2)
									cp2.put(i, checkpoints.get(i));
							}
							checkpoints = cp2;
							checkpoints.put(new TLong(nb_checks), new TLong(
									applState));
							storage.store(0, 0, checkpoints, true);
						}
					};

					stack.commit(r);
				} else if (line.equals("flood")) {
					System.out.print("First number > ");
					int inf = (new Integer(readLine())).intValue();
					System.out.print("Last number > ");
					int sup = (new Integer(readLine())).intValue();
					System.out.print(" Message length (ENTER -> 0) > ");
					int l = 0;
					try {
						l = (new Integer(in.readLine())).intValue();
					} catch (Exception exc) {
					}
					System.out.print(" Sleep time (ENTER -> 0) > ");
					int s = 0;
					try {
						s = (new Integer(in.readLine())).intValue();
					} catch (Exception exc) {
					}
					for (int i = inf; i < sup; i++) {
						Integer fl = new Integer(i);
						TLinkedList toSend = new TLinkedList();

						if (i == inf)
							toSend
									.addLast(new TLong(System
											.currentTimeMillis()));
						toSend.addLast(new TString("FLOOD MESSAGE " + fl));
						toSend.addLast(new TLong(System.currentTimeMillis()));
						for (int j = 0; j < l / 16; j++)
							toSend.addLast(new TInteger(j));

						try {
							stack.abcastMessage(toSend);
							if (s > 0)
								Thread.sleep(s);
						} catch (InterruptedException ex) {
							System.err.println("Sleep Interrupted");
						}
					}
				} else if (!line.equals("")) {
					stack.abcastMessage(new TString(line));
				}
				System.out.print("> ");
				line = in.readLine();
			} // while
			// exiting
			if (line.equals("kill"))
				System.exit(1);

			stack.finalize();
			// bye
			try {
				Thread.sleep(1000);
			} catch (Exception e) {
			}
			System.exit(0);
		} catch (Exception ex) {
			System.err.println("CrashRecovery: TestAbcast: An error occured.");
			System.err.println(ex.toString());
			ex.printStackTrace();
			System.err.println("Application must close.");
			System.exit(1);
		}
	}

	private static void help() {
		System.out.println("\nType messages to be sent after the prompt.");
		System.out.println("  Available commands: ");
		System.out.println("  'help'   to show this menu");
		System.out.println("  'exit'  for a PERMANENT crash (logs deleted)");
		System.out
				.println("  'kill'  for a TEMPORARY crash (logs not deleted)");
		System.out.println("  'dump'  to dump the stack");
		System.out.println("  'debug' to enable/disable logging");
		System.out.println("  'cp' to make an Application Checkpoint");
		System.out.println("  'flood' to send several messages at full speed");
	}

	// Read a line form standard input
	static private String readLine() {
		String str = null;
		try {
			str = in.readLine();
		} catch (IOException e) {
			System.err.println("MicroTest : readLine : Erreur le lecture!");
		}
		if (str == null) {
			in = new BufferedReader(new InputStreamReader(System.in));
			return readLine();
		}
		return str;
	}

	// Ask user for a PID
	static private PID readPID() {
		String host = null;
		int port = 0;
		int incarnation = 0;
		String str = null;

		System.out.print("Host name  :");
		host = readLine();

		System.out.print("Port number: ");
		port = Integer.parseInt(readLine());
		System.out.print("Incarnation number (ENTER -> 0) :");
		str = readLine();
		if (!str.equals("")) {
			incarnation = Integer.parseInt(str);
		}
		PID p = null;
		try {
			p = new PID(InetAddress.getByName(host), port, incarnation);
		} catch (UnknownHostException e) {
			System.err.println("MicroTestFD : readPID : UnknownHostException");
		}
		return p;
	}

	// Return a PID created thanks to the parameters
	static private PID readPID(String host, String Port, String Incar) {
		int port = 0;
		int incarnation = 0;

		port = Integer.parseInt(Port);
		incarnation = Integer.parseInt(Incar);

		PID p = null;
		try {
			p = new PID(InetAddress.getByName(host), port, incarnation);
		} catch (UnknownHostException e) {
			System.err.println("MicroTestFD : readPID : UnknownHostException");
		}
		return p;
	}

	public void serviceCallback(Object infos, Transportable message) {
		if (infos instanceof AbcastResponseParameters) {
			applState++;
			TLinkedList msg = (TLinkedList) message;

			if (msg.getFirst() instanceof TLong)
				start = ((TLong) msg.removeFirst()).longValue();
			System.out.println("ADeliver: "
					+ msg.removeFirst()
					+ " TEMPS ECOULE: "
					+ (System.currentTimeMillis() - start)
					+ " LATENCE MESSAGE: "
					+ (System.currentTimeMillis() - ((TLong) msg.removeFirst())
							.longValue()));
		} else if (infos instanceof NbCommitsCallParameters) {
			NbCommitsCallParameters commitInfos = (NbCommitsCallParameters) infos;

			nb_checks = commitInfos.nbCommits.longValue();
			TLong newState = (TLong) checkpoints.get(new TLong(nb_checks));
			applState = newState.longValue();
			System.out.println("**Nb_commits event received. Value: "
					+ nb_checks + ". New application state: " + newState);
		}
	}
}
