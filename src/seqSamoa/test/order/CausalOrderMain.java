package seqSamoa.test.order;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;

import seqSamoa.BoundModuleOrderManager;
import seqSamoa.Callback;
import seqSamoa.ConcurrencyManager;
import seqSamoa.Message;
import seqSamoa.ProtocolStack;
import seqSamoa.RouteModuleOrderManager;
import seqSamoa.SamoaFlowControl;
import seqSamoa.SamoaScheduler;
import seqSamoa.SequentialManager;
import seqSamoa.Service;
import seqSamoa.ServiceCallOrResponse;
import seqSamoa.SimpleModuleOrderManager;
import seqSamoa.exceptions.AlreadyBoundServiceException;
import seqSamoa.exceptions.AlreadyExistingProtocolModuleException;
import seqSamoa.exceptions.AlreadyExistingServiceException;
import seqSamoa.protocols.order.ProtocolCausalOrder;
import seqSamoa.services.order.CausalOrder;
import seqSamoa.services.order.CausalOrderCallParameters;
import seqSamoa.services.order.CausalOrderResponseParameters;
import uka.transport.Transportable;
import framework.PID;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TLinkedList;
import framework.libraries.serialization.TList;
import framework.libraries.serialization.TLong;
import framework.libraries.serialization.TString;

public class CausalOrderMain extends ProtocolStack {
	private static long start = 0;

	// Buffer for the standard input
	private static BufferedReader in = new BufferedReader(
			new InputStreamReader(System.in));

	// Service
	CausalOrder causalOrder;

	// Listener
	@SuppressWarnings("unchecked")
	Service.Listener causalOrderListener;

	public CausalOrderMain(PID myself, TList processes,
			SamoaScheduler scheduler, SamoaFlowControl fc, Callback callback,
			String logFile) {

		super(myself, processes, scheduler, fc, callback, logFile, new String(
				"groupcomm"), true, true);

		try {
			causalOrder = new CausalOrder("causalOrder", this);
		} catch (AlreadyExistingServiceException ex) {
			throw new RuntimeException("This could not happen!");
		}

		causalOrderListener = causalOrder.new Listener(this, new LinkedList<ServiceCallOrResponse>()) {
			public void evaluate(CausalOrderResponseParameters infos,
					Transportable message) {
				TLinkedList msg = (TLinkedList) message;

				if (msg.getFirst() instanceof TLong)
					start = ((TLong) msg.removeFirst()).longValue();
				System.out.println("CausalOrderDeliver: "
						+ msg.removeFirst()
						+ " TEMPS ECOULE: "
						+ (System.currentTimeMillis() - start)
						+ " LATENCE MESSAGE: "
						+ (System.currentTimeMillis() - ((TLong) msg
								.removeFirst()).longValue()));
			}
		};

		try {
			new ProtocolCausalOrder(new String("CausalOrder"), this,
					causalOrder, rpt2pt);
		} catch (AlreadyExistingProtocolModuleException aepm) {
			throw new RuntimeException("This could not happen!");
		}
	}

	public void call(Transportable message, TList dests) {
		CausalOrderCallParameters params = new CausalOrderCallParameters(dests);
		fc.enter();

		long cid = causalOrder.externalCall(params, new Message(message, causalOrderListener));
		this.scheduler.waitEnd(cid);
	}

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
		System.out.println("|                    GMP_STACK                  |");
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
			} while (!line.equals("") && !line.equals("OR"));
		} else if (line.equals("OR")) {
			PID pid;

			processes.clear();
			pid = readPID("laptop", "2765", "0");
			processes.addLast(pid);
			pid = readPID("laptop", "2766", "0");
			processes.addLast(pid);
			pid = readPID("laptop", "2777", "0");
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
		CausalOrderMain stack = new CausalOrderMain(myself, processes,
				new SamoaScheduler(manager), new SamoaFlowControl(100), null,
				null);
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
			System.out.println("|        CAUSAL_ORDER_PROTOCOL       |");
			System.out.println("|------------------------------------|");
			System.out.println("LOCALHOST.ip          = " + myself.ip);
			System.out.println("LOCALHOST.port        = " + port);
			System.out.println("LOCALHOST.incarnation = " + inc);
			System.out.println("Process Group = " + processes);
			System.out.println("|                                    |");
			System.out.println("+------------------------------------+");
			System.out.println("|                                    |");
			System.out.println("|  1...........................FLOOD |");
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
				TLinkedList dests = new TLinkedList();
				do {
					PID pid;

					System.out.println("PID to send m.");
					pid = readPID();
					dests.addLast(pid);
					System.out.println("Added process: " + pid.toString());
					System.out
							.print("Press 'y' to add more. Press ENTER if you are done: ");
					line = readLine();
				} while (!line.equals("") && !line.equals("OR"));
				if (line.equals("OR")) {
					PID pid;

					dests.clear();
					pid = readPID("laptop", "2765", "0");
					dests.addLast(pid);
					pid = readPID("laptop", "2766", "0");
					dests.addLast(pid);
					pid = readPID("laptop", "2777", "0");
					dests.addLast(pid);
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

					System.out.println("CausalOrderSend   : FLOOD MESSAGE "
							+ fl);
					try {
						stack.call(toSend, (TList) dests.clone());
						if (s > 0)
							Thread.sleep(s);
					} catch (InterruptedException ex) {
						System.err.println("Sleep Interrupted");
					}
				}
				break;
			case 0:
				System.out.println("APPLICATION CLOSING.");

				open = false;
				stack.finalize();
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
}
