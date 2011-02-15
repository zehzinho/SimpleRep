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
package seqSamoa.services.abcast;

import framework.PID;
import framework.libraries.serialization.TInteger;

/**
 * The parameters of a call to DynAbcast
 */
public class DynAbcastCallParameters {
    /**
     * <CODE>type</CODE> denotes the type of the Abcast. (1=Message, 2=Add a
     * processt o the view, 3=Remove a process from the view)
     */
    public TInteger type;

    /**
     * <CODE>pid</CODE> denotes the process to remove or add. (null if type is
     * 1).
     */
    public PID pid;

    /**
     * Constructor
     * 
     * @param type
     *            the type of the Abcast(1=Message, 2=Add, 3=Remove)
     * @param pid
     *            the process to remove or add (if type=1, null)
     */
    public DynAbcastCallParameters(TInteger type, PID pid) {
        this.type = type;
        this.pid = pid;
    }
}
