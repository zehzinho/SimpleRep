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
/*
 * Created on Feb 20, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package framework.libraries;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import uka.transport.MarshalStream;
import uka.transport.Transportable;
import uka.transport.UnmarshalStream;

/**
 * @author mena
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class DefaultSerialization {
    public static byte[] marshall(Transportable message) throws IOException{
    // Outputstreams to transform objects into byte arrays.
    ByteArrayOutputStream baos  = new ByteArrayOutputStream();
    //ObjectOutputStream    oos   = new ObjectOutputStream(baos);
    MarshalStream oos = new MarshalStream(baos);

    // Writes the object
    oos.writeObject(message);
    oos.flush();

    byte[] b = baos.toByteArray();
    return b;
    }

    public static Transportable unmarshall(byte[] b) 
    throws IOException, ClassNotFoundException{
    // Inputstreams to transform byte arrays into objects.
    ByteArrayInputStream  bais  = new ByteArrayInputStream(b);
    //ObjectInputStream     ois   = new ObjectInputStream(bais);
    UnmarshalStream ois = new UnmarshalStream(bais);
    
    // Reads the object
    Transportable o = (Transportable) ois.readObject();
    return o;
    }

}
