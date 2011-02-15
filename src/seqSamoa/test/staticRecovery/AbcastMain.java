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

import seqSamoa.BoundModuleOrderManager;
import seqSamoa.Callback;
import seqSamoa.ConcurrencyManager;
import seqSamoa.RouteModuleOrderManager;
import seqSamoa.SamoaFlowControl;
import seqSamoa.SamoaScheduler;
import seqSamoa.SequentialManager;
import seqSamoa.SimpleModuleOrderManager;
import seqSamoa.exceptions.AlreadyBoundServiceException;
import seqSamoa.api.ApiSamoaCrashRecoveryAbcastStack;
import seqSamoa.services.abcast.AbcastResponseParameters;
import seqSamoa.services.commit.UpdateStateCallParameters;
import uka.transport.Transportable;
import framework.PID;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TLinkedList;
import framework.libraries.serialization.TLong;
import framework.libraries.serialization.TString;

// Test the Group Membership Protocols
public class AbcastMain implements Callback {

	private static long start = 0;

	private static boolean debug = false;

	private static long applState = 0;

	// Buffer for the standard input
	private static BufferedReader in = new BufferedReader(
			new InputStreamReader(System.in));

	public static void main(String args[]) {
		boolean open = true;
		String line;

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
			throw new RuntimeException("IP address for local host not found!");
		}

		// 2. Welcome
		System.out.println("+-----------------------------------------------+");
		System.out.println("|                   SAMBA TEST                  |");
		System.out.println("+-----------------------------------------------+");
		System.out.println("|              STATIC_RECOVERY_STACK            |");
		System.out.println("+-----------------------------------------------+");
		System.out.println("LOCALHOST.ip   = " + myself.ip);
		System.out.println("LOCALHOST.port = " + port);
		System.out.println("LOCALHOST.inc  = " + inc);

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
		} while (!line.equals("") && !line.equals("OR") && !line.equals("KC"));
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

		System.out.println("There are " + processes.size()
				+ " processes registered");

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
		ApiSamoaCrashRecoveryAbcastStack stack = new ApiSamoaCrashRecoveryAbcastStack(
				myself, processes, new SamoaScheduler(manager),
				new SamoaFlowControl(100), new AbcastMain(), null);
		try {
			((ApiSamoaCrashRecoveryAbcastStack) stack).init();
		} catch (AlreadyBoundServiceException abse) {
			System.err.println("Can load the stack!");
			System.exit(1);
		}

		System.out.println(stack);

		// Printing user interface and managing the interaction with Protocols
		while (open) {
			System.out.println("+------------------------------------+");
			System.out.println("|             SAMBA TEST             |");
			System.out.println("+------------------------------------+");
			System.out.println("|        STATIC_RECOVERY_STACK       |");
			System.out.println("|------------------------------------|");
			System.out.println("LOCALHOST.ip          = " + myself.ip);
			System.out.println("LOCALHOST.port        = " + port);
			System.out.println("LOCALHOST.incarnation = " + inc);
			System.out.println("Process Group = " + processes);
			System.out.println("|                                    |");
			System.out.println("+------------------------------------+");
			System.out.println("|                                    |");
			System.out.println("|  1...........................FLOOD |");
			if (debug)
				System.out.println("|  2........................NO DEBUG |");
			else
				System.out.println("|  2...........................DEBUG |");
			System.out.println("|  3......................CHECKPOINT |");
			System.out.println("|  4............................DUMP |");
			System.out.println("|                                    |");
			System.out.println("|  0............................QUIT |");
			System.out.println("|                                    |");
			System.out.println("+------------------------------------+");
			System.out.println();
			int c = Integer.parseInt(readLine());
			switch (c) {
			case 1:
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
						toSend.addLast(new TLong(System.currentTimeMillis()));
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
				break;
			case 2:
				debug = !debug;

				stack.sendDebug(debug);
				break;
			case 3:
				stack.checkpoint(new TLong(applState));
				break;
			case 4:
				stack.sendDump(System.out);
				break;
			case 0:
				System.out.println("APPLICATION CLOSING.");

				stack.finalize();
				open = false;
			}
		}

		System.out.println("Ending application ...");
		System.exit(1);
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
		} else if (infos instanceof UpdateStateCallParameters) {
			UpdateStateCallParameters upstateInfos = (UpdateStateCallParameters) infos;

			applState = ((TLong) upstateInfos.state).longValue();
			System.out
					.println("**Application update!! New value: " + applState);

		}
	}
}
