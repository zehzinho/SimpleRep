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
package groupcomm.common.monitoring;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import framework.Constants;
import framework.GroupCommEventArgs;
import framework.PID;
import framework.libraries.Trigger;
import framework.libraries.serialization.TSet;

/**
 * Common code for the Group Membership Layer.
 * <br />
 * Events handled :
 * <ul>
 * <li>Init</li>
 * <li>Join</li>
 * <li>Remove</li>
 * <li>Pt2PtDeliver</li>
 * <li>GDeliver (or ADeliver)</li>
 * </ul>
 * Events provided :
 * <ul>
 * <li>Agcast (or Abcast)</li>
 * <li>JoinRemoveList</li>
 * <li>Pt2PtSend</li>
 * <li>NewView</li>
 * <li>GmpDeliver</li>
 * </ul>
 * @author Sergio Mena
 * @see groupcomm.appia.gmp
 * @see groupcomm.cactus.gmp
 */
public class SimpleMonitoringImpl{
    // Logging
    private static final Logger logger =
	Logger.getLogger(SimpleMonitoringImpl.class.getName());
    
    /** The trigger used to launch events */
    protected Trigger wrapper = null;

    public SimpleMonitoringImpl(Trigger trig){
	logger.entering("SimpleMonitoring","<constr>");
	wrapper = trig;
	logger.exiting("SimpleMonitoring","<constr>");
    }
  
    /**
     * Convert all suspicions into exclusions
     *
     * @param ev the process that join. Must contain a {@link PID} as first element.
     */
    public void handleSuspect(GroupCommEventArgs ev){
	logger.entering("SimpleMonitoring","handleSuspect");

	Iterator i = ((TSet)ev.get(0)).iterator();
        while (i.hasNext()){
	    PID p = (PID)i.next();
	    logger.log(Level.FINE,"Removing PID from mbship: {0}", p);
	    GroupCommEventArgs arg = new GroupCommEventArgs();
	    arg.addLast(p);
	    wrapper.trigger(Constants.REMOVE, arg);
	}
	logger.exiting("SimpleMonitoring","handleSuspect");
    }

    /**
     * Print the current state of the layer.
     *
     * @param out The output stream used for showing infos
     */
    public void dump(OutputStream out){
	PrintStream err = new PrintStream(out);
	err.println("=========== SimpleMonitoring debug infos ============");
	err.println(" SimpleMonitoring has no state ");
	err.println("=====================================================");
    }
}
