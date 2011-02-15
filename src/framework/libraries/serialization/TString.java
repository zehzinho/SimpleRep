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
// Transportable class for String
package framework.libraries.serialization;

import uka.transport.Transportable;

public class TString implements Transportable{

	// Value
    private char[] value;
    //ORUTTI: the field below must be suppressed or updated upon unmarshalling or deepclone
    //         Otherwise, the function hashCode may be inconsistent with the function equals
    //         which result in bugs!
    //private int length; 

    // Constructor
    public TString(){
	this.value = new char[0];
    //this.length = 0;
    }

    public TString(String string){
	this.value = string.toCharArray();
    //this.length = this.value.length;
    }

    public TString(char[] value){
	this.value = value;
    //this.length = value.length;
    }

    // Cache the hash code for the string
    private int hash; // Default to 0

    public int hashCode() {
        int h = hash;
        int len = value.length;
        if (h == 0 && len != 0) {
            int off = 0;
            char val[] = value;
                for (int i = 0; i < len; i++) {
                    h = 31*h + val[off++];
                }
                hash = h;
        }
        return h;
    }

    public boolean equals(Object obj) {
    if (this == obj) {
        return true;
    }
    if (obj instanceof TString) {
        TString str = (TString)obj;
        int n = value.length;
        if (n == str.value.length) {
        char v1[] = value;
        char v2[] = str.value;
        int i = 0;
        int j = 0;
        while (n-- != 0) {
            if (v1[i++] != v2[j++])
            return false;
        }
        return true;
        }
    }
    return false;
    }

    public int compareTo(Object obj){
        throw new InternalError("TString not implementing compareTo");
    }

    public String toString(){
	return new String(value);
    }

    /**
     *  Methods defined by the Transportable interface
     */

    // Size of primitive fields
   protected static final int _SIZE = uka.transport.BasicIO.SIZEOF_int; //value's length

   // Length of char array
   transient private int _length; 

   /** Used by uka.transport.UnmarshalStream to unmarshal the object */
   public  TString(uka.transport.UnmarshalStream _stream)
   throws java.io.IOException, ClassNotFoundException
   {
   this(_stream, _SIZE);
   _stream.accept(_SIZE);
   }

   protected TString(uka.transport.UnmarshalStream  _stream, int _size)
   throws java.io.IOException, ClassNotFoundException
   {
   _stream.request(_size); 
   byte[] _buffer = _stream.getBuffer();
   int    _pos    = _stream.getPosition();
   _length = uka.transport.BasicIO.extractInt(_buffer, _pos);
   _pos += uka.transport.BasicIO.SIZEOF_int;        
   }

   /** Method of interface Transportable, it must be declared public.
   It is called from within UnmarshalStream after creating the 
   object and assigning a stream reference to it. */
   public void unmarshalReferences(uka.transport.UnmarshalStream _stream)
   throws java.io.IOException, ClassNotFoundException
   {
       //Extracting the char array
       int _size = _length * uka.transport.BasicIO.SIZEOF_char;
       if(_size > uka.transport.BasicIO.REQUEST_MAX){
           //b is too big to be serialized as a primitive field
           //value = (char[]) _stream.readObject();
           value = new String((byte[]) _stream.readObject()).toCharArray();
           // 20070831 Sergio: this change is due to "special" way that
    	   // uka.transport treats array of chars, which limits the legnth
    	   // to REQUEST_MAX_char... too bad :(
       } else {
           //b is small enough to be serialized as a primitive field
           _stream.request(_size);
           byte[] _buffer = _stream.getBuffer();
           int    _pos    = _stream.getPosition();
           value = new char[_length];
           _pos = uka.transport.BasicIO.extract(_buffer, _pos, value);
           _stream.accept(_size);
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
   _length = value.length;
   _pos = uka.transport.BasicIO.insert(_buffer, _pos, _length);
   }

   protected void marshalReferences(uka.transport.MarshalStream _stream)
   throws java.io.IOException
   {
       //Inserting the char array
       int _size = _length * uka.transport.BasicIO.SIZEOF_char;
       if(_size > uka.transport.BasicIO.REQUEST_MAX){
           //b is too big to be serialized as a primitive field
    	   //_stream.writeObject(value);
    	   _stream.writeObject((new String(value)).getBytes());
           // 20070831 Sergio: this change is due to "special" way that
    	   // uka.transport treats array of chars, which limits the legnth
    	   // to REQUEST_MAX_char... too bad :(

       } else {
           //b is small enough to be serialized as a primitive field
           _stream.reserve(_size);
           byte[] _buffer = _stream.getBuffer();
           int    _pos    = _stream.getPosition();
           _pos = uka.transport.BasicIO.insert(_buffer, _pos, value);
           _stream.deliver(_size);
       }
   }

   public final Object deepClone(uka.transport.DeepClone _helper)
   throws CloneNotSupportedException
   {
   Object _copy = clone();
   _helper.add(this, _copy);
   ((TString) _copy).deepCloneReferences(_helper);
   return _copy;
   }

   /** Clone all references to other objects. Use the 
   DeepClone to resolve cycles */
   protected void deepCloneReferences(uka.transport.DeepClone _helper)
   throws CloneNotSupportedException
   {
       //Cloning the char array
	   this.value = _helper.doDeepClone(this.value);
       //char[] value_clone = new char[this.value.length]; 
       //System.arraycopy(this.value, 0, value_clone, 0, this.value.length);
       //this.value = value_clone;
   }  


}
