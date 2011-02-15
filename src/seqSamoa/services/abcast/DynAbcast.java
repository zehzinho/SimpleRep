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

import seqSamoa.ProtocolStack;
import seqSamoa.Service;
import seqSamoa.exceptions.AlreadyExistingServiceException;

/**
 * <CODE>DynAbcast</CODE> delivers all messages in the same order on all
 * processes member of the current view. (Total Ordered View Synchrony)
 */
public class DynAbcast extends
        Service<DynAbcastCallParameters, DynAbcastResponseParameters> {
    public DynAbcast(String name, ProtocolStack stack) throws AlreadyExistingServiceException {
        super(name, stack);
    }
};