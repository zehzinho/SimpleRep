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
package seqSamoa.exceptions;
/**
 * A <CODE>NotInAComputationException</CODE> is raised when a service call
 * or a service response is issued outside the protocol stack (i.e. outside a computation).
 * Note that in order to issue a call or a response outside the protocol stack, one has to
 * use the methods {@link seqSamoa.Service.externalCall externalCall}
 * and {@link seqSamoa.Service.externalResponse externalResponse}
 * implemented by the {@link seqSamoa.Service Service} class.
 */
@SuppressWarnings("serial")
public class NotInAComputationException extends RuntimeException {
    /**
     * Constructs an <i>NotScheduledComputationException </i> with the details
     * message given.
     * 
     * @param m
     *            the details message
     */
    public NotInAComputationException(String m) {
        super("NotInAComputationException: " + m);
    }
}; // NotScheduledComputationException
