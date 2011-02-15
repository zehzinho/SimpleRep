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
 * A <CODE>InterruptedSchedulerException</CODE> is raised when the scheduler
 *  is interrupted, i.e. when one thread managed by the scheduler is interrupted.
 */
@SuppressWarnings("serial")
public class InterruptedSchedulerException extends RuntimeException {
    /**
     * Constructs an <i>InterruptedComputationException </i> without a details
     * message.
     */
    public InterruptedSchedulerException(long cid) {
        super("InterruptedComputationException " + cid + ":");
    }

    /**
     * Constructs an <i>InterruptedComputationException </i> with the details
     * message given. <br>
     * 
     * @param s
     *            the details message
     */
    public InterruptedSchedulerException(long cid, String s) {
        super("InterruptedComputationException " + cid + ":" + s);
    }
}; 