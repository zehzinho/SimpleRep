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
package seqSamoa.services.order;

import framework.libraries.serialization.TList;

/**
 * The parameters of a call to FIFO
 */
public class FIFOCallParameters {
    
    /**
     * <CODE> pids </CODE> is the list of process ids to which the message is sent 
     */
    public TList pids;
    
    /**
     * Constructor
     * 
     * @param pids
     *            the list of process ids to which the message is sent
     */
    public FIFOCallParameters(TList pids) {
    	super();
    	
        this.pids = pids;
    }
}
