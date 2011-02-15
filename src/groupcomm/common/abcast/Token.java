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
// He Hui-Yang        Informatique
// Février 2005
// ###############################

package groupcomm.common.abcast;

import uka.transport.Transportable;
import framework.PID;
import framework.libraries.serialization.TArrayList;
import framework.libraries.serialization.TList;

public class Token implements Transportable{
    
    //round which the token belongs to 
    protected int round;
    //sender of the token
    protected PID sender;  
    //list of messages are delivered as soon as there are enough votes  
    protected TList proposalSeq;
    //the votes
    protected int votes;
    //sequence of messages adelivered that the token is aware of
    protected TList adeliv; 
    //List of messages broadccast atomically
    protected TList nextSet;  

    // Size of primitive fields
    protected static final int _SIZE = uka.transport.BasicIO.SIZEOF_int + uka.transport.BasicIO.SIZEOF_int;

    //constructor of Token
    protected Token(int round, PID sender, TList proposalSeq, int votes,  TList adeliv, TList nextSet){
      
        this.round=round;
        this.sender=sender;	  
        this.proposalSeq=proposalSeq;	  
        this.votes=votes;	  
        this.adeliv=adeliv;	  
        this.nextSet=nextSet;
    }
  
    protected Object clone(){
//TODO: Doesn't this have side effects on non_primitive values?
        return new Token(round,sender,proposalSeq,votes,adeliv,nextSet);
    }

    /**
     *  Methods defined by the Transportable interface
     *  Generated automatically with javaparty
     */
    /** Used by uka.transport.UnmarshalStream to unmarshal the object */
    public  Token(uka.transport.UnmarshalStream _stream)
	throws java.io.IOException, ClassNotFoundException
    {
	this(_stream, _SIZE);
	_stream.accept(_SIZE);
    }

    protected Token(uka.transport.UnmarshalStream  _stream, int _size)
	throws java.io.IOException, ClassNotFoundException
    {
	_stream.request(_size); 
	byte[] _buffer = _stream.getBuffer();
	int    _pos    = _stream.getPosition();
	votes = uka.transport.BasicIO.extractInt(_buffer, _pos);
	_pos += uka.transport.BasicIO.SIZEOF_int;
	round = uka.transport.BasicIO.extractInt(_buffer, _pos);
	_pos += uka.transport.BasicIO.SIZEOF_int;
    }

    /** Method of interface Transportable, it must be declared public.
	It is called from within UnmarshalStream after creating the 
	object and assigning a stream reference to it. */
    public void unmarshalReferences(uka.transport.UnmarshalStream _stream)
	throws java.io.IOException, ClassNotFoundException
    {
	nextSet = (TArrayList) _stream.readObject();
	adeliv = (TArrayList) _stream.readObject();
	proposalSeq = (TArrayList) _stream.readObject();
	sender = (framework.PID) _stream.readObject();
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
	_pos = uka.transport.BasicIO.insert(_buffer, _pos, votes);
	_pos = uka.transport.BasicIO.insert(_buffer, _pos, round);
    }

    protected void marshalReferences(uka.transport.MarshalStream _stream)
	throws java.io.IOException
    {
	_stream.writeObject(nextSet);
	_stream.writeObject(adeliv);
	_stream.writeObject(proposalSeq);
	_stream.writeObject(sender);
    }

    public final Object deepClone(uka.transport.DeepClone _helper)
	throws CloneNotSupportedException
    {
	Object _copy = clone();
	_helper.add(this, _copy);
	((Token) _copy).deepCloneReferences(_helper);
	return _copy;
    }

    /** Clone all references to other objects. Use the 
	DeepClone to resolve cycles */
    protected void deepCloneReferences(uka.transport.DeepClone _helper)
	throws CloneNotSupportedException
    {
	this.nextSet = (TArrayList) _helper.doDeepClone(this.nextSet);
	this.adeliv = (TArrayList) _helper.doDeepClone(this.adeliv);
	this.proposalSeq = (TArrayList) _helper.doDeepClone(this.proposalSeq);
	this.sender = (framework.PID) _helper.doDeepClone(this.sender);
    }  
}
