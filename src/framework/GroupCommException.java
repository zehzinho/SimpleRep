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
package framework;

/**
 * This exception should be triggered whenever an abnormal situation is detected 
 * in the common code of any protocol.
 * 
 * It is normally catched by the wrapping micro-protocol. The default behavoiur of the
 * latter is to exit the JVM. This is desirable to prevent fail-silence violations 
 * of faulty processes. 
 */

public class GroupCommException extends Exception {
	public GroupCommException() {
		super("GroupCommException :");
	}

	public GroupCommException(String s) {
		super("GroupCommException :" + s);
	}
}
