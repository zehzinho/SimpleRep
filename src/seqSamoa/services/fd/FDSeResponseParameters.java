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
package seqSamoa.services.fd;

import framework.libraries.serialization.TMap;
import framework.libraries.serialization.TSet;

/**
 * The parameters of a response to FDSe
 */
public class FDSeResponseParameters {
    /**
     * <CODE>s</CODE> denotes the set of suspected processes
     */
    public TSet suspected;

    /**
     * <CODE>s</CODE> denotes the map of processes with number of recoveries
     */
    public TMap epoch;

    /**
     * Constructor
     * 
     * @param suspected
     *            the set of supsected processes
     * @param epoch
     *            the map of processes with number of recoveries
     */
    public FDSeResponseParameters(TSet suspected, TMap epoch) {
        this.suspected = suspected;
        this.epoch = epoch;
    }
}
