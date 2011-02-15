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
package groupcomm.common.consensus;

import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import uka.transport.DeepClone;
import uka.transport.MarshalStream;
import uka.transport.Transportable;
import uka.transport.UnmarshalStream;
import framework.Constants;
import framework.GroupCommEventArgs;
import framework.GroupCommException;
import framework.GroupCommMessage;
import framework.PID;
import framework.libraries.Trigger;
import framework.libraries.serialization.TArrayList;
import framework.libraries.serialization.TBoolean;
import framework.libraries.serialization.THashSet;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TLinkedList;
import framework.libraries.serialization.TList;
import framework.libraries.serialization.TSet;
import framework.libraries.serialization.TSortedMap;
import framework.libraries.serialization.TTreeMap;

/** 
 * Cette classe gère une exécution de ConsensusMR. <br>
 */
public class ConsensusMRExecution implements Transportable {
    //TODO: remove implements Transportable 

    private static final Logger logger =
	Logger.getLogger(ConsensusMRExecution.class.getName());

    private TSet suspected = new THashSet();

    private Trigger trigger;
    /**
     * Identifiers of ConsensusMR messages
     */
    public static final int CONS_PROPOSE = 1;
    public static final int CONS_RBCAST = 2;
    public static final int CONS_NACK = 3;

    /* VARIABLES GLOBALES DE CONSENSUS */
    private PID myself;

    /** 
     * The Consensus number
     */
    private Transportable k;

    /** The serial number of the current round.
     * -1 means that the algorithm has not started yet.
     * +infinity means that the algorithm has finished
     * (with a decision).
     */
    private int round = -1;
    /** Number of current phase. Can be 1, 2, 3 and 4.
     */
    private int phase = -1;

    /**le coordinateur courant
     */
    private PID coordinator;

    /**l'estimation courante du processus
     */
    private Transportable estimate;
    private Transportable estimateFromC;

    // Number of estimates received. Only valid if the process is executing Phase 2.
    private int numReceived = 0;
    // We received a null value ?
    private boolean receivedNullValue;
    // Le nombre de messages à recevoir lors de l'attente d'une majorité de messages.
    private int limit;
    // Un tableau qui ne contient que l'identifiant de ce processus.
    //private PID[] self;
    // Un tableau qui contient tous les processus du groupe sauf soi-même.
    private TList others;
    // Un tableau qui contient tous les processus du groupe.
    private TList group;
    // La liste des messages délivrés à cette couche mais non encore traité car destiné
    private TSortedMap pushedBack = new TTreeMap();

    /** Compares ConsensusMRMsg with a ContentWithRound,
     * according to the round number.
     */
    private class ConsensusMRMessage implements Comparable {
	public final int round;
	public final int type;
	public final GroupCommMessage message;

	public ConsensusMRMessage(int r, int t, GroupCommMessage m) {
	    round = r;
	    type = t;
	    message = m;
	}

	public int compareTo(Object o) {
	    if (o == this) {
		return 0;
	    }
	    ConsensusMRMessage other = (ConsensusMRMessage) o;
	    if (round < other.round) {
		return -1;
	    } else if (round > other.round) {
		return +1;
	    } else if (type < other.type) {
		return -1;
	    } else if (type > other.type) {
		return +1;
	    }
	    return 0;
	}

	public String toString() {
	    return new String(
			      "(| r: "
			      + round
			      + " type: "
			      + type
			      + " msg: "
			      + message
			      + "|)");
	}
    }

    public ConsensusMRExecution(PID myself,
				Transportable k,
				TSet suspected,
				Trigger trigger) {

	this.myself = myself;
	this.k = k;
	this.suspected = suspected;
	this.trigger = trigger;
    }

    /**
     * Lance l'éxécution de consensus.
     */
    public void processStart(Transportable proposal, TList group)
	throws GroupCommException {
	estimate = proposal;
	// List of all processes in the group
	//this.group = new PID[group.size()];
	this.group = new TArrayList();
	// List of all processes in the group but myself
	this.others = new TArrayList();
	for (int i = 0; i < group.size(); i++) { //TODO: Optimize!
		PID p = (PID) group.get(i);
		this.group.add(p);
		if (!p.equals(myself)) {
			this.others.add(p);
		}
	}
	// Limit for ack and estimate messages
	limit = this.group.size() / 2;
	if (round != -1)
	    throw new GroupCommException("ConsensusMRExecution: Calling start while round != -1!!");

	incRound();
    }

    /**
     * Réception de l'estimation du coordinateur lors de la phase 3.
     */
    public void processSuspect(int r, GroupCommMessage m)
	throws GroupCommException {
	if (r > round){
	    // The message is for a future round
	    // We keep it for later handling
	    pushback(r, CONS_NACK, m);
	    logger.log(Level.FINE,
		       "A NACK was pushed back. Current pushed-back messages: {0}",
		       pushedBack);}
	if (r != round)
	    // The message is not for this round
	    return;

	if (phase == 1){
	    // I am the coordinator => BAD!
	    if (myself.equals(coordinator)) {
		throw new GroupCommException(
					     "Unexpected message received in round "
					     + r
					     + "by coordinator: "
					     + m);
	    }

	    estimateFromC = null;

	    sendSuspect();
	    initReceivedEstimate();
	    receivedEstimate(null);
	} else if (phase == 2){
	    receivedEstimate(null);
	}
    }

    /**
     * Réception de l'estimation du coordinateur lors de la phase 3.
     */
    public void processPropose(int r, GroupCommMessage m)
	throws GroupCommException {
	if (r > round){
	    // The message is for a future round
	    // We keep it for later handling
	    pushback(r, CONS_PROPOSE, m);
	    logger.log(Level.FINE,
		       "An ACK was pushed back. Current pushed-back messages: {0}",
		       pushedBack);}
	if (r != round)
	    // The message is not for this round
	    return;

	if (phase == 1){
	    // I am the coordinator => BAD!
	    if (myself.equals(coordinator)) {
		throw new GroupCommException(
					     "Unexpected message received in round "
					     + r
					     + "by coordinator: "
					     + m);
	    }

	    estimateFromC = m.tunpack();

	    sendPropose();
	    initReceivedEstimate();
	    receivedEstimate(estimateFromC);
	} else if (phase == 2){
	    Transportable content = m.tunpack();
	    
	    receivedEstimate(content);
	}
    }

    /**
     * Envoit d'un NACK au coordinateur si et seulement si celui-ci est suspecté.
     */
    public void processSuspicion(TSet suspected) throws GroupCommException {
	this.suspected = suspected;

	//ConsensusMR is not working (either not started yet or already finished)
	if (round == Integer.MAX_VALUE || round == -1)
	    return;

	if (!myself.equals(coordinator) && suspected.contains(coordinator)) {
	    if (phase == 1){
		estimateFromC = null;

		sendSuspect();
		initReceivedEstimate();	
		receivedEstimate(null);
	    }
	}
    }

    /**
     * Indique si le consensus a déjà commencer pour ce processus. <br>
     *
     * @return Ce consensus a déjà commencer.
     */
    public boolean hasStarted() {
	return round > -1;
    }

    /**
     * Méthode qui le passage à un round supérieur mais aussi
     * une grande partie du passage d'une phase à l'autre.
     */
    private void incRound() throws GroupCommException {

	// Incrémente le round et change le coordinateur.
	round++;
	if(round > 1000) System.err.println("WARNING: ConsensusMR took too many rounds!!!");
	coordinator = (PID) group.get(round % group.size());
	phase = 1;
	numReceived = 0;
	estimateFromC = null;

	// In any case,
	// Pushed back messages are not treated if current round is theirs
	processPostponed();

	if (myself.equals(coordinator)) {
	    // I am the coordinator		    
	    estimateFromC = estimate;

	    sendPropose();
	    initReceivedEstimate();
	} else if (suspected.contains(coordinator)){
	    estimateFromC = null;
	    
	    sendSuspect();
	    initReceivedEstimate();	
	    receivedEstimate(null);	    
	}
    }

    // Init Reception
    private void initReceivedEstimate() 
	throws GroupCommException{
	numReceived++;
	receivedNullValue = false;
	phase = 2;

	processPostponed();
    }

    // Received a estimate in phase 2
    private void receivedEstimate(Transportable content) 
	throws GroupCommException{

	numReceived++;
	if (content == null)
	    receivedNullValue = true;

	if (numReceived > limit){
	    if (estimateFromC!=null){
		estimate = estimateFromC;

		if (receivedNullValue)
		    incRound();
		else
		    broadcastDecision();
	    } else
		incRound();
	}
    }

    // Process message postponed for the current moment
    private void processPostponed() 
	throws GroupCommException{
	while (!pushedBack.isEmpty()) {
	    TInteger rObj = (TInteger) pushedBack.firstKey();
	    int r = rObj.intValue();
	    if (r > round){
		// Messages for future round, we keep it and quit the loop
		logger.exiting("ConsensusExecution", "nextRound");
		return;
	    }
	    // We remove all messages of round r from the pushed back queue
	    TLinkedList l = (TLinkedList) pushedBack.remove(rObj);
	    if(r < round){
		logger.log(
			   Level.FINE,
			   "Discarding old messages {0} in pushedBack for round {1}", 
			   new Object[]{ l, rObj} );
	    } else { // r == round
		logger.log(
			   Level.FINE,
			   "Processing messages {0} in pushedBack for round {1}", 
			   new Object[]{ l, rObj} );
		Iterator it = l.iterator();
		while(it.hasNext()){
		    GroupCommMessage m = (GroupCommMessage) it.next();
		    int type = ((TInteger)m.tunpack()).intValue();
		    switch (type) {
		    case CONS_NACK :
			processSuspect(r, m);
			break;
		    case CONS_PROPOSE :
			processPropose(r, m);
			break;
		    default :
			throw new GroupCommException("Weird message type "
						     + type
						     + " in pushed back set!");
		    }
		}
	    }	
	}
    }

    private void pushback(int r, int type, GroupCommMessage m) {
	logger.entering("ConsensusExecution", "pushback");
	TInteger rObj = new TInteger(r);
	TLinkedList l = (TLinkedList) pushedBack.get(rObj);
	if(l == null){
	    l = new TLinkedList();
	    pushedBack.put(rObj, l);
	}
	m.tpack(new TInteger(type));
	l.addLast(m);
	logger.exiting("ConsensusExecution", "pushback");
    }

    private void sendPropose() {
	GroupCommMessage proposeMessage = new GroupCommMessage();
	//m = <<>>
	proposeMessage.tpack(estimateFromC);
	//m = <<estimate>>
	proposeMessage.tpack(new TInteger(round));
	//m = <<round::estimate>>
	proposeMessage.tpack(new TInteger(CONS_PROPOSE));
	//m = <<CONS_PROPOSE::round::estimate>>
	proposeMessage.tpack(k);
	//m = <<k::CONS_PROPOSE::round::estimate>>
	triggerSend(proposeMessage, others);
    }

    private void sendSuspect() {
	GroupCommMessage suspectMessage = new GroupCommMessage();
	//m = <<>>
	suspectMessage.tpack(new TInteger(-1));
	//m = <<estimate>>
	suspectMessage.tpack(new TInteger(round));
	//m = <<round::estimate>>
	suspectMessage.tpack(new TInteger(CONS_NACK));
	//m = <<CONS_PROPOSE::round::estimate>>
	suspectMessage.tpack(k);
	//m = <<k::CONS_PROPOSE::round::estimate>>
	triggerSend(suspectMessage, others);
    }

    private void broadcastDecision() {
	GroupCommMessage decisionMessage = new GroupCommMessage();
	//m = <<>>
	decisionMessage.tpack(group);
	//m = <<group>>
	//decisionMessage.pack(hardClone(estimate));//Because I'm sending to myself
	decisionMessage.tpack(estimate);
	//m = <<decision::group>>
	decisionMessage.tpack(new TInteger(CONS_RBCAST));
	//m = <<CONS_BROADCAST::decision::group>>
	decisionMessage.tpack(k);
	//m = <<k::CONS_BROADCAST::decision::group>>
	triggerSend(decisionMessage, group);
    }

    private void triggerSend(GroupCommMessage m, TList g) {
	for (int i = 0; i < g.size(); i++) {
	    triggerSend(m.cloneGroupCommMessage(), (PID) g.get(i));
	}
    }

    private void triggerSend(GroupCommMessage m, PID p) {
	GroupCommEventArgs pt2ptSend = new GroupCommEventArgs();
	pt2ptSend.addLast(m);
	pt2ptSend.addLast(p);
	pt2ptSend.addLast(new TBoolean(false)); // not promisc
	logger.log(
		   Level.FINE,
		   "Sending Pt2Pt message {0} to {1}",
		   new Object[] { m, p });
	trigger.trigger(Constants.PT2PTSEND, pt2ptSend);
    }
    
    public String toString() {
	return new String(
			  "(** k: "
			  + k
			  + " r: "
			  + round
			  + " phase: "
			  + phase
			  + " coord: "
			  + coordinator
			  + " nbEstimate: "
			  + numReceived
			  + " estimate: "
			  + estimate
			  + " estimateFromC: "
			  + estimateFromC
			  + " suspected: "
			  + suspected
			  //				+ " group: "
			  //				+ group
			  + " pushedBack: "
			  + pushedBack
			  + "**)");
    }

	//TODO:remove!
    public void marshal(MarshalStream arg0) throws IOException {
        throw new IOException("unimplemented!");
	}
	public void unmarshalReferences(UnmarshalStream arg0) throws IOException, ClassNotFoundException {
        throw new IOException("unimplemented!");
	}
	public Object deepClone(DeepClone arg0) throws CloneNotSupportedException {
        throw new CloneNotSupportedException("unimplemented!");
	}
}
