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
// Transportable class for Boolean
package framework.libraries.serialization;

import java.util.HashMap;
import java.util.Iterator;

import uka.transport.Transportable;

public class THashMap implements TMap, Cloneable {

	//TODO: To remove when extending ArrayList
	HashMap contents;
	public THashMap(){
		contents = new HashMap();
	}
	public void clear(){
		contents.clear();
	}
    public boolean containsKey(Transportable key) {
        return contents.containsKey(key);
    }
    public Transportable get(Transportable o) {
        return (Transportable) contents.get(o);
    }
    public boolean isEmpty() {
        return contents.isEmpty();
    }
    public TCollection keySet() {
        TSet tkeys = new THashSet();
        Iterator it = contents.keySet().iterator();
        while(it.hasNext()){
            tkeys.add( (Transportable)it.next() );
        }
        return tkeys;
    }
    public Transportable put(Transportable key, Transportable o) {
        return (Transportable) contents.put(key,o);
    }
    public Transportable remove(Transportable o) {
        return (Transportable) contents.remove(o);
    }
    public int size() {
        return contents.size();
    }
    public TCollection values(){
		TSet tvalues = new THashSet();
    	Iterator it = contents.values().iterator();
		while(it.hasNext()){
			tvalues.add( (Transportable)it.next() );
		}
		return tvalues;
    }
    public String toString()
    {
        return ("THashMap{" + contents + "}");
    }
    //End: to remove

    public boolean equals(Object o){
        throw new InternalError("THashMap not implementing equals");
    }

    public int compareTo(Object o){
        throw new InternalError("THashMap not implementing compareTo");
    }

    public Object clone(){
        THashMap clone;
        try {
            clone = (THashMap) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError();
        }
        HashMap clone_contents = (HashMap) contents.clone(); 
        clone.contents = clone_contents;
        return clone;
    }

	/*
	* Code for uka.transport serialization
	*/

   /**
	* The number of elements
	*/
   protected static final int _SIZE = uka.transport.BasicIO.SIZEOF_int;
   private int _nbelements;

   /** Used by uka.transport.UnmarshalStream to unmarshal the object */
   public  THashMap(uka.transport.UnmarshalStream _stream)
	 throws java.io.IOException, ClassNotFoundException
   {
	 this(_stream, _SIZE);
	 _stream.accept(_SIZE);
   }

   protected THashMap(uka.transport.UnmarshalStream  _stream, int _size)
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
       contents = new HashMap();
       for(int i = 0; i < _nbelements; i++){
	   	Object key = _stream.readObject();
	   	Object value = _stream.readObject();
	   	contents.put(key, value);
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
	   Iterator it = contents.keySet().iterator();
	   for(int i = 0; i < _nbelements; i++){
	   	Object key = it.next();
		_stream.writeObject(key);
		_stream.writeObject(contents.get(key));
	   }
   }

   public final Object deepClone(uka.transport.DeepClone _helper)
	 throws CloneNotSupportedException
   {
	 Object _copy = clone();
	 _helper.add(this, _copy);
	 ((THashMap) _copy).deepCloneReferences(_helper);
	 return _copy;
   }

   /** Clone all references to other objects. Use the 
	   DeepClone to resolve cycles */
   protected void deepCloneReferences(uka.transport.DeepClone _helper)
	 throws CloneNotSupportedException
   {
	   HashMap aux = new HashMap();
	   Iterator it = this.contents.keySet().iterator();
	   while (it.hasNext()) {
	   	Object key = _helper.doDeepClone(it.next());
	   	Object value = _helper.doDeepClone(contents.get(key));
	   	aux.put(key, value);
	   }
	   this.contents = aux;
   }
}
