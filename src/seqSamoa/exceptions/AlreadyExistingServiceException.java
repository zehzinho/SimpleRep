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
 * A <CODE>AlreadyExistingServiceException</CODE> is raised when a
 * {@link seqSamoa.Service service} with a name NAME is added
 * to a {@link seqSamoa.ProtocolStack stack} that already contains a
 * service with the name NAME
 */
@SuppressWarnings("serial")
public class AlreadyExistingServiceException extends Exception {
    /**
     * Constructs an <i>AlreadyBoundServiceException </i> without a details
     * message.
     * 
     * @param name
     *            the name of the {@link seqSamoa.Service Service} that we try to add
     */
    public AlreadyExistingServiceException(String name) {
        super("AlreadyExistingServiceException " + name + ": ");
    }
    
    /**
     * Constructs an <i>AlreadyBoundServiceException </i> without a details
     * message.
     * 
     * @param s
     *            the {@link seqSamoa.Service Service} that we try to add
     */
    @SuppressWarnings("unchecked")
	public AlreadyExistingServiceException(Service s) {
        super("AlreadyExistingServiceException " + s.getName() + ": ");
    }

    /**
     * Constructs an <i>AlreadyBoundServiceException </i> with the details
     * message given.
     * 
     * @param s
     *            the {@link seqSamoa.Service Service} that we try to add
     * @param m
     *            the details message
     */
    @SuppressWarnings("unchecked")
	public AlreadyExistingServiceException(Service s, String m) {
        super("AlreadyExistingServiceException " + s.getName() + ": " + m);
    }
}; // AlreadyBoundServiceException
