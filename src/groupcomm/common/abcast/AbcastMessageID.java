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
// ###############################
// Projet de semestre    I&C - LSR
// Jean Vaucher      D�cembre 2002
// ###############################

package groupcomm.common.abcast;

import uka.transport.Transportable;
import framework.Compressable;
import framework.PID;

/**
 * <b> This class implements a data stucture to identify ABCast messages. </b><br>
 *     the objects are ordred.
 */
public class AbcastMessageID implements Compressable, Comparable, Transportable {
    // Sender of the message
    protected PID proc;
    // Id
    protected long id;

    /**
     * Main constructor
     *
     * @param p   Sender of the message
     * @param id  Id of the message
     */
    public AbcastMessageID (PID p, long id) {
	this.proc = p;
	this.id = id;
    }

    /**
     * Return a new AbcastMessageID for the next message. Calling this method
     * ensure unicity of the ID's
     */
    AbcastMessageID nextId() {
	return new AbcastMessageID(proc, id++);
    }

    /**
     * Indicates if the two id's are the same
     *
     * @param o AbcastMessageID object to compare
     */
    public boolean equals(Object o) {
	AbcastMessageID m = (AbcastMessageID) o;
	return (m.proc.equals(this.proc))&&(m.id == id);
    }

    /**
     * Return a String representation of the AbcastMessageID
     */
    public String toString() {
	return (proc + "#" + id);
    }

    /**
     * Compare two AbcastMessageID objects
     *
     * @param o Object to compare with
     */
    public int compareTo(Object o) {
	
	int i = proc.compareTo(((AbcastMessageID)o).proc);
	if (i != 0)
	    return i;
	else
	    return (new Long(this.id).compareTo(new Long(((AbcastMessageID)o).id)));
	
    }
    
    /**
     * Compare two AbcastMessageID objects
     *
     * @param o Object to compare with
     */   
    public int compareToCompressable(Object o) {        
        if (!(o instanceof AbcastMessageID))
            return NOT_COMPARABLE;
        
        AbcastMessageID aID = (AbcastMessageID) o;
        
        if (!(aID.proc.equals(this.proc)))
            return NOT_COMPARABLE;
        
        if (this.id == aID.id+1)
            return SUCCESSOR;
        if (this.id > aID.id)
            return BIGGER;
        if (this.id+1 == aID.id)
            return PREDECESSOR;
        if (this.id < aID.id)
            return SMALLER;
        return EQUAL;
    }

    /**
     * Returns an hashcode value for this Message ID.
     */
    public int hashCode () {
	String tmp = String.valueOf(proc.port) + proc.ip.getHostAddress() + String.valueOf(proc.incarnation) + String.valueOf(id);
	return tmp.hashCode();
    }

    /**
     *  Methods defined by the Transportable interface
     *  Generated automatically with javaparty
     */

    // Size of primitive fields
    protected static final int _SIZE = uka.transport.BasicIO.SIZEOF_long;

    /** Used by uka.transport.UnmarshalStream to unmarshal the object */
    public  AbcastMessageID(uka.transport.UnmarshalStream _stream)
	throws java.io.IOException, ClassNotFoundException
    {
	this(_stream, _SIZE);
	_stream.accept(_SIZE);
    }

    protected AbcastMessageID(uka.transport.UnmarshalStream  _stream, int _size)
	throws java.io.IOException, ClassNotFoundException
    {
	_stream.request(_size); 
	byte[] _buffer = _stream.getBuffer();
	int    _pos    = _stream.getPosition();
	id = uka.transport.BasicIO.extractLong(_buffer, _pos);
	_pos += uka.transport.BasicIO.SIZEOF_long;
    }

    /** Method of interface Transportable, it must be declared public.
	It is called from within UnmarshalStream after creating the 
	object and assigning a stream reference to it. */
    public void unmarshalReferences(uka.transport.UnmarshalStream _stream)
	throws java.io.IOException, ClassNotFoundException
    {
	proc = (framework.PID) _stream.readObject();
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
	_pos = uka.transport.BasicIO.insert(_buffer, _pos, id);
    }

    protected void marshalReferences(uka.transport.MarshalStream _stream)
	throws java.io.IOException
    {
	_stream.writeObject(proc);
    }

    public final Object deepClone(uka.transport.DeepClone _helper)
	throws CloneNotSupportedException
    {
	Object _copy = clone();
	_helper.add(this, _copy);
	((AbcastMessageID) _copy).deepCloneReferences(_helper);
	return _copy;
    }

    /** Clone all references to other objects. Use the 
	DeepClone to resolve cycles */
    protected void deepCloneReferences(uka.transport.DeepClone _helper)
	throws CloneNotSupportedException
    {
	this.proc = (framework.PID) _helper.doDeepClone(this.proc);
    }
}
