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
import framework.libraries.serialization.THashMap;
import framework.libraries.serialization.THashSet;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TLinkedList;
import framework.libraries.serialization.TMap;
import framework.libraries.serialization.TSet;

public class HBHandler{

    private PID myself;
    private static final int DEFAULT_SEND_TIMEOUT    = 1000; //milliseconds
    private static final int DEFAULT_SUSPECT_TIMEOUT = 3;    //retries
    private static final int DEFAULT_SUSPECT_ASKING  = 3;
    
    // Constants for communication
    private static final int ASK_LISTEN = 0;
    private static final int LISTEN     = 1;
    private static final int HEARTBEAT  = 2;

    // Set of monitored processes
    private TMap listenProcesses = null;
    // Set of processes we want to monitor
    private TMap askListenProcesses = null;
    // Set of processes we send HeartBeat
    private TLinkedList speakProcesses = null;

    // Set of Suspected processes
    private TSet suspects = null;
    
    // Object that routes outgoing events
    private Trigger trigger = null;

    // Interface to framework's timer facilities
    private Timer timer = null;

    // Number of timeouts before suspecting a process
    private int n = DEFAULT_SUSPECT_TIMEOUT;

    // Number of demands before suspecting a process
    private int nD = DEFAULT_SUSPECT_ASKING;

    // Timeout value (milliseconds)
    private int sendTimeOut = DEFAULT_SEND_TIMEOUT;

    private static final Logger logger =
	Logger.getLogger(HBHandler.class.getName());
  
    public HBHandler (Trigger trigger, 
		      Timer timer,
		      PID myself,
		      int sendTimeOut, 
		      int suspectTimeOut,
		      int suspectAsking){

	logger.entering("HBHandler","<constr> 4 parameters");
        this.trigger = trigger;
        this.timer = timer;
        this.myself = myself;

        speakProcesses = new TLinkedList();
	listenProcesses = new THashMap();
	askListenProcesses = new THashMap();
        suspects = new THashSet(); //Repéré par A. Klaey
	if (sendTimeOut > 0)
	    this.sendTimeOut = sendTimeOut;
	if (suspectTimeOut > 0)
	    this.n = suspectTimeOut;
	if (suspectAsking > 0)
	    this.nD = suspectAsking;
	logger.exiting("HBHandler","<constr> 4 parameters");
    }

    public TLinkedList getState(){
	TLinkedList state = new TLinkedList();
	TSet tmpProcesses = new THashSet();
	
	tmpProcesses.addAll(listenProcesses.keySet());
	tmpProcesses.addAll(askListenProcesses.keySet());
	tmpProcesses.addAll(speakProcesses);
	state.addLast(tmpProcesses);
	state.addLast(suspects);
	
	return state;
    }
    
    public void setState(TLinkedList state){
	askListenProcesses.clear();
	listenProcesses.clear();
	speakProcesses.clear();

	TSet tmpProcesses = (TSet) state.removeFirst();
	suspects = (TSet) state.removeFirst();
       
	Iterator it = tmpProcesses.iterator();
	while(it.hasNext()){
	    PID p = (PID) it.next();

	    if (!askListenProcesses.containsKey(p) &&
	        !p.equals(myself)) {
		
		askListenProcesses.put(p, new TInteger(0));

		TLinkedList l = new TLinkedList();
		l.addLast(p);
		l.addLast(new TInteger(ASK_LISTEN));

		timer.schedule(l, true, sendTimeOut);
		triggerAskListen(p);
	    }

	    if (!speakProcesses.contains(p) &&
		!p.equals(myself)) {

		speakProcesses.add(p);

		TLinkedList l = new TLinkedList();
		l.addLast(p);
		l.addLast(new TInteger(HEARTBEAT));

		timer.schedule(l, true, sendTimeOut);
		triggerHeartBeat(p);
	    }	    
	}

	triggerSuspect();
    }
      
    /**
     * Le handler de l'événement <i>StartStopMonitor</i>. <br>
     * Cet événement permet de débuter ou d'arreter le failure detector
     * pour certains processus.
     *
     * @param e <dl>
     *              <dt> start : Set </dt> <dd> L'ensemble de processus que nous devons commencer à monitorer. </dd>
     *              <dt> stop  : Set </dt> <dd> L'ensemble de processus que nous devons arrêter de monitorer.</dd>
     *          </dl>
     */
    public void handleStartStopMonitor (GroupCommEventArgs e)
	throws GroupCommException{

	logger.entering("HBHandler","handleStartStopMonitor");
	TSet start = (TSet)e.removeFirst();
	TSet stop = (TSet)e.removeFirst();

	//Checks if there is some process in both sets
	Iterator i = stop.iterator();
        while (i.hasNext()){
	    PID p = (PID)i.next();
            if (start.contains(p)){
                throw new GroupCommException();
	    }
	}

        stop(stop);
        start(start);
	logger.exiting("HBHandler","handleStartStopMonitor");
      }

    /**
     * Le "handler" d'un <i>TimeOut</i>. <br>
     * Le FDPeriodicTimer appellera cette méthode lorsque le timer
     *  du processus <i>p</i> expire.
     *
     * @param  p : Le processus auquel nous devons envoyer un Alive.
     */
    public void handleTimeOut(GroupCommEventArgs arg){
	logger.entering("HBHandler","handleTimeOut");
	TLinkedList l = (TLinkedList) arg.get(0);

	PID pid = (PID) l.getFirst();
	int role = ((TInteger) l.getLast()).intValue();
	int timeOut;

	switch(role){	    
	case ASK_LISTEN:
	    timeOut = ((TInteger)askListenProcesses.get(pid)).intValue();
	    timeOut++;

	    if (timeOut < nD){ 
		// Timeout not yet expired
		askListenProcesses.put(pid, new TInteger(timeOut));
		triggerAskListen(pid);
	    } else {
		// Timeout expired
		if(!suspects.contains(pid)){
		    suspects.add(pid);
		    triggerSuspect();
		}
	    }
	    break;
	case LISTEN:
	    timeOut = ((TInteger)listenProcesses.get(pid)).intValue();
	    timeOut++;

	    if (timeOut < n){ 
		// Timeout not yet expired
		listenProcesses.put(pid, new TInteger(timeOut));
	    } else {
		// Timeout expired
		if(!suspects.contains(pid)){
		    suspects.add(pid);
		    triggerSuspect();
		}
	    }	    
	    break;
	case HEARTBEAT:
	    triggerHeartBeat(pid);	    
	    break;
	}

	logger.exiting("HBHandler","handleTimeOut");
    }

    /**
     * Le handler de l'événement <i>Alive</i>. <br>
     * Cet événement indique au failure detector que le processus <i>src</i> est vivant.
     *
     * @param e <dl>
     *              <dt> src    : PID     </dt> <dd> Le processus ayant émis les message. </dd>
     *              <dt> origin : boolean </dt> <dd> Vaut true si src est à l'origine du Alive, et faux si src répond à un de nos Alive's.</dd>
     *          </dl>
     */
    public void handleUDPReceive(GroupCommEventArgs e){
	//throws InvalidHostException{

	logger.entering("HBHandler","handleAlive");
	GroupCommMessage msg = (GroupCommMessage)e.removeFirst();
	PID src = (PID) msg.tunpack();
	int role = ((TInteger) msg.tunpack()).intValue();
	PID dest = (PID) msg.tunpack();

        if (!dest.equals(myself)){
	    //Maybe I'm another incarnation. Discard it
	    logger.exiting("HBHandler","handleAlive");
	    return;
	}
	if (role == HEARTBEAT){
	    if(askListenProcesses.containsKey(src)){
		askListenProcesses.remove(src);
		TLinkedList l = new TLinkedList();
		l.addLast(src);
		l.addLast(new TInteger(ASK_LISTEN));
		
		timer.cancel(l);	    
		
		TLinkedList l2 = new TLinkedList();
		l2.addLast(src);
		l2.addLast(new TInteger(LISTEN));

		timer.schedule(l2, true, sendTimeOut);

		listenProcesses.put(src, new TInteger(0));
	    }else if(listenProcesses.containsKey(src)){
		//I monitor p
		// I have to reset its timer
		// and remove it from the suspects (if he was inside)
		listenProcesses.put(src, new TInteger(0));

		TLinkedList l = new TLinkedList();
		l.addLast(src);
		l.addLast(new TInteger(LISTEN));

		timer.reset(l);
	    }

	    if(suspects.contains(src)){
		suspects.remove(src);

		if (listenProcesses.containsKey(src)){
		    TLinkedList l = new TLinkedList();
		    l.addLast(src);
		    l.addLast(new TInteger(LISTEN));

		    listenProcesses.remove(src);
		    timer.cancel(l);

		    TLinkedList l2 = new TLinkedList();
		    l2.addLast(src);
		    l2.addLast(new TInteger(ASK_LISTEN));
		    
		    askListenProcesses.put(src, new TInteger(0));
		    timer.schedule(l2, true, sendTimeOut);
		}else if (askListenProcesses.containsKey(src)){
		    TLinkedList l = new TLinkedList();
		    l.addLast(src);
		    l.addLast(new TInteger(ASK_LISTEN));

		    askListenProcesses.put(src, new TInteger(0));
		    timer.reset(l);
		}

		triggerSuspect();
	    }
	}else if (role == ASK_LISTEN){
	    if (!speakProcesses.contains(src)){
		speakProcesses.add(src);
		TLinkedList l = new TLinkedList();
		l.addLast(src);
		l.addLast(new TInteger(HEARTBEAT));

		timer.schedule(l, true, sendTimeOut);
	    }else{
		TLinkedList l = new TLinkedList();
		l.addLast(src);
		l.addLast(new TInteger(HEARTBEAT));

		timer.reset(l);
	    }	

	    triggerHeartBeat(src);

	    if(suspects.contains(src)){
		suspects.remove(src);

		if (listenProcesses.containsKey(src)){
		    TLinkedList l = new TLinkedList();
		    l.addLast(src);
		    l.addLast(new TInteger(LISTEN));

		    listenProcesses.remove(src);
		    timer.cancel(l);

		    TLinkedList l2 = new TLinkedList();
		    l2.addLast(src);
		    l2.addLast(new TInteger(ASK_LISTEN));
		    
		    askListenProcesses.put(src, new TInteger(0));
		    timer.schedule(l2, true, sendTimeOut);
		}else if (askListenProcesses.containsKey(src)){
		    TLinkedList l = new TLinkedList();
		    l.addLast(src);
		    l.addLast(new TInteger(ASK_LISTEN));

		    askListenProcesses.put(src, new TInteger(0));
		    timer.reset(l);
		}

		triggerSuspect();
	    }	
	}
	logger.exiting("HBHandler","handleAlive");
    }

    /**
     * Commence à monitorer les processus dans <i>s</i>, si ils ne l'étaient pas déjà.
     */
    private void start (TSet s){
	logger.entering("HBHandler","start");
	boolean startSuspect = false;

	TLinkedList toRemove = new TLinkedList();
	Iterator k = suspects.iterator();
	while(k.hasNext()){
	    PID p = (PID)k.next();

	    if(!listenProcesses.containsKey(p) && 
	       !askListenProcesses.containsKey(p)){
		if(s.contains(p)){
		    startSuspect = true;
		} else {
		    toRemove.add(p);
		}
	    }
	}

	while(!toRemove.isEmpty())
	    suspects.remove(toRemove.removeFirst());

	Iterator i = s.iterator();
        while (i.hasNext()){
	    PID p = (PID)i.next();
	    //If the process is already being monitored, do nothing
	    //If the process is myself, do nothing, either
	    if (!listenProcesses.containsKey(p) &&
		!askListenProcesses.containsKey(p) &&
		!p.equals(myself)){

		TLinkedList l = new TLinkedList();
		l.addLast(p);
		l.addLast(new TInteger(ASK_LISTEN));

		timer.schedule(l, true, sendTimeOut);

		askListenProcesses.put(p, new TInteger(0));
		triggerAskListen(p);
	    }
	}

	if (startSuspect)
	    triggerSuspect();

	logger.exiting("HBHandler","start");
    }
      
    /**
    * Arrête de monitorer les processus contenus dans <i>s</i>.
    * Tous les processus dans <i>s</i> doivent être monitorés.
    */
    private void stop (TSet s){
	logger.entering("HBHandler","stop");
	Iterator i = s.iterator();
	while (i.hasNext()){
	    PID p = (PID)i.next();
	    if(listenProcesses.containsKey(p)){
		listenProcesses.remove(p);

		TLinkedList l = new TLinkedList();
		l.addLast(p);
		l.addLast(new TInteger(LISTEN));

		timer.cancel(l);
	    }else if(askListenProcesses.containsKey(p)){
		askListenProcesses.remove(p);

		TLinkedList l = new TLinkedList();
		l.addLast(p);
		l.addLast(new TInteger(ASK_LISTEN));

		timer.cancel(l);
	    }
	}
	logger.exiting("HBHandler","stop");
    }

    private void triggerAskListen(PID dest){
	logger.entering("HBHandler","triggerAskListen");
	GroupCommMessage m = new GroupCommMessage();
	m.tpack(dest);
	m.tpack(new TInteger(ASK_LISTEN));
	m.tpack(myself);

	GroupCommEventArgs e = new GroupCommEventArgs();
	e.addLast(m);
	e.addLast(dest);
	trigger.trigger(Constants.ALIVE, e);
	logger.exiting("HBHandler","triggerAskListen");
    }

    private void triggerHeartBeat(PID dest){
	logger.entering("HBHandler","triggerHeartBeat");
	GroupCommMessage m = new GroupCommMessage();
	m.tpack(dest);
	m.tpack(new TInteger(HEARTBEAT));
	m.tpack(myself);
		  
	GroupCommEventArgs e = new GroupCommEventArgs();
	e.addLast(m);
	e.addLast(dest);
	trigger.trigger(Constants.ALIVE, e);
	logger.exiting("HBHandler","triggerAlive");
    }

    private void triggerSuspect(){
	logger.entering("HBHandler","triggerSuspect");
	THashSet s = new THashSet(suspects);
	GroupCommEventArgs e = new GroupCommEventArgs();
	e.addFirst(s);
	trigger.trigger(Constants.SUSPECT, e);
	logger.exiting("HBHandler","triggerSuspect");
    }

    /**
     * Used for debugging. </br>
     * Undocumented.
     */
    public void dump (OutputStream out) {
	PrintStream err = new PrintStream(out);
	err.println("========= HBHandler: dump =========");
	err.println(" Monitored Processes: " + listenProcesses);
	err.println(" Asking    Processes: " + askListenProcesses);
	err.println(" Speaking  Processes: " + speakProcesses);
	err.println(" Suspected Processes: " + suspects);
	err.println(" Send timeout: " + sendTimeOut);
	err.println(" Suspect timeout: " + n);
	err.println("===================================");
    }
}
