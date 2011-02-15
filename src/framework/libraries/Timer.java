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

import uka.transport.Transportable;


/**
   * This interface represents the framework library implementing timers.
   * All protocols that need timers must accept an instance of a class implementing 
   * this interface. This instance is usually passed as a parameter in the protocol
   * constructor.  
   */
public interface Timer {
	/**
	* Schedules a timer event to be triggered after the amount of time passed
	* in the third parameter. When this period of time has passed by, the 
	* timer library must trigger a timer event including the first parameter of this
	* method.
	* 
	* Timers can be non-periodic: the timer event is triggered at most once. Then,
	* it is immediately canceled. Timers can also be periodic: they are re-scheduled
	* every time they expire, until the protocol cancels them. 
	* 
	* There is no guarantee in the maximum amount of time between the scheduling 
	* operation and the triggering of the timer event. The only guarantee is in the 
	* minimum amount of time.
	* 
	* @param key The parameter that will be attached to the timer event when it is triggered 
	* @param periodic Indicates whether this timer is periodic or non-periodic
	* @param time The lapse, in milliseconds, before triggering the timer event
	*/
	public void schedule(Transportable key, boolean periodic, int time);

	/**
	 * Cancels a previously set timer. If the timer event was allready triggered, this method 
	 * has no effect.
	 *  
	 * @param key The parameter that was passed as first argument (of equivalent: 
	 *  equals method would return <b>true</b>) when the timer was scheduled
	 */
	public void cancel(Transportable key);

	/**
	 * Resets a previously set timer to zero. The achieved effect is as though the timer had just been 
	 * scheduled for the fisrt time.
	 *   
	 * @param key The parameter that was passed as first argument (of equivalent: 
	 *  equals method would return <b>true</b>) when the timer was scheduled
	 */
	public void reset(Transportable key);
}
