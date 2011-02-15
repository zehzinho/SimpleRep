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

import java.io.IOException;

import uka.transport.MarshalStream;
import uka.transport.UnmarshalStream;
import framework.libraries.serialization.TLinkedList;

/**
* This class represents the list of arguments the an event is triggered with. It conveys all data 
* that the event handler will need from the triggering handler. The items (arguments) can be of 
* any type, being GroupCommMessage a very frequent one.
* 
* Its is implemented (for now) as a LinkedList object.
*/

public class GroupCommEventArgs extends TLinkedList {
    //TODO: change back to extends LinkedList
    
    //TODO: remove!
    public void marshal(MarshalStream arg0) throws IOException {
        throw new IOException("unimplemented!");
    }
    public void unmarshalReferences(UnmarshalStream arg0) throws IOException, ClassNotFoundException {
        throw new IOException("unimplemented!");
    }
}
