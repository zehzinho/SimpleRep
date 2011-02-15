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

import javax.swing.JOptionPane;

import framework.Constants;
import framework.GroupCommEventArgs;
import framework.PID;
import framework.libraries.Trigger;
import framework.libraries.serialization.TSet;

public class InteractiveMonitoringImpl{
    private static final Logger logger =
	Logger.getLogger(InteractiveMonitoringImpl.class.getName());

    /** The trigger used to launch events */
    protected Trigger wrapper = null;

    /**
     * Constructor
     *
     * @param trig the trigger used to launch events
     */
    public InteractiveMonitoringImpl(Trigger trig){
	logger.entering("InteractiveMonitoring","<constr>");
	wrapper = trig;
	logger.exiting("InteractiveMonitoring","<constr>");
    }
  
    /**
     * To handle suspicion of TCP connection
     *
     * @param ev  the process with whom we have TCP overflow. 
     *            Must contain a {@link PID} as first element.
     */
    public void handleSuspect(GroupCommEventArgs ev){
	logger.entering("InteractiveMonitoring","handleSuspect");

	Iterator i = ((TSet)ev.get(0)).iterator();
	while (i.hasNext()){
	    PID p = (PID)i.next();
	    int res = JOptionPane.showConfirmDialog(null,"The connection with "+ p + " is overloaded! Do you want to remove this problematic process form view?","TCP OVERLOW",JOptionPane.YES_NO_OPTION,JOptionPane.QUESTION_MESSAGE);

	    if (res == 1){
		logger.log(Level.FINE,"Removing PID from mbship: {0}", p);
		GroupCommEventArgs arg = new GroupCommEventArgs();
		arg.addLast(p);
		wrapper.trigger(Constants.REMOVE, arg);
	    }
	}

	logger.exiting("InteractiveMonitoring","handleSuspect");
    }

    /**
     * Print the current state of the layer.
     *
     * @param out The output stream used for showing infos
     */
    public void dump(OutputStream out){
	PrintStream err = new PrintStream(out);
	err.println("========= InteractiveMonitoring debug infos =========");
	err.println(" InteractiveMonitoring has no state ");
	err.println("=====================================================");
    }
}
