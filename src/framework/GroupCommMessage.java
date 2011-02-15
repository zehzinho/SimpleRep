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

import java.util.NoSuchElementException;

import uka.transport.Transportable;
import framework.libraries.serialization.TLinkedList;

/**
* This class implements abrstract messages. Messages consist of a list of headers. 
* The normal usage is that protocols push headers when the message is being conveyed 
* down the stack, and pop headers when the message travels upwards.
* 
* These messages travel from one protocol to another as a parameter of a triggered 
* event (i.e., as an element in a GroupCommEventArgs structure).
*/
//public class GroupCommMessage extends LinkedList implements uka.transport.Transportable {
public class GroupCommMessage extends TLinkedList{

    public GroupCommMessage(){
        super();
    }

    /**
    * Adds a header to the begining of the message.
    * <b>This version is compatible with <i>uka.transport</i></b>
    * 
    * @returns The new message.
    */
    public void tpack(Transportable m) {
        addFirst(m);
    }

	public void tpackLast(Transportable m) {
		addLast(m);
	}

    /**
    * Returns and removes the first header from the message.
    * <b>This version is compatible with <i>uka.transport</i></b>
    *
    * @returns The first header.
    * @exception NoSuchElementException if this event has no header.
    */
    public Transportable tunpack() throws NoSuchElementException {
        return removeFirst();
    }


	public Transportable tunpackLast() throws NoSuchElementException {
		return removeLast();
	}

    /**
    * Returns the specified header of the message without removing it.
    * <b>This version is compatible with <i>uka.transport</i></b>
    *
    * @returns The specified header.
    * @exception NoSuchElementException if this event has no header.
    */
    public Transportable tpeek(int place) {
        return get(place);
    }

	/**
	 * Makes a shallow copy of this message. It is shallow in that it only clones 
	 * the list structure representing the headers. The objects referenced by 
	 * these headers are <b>not</b> cloned.  
	 * 
	 * @return The clone of the initial message
	 */
	public GroupCommMessage cloneGroupCommMessage() {
		GroupCommMessage m = new GroupCommMessage();
		for (int i = 0; i < size(); i++) {
			m.tpackLast(get(i));
		}
		return m;
	}

	/**
	 * For debugging. Not documented
	 */
	public String toString() {

		String str = "<<";
		Object o;
		boolean stop = false;

		try {
			o = get(0);
			str = str + o.toString();
		} catch (IndexOutOfBoundsException e) {
			stop = true;
		}

		int i = 1;
		while (!stop) {
			try {
				o = get(i);
				str = str + "::" + o.toString();
				i++;
			} catch (IndexOutOfBoundsException e) {
				stop = true;
			}
		}
		return str + ">>";
	}


    /**
     *  Methods defined by the Transportable interface
     */
    
    // Size of primitive fields
    protected static final int _SIZE = TLinkedList._SIZE + 0;

   /** Used by uka.transport.UnmarshalStream to unmarshal the object */
   public  GroupCommMessage(uka.transport.UnmarshalStream _stream)
     throws java.io.IOException, ClassNotFoundException
   {
     this(_stream, _SIZE);
     _stream.accept(_SIZE);
   }

   protected GroupCommMessage(uka.transport.UnmarshalStream  _stream, int _size)
     throws java.io.IOException, ClassNotFoundException
   {
     super(_stream, _size); 
   }

   /** Method of interface Transportable, it must be declared public.
       It is called from within UnmarshalStream after creating the 
       object and assigning a stream reference to it. */
//   public void unmarshalReferences(uka.transport.UnmarshalStream _stream)
//     throws java.io.IOException, ClassNotFoundException
//   {
//       super.unmarshalReferences(_stream)
//   }

   /** Called directly by uka.transport.MarshalStream */
   public void marshal(uka.transport.MarshalStream _stream)
     throws java.io.IOException
   {
     _stream.reserve(_SIZE);
     byte[] _buffer = _stream.getBuffer();
     int    _pos    = _stream.getPosition();
     marshalPrimitives(_buffer, _pos);
     _stream.deliver(_SIZE);
     marshalReferences(_stream);
   }

//   protected void marshalPrimitives(byte[] _buffer, int _pos)
//     throws java.io.IOException
//   {
//     super.marshalPrimitives(_buffer, _pos);
//   }

//   protected void marshalReferences(uka.transport.MarshalStream _stream)
//     throws java.io.IOException
//   {
//       super.marshalReferences(_stream);
//   }


//   /** Clone all references to other objects. Use the 
//       DeepClone to resolve cycles */
//   protected void deepCloneReferences(uka.transport.DeepClone _helper)
//     throws CloneNotSupportedException
//   {
//       super.deepCloneReferences(_helper);
//   }

}
