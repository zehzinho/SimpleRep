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
package seqSamoa.services.rpt2pt;

import seqSamoa.services.udp.UDPCallParameters;
import framework.PID;

import framework.libraries.serialization.TBoolean;

/**
 * The parameters of a call to RPT2PT
 */
public class RPT2PTCallParameters extends UDPCallParameters {
    /**
     * <CODE>added</CODE> is true if a message is promisc (the destination do
     * not have to know the source to deliver the message) if send is true.
     * Otherwise, it is true for adding a process to the list of known processes
     * and false for removing a process.
     */
    public TBoolean added; // if (!send) "add or remove" else "promisc or not"

    /**
     * <CODE>send</CODE> is true for sending a message and false for changing
     * the list of know processes
     */
    public TBoolean send;

    /**
     * Constructor
     * 
     * @param pid
     *            a process ID
     * @param added
     *            true for promisc/add
     * @param send
     *            true for sending a message
     */
    public RPT2PTCallParameters(PID pid, TBoolean added, TBoolean send) {
        super(pid);
        this.added = added;
        this.send = send;
    }

    /**
     * Constructor translating UDPCallParameters to RPT2PTCallParameters
     * 
     * @param udpparams
     *            the UDPCallParameters
     */
    public RPT2PTCallParameters(UDPCallParameters udpparams) {
        super(udpparams.pid);
        this.added = new TBoolean(true);
        this.send = new TBoolean(true);
    }

    public String toString() {
        return new String("PID :" + pid + " added: " + added.booleanValue()
                + " send: " + send.booleanValue());
    }
}
