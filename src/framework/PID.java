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

import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.text.ParsePosition;

import uka.transport.Transportable;

/**
 * This class represents the identity of a process.  It is used whenever a process wants to 
 * refer to anther process inside any of the protocols in the composition.
 * 
 * Two PID's are different if any of their three <i>final</i> attributes differ: ip, port, or incarnation.
 * 
 * Almost every protocol needs to be passed a PID at the constructor which refers to the local process. 
 * This PID is usually called <i>myself</i>
 * 
 * Once a PID is created it is immutable, this is why it does not need to be cloned to avoid side effects in it. 
 *
 * It implements the <i>comparable</i> and <i>equals</i> interface. This allows its instances to be ordered in 
 * lists or compared among them.
 * 
 * It also implements a static method <i>parsePID</i> that contructs an instance of PID based on the data 
 * provided by a string (in a certain format, see parsePID).
 *  
 */
public class PID implements Comparable, Transportable {
	/**
	 * IP address of the process.
	 */
    //public final InetAddress ip;  Change necessary for uka.transport
    public InetAddress ip;

	/**
	 * Port of the process.
	 */
	public final int port;

	/**
	 * Incarnation of the process.
	 */
	public final int incarnation;

	private final static String format =
		new String("{0}:{1,number,#}:{2,number,#}");

	/**
	 * Initializes the public fields.
	 *
	 * @param i The IP address
	 * @param p The port
	 * @param z The current incarnation
	 */
	public PID(InetAddress i, int p, int z) {
		ip = i;
		port = p;
		incarnation = z;
	}

	/**
	 * Returns a hashcode value for this PID.
	 */
	public int hashCode() {
		return (int)
			((((long) ip.hashCode())
				* ((long) port)
				* ((long) (incarnation + 1)))
				& ((long) Integer.MAX_VALUE));
	}

	/**
	 * Indicates if two PIDs represent the same process.
	 *
	 * @param o          The other PID.
	 * @return           <b>true</b> if both PIDs identify the same process. <b>false </b> otherwise.
	 */
	public boolean equals(Object o) {
	    if (!(o instanceof PID))
	        return false;
	        
		PID p = (PID) o;
		return (
			ip.equals(p.ip) && port == p.port && incarnation == p.incarnation);
	}

	/**
	 * Returns a String representation of this PID. For debugging
	 */
	public String toString() {
		return MessageFormat.format(
			format,
			new Object[] {
				ip.getHostAddress(),
				new Integer(port),
				new Integer(incarnation)});
	}

	/**
	 * Compares Two PIDs.
	 *
	 * @param o The PID to compare to <i>this</i>
	 * @return The comparison between both PIDs.
	 */
	public int compareTo(Object o) {
		PID p = (PID) o;

		// Compares the InetAddress
		int i = ip.getHostAddress().compareTo(p.ip.getHostAddress());
		if (i != 0) {
			return i;
		}
		// Compares the ports
		i = port - p.port;
		if (i != 0) {
			return i;
		}
		// Compares the incarnation numbers
		return (incarnation - p.incarnation);
	}

	/**
	 * This static method constructs a new PID instance based on the formatted information given in the 
	 * String passed as parameter.
	 * 
	 * @param pid The string containing the information of the new PID instance 
	 * @return The newly created PID instance
	 * @throws UnknownHostException
	 */
	public static PID parsePID(String pid) throws UnknownHostException {
		MessageFormat mf = new MessageFormat(format);
		Object[] o = mf.parse(pid, new ParsePosition(0));
		return new PID(
			InetAddress.getByName((String) o[0]),
			((Number) o[1]).intValue(),
			((Number) o[2]).intValue());
	}

	/**
	 * This method is similar to parsePID, but it uses the console to ask for the ip, port and incarnation.
	 * This method is tipically used in test programs.
	 * It reads the data asked from the BufferedReader passed as parameter.
	 * 
	 * @param in The BufferedReader to read the data from
	 * @return A newly created PID instance initialised with data provided through the BufferedReader
	 * @throws IOException
	 */
	public static PID readPID(BufferedReader in) throws IOException {
		String host = null;
		int port = 0;
		int incarnation = 0;
		String str = null;

		System.out.print(
			"Host name (ENTER ->" + InetAddress.getLocalHost() + ") :");
		host = in.readLine();
		if (host.length() == 0) {
			host = InetAddress.getLocalHost().getHostAddress();
		}
		System.out.print("Port number: ");
		port = Integer.parseInt(in.readLine());
		System.out.print("Incarnation number (ENTER -> 0) :");
		str = in.readLine();
		if (!str.equals("")) {
			incarnation = Integer.parseInt(str);
		}
		return new PID(InetAddress.getByName(host), port, incarnation);
	}

    /*
     * Code for uka.transport serialization
     */
     //TODO: try 16... if it works, leave it at 16 (for IP6)
     protected static final int nbytesIP = 4;

    protected static final int _SIZE = uka.transport.BasicIO.SIZEOF_int + 
        uka.transport.BasicIO.SIZEOF_int +
        nbytesIP * uka.transport.BasicIO.SIZEOF_byte; 

    /** Used by uka.transport.UnmarshalStream to unmarshal the object */
    public  PID(uka.transport.UnmarshalStream _stream)
      throws java.io.IOException, ClassNotFoundException
    {
      this(_stream, _SIZE);
      _stream.accept(_SIZE);
    }

    protected PID(uka.transport.UnmarshalStream  _stream, int _size)
      throws java.io.IOException, ClassNotFoundException
    {
      _stream.request(_size); 
      byte[] _buffer = _stream.getBuffer();
      int    _pos    = _stream.getPosition();
      incarnation = uka.transport.BasicIO.extractInt(_buffer, _pos);
      _pos += uka.transport.BasicIO.SIZEOF_int;
      port = uka.transport.BasicIO.extractInt(_buffer, _pos);
      _pos += uka.transport.BasicIO.SIZEOF_int;
      byte[] addr = new byte[nbytesIP];
      _pos = uka.transport.BasicIO.extract(_buffer, _pos, addr);
      ip = InetAddress.getByAddress(addr);
    }

    /** Method of interface Transportable, it must be declared public.
        It is called from within UnmarshalStream after creating the 
        object and assigning a stream reference to it. */
    public void unmarshalReferences(uka.transport.UnmarshalStream _stream)
      throws java.io.IOException, ClassNotFoundException
    {
        //No references to unmarshall
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
      _pos = uka.transport.BasicIO.insert(_buffer, _pos, incarnation);
      _pos = uka.transport.BasicIO.insert(_buffer, _pos, port);
      _pos = uka.transport.BasicIO.insert(_buffer, _pos, ip.getAddress());
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
      ((PID) _copy).deepCloneReferences(_helper);
    
      return _copy;
    }

    /** Clone all references to other objects. Use the 
        DeepClone to resolve cycles */
    protected void deepCloneReferences(uka.transport.DeepClone _helper)
      throws CloneNotSupportedException
    {
      byte[] addr = this.ip.getAddress();
      try {
       	  InetAddress _copy = InetAddress.getByAddress(addr);
       	  _helper.add(this.ip, _copy);
       	  this.ip = _copy;
      } catch (UnknownHostException e) {
          e.printStackTrace();
          System.exit(1);
      }
    }

}
