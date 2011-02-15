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
// Transportable class for Float
package framework.libraries.serialization;

import uka.transport.Transportable;

public class TFloat implements Transportable, Comparable{

    // Value
    private float value;

    // Constructor
    public TFloat(float value){
	this.value = value;
    }

    public float floatValue(){
	return value;
    }

    public int hashCode() {
        return Float.floatToIntBits(value);
    }

    public boolean equals(Object obj) {
    return (obj instanceof TFloat)
           && (Float.floatToIntBits(((TFloat)obj).value) == Float.floatToIntBits(value));
    }

    public int compareTo(Object o){
        return Float.compare(value, ((TFloat) o).value);
    }

    public String toString(){
        return "TFloat(" + value + ")";
    }

    /**
     *  Methods defined by the Transportable interface
     */

    // Size of primitive fields
    protected static final int _SIZE = uka.transport.BasicIO.SIZEOF_float;

    /** Used by uka.transport.UnmarshalStream to unmarshal the object */
    public  TFloat(uka.transport.UnmarshalStream _stream)
    throws java.io.IOException, ClassNotFoundException
    {
    this(_stream, _SIZE);
    _stream.accept(_SIZE);
    }

    protected TFloat(uka.transport.UnmarshalStream  _stream, int _size)
    throws java.io.IOException, ClassNotFoundException
    {
    _stream.request(_size); 
    byte[] _buffer = _stream.getBuffer();
    int    _pos    = _stream.getPosition();
    value = uka.transport.BasicIO.extractFloat(_buffer, _pos);
    _pos += uka.transport.BasicIO.SIZEOF_float;        
    }

    /** Method of interface Transportable, it must be declared public.
    It is called from within UnmarshalStream after creating the 
    object and assigning a stream reference to it. */
    public void unmarshalReferences(uka.transport.UnmarshalStream _stream)
    throws java.io.IOException, ClassNotFoundException
    {
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
    _pos = uka.transport.BasicIO.insert(_buffer, _pos, value);
    }

    protected void marshalReferences(uka.transport.MarshalStream _stream)
    throws java.io.IOException
    {
    }

    public final Object deepClone(uka.transport.DeepClone _helper)
    throws CloneNotSupportedException
    {
    Object _copy = clone();
    _helper.add(this, _copy);
    ((TFloat) _copy).deepCloneReferences(_helper);
    return _copy;
    }

    /** Clone all references to other objects. Use the 
    DeepClone to resolve cycles */
    protected void deepCloneReferences(uka.transport.DeepClone _helper)
    throws CloneNotSupportedException
    {
    }

}
