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
package seqSamoa.test.replaceStack;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;

import org.jdom.Element;

import seqSamoa.BoundModuleOrderManager;
import seqSamoa.Callback;
import seqSamoa.ConcurrencyManager;
import seqSamoa.RouteModuleOrderManager;
import seqSamoa.SamoaFlowControl;
import seqSamoa.SamoaScheduler;
import seqSamoa.SequentialManager;
import seqSamoa.SimpleModuleOrderManager;
import seqSamoa.api.ApiSamoaDynAbcastWithReplaceableProtocolStack;
import seqSamoa.exceptions.AlreadyBoundServiceException;
import seqSamoa.services.abcast.DynAbcastResponseParameters;
import seqSamoa.services.gms.ManageViewResponseParameters;
import seqSamoa.services.replacement.ReplaceProtocolResponseParameters;
import uka.transport.Transportable;
import framework.PID;
import framework.libraries.serialization.TElement;
import framework.libraries.serialization.TGLinkedList;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TLinkedList;
import framework.libraries.serialization.TLong;
import framework.libraries.serialization.TString;

// Test the Group Membership Protocols
public class ReplaceStackMain implements Callback {
	private static long start = 0;

	private static boolean hasLeader = false;

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
		System.out.println("|                    DYN_STACK                  |");
		System.out.println("+-----------------------------------------------+");
		System.out.println("LOCALHOST.ip   = " + myself.ip);
		System.out.println("LOCALHOST.port = " + port);
		System.out.println("LOCALHOST.inc  = " + inc);

		// 3. Get processes
		TLinkedList processes = new TLinkedList();

		System.out.println("\nEnter a list of processes to broadcast?");
		line = readLine();

		if (line.equals("y")) {
			do {
				PID pid;

				System.out.println("PID to add.");
				pid = readPID();
				processes.addLast(pid);
				System.out.println("Added process: " + pid.toString());
				System.out
						.print("Press 'y' to add more. Press ENTER if you are done: ");
				line = readLine();
			} while (!line.equals(""));
		} else if (line.equals("OR")) {
			PID pid;

			processes.clear();
			pid = readPID("lsepc32.epfl.ch", "2765", "0");
			processes.addLast(pid);
			pid = readPID("lsepc32.epfl.ch", "2766", "0");
			processes.addLast(pid);
			pid = readPID("lsepc32.epfl.ch", "2777", "0");
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
		ApiSamoaDynAbcastWithReplaceableProtocolStack stack = new ApiSamoaDynAbcastWithReplaceableProtocolStack(
				myself, processes, new SamoaScheduler(manager),
				new SamoaFlowControl(100), new ReplaceStackMain(), null);
		try {
			stack.init();
		} catch (AlreadyBoundServiceException abse) {
			System.err.println("Can load the stack!");
			System.exit(1);
		}

		// Printing user interface and managing the interaction with Protocols
		while (open) {
			System.out.println("+------------------------------------+");
			System.out.println("|             SAMBA TEST             |");
			System.out.println("+------------------------------------+");
			System.out.println("|              DYN_STACK             |");
			System.out.println("|------------------------------------|");
			System.out.println("LOCALHOST.ip          = " + myself.ip);
			System.out.println("LOCALHOST.port        = " + myself.port);
			System.out.println("LOCALHOST.incarnation = " + myself.incarnation);
			System.out.println("Process Group = " + processes);
			System.out.println("Reconfiguration Status: "+stack.getReconfigurationStatus());
			System.out.println("|                                    |");
			System.out.println("+------------------------------------+");
			System.out.println("|                                    |");
			System.out.println("|  1...........................FLOOD |");
			System.out.println("|  2............................JOIN |");
			System.out.println("|  3..........................REMOVE |");
			System.out.println("|  4................CHANGE CONSENSUS |");
			System.out.println("|  5...................CHANGE ABCAST |");
			System.out.println("|  6...CHANGE RECONFIGURATION STATUS |");
			System.out.println("|  7...........................PRINT |");
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
				System.out.println("PID to add.");
				PID thePid = readPID();
				System.out.println("Adding process: " + thePid);

				stack.sendJoin(thePid);
				break;
			case 3:
				System.out.println("PID to remove.");
				PID thePid2 = readPID();
				System.out.println("Removing process: " + thePid2);

				stack.sendRemove(thePid2);
				break;
			case 4: {
				System.out.println("Enter the name of the consensus class: ");
				String nameConsensus = readLine();
				int type = 4;
				while ((type < 1) || (type > 3)) {
					System.out
							.println("Enter the type of the consensus protocol (1=CT, 2=MR, 3=Paxos): ");
					type = Integer.parseInt(readLine());
				}
				TGLinkedList<TElement> newFeatures = new TGLinkedList<TElement>();

				// If the type is paxos, we have to pass the new service leader and its protocol
				if ((type == 3) && (!hasLeader)) {
					//The Leader service
					Element service = new Element("Service");
					Element serviceName = new Element("Name");
					serviceName.setText("leader");
					service.addContent(serviceName);

					// The simple class name of the protocol  
					Element sClassName = new Element("Name");
					sClassName.setText("Leader");
					// The package name of the protocol
					Element sPackageName = new Element("Package");
					sPackageName.setText("seqSamoa.services.fd");

					// Both form the complete class name of the protocol
					Element sImplementingClass = new Element("Class");
					sImplementingClass.addContent(sClassName);
					sImplementingClass.addContent(sPackageName);
					service.addContent(sImplementingClass);
					Element sIsProvided = new Element("ServiceProvided");
					sIsProvided.setText("true");
					service.addContent(sIsProvided);
					newFeatures.add(new TElement(service));

					// The leader protocol
					Element leader = new Element("Protocol");
					Element leaderName = new Element("Name");
					leaderName.setText(nameConsensus + "leader");
					leader.addContent(leaderName);
					// The simple class name of the protocol  
					Element lClassName = new Element("Name");
					lClassName.setText("ProtocolOmega");
					// The package name of the protocol
					Element lPackageName = new Element("Package");
					lPackageName.setText("seqSamoa.protocols.fd");

					// Both form the complete class name of the protocol
					Element lImplementingClass = new Element("Class");
					lImplementingClass.addContent(lClassName);
					lImplementingClass.addContent(lPackageName);
					leader.addContent(lImplementingClass);

					// The parameters
					Element lParameters = new Element("Parameters");
					leader.addContent(lParameters);

					// The provided services
					Element lProvidedServices = new Element("ProvidedServices");
					Element lProvidedService = new Element("ProvidedService");
					Element lProvidedServiceName = new Element("Name");
					lProvidedServiceName.setText("leader");
					lProvidedService.addContent(lProvidedServiceName);
					lProvidedServices.addContent(lProvidedService);
					leader.addContent(lProvidedServices);

					// The required services
					Element lRequiredServices = new Element("RequiredServices");
					Element lRequiredService1 = new Element("RequiredService");
					Element lRequiredServiceName1 = new Element("Name");
					lRequiredServiceName1.setText("fd");
					lRequiredService1.addContent(lRequiredServiceName1);
					lRequiredServices.addContent(lRequiredService1);
					leader.addContent(lRequiredServices);
					newFeatures.add(new TElement(leader));

					hasLeader = true;
				}

				// Construct the Element for the new Protocol
				Element element = new Element("Protocol");
				Element protName = new Element("Name");
				protName.setText(nameConsensus);
				element.addContent(protName);

				// The simple class name of the protocol  
				Element className = new Element("Name");
				if (type == 1)
					className.setText("ProtocolConsensus");
				else if (type == 2)
					className.setText("ProtocolConsensusMR");
				else
					className.setText("ProtocolConsensusPaxos");
				// The package name of the protocol
				Element packageName = new Element("Package");
				packageName.setText("seqSamoa.protocols.consensus");

				// Both form the complete class name of the protocol
				Element implementingClass = new Element("Class");
				implementingClass.addContent(className);
				implementingClass.addContent(packageName);
				element.addContent(implementingClass);

				// The parameters
				Element parameters = new Element("Parameters");
				element.addContent(parameters);

				// The provided services
				Element providedServices = new Element("ProvidedServices");
				Element providedService = new Element("ProvidedService");
				Element providedServiceName = new Element("Name");
				providedServiceName.setText("consensus");
				providedService.addContent(providedServiceName);
				providedServices.addContent(providedService);
				element.addContent(providedServices);

				// The required services
				Element requiredServices = new Element("RequiredServices");
				Element requiredService1 = new Element("RequiredService");
				Element requiredServiceName1 = new Element("Name");
				if (type < 3)
					requiredServiceName1.setText("fd");
				else
					requiredServiceName1.setText("leader");
				requiredService1.addContent(requiredServiceName1);
				requiredServices.addContent(requiredService1);
				Element requiredService2 = new Element("RequiredService");
				Element requiredServiceName2 = new Element("Name");
				requiredServiceName2.setText("rpt2pt");
				requiredService2.addContent(requiredServiceName2);
				requiredServices.addContent(requiredService2);
				element.addContent(requiredServices);

				newFeatures.add(new TElement(element));
				stack.sendReplaceConsensus(new TString(nameConsensus),
						newFeatures);

				break;
			}
			case 5: {
				System.out.println("Enter the name of the abcast class: ");
				String nameAbcast = readLine();
				TGLinkedList<TElement> newFeatures = new TGLinkedList<TElement>();

				// Construct the Element for the new Protocol
				Element element = new Element("Protocol");
				Element protName = new Element("Name");
				protName.setText(nameAbcast);
				element.addContent(protName);

				// The simple class name of the protocol  
				Element className = new Element("Name");
				className.setText("ProtocolDynAbcast");
				// The package name of the protocol
				Element packageName = new Element("Package");
				packageName.setText("seqSamoa.protocols.abcast");

				// Both form the complete class name of the protocol
				Element implementingClass = new Element("Class");
				implementingClass.addContent(className);
				implementingClass.addContent(packageName);
				element.addContent(implementingClass);

				// The parameters
				Element parameters = new Element("Parameters");
				element.addContent(parameters);

				// The provided services
				Element providedServices = new Element("ProvidedServices");
				Element providedService = new Element("ProvidedService");
				Element providedServiceName = new Element("Name");
				providedServiceName.setText("dynAbcast");
				providedService.addContent(providedServiceName);
				providedServices.addContent(providedService);
				element.addContent(providedServices);

				// The required services
				Element requiredServices = new Element("RequiredServices");
				Element requiredService1 = new Element("RequiredService");
				Element requiredServiceName1 = new Element("Name");
				requiredServiceName1.setText("consensus");
				requiredService1.addContent(requiredServiceName1);
				requiredServices.addContent(requiredService1);
				Element requiredService2 = new Element("RequiredService");
				Element requiredServiceName2 = new Element("Name");
				requiredServiceName2.setText("rpt2pt");
				requiredService2.addContent(requiredServiceName2);
				requiredServices.addContent(requiredService2);
				element.addContent(requiredServices);

				newFeatures.add(new TElement(element));
				stack.sendReplaceAbcast(new TString(nameAbcast), newFeatures);

				break;
			}
			case 6:
				boolean status = stack.getReconfigurationStatus();
				stack.setReconfigurationStatus(!status);
				break;
			case 7:
				System.out.println();
				System.out.println("#############################");
				System.out.println("#     STACK STRUCTURE       #");
				System.out.println("#############################");
				System.out.println(stack);
				System.out.println();
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

	@SuppressWarnings("unchecked")
	public void serviceCallback(Object infos, Transportable message) {
		if (infos instanceof DynAbcastResponseParameters) {
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
		} else if (infos instanceof ManageViewResponseParameters) {
			ManageViewResponseParameters gmpInfos = (ManageViewResponseParameters) infos;

			System.out.println("****NewView: " + gmpInfos.viewID);

			Iterator it = gmpInfos.view.iterator();
			PID pid;
			while (it.hasNext()) {
				pid = (PID) it.next();
				System.out.println("\t" + pid.toString());
			}
		} else if (infos instanceof ReplaceProtocolResponseParameters) {
			ReplaceProtocolResponseParameters repInfos = (ReplaceProtocolResponseParameters) infos;

			System.out.println("Service <" + repInfos.s.getName()
					+ "> has a new provider: " + repInfos.p.getName());
		}
	}
}
