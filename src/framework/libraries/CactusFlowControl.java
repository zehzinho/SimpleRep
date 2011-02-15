/**
*  Fortika - Robust Group Communication
*  Copyright (C) 2002-2006  Sergio Mena de la Cruz (EPFL) (sergio.mena@epfl.ch)
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
package framework.libraries;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class that implements flow control for Cactus. See interface 
 * <i>FlowControl</i> for more details.
 * 
 * @author smenadel
 */
public class CactusFlowControl implements FlowControl {
	// Logging
	private static final Logger logger =
		Logger.getLogger(CactusFlowControl.class.getName());

//	protected int[] threshold;
//	protected int[] used;
	protected boolean[] blocked;
    protected boolean[] used;
//	protected int nextKey = 0;

	public CactusFlowControl(int keys) {
//		threshold = new int[keys];
//		used = new int[keys];
		blocked = new boolean[keys];
        used = new boolean[keys];
        for(int i = 0; i < keys; i++) used[i] = false;
	}

    public synchronized int getFreshKey() {
        int i;
        for(i = 0; i < used.length && used[i]; i++); // Look for the first unused key

        if (i >= used.length) {
            System.err.println(
                "Cactus Flow Control: getFreshKey: key limit "
                    + i
                    + "reached");
            System.exit(1);
        }

//      threshold[nextKey] = 1;
//      used[nextKey] = 0;
            blocked[i] = false;
            used[i] = true;
            return i;        
    }

    public synchronized void releaseKey(int key){
            if(!used[key]) {
                System.err.println(
                "Cactus Flow Control: getFreshKey: trying to releaseKey an unused key"
                + key);
                System.exit(1);
            }

                used[key] = false;
                blocked[key] = false;
    }


//	public synchronized void alloc(int key, int amount) {
//		if (key < nextKey) {
//			used[key] += amount;
//		} else {
//			System.err.println(
//				"Cactus Flow Control: alloc: key " + key + "not yet provided");
//			System.exit(1);
//		}
//	}

//	public synchronized void free(int key, int amount) {
//		if (key < nextKey) {
//			used[key] -= amount;
//			if (used[key] < 0)
//				logger.log(
//					Level.FINE,
//					"CactusFlowControl : used < 0. Key: {0}",
//					new Integer(key));
//			if (threshold[key] >= used[key])
//				notifyAll();
//		} else {
//			System.err.println(
//				"Cactus Flow Control: free: key " + key + "not yet provided");
//			System.exit(1);
//		}
//	}

	public synchronized void block(int key) {
        if(!used[key]) {
            System.err.println(
            "CactusFlowControl: Trying to block an unused key!"
            + key);
            System.exit(1);
        }

		blocked[key] = true;
		logger.log(
			Level.FINE,
			"CactusFlowControl : Blocked. Key: {0}",
			new Integer(key));
	}

	public synchronized void release(int key) {
        if(!used[key]) {
            System.err.println(
            "CactusFlowControl: Trying to release an unused key!"
            + key);
            System.exit(1);
        }

		if (blocked[key])
			notifyAll();
		blocked[key] = false;
		logger.log(
			Level.FINE,
			"CactusFlowControl : Released. Key: {0}",
			new Integer(key));
	}

	public synchronized void enter() {
		while (blocked()) {
			try {
				wait();
			} catch (InterruptedException e) {
				System.err.println("Interrupted!!!!");
			}
		}
	}

//	public synchronized void setThreshold(int key, int t) {
//		if (key < nextKey) {
//			threshold[key] = t;
//            if (threshold[key] >= used[key])
//                notifyAll();
//		} else {
//			System.err.println(
//				"Cactus Flow Control: setThreshold: key "
//					+ key
//					+ "not yet provided");
//			System.exit(1);
//		}
//	}

//	public synchronized int getThreshold(int key) {
//		if (key < nextKey) {
//			return threshold[key];
//		} else {
//			System.err.println(
//				"Cactus Flow Control: getThreshold: key "
//					+ key
//					+ "not yet provided");
//			System.exit(1);
//			return 0;
//		}
//	}

//	public synchronized int used(int key) {
//		if (key < nextKey) {
//			return used[key];
//		} else {
//			System.err.println(
//				"Cactus Flow Control: used: key " + key + "not yet provided");
//			System.exit(1);
//			return 0;
//		}
//	}


	private synchronized boolean blocked() {
		boolean block = false;
		for (int i = 0; i < used.length && !block; i++) {
//            block = block || (used[i] > threshold[i]) || blocked[i];
            block = block ||  (used[i] && blocked[i]);
		}
		return block;
	}

	public synchronized void dump(OutputStream out) {
		PrintStream err = new PrintStream(out);
		err.println("========= Cactus Flow Control: dump =========");
		for (int i = 0; i < used.length; i++) {
			err.println(
				"Key #"
					+ i
//					+ ". Threshold: "
//					+ threshold[i]
					+ ".Used: "
					+ used[i]
					+ ".Blocked: "
					+ blocked[i]);
		}
	}

	//     public static void main(String args[]){
	// 	final CactusFlowControl cfc = new CactusFlowControl(12);

	// 	Thread t1 = new Thread(){
	// 	    public void run(){
	// 		while (true) {
	// 		    cfc.enter();
	// 		    cfc.alloc(3);
	// 		    System.out.println("1 Used : "+cfc.used());
	// 		}
	// 	    }
	// 	};

	// 	Thread t2 = new Thread(){
	// 	    public void run(){
	// 		while (true){
	// 		    cfc.free(2);
	// 		    System.out.println("2 Used : "+cfc.used());

	// 		    try {sleep(500);}
	// 		    catch (InterruptedException e){
	// 			e.printStackTrace();
	// 		    }
	// 		}
	// 	    }
	// 	};

	// 	cfc.setThreshold(6);
	// 	System.out.println("Threshold is : "+cfc.getThreshold());
	// 	t1.start();
	// 	t2.start();
	//     }
}
