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
package seqSamoa.services.pt2pt;

import framework.PID;

import uka.transport.Transportable;

/**
 * The parameters of a call to PT2PT
 */
public class PT2PTCallParameters {
    /**
     * <CODE>pid</CODE> denotes the destination of the message delivered with
     * PT2PT
     */
    public PID pid;

    /**
     * <CODE>key</CODE> denotes the id of the message delivered with PT2PT
     */
    public Transportable key;

    /**
     * Constructor
     * 
     * @param pid
     *            the sender of the message delivered with PT2PT
     * @param key
     *            the id of the message delivered with PT2PT
     */
    public PT2PTCallParameters(PID pid, Transportable key) {
        this.pid = pid;
        this.key = key;
    }
}
