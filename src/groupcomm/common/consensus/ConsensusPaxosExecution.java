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
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TList;

/** 
 * Cette classe g�re une ex�cution de ConsensusMR. <br>
 */
public class ConsensusPaxosExecution implements Transportable{
    //TODO: remove "implements Transportable"
    /**
     * (Exclusive) variants/optimizations.
     */
    private int optVariant;

    /**
     * Variant flag: basic paxos.
     */
    protected static final int P0 = 0;

    /**
     * Variant flag: skip 1st read.
     */
    protected static final int P1 = 1;

    private static final Logger logger =
	Logger.getLogger(ConsensusPaxosExecution.class.getName());

    private Trigger trigger;
    /**
     * Identifiers of ConsensusPAXOS messages
     */
    public static final int CONS_READ = 15965;
    public static final int CONS_ACKREAD = 15966;
    public static final int CONS_NACKREAD = 15967;
    public static final int CONS_ACKWRITE = 15968;
    public static final int CONS_NACKWRITE = 15969;
    public static final int CONS_WRITE = 15970;
    public static final int CONS_DECISION = 15971;

    /* VARIABLES GLOBALES DE CONSENSUS */
    private PID myself;

    /** le num�ro du consensus.
     */
    private Transportable k;

    /** The serial number of the current round.
     * -1 means that the algorithm has not started yet.
     * +infinity means that the algorithm has finished
     * (with a decision).
     */
    private int round = -1;

    /**le leader courant
     */
    private PID leader;

    /**l'estimation courante du processus
     */
    private Transportable estimate;

    /**
     * Number of processes that execute this consensus algorithm.
     */
    private int n;

    /**
     * Latest read and write phase.
     */
    private int write = -1;
    private int read = -1;

    /**
     * Number of [Read|Write]Ack] received so far, in the current round.
     */
    private int nbAckRead;
    private int nbAckWrite;
    private int nbNack;
    private int highestWrite;

    /**
     * The round at which the process initiated a propose.
     */
    private int proposedRound = -1; 

    // Le nombre de messages � recevoir lors de l'attente d'une majorit� de messages.
    private int limit;
    protected int nackLimit;
    // Un tableau qui contient tous les processus du groupe sauf soi-m�me.
    private TList others;
    // Un tableau qui contient tous les processus du groupe.
    private TList group;

    private boolean ignoreSuspicions = false;

    public ConsensusPaxosExecution(PID myself,
				   Transportable k,
				   Trigger trigger,
				   int optVariant) {

	this.myself = myself;
	this.k = k;
	this.leader = null;
	this.trigger = trigger;
	this.optVariant = optVariant;
    }

    /**
     * Lance l'�x�cution de consensus.
     */
    public void processStart(Transportable proposal, TList group)
	throws GroupCommException {
	estimate = proposal;
	// Set the number of processes
	n = group.size();
	// List of all processes in the group
	//this.group = new PID[n];
	this.group = new TArrayList();
	// List of all processes in the group but myself
	this.others = new TArrayList();
	for (int i = 0; i < n; i++) { //TODO: Optimize!
		PID p = (PID) group.get(i);
		this.group.add(p);
		if (!p.equals(myself)) {
			this.others.add(p);
		}
	}
	// Limit for ack and estimate messages
	limit = n / 2;
        nackLimit = 1;

	// If no leader is choosed, take the first member of the list
	if (this.leader == null)
	    this.leader = (PID) this.group.get(0);

	if (round != -1)
	    throw new GroupCommException("ConsensusPaxosExecution: Calling start while round != -1!!");

	round = group.indexOf(myself);
	processPropose();
	ignoreSuspicions = false;
    }

    /**
     * Commence la proc�dure de proposition. Si l'optimisation est choisie
     * on envois directement un CONS_WRITE avce notre estimate au lieu du
     * CONS_READ pr�liminaire.
     */
    public void processPropose()
	throws GroupCommException {
	if ((!hasDecided()) && (isLeader()) && (round!=proposedRound)){
            if (optVariant == P0 || (optVariant == P1 && round != 0)) {
		sendRead();
                read = round;

            } else if (optVariant == P1 && round == 0) {
                // regular Paxos without READ on round 1.
  		sendWrite();
                read = round;
                write = round;
                // FIXME is that correct ?
            } else {
		throw new GroupCommException("ConsensusPaxosExecution: Invalid option!!!");
            }

            // reset internal counters
            nbAckRead = 0;
            nbAckWrite = 0;
            nbNack = 0;
            highestWrite = write;
            proposedRound = round;
	}
    }

    /**
     * Re�oit un read. Le consid�re seulement s'il est d'un round non 
     * connu (-> sup�rieur au round connu actuellement
     */
    public void processRead(int r, PID source) {
        if ((read > r) || (write > r))
	    sendNackRead(source, r);
	else {
	    read = r;
	    sendAckRead(source, r);
	}
    }

    /**
     * Re�oit un write. Le consid�re seulement s'il est d'un round non 
     * connu (-> sup�rieur au round connu actuellement
     */
    public void processWrite(int r, PID source, Transportable estimateFromW) {
        if ((read > r) || (write > r))
	    sendNackWrite(source, r);
	else {
	    write = r;
	    estimate = estimateFromW;
	    sendAckWrite(source, r);
        }
    }

    /**
     * Re�oit un ackRead. Envoi un write si on a obtenu le nombre limite d'ack
     */    
    public void processAckRead(int r, int lastWrite, Transportable estimateFromAck) {
        if ((r == round) && (nbNack == 0)) {
            nbAckRead++;
            if (lastWrite > highestWrite) {
                estimate = estimateFromAck;
                highestWrite = lastWrite;
            }

            if (nbAckRead == limit){
                if (write < round)
                    write = round;
                highestWrite = Integer.MAX_VALUE;
		sendWrite();
            }
        }
    }

    /**
     * Re�oit un nack.
     */ 
    public void processNack(int r)
	throws GroupCommException  {
        if (hasDecided())
            return;

        if (r == round) {
            nbNack++;
            if (((nbAckWrite + nbNack) == limit) && (nbNack>nackLimit))
                processNackOnLimit();
        }
    }

    /**
     * Re�oit un ackWrite. Decide si le nombre d'ack+nack>limit et que l'on
     * a pas re�u un nombre de nack>=nackLimit
     */
    public void processAckWrite(int r)
	throws GroupCommException {
        // check if the ack comes from our current round
        if (r == round) {
            nbAckWrite++;
            if ((nbAckWrite + nbNack) == limit) {
                if (nbNack < nackLimit)
                    // decide
		    broadcastDecision();
                else 
		    processNackOnLimit();
            }
        }/* else {
	   throw new GroupCommException("ConsensusPaxosExecution: Invalid Ack Write!!!");
	    }*/
    }
    
    /**
     * Envoit d'un NACK au coordinateur si et seulement si celui-ci est suspect�.
     */
    public void processNewLeader(PID newLeader)
	throws GroupCommException {
	
	this.leader = newLeader;

	if (!ignoreSuspicions)
	    processAbort();
    }

    private void processAbort()
	throws GroupCommException {
        //only a leader can abort
        if (!isLeader() || hasDecided())
            return;
	
        processPropose();
    }

    /**
     * Indique si ce processus est leader. <br>
     *
     * @return Ce processsus est leader.
     */    
    private boolean isLeader(){
	return (leader.equals(myself));
    }

    /**
     * Indique si le consensus a d�j� d�cid� pour ce processus. <br>
     *
     * @return Ce consensus a d�j� d�cid�.
     */
    private boolean hasDecided() {
        return (round >= Integer.MAX_VALUE);
    }

    /**
     * Indique si le consensus a d�j� commencer pour ce processus. <br>
     *
     * @return Ce consensus a d�j� commencer.
     */
    public boolean hasStarted() {
	return round > -1;
    }

    /**
     * Appel�e si le nombre Ack+Nack>limit et Nack > nackLimit
     */
    private void processNackOnLimit()
	throws GroupCommException {
        // P0,P1 : increment round and restart loop
        round = round + n;
        processPropose();
    }

    private void sendRead() {
	GroupCommMessage proposeMessage = new GroupCommMessage();
	//m = <<>>
	proposeMessage.tpack(new TInteger(round));
	//m = <<round>>
	proposeMessage.tpack(new TInteger(CONS_READ));
	//m = <<CONS_READ::round>>
	proposeMessage.tpack(k);
	//m = <<k::CONS_READ::round>>
	triggerSend(proposeMessage, others);
    }

    private void sendWrite() {
	GroupCommMessage proposeMessage = new GroupCommMessage();
	//m = <<>>
	proposeMessage.tpack(estimate);
	//m = <<estimate>>
	proposeMessage.tpack(new TInteger(round));
	//m = <<round::estimate>>
	proposeMessage.tpack(new TInteger(CONS_WRITE));
	//m = <<CONS_WRITE::round::estimate>>
	proposeMessage.tpack(k);
	//m = <<k::CONS_WRITE::round::estimate>>
	triggerSend(proposeMessage, others);
    }

    private void sendAckRead(PID receiver, int r){
	GroupCommMessage proposeMessage = new GroupCommMessage();
	//m = <<>>
	proposeMessage.tpack(estimate);
	//m = <<estimate>>
	proposeMessage.tpack(new TInteger(write));
	//m = <<write::estimate>>
	proposeMessage.tpack(new TInteger(r));
	//m = <<round::write::estimate>>
	proposeMessage.tpack(new TInteger(CONS_ACKREAD));
	//m = <<CONS_ACKREAD::round::write::estimate>>
	proposeMessage.tpack(k);
	//m = <<k::CONS_ACKREAD::round::write::estimate>>
	triggerSend(proposeMessage, receiver);
    }

    private void sendNackRead(PID receiver, int r){
	GroupCommMessage proposeMessage = new GroupCommMessage();
	//m = <<>>
	proposeMessage.tpack(new TInteger(r));
	//m = <<round>>
	proposeMessage.tpack(new TInteger(CONS_NACKREAD));
	//m = <<CONS_NACKREAD::round>>
	proposeMessage.tpack(k);
	//m = <<k::CONS_NACKREAD::round>>
	triggerSend(proposeMessage, receiver);
    }

    private void sendAckWrite(PID receiver, int r){
	GroupCommMessage proposeMessage = new GroupCommMessage();
	//m = <<>>
	proposeMessage.tpack(new TInteger(r));
	//m = <<round>>
	proposeMessage.tpack(new TInteger(CONS_ACKWRITE));
	//m = <<CONS_ACKWRITE::round>>
	proposeMessage.tpack(k);
	//m = <<k::CONS_ACKWRITE::round>>
	triggerSend(proposeMessage, receiver);
    }

    private void sendNackWrite(PID receiver, int r){
	GroupCommMessage proposeMessage = new GroupCommMessage();
	//m = <<>>
	proposeMessage.tpack(new TInteger(r));
	//m = <<round>>
	proposeMessage.tpack(new TInteger(CONS_NACKWRITE));
	//m = <<CONS_NACKWRITE::round>>
	proposeMessage.tpack(k);
	//m = <<k::CONS_NACKWRITE::round>>
	triggerSend(proposeMessage, receiver);
    }

    private void broadcastDecision() {
	GroupCommMessage decisionMessage = new GroupCommMessage();
	//m = <<>>
	decisionMessage.tpack(group);
	//m = <<group>>
	//decisionMessage.pack(hardClone(estimate));//Because I'm sending to myself
	decisionMessage.tpack(estimate);
	//m = <<decision::group>>
	decisionMessage.tpack(new TInteger(CONS_DECISION));
	//m = <<CONS_DECSISON::decision::group>>
	decisionMessage.tpack(k);
	//m = <<k::CONS_DECISION::decision::group>>
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
			  + " leader: "
			  + leader
			  + " write: "
			  + write
			  + " read: "
			  + read
			  + " nbAckRead: "
			  + nbAckRead
			  + " nbAckWrite: "
			  + nbAckWrite
			  + " nbNack: "
			  + nbNack
			  + " highestWrite: "
			  + highestWrite
			  + " estimate: "
			  + estimate
			  + "**)");
    }

	//TODO: remove this
    public void marshal(MarshalStream arg0) throws IOException {
		throw new IOException("not implemented");
	}
	public void unmarshalReferences(UnmarshalStream arg0) throws IOException, ClassNotFoundException {
        throw new IOException("not implemented");
	}
	public Object deepClone(DeepClone arg0) throws CloneNotSupportedException {
        throw new CloneNotSupportedException("not implemented");
	}
}
