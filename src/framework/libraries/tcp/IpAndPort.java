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
package framework.libraries.tcp;

import java.net.InetAddress;
/**
 * This class is used by Connection class to compare IPs and port numbers.
 * 
 * @author smenadel
 *
 */

public class IpAndPort{
    private int ip;
    private int port;

    public IpAndPort (InetAddress ip, int port){
	this.ip   = byteArrayToInt(ip.getAddress());
        this.port = port;
    }

    public int compareTo (IpAndPort other){
	if ( ip < other.getIp() ){
	    return -1;
	} else if ( ip > other.getIp() ) {
	    return +1;
	} else {
	    if ( port < other.getPort() ) {
		return -1;
	    } else if ( port > other.getPort() ) {
		return +1;
	    } else return 0;
	}
    }

    public int getIp ()
      { return ip; }

    public int getPort ()
      { return port; }
      
    private int byteArrayToInt (byte[] b)
      { int res = (int)(b[3]);
        res += (int)(b[2]) << 8;
        res += (int)(b[1]) << 16;
        res += (int)(b[0]) << 24;
        return res;
      }
  }

