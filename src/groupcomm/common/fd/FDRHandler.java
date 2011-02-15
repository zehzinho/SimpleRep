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
// ###############################
// Projet de semestre    I&C - LSR
// He Hui-Yang        Informatique
// Février 2005
// ###############################


package groupcomm.common.fd;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import framework.Constants;
import framework.GroupCommEventArgs;
import framework.GroupCommException;
import framework.GroupCommMessage;
import framework.PID;
import framework.libraries.Timer;
import framework.libraries.Trigger;




public class FDRHandler{

private PID myself;
//defaut value for suspicion and heartbeat 
private static final int TIME_SUSPECT=5000;
private static final int TIME_HEARTBEAT=2000;
	
//precedent and succcessor
private PID pre;
private PID suc;

//define time for suspicion
private int timeS= TIME_SUSPECT;

//define time for heartbeat
private int timeH=TIME_HEARTBEAT;

//Initialize a timer for suspicion and heartbeat
private Timer timer;

//initialize a trigger
private Trigger trigger;

//variable for detecting if precedent is suspected
public  boolean suspected=false;

//initialize a logger
private static final Logger logger=Logger.getLogger(FDRHandler.class.getName());
	
//constructor of Fault Detector R 
public FDRHandler( Trigger trigger, Timer timer,
		   PID myself,PID pre, PID suc, 
		   int timeS,int timeH) throws GroupCommException{
	logger.entering("FDRHandler","<constr> 7 parameters");
	this.trigger=trigger;
	this.timer=timer;
	this.myself=myself;
	this.pre=pre;
	this.suc=suc;
	    //precedent and successor should not be the same
        if (pre.equals(suc)){
	    throw new GroupCommException("pre and suc can not be the same");}
	   //time for suspicion must be positive
        if (timeS>0)
	    this.timeS=timeS;
	    logger.log(Level.FINE,"Scheduling timer for: {0} not periodic with time:{1}",
                           new Object[] {pre,new Integer(timeS)}); 
	    //schedule timer for precedent	    
            timer.schedule(pre,false,this.timeS);	
	    //time for heartbeat must be positive
	if (timeH>0)
	    this.timeH=timeH;
            logger.log(Level.FINE,"Scheduling timer for: {0} periodic with time:{1}",
                           new Object[] {suc,new Integer(timeH)}); 
	    //schedule timer for successor
            timer.schedule(suc,true,this.timeH);
          
	logger.exiting("FDRHandler","<constr>  7 parameters");
}


//change the precedent
public void changePre(PID newPre) throws GroupCommException{
	
            //cancel the timer for the old precedent only if it is not suspected
        if (!suspected)
	    timer.cancel(pre);
	    //validate the new precedent
        pre = newPre;
            //precedent and successor cannot be the same
        if (pre.equals(suc)){
	   throw new GroupCommException("pre and suc can not be the same");
	}
	    //reinitialize boolean suspected
	suspected=false;
        logger.log(Level.FINE,"Scheduling timer for: {0} not periodic with time:{1}",
                           new Object[] {pre,new Integer(timeS)}); 
            //schedule timer for the new precedent	    
        timer.schedule(pre,false,timeS);
        
}


//change the successor
public void changeSuc(PID newSuc) throws GroupCommException{

            //cancel timer of the lod successor
        timer.cancel(suc);
	    //validate the new successor
	suc= newSuc;
	    //precedent and successor cannot be the same
        if (suc.equals(pre)){
	    throw new GroupCommException("pre and suc can not be the same");
	}
        logger.log(Level.FINE,"Scheduling timer for: {0} periodic with time:{1}",
                           new Object[] {suc,new Integer(timeH)}); 
	    //schedule timer for the new successor
	timer.schedule(suc,true,timeH);
}

//deal with the suspicion of the precedent
public void handleUdpDeliver(GroupCommEventArgs arg){
	logger.entering("FDRHandler","handleSuspicion");
	    //obtain the precessor to suspect by unpacking from the message
        PID pid=(PID)(((GroupCommMessage)arg.get(0)).tunpack());
            //if the precessor is really his precedent
        if (pid.equals(pre)){
            if (suspected){//if it has been suspected,we have to reactive it
		 triggerSuspect(null);
		 suspected=false;
		 logger.log(Level.FINE,"Scheduling timer for: {0} not periodic with time:{1}",
                           new Object[] {pre,new Integer(timeS)}); 
	                        //schedule timer for the precedent
			    timer.schedule(pre,false,timeS);
			
	     }
	    else{
                logger.log(Level.FINE,"Resetting timer for: {0} ", pre); 
	    //reset timer for the precedent
		timer.reset(pre);
	     }
	}
	logger.exiting("FDRHandler","handleSuspicion");
}

//deal with timeout of timer
public void handleTimeout(GroupCommEventArgs arg){
            //obtain the processor from event received
        PID p=(PID)(arg.get(0));
	    //if it is the precedent, decide to suspect 
        if (p.equals(pre)){
            //if not suspected yet
           if (!suspected){
		suspected=true;
                logger.log(Level.FINE,"Suspecting : {0} ", pre); 
	    //suspect precedent
		triggerSuspect(pre);
	   }
	}
	   //if it is successor, send a heartbeat
	else if (p.equals(suc)){
            logger.log(Level.FINE,"Sending heartbeat to : {0} ", suc); 
	   //send heartbeat to successor	
            triggerSend(suc);
		 
	}
        
}

//handler of suspicion
private void triggerSuspect(PID pid){

	logger.entering("FDRHandler","triggerSuspect");
       
        GroupCommEventArgs e=new GroupCommEventArgs();
	   //add processor to event
	e.addLast(pid);
	   //trigger event
        logger.log(Level.FINE,"triggering event Suspect: {0} ", e); 
        trigger.trigger(Constants.SUSPECT,e);
	logger.exiting("FDRHandler","triggerSuspect");
}

//handler of send heartbeat
private void triggerSend(PID dest){
	
	logger.entering("FDRHandler","triggerSend");
	
        GroupCommMessage m=new GroupCommMessage();
        
           //pack myself in message m
	m.tpack(myself);
      
	GroupCommEventArgs e=new GroupCommEventArgs();
	   //add message and precessor destinated to event to be sent 
        e.addLast(m);
	e.addLast(dest);
           //trigger event
        logger.log(Level.FINE,"triggering event UdpSend: {0} ", e); 
        trigger.trigger(Constants.UDPSEND,e);
	logger.exiting("FDRHandler","triggerSend");
		
}
 /**
     * Used for debugging. </br>
     * Undocumented.
     */
public void dump(OutputStream out) {
	PrintStream err = new PrintStream(out);
	err.println("========= FDRHandler: dump =========");
	err.println(" Previous Process: " + pre);
        err.println(" Successive Process:" +suc);
	err.println(" Precedent Suspected or not: " + suspected);
	err.println(" Timeout set for suspicion: " + timeS);
       	err.println(" Timeout set for heartbeat: " + timeH);
	err.println("===================================");
    }

}
