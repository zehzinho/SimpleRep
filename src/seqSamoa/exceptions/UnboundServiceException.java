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

import seqSamoa.Service;

/**
 * A <CODE>UnboundServiceException</CODE> is raised when calling a
 * {@link seqSamoa.Service Service} without any
 * {@link seqSamoa.Service.Executer Executer} bound and any default provider
 * set.
 * 
 * @see Service
 * @see Service#setDefaultProvider
 */
@SuppressWarnings("serial")
public class UnboundServiceException extends RuntimeException {
    /**
     * Constructs an <i>UnboundServiceException </i> without a details message.
     * 
     * @param s
     *            the {@link seqSamoa.Service Service}without linked
     *            {@link seqSamoa.Service.Executer Executer}
     */
    @SuppressWarnings("unchecked")
	public UnboundServiceException(Service s) {
        super("UnboundServiceException " + s.getName() + ": ");
    }

    /**
     * Constructs an <i>UnboundServiceException </i> with the details message
     * given. <br>
     * 
     * @param s
     *            the {@link seqSamoa.Service Service}without linked
     *            {@link seqSamoa.Service.Executer Executer}
     * @param m
     *            the details message
     */
    @SuppressWarnings("unchecked")
	public UnboundServiceException(Service s, String m) {
        super("UnboundServiceException " + s.getName() + ": " + m);
    }
}; // UnboundServiceException
