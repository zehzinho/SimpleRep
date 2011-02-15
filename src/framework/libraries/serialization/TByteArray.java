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
// Transportable class for ByteArray
package framework.libraries.serialization;

import uka.transport.Transportable;

public class TByteArray implements Transportable{
    //TODO: remove this class completely
	//TODO: 20070826: remove? not so clear (used for serialized objects)

    // Value
    private byte[] value;
    private int length;

    // Constructor
    public TByteArray(byte[] value){
	this.value = value;
	if (value != null)
		this.length = this.value.length;
	else
		this.length = 0;
    }

    public byte[] byteValue(){
	return value;
    }

    // Cache the hash code for the byte array
    private int hash; // Default to 0

    public int hashCode() {
        int h = hash;
        int len = length;
        if (h == 0 && len != 0) {
            int off = 0;
            byte val[] = value;
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
    if (obj instanceof TByteArray) {
        TByteArray b = (TByteArray)obj;
        int n = value.length;
        if (n == b.value.length) {
        byte v1[] = value;
        byte v2[] = b.value;
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
        throw new InternalError("TByteArray not implementing compareTo");
    }

    // Cache the string value for the byte array
    String cachedS = null;

    public String toString(){
    	if(cachedS == null){
        	int len = value.length;
    		StringBuffer s = new StringBuffer(len);
    		s.append("TByteArray[");
    		for(int i = 0; i < len; i++){
    			s.append(Byte.toString(value[i]));
    			s.append(' ');
    		}
    		s.setCharAt(s.length()-1, ']');
    		cachedS = s.toString();
    	}
    	return cachedS;
        //throw new InternalError("TByteArray not implementing toString");
    }

    /**
     *  Methods defined by the Transportable interface
     */

    // Size of primitive fields
   protected static final int _SIZE = uka.transport.BasicIO.SIZEOF_int; //value's length

   // Length of byte array
   private int _length; 

   /** Used by uka.transport.UnmarshalStream to unmarshal the object */
   public  TByteArray(uka.transport.UnmarshalStream _stream)
   throws java.io.IOException, ClassNotFoundException
   {
   this(_stream, _SIZE);
   _stream.accept(_SIZE);
   }

   protected TByteArray(uka.transport.UnmarshalStream  _stream, int _size)
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
       //Extracting the byte array
       int _size = _length * uka.transport.BasicIO.SIZEOF_byte;
       if(_size > uka.transport.BasicIO.REQUEST_MAX){
           //b is too big to be serialized as a primitive field
           value = (byte[]) _stream.readObject();
       } else {
           //b is small enough to be serialized as a primitive field
           _stream.request(_size);
           byte[] _buffer = _stream.getBuffer();
           int    _pos    = _stream.getPosition();
           value = new byte[_length];
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
       //Inserting the byte array
       int _size = _length * uka.transport.BasicIO.SIZEOF_byte;
       if(_size > uka.transport.BasicIO.REQUEST_MAX){
           //b is too big to be serialized as a primitive field
           _stream.writeObject(value);
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
   ((TByteArray) _copy).deepCloneReferences(_helper);
   return _copy;
   }

   /** Clone all references to other objects. Use the 
   DeepClone to resolve cycles */
   protected void deepCloneReferences(uka.transport.DeepClone _helper)
   throws CloneNotSupportedException
   {
       //Cloning the byte array
	   this.value = _helper.doDeepClone(this.value);
       //byte[] value_clone = new byte[this.value.length]; 
       //System.arraycopy(this.value, 0, value_clone, 0, this.value.length);
       //this.value = value_clone;
   }  


}
