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
package seqSamoa.services.gms;

import framework.PID;
import framework.libraries.serialization.TBoolean;

/**
 * The parameters of a call to ManageView
 */
public class ManageViewCallParameters {
    /**
     * <CODE>pid</CODE> denotes the process to be added or removed to the view
     */
    public PID pid;

    /**
     * <CODE>add</CODE> true if the process must be added to the view.
     */
    public TBoolean add;

    /**
     * Constructor
     * 
     * @param pid
     *            the process to be added or removed to the view
     * @param add
     *            true if the process must be added to the view
     */
    public ManageViewCallParameters(PID pid, TBoolean add) {
        this.pid = pid;
        this.add = add;
    }
}
