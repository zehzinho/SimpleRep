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

package groupcomm.common.abcast;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import uka.transport.Transportable;
import framework.Constants;
import framework.GroupCommEventArgs;
import framework.GroupCommException;
import framework.GroupCommMessage;
import framework.PID;
import framework.libraries.FlowControl;
import framework.libraries.Trigger;
import framework.libraries.serialization.TArrayList;
import framework.libraries.serialization.TBoolean;
import framework.libraries.serialization.TCollection;
import framework.libraries.serialization.THashMap;
import framework.libraries.serialization.THashSet;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TList;
import framework.libraries.serialization.TMap;


/**
 * <b> This class implements the common code for token-based algorithm abcast. </b>
 * <hr>
 * <b> Events: 
 * <dt> <i>Init</i>           </dt> <dd> Initializes the abcast layer </dd>
 * <dt> <i>Abcast</i>         </dt> <dd> Send a Broadcast message, with the abcast algorithm </dd>
 * <dt> <i>Pt2PtDeliver</i>   </dt> <dd> Happend when a message is received by the lower layers </dd>
 * <dt> <i>Suspect</i>        </dt> <dd> Happend when the precedent is suspected</dd>
 * </dl>
 */
public class TokenAbcast{

  //initialized?
  private boolean initialized=false;
  
  //a list of all active processes, includes myself as well. 
  private TList processes;
  //list  of precedents 
  private TList procede;
  //list of successors
  private TList succede;
  //Known processes to send the messages
  private TArrayList known=null;

  //Set of messages that have been abroadcast by pi or by another process,and not yet ordered. 
  private TList aBroadcast=new TArrayList();
  //Sequence of messages adelivered by pi; 
  private TList aDeliver=new TArrayList();
  
  //Abcast message current id;
  private AbcastMessageID abcastId;
  
 
  //initialize a trigger for myself
  private Trigger trigger=null;
  //indicate if "pre" is suspected; 
  private boolean suspected=false;
  //a map of all token received ,pair( round,token),initialized empty
  private TMap myReceivedToken=null;
  
  //actual round of myself
  private int myRound=0;
  //identifier of myself
  private int myID=0;
  //number of precedent
  private int f;
  //votes
  private int myVotes=0;
  //direct precedent
  private PID pre=null;

  private FlowControl flow_control;
  private int fc_key;
  private int fc_threshold;
  private int fc_used = 0;
  
  private static final Logger logger = 
  Logger.getLogger(TokenAbcast.class.getName());
  public  static final int MAX_PROPOSE=50;
	
 //constructor of TokenAbcast
 public TokenAbcast(TList processes,int myID,int f,Trigger trigger,FlowControl fc)throws GroupCommException{  
        
        logger.entering("TokenAbcast", "<constr> 4 parameters");
        
        //test of condition n>=f*(f+1)+1
	if (processes.size()<=0 ||f<=0 || processes.size()<(f*(f+1)+1)){
	   
            throw new GroupCommException("construction Tokenabcast failed cause list size error");}
        
	//test if processes contains myself
        if (myID < 0 || myID>processes.size()){
	    
            throw new GroupCommException("construction Tokenabcast failed cause error myID");}
        
        
        this.myID=myID;
        this.f=f;
        this.trigger=trigger;
        this.flow_control=fc;
	this.processes=new TArrayList();
	this.processes=processes;
        
        //list of f+1 precedents
        int pred;
        pred=(myID-f-1+processes.size())%processes.size();
        this.procede=new TArrayList();
        int nbrP=f+1;
        while(nbrP>0){
        this.procede.add((PID)(processes.get(pred)));
	    pred=(pred+1)%processes.size(); 
        nbrP=nbrP-1;
        }
           
        //list of f+1 successors
        int succ=(myID+1)%processes.size();
	this.succede=new TArrayList();
        int nbrS=f+1;
        while(nbrS>0){
          this.succede.add((PID)(processes.get(succ)));
          succ=(succ+1)%processes.size();
          nbrS=nbrS-1;
        }     
        
        //direct precedent of myself
        pre=(PID)(this.procede.get(this.procede.size()-1));
	
        //construction of a new class AbcastMessageID to identify the messages sent by myself
        abcastId= new AbcastMessageID((PID)(processes.get(myID)),0);  
	
        //Initialize the map of received tokens
        myReceivedToken=new THashMap();
        
        logger.exiting("TokenAbcast", "<constr> 6 parameters");
    }	
    
 
 //public method of initialization
 public void handleInit(GroupCommEventArgs arg)throws GroupCommException{
        
        logger.entering("TokenAbcast", "handleInit");	  
        
        TList p=(TList)arg.removeFirst();	 
        if (initialized)
	   throw new GroupCommException("TokenAbcast already initialized.");
        initialized = true;
     fc_key=flow_control.getFreshKey();
        known = new TArrayList(p);
	
        //Look for duplicate processes in the group
	for(int i = 0; i < known.size() ; i++)
	  for(int j = i+1; j < known.size(); j++)
	     if( known.get(i).equals(known.get(j)) )
		throw new GroupCommException("Process" + known.get(i) +
					    " appears more than once in the group.");
	
	// flow control
	//flow_control.setThreshold(fc_key, MAX_PROPOSE / known.size() + 1);
    fc_threshold = Math.max(MAX_PROPOSE / known.size(), 1); 
	
        // join-remove
	GroupCommEventArgs jrl = new GroupCommEventArgs();
	jrl.addLast (new THashSet(p)); // join
	jrl.addLast (new THashSet()); // remove
	trigger.trigger (Constants.JOINREMOVELIST, jrl);
        
        //if it is the first processor, then it will send the tokens to the f+1 successors.
        if (myID==0){
            //construct a new token to send
	    Token tk=new Token(0, (PID)(processes.get(myID)),aBroadcast,0,new TArrayList(),aBroadcast );
            //send tokens to f+1 successors
            triggerSend(tk,succede);
	    myRound=1;
        }  
        
        //if myId is between n-f and n-1, then it will send the tokens to P1 to P(myID+f+1)  
        else if ((myID>=(processes.size()-f))&&(myID<=(processes.size()-1))){
	    //construct a "dummy" token to send with round -1 
            Token tok=new Token(-1,(PID)(processes.get(myID)),new TArrayList(),0,new TArrayList(),new TArrayList());
            
        //list contains p1 to p(myID+f+1)
        TArrayList initialSuccede=new TArrayList();
	int newPro=myID+f+1;
        int first=1;
        while(newPro>0){
          initialSuccede.add((PID)(processes.get(first)));
          first=(first+1)%processes.size();
          newPro=newPro-1;
	}                 
	    
        //send tokens to destinated processors
        triggerSend(tok,initialSuccede);
	}	  
	
        logger.exiting("TokenAbcast","handleInit");
   
  }												   
  
  
  //public method dealing with atomic broadcast
  public void handleAbcast(GroupCommEventArgs arg){      	
        logger.entering("TokenAbcast", "handleAbcast");
        
        //Flow control
	//flow_control.alloc(fc_key, 1);
    fc_used++; if(fc_used >= fc_threshold) flow_control.block(fc_key);
    
	
        // msg
	GroupCommMessage msg = (GroupCommMessage)arg.removeFirst();
    
        //get a identifier for this new message 
        AbcastMessageID id=abcastId.nextId();
        msg.tpack(id);
        
        logger.log(Level.FINE,
		   "Abroadcast message id: {0} ",id);
        //msg=id:m
	aBroadcast.add(msg);
        
        logger.exiting("TokenAbcast","handleAbcast");
  }	
  
  
  //public method dealing with token received
  public void handlePt2PtReceive(GroupCommEventArgs arg)throws GroupCommException{
        
        logger.entering("TokenAbcast", "handlePt2PtReceive");
        
        PID pro=(PID)arg.get(1);
        System.out.println("source of the token is:" +pro.toString());
        
        GroupCommMessage m=(GroupCommMessage)arg.get(0);
	Token tok = (Token) m.tunpack();
	//test if the sender of token is correct
        if (!pro.equals(tok.sender))
            throw new GroupCommException("source of token not correct");
        
        /*if receive a token from a process with an index greater,
        then the round of that token must be a previous round, 
        thus increase one*/
        if (processes.indexOf(tok.sender)>myID)
	    tok.round=tok.round+1;
        
        //test if round of token is greater or equals to myself.
        if (tok.round<myRound){
	    tokenOfSmallRound(tok);
        }
        else{
           //call private proedure to deal with this token
           tokenReceived(tok);
        }
	
        logger.exiting("TokenAbcast", "handlePt2PtReceive");
  }
 
  
  //public method  used when receive a token,but has already suspected "pre" 
  public void suspect(GroupCommEventArgs arg){
    
        logger.entering("TokenAbcast", "suspect");
       
        PID pi=(PID)arg.get(0);
       
        //if process suspected is indeed his precedent 
	if ((pi==null)||(!pi.equals(pre))){
	   suspected=false;
           System.out.println("now the precedent is not suspected");
	}
        else{
           suspected=true;
           System.out.println("precedent suspected");
                   
        //test if the token from one of his precedents exists,if it is the case,send it. 
        if (!myReceivedToken.containsKey(new TInteger(myRound)))
	   System.out.println("has not yet received a valid token"); 
	else
           processToken((Token)(myReceivedToken.get(new TInteger(myRound)))); 
         
        } 
 
       logger.exiting("TokenAbcast", "suspect");
  }

  
  //when receiving a token of smaller round than myself
  private void tokenOfSmallRound(Token tok){
      
      logger.entering("TokenAbcast", "tokenOfSmallRound");
      
      //print values of arributes of tokens
      logger.log(Level.FINE,
		 "dealing with token of small round: {0} with sender {1}\nproposalSeq is  {2}\nadeliv is {3}\n\tnextSet is  {4}\nvotes {5}", new Object[]{new Integer(tok.round),tok.sender,tok.proposalSeq,tok.adeliv,tok.nextSet,new Integer(tok.votes)});
    
      System.out.println("token of round: "+tok.round);
     
      if ((tok.adeliv).size() > aDeliver.size())			  
         triggerDelivery(tok.adeliv);
      
      //update sequence aBroadcast, aBroadcast <- aBroadcast union tok.nextSet
      int nextSetsize=0;
      while ((nextSetsize<(tok.nextSet).size()) && (!(tok.nextSet).isEmpty())) {
	
          GroupCommMessage gcmmm= (GroupCommMessage)((tok.nextSet).get(nextSetsize));
          //get identifiant of messages
          AbcastMessageID  id1 = (AbcastMessageID) gcmmm.tunpack();
          gcmmm.tpack(id1);
         
          //myList1 is a sequence of identifiants of messages in aBroadcast 
         TArrayList myList1=new TArrayList();
         int size=0;
         while ((size<aBroadcast.size()) && (!aBroadcast.isEmpty())){
	   GroupCommMessage msgI=(GroupCommMessage)(aBroadcast.get(size));
           AbcastMessageID  id2 = (AbcastMessageID) msgI.tunpack();    
           msgI.tpack(id2); 
           myList1.add(id2);
           size++;
         }
         
         //update aBroadcast by adding a new message from tok.nextSet       
         if (!myList1.contains(id1)){
             aBroadcast.add(gcmmm);
             System.out.println(" a new message is added to aBroadcast: "+ id1);
	 }    
         nextSetsize++;
            
      }
      
     
      logger.exiting("TokenAbcast", "tokenOfSmallRound");   	
  };

  
  //when receive a token from a certain process
  private void tokenReceived(Token tok)throws GroupCommException{
    
      logger.entering("TokenAbcast", "tokenReceived");

      logger.log(Level.FINE,
		   "dealing with token of small round: {0} with sender {1}\nproposalSeq is {2}\nadeliv is  {3}\n\tnextSet is {4}\nvotes {5}", new Object[]{new Integer(tok.round),tok.sender,tok.proposalSeq,tok.adeliv,tok.nextSet,new Integer(tok.votes)});
      System.out.println("token is of round: "+tok.round);
    
        
      PID p=tok.sender;
      int round=tok.round;
       
      //test if the token is from one of his precedents  
      if (!procede.contains(p))
         throw new GroupCommException("token is not from a valid precedent");
     
      //test if has already received a token with the same round as the new one
      //if not the case
      if (!myReceivedToken.containsKey(new TInteger(round))){
         myReceivedToken.put(new TInteger(round),tok);
      }
      //otherwise
      else{
         PID q=((Token)(myReceivedToken.get(new TInteger(round)))).sender;
	 //if sender of token newly received is much nearer than sender of the old one
	 if ((myID-processes.indexOf(p))%processes.size() < (myID-processes.indexOf(q))%processes.size())
           //update to new token
	   myReceivedToken.put(new TInteger(round), tok);
	}
      
      //test if token is from his direct precedent "pre",
      //if it's the case, accept it even if it not has been suspected
      if (( (p.equals(pre)) || suspected) && (round==myRound))
	 processToken(tok);
       
         logger.exiting("TokenAbcast", "tokenReceived");
  } 
	
 //deal with token
 private void processToken(Token tok){
    
        logger.entering("TokenAbcast", "processToken");
   
        
        //aBroadcast<-(aBroadcast) union (token.proposalSeq) union (token.nextSet)
        int proposalSize=0; 
        while ((proposalSize<(tok.proposalSeq).size())&&(!tok.proposalSeq.isEmpty())){
            GroupCommMessage gcm= (GroupCommMessage)((tok.proposalSeq).get(proposalSize));
            //get identifiant
            AbcastMessageID  id3 = (AbcastMessageID) gcm.tunpack();
            gcm.tpack(id3);
           
            //all ids of messages in aBroadcast 
            int size2=0;
            TArrayList myList2=new TArrayList();
            while ((size2<aBroadcast.size())&&(!aBroadcast.isEmpty())){
              GroupCommMessage msgII=(GroupCommMessage)(aBroadcast.get(size2));
              //get identifiant
              AbcastMessageID  id4 = (AbcastMessageID) msgII.tunpack();   
              msgII.tpack(id4);  
              myList2.add(id4);
              size2++;
            }    
	    //update aBroadcast, aBroadcast <- aBroadcast union tok.proposalSeq 
            if (!myList2.contains(id3)){
              aBroadcast.add(gcm);
              System.out.println("new message added to aBroadcast: " + id3);}
        
            proposalSize++;
	}
    
        int nextSize=0; 
        while ((nextSize<(tok.nextSet).size()) && (!(tok.nextSet).isEmpty())){
            GroupCommMessage gcmm= (GroupCommMessage)((tok.nextSet).get(nextSize));
            AbcastMessageID  id5 = (AbcastMessageID) gcmm.tunpack();
            gcmm.tpack(id5);
            //ids of message in aBroadcast 
	    int size3=0;
            TArrayList myList3=new TArrayList();
            while ((size3<aBroadcast.size())&&(!aBroadcast.isEmpty())){
               GroupCommMessage msgIII=(GroupCommMessage)(aBroadcast.get(size3));
               AbcastMessageID  id6 = (AbcastMessageID) msgIII.tunpack();     
               msgIII.tpack(id6);
               myList3.add(id6);
                size3++;
            }    
	    if (!myList3.contains(id5)){
	      aBroadcast.add(gcmm);
              System.out.println("new message added to aBroadcast: " + id5);
             }
	 
            nextSize++;
         
 	} 
        
        //old token
        if ((tok.adeliv).size() < aDeliver.size())
	    (tok.proposalSeq).clear();	
        
        //token with new information
        else{
	    
            triggerDelivery(tok.adeliv);
	    
            if ((((PID)(tok.sender)).equals(pre)) && (!((tok.proposalSeq).isEmpty())))
               myVotes=tok.votes+1;
	    else 
	       myVotes=1;
            
            System.out.println("now myVotes of pi is: "+myVotes);		 
            
            if (myVotes>=(f+1)){
		    triggerDelivery(tok.proposalSeq);
		    (tok.proposalSeq).clear();	
	     }
	}

        //new proposal
	if ((tok.proposalSeq).isEmpty()){
	  tok.proposalSeq=aBroadcast;
          myVotes=1;	
        }
        //now thw sender of token is myself
        tok.sender=(PID)processes.get(myID);
	
        //re-construct the token   
	tok=new Token(myRound,tok.sender,tok.proposalSeq,myVotes,aDeliver,aBroadcast);
        
        //send token to all his f+1 successors
        triggerSend(tok,succede);
        

        //update the round 
        myRound=myRound+1;

        //remove the old token from Map  myReceivedToken
        myReceivedToken.remove(new TInteger(myRound-1));
        
        logger.exiting("TokenAbcast", "processToken");
  }


 //trigger event adeliver
 private void triggerDelivery(TList col){
        logger.entering("TokenAbcast", "triggerDelivery");

 	 
        //Adeliver messages in col not in aDeliver
        int colSize=0;
        while ((colSize<col.size()) && (!col.isEmpty())){
            GroupCommMessage grc=(GroupCommMessage)(col.get(colSize));
            //get identifier
            AbcastMessageID  id7 = (AbcastMessageID) grc.tunpack();
            grc.tpack(id7);
         
        //id of message in aBroadcast 
	int aSize=0;
        TArrayList myList4=new TArrayList();
        while ((aSize<aDeliver.size()) && (!aDeliver.isEmpty())){
	    GroupCommMessage msgIV=(GroupCommMessage)(aDeliver.get(aSize));
            AbcastMessageID  id8 = (AbcastMessageID) msgIV.tunpack();     
            msgIV.tpack(id8);
            myList4.add(id8);
            aSize++;
        }    
         
        if (!myList4.contains(id7)){   
	    GroupCommEventArgs ad=new GroupCommEventArgs();
            ad.addFirst(grc);
            //Flow control (added by Sergio: 17/2/2006)
            if(id7.proc.equals(processes.get(myID))){
                fc_used--; if(fc_used < fc_threshold) flow_control.release(fc_key);
            }
            logger.log(Level.FINE,"trigger Adeliver event {0}:",ad);				
            trigger.trigger(Constants.ADELIVER,ad);
       
	    //add this message to aDeliver
            aDeliver.add(grc);
	    System.out.println("a new message added to aDeliver: "+id7);
	   }
        
        colSize++;
	}

        //update aBroadcast ,aBroadcast <- aBroadcast\ aDeliver
        int aDsize=0;
        while ((aDsize<aDeliver.size()) &&(!aDeliver.isEmpty()) ){
            GroupCommMessage grpc=(GroupCommMessage)(aDeliver.get(aDsize));
            AbcastMessageID  id9 = (AbcastMessageID) grpc.tunpack();
            grpc.tpack(id9);
          
            //id of message in aBroadcast 
            int size3=0;
            TArrayList myList5=new TArrayList();
            while ((size3<aBroadcast.size()) && (!aBroadcast.isEmpty())){
	       GroupCommMessage msgV=(GroupCommMessage)(aBroadcast.get(size3));
               AbcastMessageID  id10 = (AbcastMessageID) msgV.tunpack();     
               msgV.tpack(id10);
               myList5.add(id10);
               size3++;
         }    	
	 
         if (myList5.contains(id9)){
	       aBroadcast.remove(grpc);
               System.out.println("an old message removed from abraodcast: "+id9);
              };
          
	 aDsize++;
        
	}
         logger.exiting("TokenAbcast", "triggerDelivery");
 }
 

 //trigger event send 
 private void triggerSend(Token token, TList succede){
  
        logger.entering("TokenAbcast", "triggerSend");

        Iterator it=succede.iterator();  
        while (it.hasNext()){
          PID dest=(PID) it.next();
          System.out.println("dest is:" +((PID)dest).toString());
          //construction of message
          GroupCommMessage m=new GroupCommMessage();
          //m=token
          m.tpack((Transportable)token.clone());
          GroupCommEventArgs e=new GroupCommEventArgs();
          e.addLast(m);
          e.addLast(dest);
          e.addLast(new TBoolean(false));
          logger.log(Level.FINE,"trigger Pt2PtSend event {0}:",e);
          trigger.trigger(Constants.PT2PTSEND,e);
	
        }
        
        logger.exiting("TokenAbcast", "triggerSend"); 
  }
 
 //print values to debug 
 public void dump(OutputStream out){
    
    int i,j;
    PrintStream err=new PrintStream(out);
    err.println("=========TokenAbcast:dump==========");
    err.println("index of myself is: "+myID);
    err.println("myself is: "+((PID)(processes.get(myID))).toString());
    err.println("the list of precedents is: ");
    for(i=0;i<procede.size();i++)
	err.println(((PID)procede.get(i)).toString());
    err.println("Direct precedent is: "+pre.toString());
    err.println("the list of of successors is: ");
    for(j=0;j<succede.size();j++)
	err.println(((PID)succede.get(j)).toString()+",");
    err.println("actual round is: "+myRound);
    err.println("Suspect the previous or not: "+suspected);
    err.println("Received tokens are:");
    TCollection col=myReceivedToken.values();
    Iterator iter=col.iterator();
    while (iter.hasNext()){
	Token myToken=(Token) iter.next();
        err.println("token from: " +((PID)myToken.sender).toString()+" is of round: "+myToken.round);
    }
    err.println("myVotes is: "+myVotes);
    err.println("Set of Messages have been abroadcast is: ");
    Iterator ite=aBroadcast.iterator();
    while (ite.hasNext()){
	GroupCommMessage myMessage=(GroupCommMessage) ite.next();
        err.println("Message is: " +myMessage);
    }
    err.println("Sequence of Messages have been adelivered by myself is: ");
    Iterator iterr=aDeliver.iterator();
    while (iterr.hasNext()){
	GroupCommMessage myMes=(GroupCommMessage) iterr.next();
        err.println("Message is: " +myMes);
    }
    err.println("====================================");
  }

  }
    

