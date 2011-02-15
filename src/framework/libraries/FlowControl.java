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

/**
* This interface defines the methods that protocols need to use for flow control.
* A protocol that can't keep up with its workload can <i>block</i> the flow control 
* to prevent the stack from crashing.
* 
* The flow control can be blocked with method <i>block</i> and released again with method 
* <i>release</i>. The caller of method <i>block</i> is <b>not</b> blocked. Rather, 
* when the flow control has been blocked, any attempt by the application's threads to enter 
* the stack will result in those threads being blocked. These threads will continue their 
* execution when the flow control is released by calling <i>release<i>.
* 
* Every protocol can individually control the flow control. At the beginning, a protocol 
* asks for an <i>operation key</i> (used later on to distinguish the different protocols) and 
* uses this key when calling <i>block</i> or <i>release</i> (in order to identify itself).
* 
* The overall effect is the following: if at least one protocol blocks the flow control, it 
* is blocked. Otherwise (if no protocol has blocked the flow control) it is released.
* 
*/
public interface FlowControl
  {
    /**
    * Triggers the GroupCommEventArgs which has type <i>type</i>.
    * The type is defined in Constants {@link GroupComm.common.Constants}
    */

//    public void alloc(int key, int amount);
//    public void free(int key, int amount);
//    public int used(int key);
//	public void setThreshold (int key, int amount);
//	public int getThreshold (int key);

    /**
     * This method blocks the flow control. The protocol should provide the <i>key</i> it has 
     * obtained by calling <i>getFreshKey</i>.
     * 
     * @param key The key used by the flow control to identify the calling protocol
     */
    public void block(int key);

	/**
	 * This method releases the flow control. The protocol should provide the <i>key</i> it has 
	 * obtained by calling <i>getFreshKey</i>.
	 * 
	 * @param key The key used by the flow control to identify the calling protocol
	 */    
    public void release(int key);

    /**
     * This method is not used by the protocols. It is used by the framework when an application's 
     * therad tries to enter the stack. If the flow control is blocked, the calling thread will be 
     * suspended until the flow control is released.
     */
    public void enter();

	/**
	 * Protocols use this method at initialization time to obtain a valid key that identify them 
	 * when interacting with the flow control. Every time this method is called, a new key is created. 
	 * Typically, protocols call it only once.
	 * 
	 * @return the key that will identify the caller when interacting with the flow control
	 */
    public int getFreshKey();
    
    /**
     * Protocols use this method at finalization time to release the key that identify them
     * when interacting with the flow control. Thus, the recently released key can be used by
     * an other protocol.
     * 
     * @param key The key used by the flow control to identify the calling protocol
     */
    public void releaseKey(int key);
  }
