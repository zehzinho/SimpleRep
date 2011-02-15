/**
 *  SAMOA - PROTOCOL FRAMEWORK
 *  Copyright (C) 2005  Olivier Rütti (EPFL) (olivier.rutti@a3.epfl.ch)
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
package seqSamoa;

import uka.transport.Transportable;

import framework.GroupCommMessage;
import framework.libraries.serialization.TString;

/**
 * A class <CODE>Message</CODE> represents a message within its destination.
 * The content of a <CODE>Message</CODE> should not be accessed by the
 * Executer. The information contained in it should be accessed only by the
 * destination of the message.
 */
@SuppressWarnings("serial")
public class Message implements Transportable {

    /* The message */
    public Transportable content;

    /* The destination */
    protected TString dest;

    /**
     * Constructor
     * 
     * @param content
     *            the message passed as an argument to a service
     * @param dest
     *            the type of listener that will receive the message
     */
    @SuppressWarnings("unchecked")
	public Message(Transportable content, Service.Listener dest) {
        super();

        if (dest != null)
            this.dest = dest.key;
        else
        	// dest.key can not be equal to NULL since dest.key = string+number
            this.dest = new TString("NULL");

        this.content = content;
    }

    /**
     * Constructor
     * 
     * @param gm
     *            the GroupCommMessage transformed in Message (about
     *            GroupCommMessage, see Fortika)
     */
    public Message(GroupCommMessage gm) {
        super();

        this.dest = (TString) gm.get(0);
        this.content = gm.get(1);
    }

    /**
     * Return a GroupCommMessage corresponding to the Message
     * 
     * @return the GroupCommMessage corresponding to the Message
     */
    public GroupCommMessage toGroupCommMessage() {
        GroupCommMessage gm = new GroupCommMessage();
        gm.addLast(dest);
        gm.addLast(content);

        return gm;
    }

    public String toString() {
    	return new String(dest.toString() + content);
    }

    /**
     * Methods defined by the Transportable interface
     */

    // Size of primitive fields
    protected static final int _SIZE = 0;

    /** Used by uka.transport.UnmarshalStream to unmarshal the object */
    public Message(uka.transport.UnmarshalStream _stream)
            throws java.io.IOException, ClassNotFoundException {
        this(_stream, _SIZE);
        _stream.accept(_SIZE);
    }

    protected Message(uka.transport.UnmarshalStream _stream, int _size)
            throws java.io.IOException, ClassNotFoundException {
        _stream.request(_size);
    }

    /**
     * Method of interface Transportable, it must be declared public. It is
     * called from within UnmarshalStream after creating the object and
     * assigning a stream reference to it.
     */
    public void unmarshalReferences(uka.transport.UnmarshalStream _stream)
            throws java.io.IOException, ClassNotFoundException {
        dest = (framework.libraries.serialization.TString) _stream.readObject();
        content = (uka.transport.Transportable) _stream.readObject();
    }

    /** Called directly by uka.transport.MarshalStream */
    public void marshal(uka.transport.MarshalStream _stream)
            throws java.io.IOException {
        _stream.reserve(_SIZE);
        byte[] _buffer = _stream.getBuffer();
        int _pos = _stream.getPosition();
        marshalPrimitives(_buffer, _pos);
        _stream.deliver(_SIZE);
        marshalReferences(_stream);
    }

    protected void marshalPrimitives(byte[] _buffer, int _pos)
            throws java.io.IOException {
    }

    protected void marshalReferences(uka.transport.MarshalStream _stream)
            throws java.io.IOException {
        _stream.writeObject(dest);
        _stream.writeObject(content);
    }

    public final Object deepClone(uka.transport.DeepClone _helper)
            throws CloneNotSupportedException {
        Object _copy = clone();
        _helper.add(this, _copy);
        ((Message) _copy).deepCloneReferences(_helper);
        return _copy;
    }

    /**
     * Clone all references to other objects. Use the DeepClone to resolve
     * cycles
     */
    protected void deepCloneReferences(uka.transport.DeepClone _helper)
            throws CloneNotSupportedException {
        this.dest = (framework.libraries.serialization.TString) _helper
                .doDeepClone(this.dest);
        this.content = (uka.transport.Transportable) _helper
                .doDeepClone(this.content);
    }

}
