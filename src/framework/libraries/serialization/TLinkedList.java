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
// Transportable class for LinkedList
package framework.libraries.serialization;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

import uka.transport.Transportable;

public class TLinkedList implements TList, Cloneable {
	

	//TODO: To remove when extending LinkedList
	LinkedList contents;
	public TLinkedList(){
		contents = new LinkedList();
	}
	public TLinkedList(TList list){
		this();
		Iterator it = list.iterator();
		while(it.hasNext()){
			contents.add(it.next());
		}
	}
	public void addFirst(Transportable o){
		contents.addFirst(o);
	}
	public void addLast(Transportable o){
		contents.addLast(o);
	}
	public Transportable removeFirst(){
		return (Transportable) contents.removeFirst();
	}
	public Transportable removeLast(){
		return (Transportable) contents.removeLast();
	}
    public Transportable getFirst(){
        return (Transportable) contents.getFirst();
    }
    public Transportable getLast(){
        return (Transportable) contents.getLast();
    }
    public void add(Transportable o) {
        addLast(o);
    }
    public void add(int index, Transportable o) {
        contents.add(index, o);
    }
    public void addAll(TCollection col) {
        Iterator it = col.iterator();
        while(it.hasNext()){
            addLast((Transportable) it.next());
        }
    }
    public void clear() {
        contents.clear();
    }
    public boolean contains(Transportable o) {
        return contents.contains(o);
    }
    public boolean containsAll(TCollection col) {
        Iterator it= col.iterator();
        boolean result = true;
        while(it.hasNext() && result){
            result = result && contents.contains(it.next());
        }
        return result;
    }
    public Transportable get(int index) {
        return (Transportable) contents.get(index);
    }
    public int indexOf(Transportable o) {
        return contents.indexOf(o);
    }
    public boolean isEmpty() {
        return contents.isEmpty();
    }
    public Iterator iterator() {
        return contents.iterator();
    }
    public boolean remove(Transportable o) {
        return contents.remove(o);
    }
    public Transportable remove(int idx) {
        return (Transportable) contents.remove(idx);
    }
    public void removeAll(TCollection col) {
        Iterator it= col.iterator();
        while(it.hasNext()){
            contents.remove(it.next());
        }
    }
    public Transportable set(int idx, Transportable o) {
        return (Transportable) contents.set(idx, o);
    }
    public int size() {
        return contents.size();
    }
    public String toString()
    {
        return ("TLinkedList{" + contents + "}");
    }
    //End: to remove

    public boolean equals(Object o){
        if (!(o instanceof TLinkedList))
            return false;
        
        return ((TLinkedList) o).contents.equals(this.contents);
    }

    public int compareTo(Object o){
        throw new InternalError("TLinkedList not implementing compareTo");
    }

    public Object clone(){
        TLinkedList clone;
		try {
			clone = (TLinkedList) super.clone();
		} catch (CloneNotSupportedException e) {
            throw new InternalError();
		}
        LinkedList clone_contents = new LinkedList(contents); 
        clone.contents = clone_contents;
        return clone;
    }

    /**
     *  Methods defined by the Transportable interface
     */
    
    // Size of primitive fields
    protected static final int _SIZE = uka.transport.BasicIO.SIZEOF_int;

    // Number of elements
    private int _nbelements;

   /** Used by uka.transport.UnmarshalStream to unmarshal the object */
   public  TLinkedList(uka.transport.UnmarshalStream _stream)
	 throws java.io.IOException, ClassNotFoundException
   {
	 this(_stream, _SIZE);
	 _stream.accept(_SIZE);
   }

   protected TLinkedList(uka.transport.UnmarshalStream  _stream, int _size)
	 throws java.io.IOException, ClassNotFoundException
   {
	 _stream.request(_size); 
	 byte[] _buffer = _stream.getBuffer();
	 int    _pos    = _stream.getPosition();
	 _nbelements = uka.transport.BasicIO.extractInt(_buffer, _pos);
	 _pos += uka.transport.BasicIO.SIZEOF_int;
   }

   /** Method of interface Transportable, it must be declared public.
	   It is called from within UnmarshalStream after creating the 
	   object and assigning a stream reference to it. */
   public void unmarshalReferences(uka.transport.UnmarshalStream _stream)
	 throws java.io.IOException, ClassNotFoundException
   {
       contents = new LinkedList();
	   for(int i = 0; i < _nbelements; i++){
		   contents.addLast(_stream.readObject());
	   }
   }

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

   protected void marshalPrimitives(byte[] _buffer, int _pos)
	 throws java.io.IOException
   {
	 _nbelements = contents.size();
	 _pos = uka.transport.BasicIO.insert(_buffer, _pos, _nbelements);
   }

   protected void marshalReferences(uka.transport.MarshalStream _stream)
	 throws java.io.IOException
   {
	   ListIterator it = contents.listIterator();
	   for(int i = 0; i < _nbelements; i++){
		   _stream.writeObject(it.next());
	   }
   }

   public final Object deepClone(uka.transport.DeepClone _helper)
	 throws CloneNotSupportedException
   {
	 Object _copy = clone();
	 _helper.add(this, _copy);
	 ((TLinkedList) _copy).deepCloneReferences(_helper);
	 return _copy;
   }

   /** Clone all references to other objects. Use the 
	   DeepClone to resolve cycles */
   protected void deepCloneReferences(uka.transport.DeepClone _helper)
	 throws CloneNotSupportedException
   {
	   LinkedList aux = new LinkedList();
	   ListIterator it = this.contents.listIterator();
	   while (it.hasNext()) {
		   aux.add(_helper.doDeepClone(it.next()));
	   }
	   this.contents = aux;
   }
}
