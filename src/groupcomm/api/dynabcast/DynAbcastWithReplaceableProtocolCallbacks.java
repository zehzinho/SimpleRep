/**
*  Fortika - Robust Group Communication
*  Copyright (C) 2002-2006  Sergio Mena de la Cruz (EPFL) (sergio.mena@epfl.ch)
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
package groupcomm.api.dynabcast;

/**
 * Extension of DynAbcastCallbacks that defines how to notify the application when
 * a new protocol is installed
 * 
 * @author Olivier Rutti
 */

public interface DynAbcastWithReplaceableProtocolCallbacks extends DynAbcastCallbacks{
    /**
     * A new consensus protocol has been installed
     * 
     * @param name Name of new consensus
     */
    void newConsensus(String name);
    
    /**
     * A new abcast protocol has been installed
     * 
     * @param name Name of new abcast protocol
     */
    void newAbcast(String name);
    
    /**
     * A new gmp protocol has been installed
     * 
     * @param name Name of new gmp protocol
     */
    void newGmp(String name);   
}
