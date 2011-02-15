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
package seqSamoa.services.network;

import framework.PID;
import framework.libraries.tcp.Connection;

/**
 * The parameters of a response to network
 */
public class NetworkResponseParameters {
	/**
	 * <CODE>code</CODE> identifies the network event
	 */
	public int code;
	
    /**
     * <CODE>connection</CODE> denotes the connection to which the event occurs.
     */
    public Connection connection;
    
    /**
     * <CODE>pid</CODE> denotes the process to which corresponds the connection
     */
    public PID pid;

    public NetworkResponseParameters(int code, Connection c, PID p) {
        this.code = code;
        this.connection = c;
        this.pid = p;
    }
}
