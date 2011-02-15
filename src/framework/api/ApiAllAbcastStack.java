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
package framework.api;

import uka.transport.Transportable;

/**
 * Minimal interface that applications need to be able to use an Atomic Broadcast protocol stack.
 * 
  * @author Sergio MENA
 *
 */
public interface ApiAllAbcastStack{
    /**
     * Atomic broadcast the message passed as parameter. The calling thread may be blocking if the flow 
     * control is blocked.
     * 
     * @param message
     */
    public void abcastMessage(Transportable message);
    /**
     * Used for debugging the stack. The state of all layers and services is printed on the 
     * {@link java.io.OutputStream} passed as parameter.
     * 
     * @param stream The output stream to print the state of the stack
     */
    public void sendDump(java.io.OutputStream stream);
    /**
     * Used for debugging the stack
     * 
     * @param mode If it is true, the stack prints tons of debugging messages all the time.
     * If it is false, the stack stops printing debugging messages.
     * @param stream An output stream to print the debugging messages
     */
    public void sendDebug(boolean mode, java.io.OutputStream stream);
    /**
     * TODO: This is useless... all objects have this method
     */
    public void finalize();
}
