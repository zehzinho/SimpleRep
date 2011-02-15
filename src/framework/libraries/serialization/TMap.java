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
 * Created on Nov 25, 2005
 *
 * Transportable class for Map
 */
package framework.libraries.serialization;

import uka.transport.Transportable;

/**
 * @author mena
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
//public interface TMap extends Map, Transportable {
public interface TMap extends Transportable {

	//TODO: remove when extending Map
    void clear();
    boolean containsKey(Transportable key);
	Transportable get(Transportable o);
    boolean isEmpty();
	TCollection keySet();
    Transportable put(Transportable key, Transportable o);
    Transportable remove(Transportable o);
    int size();
    TCollection values();
}
