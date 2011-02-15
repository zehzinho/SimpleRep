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

import framework.GroupCommEventArgs;

/**
* Interface allowing event triggering. Any protocol defined as <i>common code</i>
* must accept a parameter of this type in its constructor, and will can its only 
* method whenever it needs to trigger an event.
*/
public interface Trigger {
	/**
	* Triggers an event of type <i>type</i>. The argument of type GroupCommEventArgs will be 
	* conveyed with the event and delvered to the receiving handler.
	*  
	* The <i>type</i> is defined in Constants {@link GroupComm.common.Constants}
	*/
	public void trigger(int type, GroupCommEventArgs args);
}
