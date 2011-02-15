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

import java.util.LinkedList;

import framework.libraries.FlowControl;

/**
 * The <CODE>SamoaFlowControl</CODE> implements a mechanism to control the
 * number of "messages" in the stack and so to avoid memory overflows
 * due to overload.
 */
public class SamoaFlowControl implements FlowControl {

    protected int[] threshold;

    protected int[] used;

    protected boolean[] blocked;

    protected int nextKey = 0;

    protected LinkedList<Integer> freeKeys;

    /**
     * Constructor
     * 
     * @param keys
     *            number of resources where we need to have a flow control
     */
    public SamoaFlowControl(int keys) {
        threshold = new int[keys];
        used = new int[keys];
        blocked = new boolean[keys];

        freeKeys = new LinkedList<Integer>();
    }

    /**
     * Block the "key" resource.
     * 
     * @param key
     *            the "key" of the resource
     */
    public void block(int key) {
        blocked[key] = true;
    }

    /**
     * Release the "key" resource.
     * 
     * @param key
     *            the "key" of the resource
     */
    synchronized public void release(int key) {
        if (blocked[key])
            notifyAll();
        blocked[key] = false;
    }

    /**
     * Wait until there is an amount available for each registered resources
     */
    public synchronized void enter() {
        while (blocked()) {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new RuntimeException("Broken wait()!!!");
            }
        }
    }

    /**
     * Get a key number for a resource
     * 
     * @return a free key
     */
    public int getFreshKey() {
        int freshKey;

        if (!freeKeys.isEmpty())
            freshKey = freeKeys.removeFirst();
        else if (nextKey < used.length)
            freshKey = nextKey++;
        else
            throw new RuntimeException("Key overflow!!!");

        threshold[freshKey] = 1;
        used[freshKey] = 0;
        blocked[freshKey] = false;

        return freshKey;
    }

    /**
     * Release a key number for a resource
     */
    public void releaseKey(int key) {
        threshold[key] = 1;
        used[key] = 0;
        blocked[key] = false;

        freeKeys.add(key);
    }

    // Is there any resources in each protocol
    private boolean blocked() {
        boolean block = false;
        for (int i = 0; i < nextKey && !block; i++) {
            block = block || (used[i] > threshold[i]) || blocked[i];
        }
        return block;
    }
}
