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
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package framework.libraries.serialization;

import java.util.Iterator;

import uka.transport.Transportable;

/**
 * @author mena
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
//public interface TCollection extends Collection, Transportable {
public interface TCollection extends Transportable {

//TODO: Remove when extending Collection
  void add(Transportable o);
  void addAll(TCollection col);
  void clear();
  boolean contains(Transportable o);
  boolean containsAll(TCollection col);
  boolean isEmpty();
  Iterator iterator();
  boolean remove(Transportable o);
  void removeAll(TCollection col);
  int size();

}
