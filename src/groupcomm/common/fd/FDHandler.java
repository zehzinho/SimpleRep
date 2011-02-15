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
package groupcomm.common.fd;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.logging.Logger;

import framework.Constants;
import framework.GroupCommEventArgs;
import framework.GroupCommException;
import framework.GroupCommMessage;
import framework.PID;
import framework.libraries.Timer;
import framework.libraries.Trigger;
import framework.libraries.serialization.TBoolean;
import framework.libraries.serialization.THashMap;
import framework.libraries.serialization.THashSet;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TLinkedList;
import framework.libraries.serialization.TMap;
import framework.libraries.serialization.TSet;

public class FDHandler {

    private PID myself;
    private static final int DEFAULT_SEND_TIMEOUT = 1000; //milliseconds
    private static final int DEFAULT_SUSPECT_RETRIES = 3; //retries

    // Set of monitored processes
    private TMap processes = null;

    //Set of Suspected processes
    private TSet suspects = null;

    //Object that routes outgoing events
    private Trigger trigger = null;

    //Interface to framework's timer facilities
    private Timer timer = null;

    //Number of timeouts before suspecting a process
    private int n = DEFAULT_SUSPECT_RETRIES;

    //Timeout value (milliseconds)
    private int sendTimeOut = DEFAULT_SEND_TIMEOUT;

    private static final Logger logger =
	Logger.getLogger(FDHandler.class.getName());

    //     public FDHandler (Trigger trigger, Timer timer, PID myself){
    // 	logger.entering("FDHandler","<constr> 2 parameters");
    //         this.trigger = trigger;
    //         this.timer = timer;
    //         processes = new HashMap();
    //         suspects = new HashSet();
    // 	logger.exiting("FDHandler","<constr> 2 parameters");
    //     }

    public FDHandler(Trigger trigger,
		     Timer timer,
		     PID myself,
		     int sendTimeOut,
		     int suspectRetries) {

	logger.entering("FDHandler", "<constr> 4 parameters");
	this.trigger = trigger;
	this.timer = timer;
        this.myself = myself;
	processes = new THashMap();
	suspects = new THashSet(); //Detected by A. Klaey
	if (sendTimeOut > 0)
	    this.sendTimeOut = sendTimeOut;
	if (suspectRetries > 0)
	    this.n = suspectRetries;
	logger.exiting("FDHandler", "<constr> 4 parameters");
    }

    public TLinkedList getState(){
	TLinkedList state = new TLinkedList();
	
	state.addLast(processes.keySet());
	state.addLast(suspects);
	
	return state;
    }
    
    public void setState(TLinkedList state){
	processes.clear();

	TSet tmpProcesses = (TSet) state.removeFirst();
 	suspects = (TSet) state.removeFirst();
      
	Iterator it = tmpProcesses.iterator();
	while(it.hasNext()){
	    PID p = (PID) it.next();

	    if (!processes.containsKey(p) && 
		!p.equals(myself)) {

		processes.put(p, new TInteger(0));
		timer.schedule(p, true, sendTimeOut);
		triggerAlive(p, true);
	    }
	}

	triggerSuspect();
    }

    public void handleStartStopMonitor(GroupCommEventArgs e)
	throws GroupCommException {
	//NoSuchTaskException,
	//AlreadySchedulingException {

	logger.entering("FDHandler", "handleStartStopMonitor");
	TSet start = (TSet) e.removeFirst();
	TSet stop = (TSet) e.removeFirst();

	//Checks if there is some process in both sets
	Iterator i = stop.iterator();
	while (i.hasNext()) {
	    PID p = (PID) i.next();
	    if (start.contains(p)) {
		throw new GroupCommException();
	    }
	}

	stop(stop);
	start(start);
	logger.exiting("FDHandler", "handleStartStopMonitor");
    }

    public void handleTimeOut(GroupCommEventArgs arg) {
	logger.entering("FDHandler", "handleTimeOut");
	PID pid = (PID) arg.get(0);
	//Manage the Suspect Timeout
	int timeOut = ((TInteger) processes.get(pid)).intValue();
	timeOut++;

	if (timeOut < n) {
	    // Timeout not yet expired
	    processes.put(pid, new TInteger(timeOut));
	} else {
	    // Timeout expired
	    if (!suspects.contains(pid)) {
		suspects.add(pid);
		triggerSuspect();
	    }
	}
	//p's timer expires...
	//  time to Send a ping to p
	triggerAlive(pid, true);
	logger.exiting("FDHandler", "handleTimeOut");
    }

    public void handleAlive(GroupCommEventArgs e) {
	//throws InvalidHostException{

	logger.entering("FDHandler", "handleAlive");
	GroupCommMessage msg = (GroupCommMessage) e.removeFirst();
	PID src = (PID) msg.tunpack();
	boolean original = ((TBoolean) msg.tunpack()).booleanValue();
	PID dest = (PID) msg.tunpack();

	if (!dest.equals(myself)) {
	    //Maybe I'm another incarnation. Discard it
	    logger.exiting("FDHandler", "handleAlive");
	    return;
	}
	if (processes.containsKey(src)) {
	    //I monitor p
	    // I have to reset its timer
	    // and remove it from the suspects (if he was inside)
	    processes.put(src, new TInteger(0));
	    timer.reset(src);

	    if (suspects.contains(src)) {
		suspects.remove(src);
		triggerSuspect();
	    }
	}
	if (original) {
	    //I am not the one which launches the heartbeat.
	    // I reply
	    triggerAlive(src, false);
	}
	logger.exiting("FDHandler", "handleAlive");
    }

    private void start(TSet s) { // throws AlreadySchedulingException {
	logger.entering("FDHandler", "start");
	boolean startSuspect = false;

	//ORUTTI CHANGE (begin)
	TLinkedList toRemove = new TLinkedList();
	Iterator k = suspects.iterator();
	while (k.hasNext()) {
	    PID p = (PID) k.next();

	    //SMENADEL CHANGE (begin)
	    //Iterator i = s.iterator();
	    //boolean isInStart = false;

	    //while (i.hasNext()){
	    //PID q = (PID)i.next();
	    //if (q.equals(p)){
	    //isInStart = true;
	    //startSuspect = true;
	    //}
	    //}

	    //if (!isInStart)
	    //toRemove.add(p);

	    //We only look at processes suspected and not monitored
	    if (!processes.containsKey(p)) {
		if (s.contains(p)) {
		    startSuspect = true;
		} else {
		    toRemove.add(p);
		}
	    }
	    //SMENADEL CHANGE (end)

	}

	while (!toRemove.isEmpty())
	    suspects.remove(toRemove.removeFirst());
	//ORUTTI CHANGE (end)

	Iterator i = s.iterator();
	while (i.hasNext()) {
	    PID p = (PID) i.next();
	    //If the process is already being monitored, do nothing
	    //If the process is myself, do nothing, either
	    if (!processes.containsKey(p) && !p.equals(myself)) {
		processes.put(p, new TInteger(0));
		timer.schedule(p, true, sendTimeOut);
		triggerAlive(p, true);
	    }
	}

	//ORUTTI CHANGE (begin)	
	if (startSuspect)
	    triggerSuspect();
	//ORUTTI CHANGE (end)
	logger.exiting("FDHandler", "start");
    }

    private void stop(TSet s) { // throws NoSuchTaskException {
	logger.entering("FDHandler", "stop");
	//boolean changed = false;
	Iterator i = s.iterator();
	while (i.hasNext()) {
	    PID p = (PID) i.next();
	    if (processes.containsKey(p)) {
		processes.remove(p);
		timer.cancel(p);
	    }
	    // ORUTTI CHANGE (Begin)
	    //if(suspects.contains(p)){
	    //suspects.remove(p);
	    //changed = true;
	    //}
	}
	//if(changed) triggerSuspect();
	// ORUTTI CHANGE (End)
	logger.exiting("FDHandler", "stop");
    }

    private void triggerAlive(PID dest, boolean orig) {
	logger.entering("FDHandler", "triggerAlive");
	GroupCommMessage m = new GroupCommMessage();
	m.tpack(dest);
	m.tpack(new TBoolean(orig));
	m.tpack(myself);

	GroupCommEventArgs e = new GroupCommEventArgs();
	e.addLast(m);
	e.addLast(dest);
	trigger.trigger(Constants.ALIVE, e);
	logger.exiting("FDHandler", "triggerAlive");
    }

    private void triggerSuspect() {
	logger.entering("FDHandler", "triggerSuspect");
	THashSet s = new THashSet(suspects);
	GroupCommEventArgs e = new GroupCommEventArgs();
	e.addFirst(s);
	trigger.trigger(Constants.SUSPECT, e);
	logger.exiting("FDHandler", "triggerSuspect");
    }

    /**
     * Used for debugging. </br>
     * Undocumented.
     */
    public void dump(OutputStream out) {
	PrintStream err = new PrintStream(out);
	err.println("========= FDHandler: dump =========");
	err.println(" Monitored Processes: " + processes);
	err.println(" Suspected Processes: " + suspects);
	err.println(" Send timeout: " + sendTimeOut);
	err.println(" Suspect timeout: " + n);
	err.println("===================================");
    }
}
