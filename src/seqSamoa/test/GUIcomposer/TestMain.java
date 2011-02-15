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
package seqSamoa.test.GUIcomposer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;

import seqSamoa.Callback;
import seqSamoa.ProtocolStack;
import seqSamoa.services.abcast.DynAbcastCallParameters;
import seqSamoa.services.abcast.DynAbcastResponseParameters;
import seqSamoa.services.gms.ManageViewCallParameters;
import seqSamoa.services.gms.ManageViewResponseParameters;
import uka.transport.Transportable;
import framework.Constants;
import framework.PID;
import framework.libraries.serialization.TBoolean;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TLinkedList;
import framework.libraries.serialization.TLong;
import framework.libraries.serialization.TString;

public class TestMain implements Callback {

    private static long start = 0;

    // Buffer for the standard input
    private static BufferedReader in = new BufferedReader(
            new InputStreamReader(System.in));

    static private ProtocolStack stack;

    public static void main(String[] args) {
        boolean open = true;
        String line;

        int port = Integer.parseInt(args[0]);
        int inc = Integer.parseInt(args[1]);
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
            if (line.equals("OR")) {
                PID pid;

                processes.clear();
                pid = readPID("127.0.0.1", "2765", "0");
                processes.addLast(pid);
                pid = readPID("127.0.0.1", "2764", "0");
                processes.addLast(pid);
                pid = readPID("127.0.0.1", "2763", "0");
                processes.addLast(pid);
            }

            System.out.println("There are " + processes.size()
                    + " processes registered");
        }
        try {
            stack = new ProtocolStack(myself, processes, new TestMain(), new String(), new String(), "Gmp.xml");
            stack.init();
        } catch (Exception ex) {
        	ex.printStackTrace();
            System.err.println(ex.getMessage());
            System.exit(1);
        }

        // Printing user interface and managing the interaction with Protocols
        while (open) {
            System.out.println("+------------------------------------+");
            System.out.println("|             SAMBA TEST             |");
            System.out.println("+------------------------------------+");
            System.out.println("|             GMP_STACK              |");
            System.out.println("|------------------------------------|");
            System.out.println("LOCALHOST.ip          = " + myself.ip);
            System.out.println("LOCALHOST.port        = " + port);
            System.out.println("LOCALHOST.incarnation = " + inc);
            System.out.println("Process Group = " + processes);
            System.out.println("|                                    |");
            System.out.println("+------------------------------------+");
            System.out.println("|                                    |");
            System.out.println("|  1...........................FLOOD |");
            System.out.println("|  2............................JOIN |");
            System.out.println("|  3..........................REMOVE |");
			System.out.println("|  4...........................PRINT |");
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
                        stack.serviceCall("abcast",
                                new DynAbcastCallParameters(new TInteger(
                                        Constants.AM), null), toSend);
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

                stack.serviceCall("gmp",
                                new ManageViewCallParameters(thePid,
                                        new TBoolean(true)), null);
                break;
            case 3:
                System.out.println("PID to remove.");
                PID thePid2 = readPID();
                System.out.println("Removing process: " + thePid2);

                stack.serviceCall("gmp",
                        new ManageViewCallParameters(thePid2, new TBoolean(
                                false)), null);
                break;
			case 4:
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
	    } else if (infos instanceof ManageViewResponseParameters){
	    	ManageViewResponseParameters gmpInfos = (ManageViewResponseParameters) infos;
	    	
	        System.out.println("****NewView: " + gmpInfos.viewID);

	        Iterator it = gmpInfos.view.iterator();
	        PID pid;
	        while (it.hasNext()) {
	            pid = (PID) it.next();
	            System.out.println("\t" + pid.toString());
	        }
	    }
	}
}
