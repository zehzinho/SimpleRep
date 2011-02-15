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
package groupcomm.api.dynabcast;

import framework.PID;
import framework.api.ApiAllAbcastStack;

/**
 * This interface defines the methods to use by applications to interact with a Dynamic Atomic Broadcast protocol stack.
 *  
 * @author Sergio Mena
 *
 */
public interface ApiDynAbcastStack extends ApiAllAbcastStack {
    /**
     * This method tells the stack to join a new process to the group. The stack will 
     * use the <i>new_view</i> callback to notify the view change that includes the 
     * joined process.
     * 
     * @param pid The process to be joined
     */
	public void sendJoin(PID pid);
    /**
     * This method tells the stack to remove a from the group. This may be due to a 
     * suspicion or other reasons.  
     * The stack will use the <i>new_view</i> callback to notify the view change that excludes the 
     * removed process.
     * 
     * @param pid The precess to be removed. It can be the local process (i.e., a process can exclude 
     * itself from the view)
     */
	public void sendRemove(PID pid);
}
