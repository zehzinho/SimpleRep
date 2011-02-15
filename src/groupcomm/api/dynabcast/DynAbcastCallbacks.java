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

import framework.api.AllAbcastCallbacks;
import framework.libraries.serialization.TList;

/**
 * This interface is a set of callbacks that the stack uses to notify the application of important events, 
 * such as Atomic Delivery of a message or a new view installed. Usually, when the stack is created 
 * it is pased an Object implementing this interface.
 * 
 * @author Sergio Mena
 */

public interface DynAbcastCallbacks extends AllAbcastCallbacks{
    /**
     * A new view has been installed
     * 
     * @param number View ID
     * @param processes List of processes that belong to the new view
     */
    void new_view(int number, TList processes);
}
