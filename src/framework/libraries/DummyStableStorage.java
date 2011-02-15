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
import framework.libraries.serialization.TArrayList;
import framework.libraries.serialization.TCollection;
import framework.libraries.serialization.TList;

/**
 * This is a dummy class implements the stable storage interface. The implementation does notihing 
 * 
 * @author smenadel
 */
public class DummyStableStorage implements StableStorage {
    public void store(int protKey, long key, Transportable log, boolean unique){}
    public Transportable retrieve(int protKey, long key){return null;}
    public TList retrieveAll(int protKey, long key){return new TArrayList();}
    public void delete(int protKey, TCollection keys){}
    public void delete(int protKey, long key){}
    public void clear(){}
    public void trim(){}
    public void close(){}
    public void dump(){}
}
