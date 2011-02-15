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
/*
 * Created on Feb 20, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package framework.libraries;

import uka.transport.Transportable;

/**
 * @author mena
 *
 * This class defines the standard interface for marshilling libraries. A protocol 
 * includes a parameter of this type in its constructor if it needs marshalling facilities 
 * from the framework.
 * 
 * This allows to choose the marshalling implementation without having to change all 
 * <i>common code</i> protocols. 
 */
public interface Serialize {
	/**
	 * Marshalls the Object passed as parameter and returns the resulting byte array
	 * 
	 * @param m The object to marshall (e.g. a <i>GroupCommMessage</i>)
	 * @return The marshalled object in a byte array
	 * @throws IOException
	 */
	public byte[] marshall(Transportable m);
	/**
	 * Unmarshalls and returns the Object previously marshalled in the byte array 
	 * passed as parameter.
	 * 
	 * @param m The byte array containing the marshalled object
	 * @return The unmarshalled object (e.g. a <i>GroupCommMessage</i>)
	 * @throws IOException
	 */
	public Transportable unmarshall(byte[] b);
}
