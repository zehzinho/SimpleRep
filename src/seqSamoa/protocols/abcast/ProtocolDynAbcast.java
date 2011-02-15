/**
 *  SAMOA - PROTOCOL FRAMEWORK
 *  Copyright (C) 2005  Olivier Rütti (EPFL) (olivier.rutti@a3.epfl.ch)
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
package seqSamoa.protocols.abcast;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import seqSamoa.Message;
import seqSamoa.ProtocolModule;
import seqSamoa.ProtocolStack;
import seqSamoa.ServiceCallOrResponse;
import seqSamoa.AtomicTask;
import seqSamoa.exceptions.AlreadyExistingProtocolModuleException;
import seqSamoa.exceptions.NotScheduledTaskException;
import seqSamoa.services.abcast.DynAbcast;
import seqSamoa.services.abcast.DynAbcastCallParameters;
import seqSamoa.services.abcast.DynAbcastResponseParameters;
import seqSamoa.services.consensus.Consensus;
import seqSamoa.services.consensus.ConsensusCallParameters;
import seqSamoa.services.consensus.ConsensusResponseParameters;
import seqSamoa.services.rpt2pt.RPT2PT;
import seqSamoa.services.rpt2pt.RPT2PTCallParameters;
import seqSamoa.services.rpt2pt.RPT2PTResponseParameters;
import uka.transport.Transportable;
import framework.Compressable;
import framework.Constants;
import framework.GroupCommEventArgs;
import framework.GroupCommException;
import framework.GroupCommMessage;
import framework.PID;
import framework.libraries.Timer;
import framework.libraries.Trigger;
import framework.libraries.serialization.TBoolean;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TList;
import framework.libraries.serialization.TLong;
import framework.libraries.serialization.TSet;
import groupcomm.common.abcast.DynAbcastImpl;

/**
 * This class implement the ABcast protocol with dynamic set of processes (view
 * synchrony).
 * 
 * This Protocol need a Protocol that implements RPT2PT and Consensus.
 * 
 * The service implemented is dynAbcast (described in util/Services.java)
 */
public class ProtocolDynAbcast extends ProtocolModule implements Trigger, Timer {
    final private static int MAX_PROCESSES = 7;
    final private static int MAX_MESSAGES = 45;
    public static int nbDynAbcast = 0;
 
    @SuppressWarnings("serial")
	public static class ConsensusID implements Transportable, Compressable {
        int protocolValue;

        TLong value;

        public ConsensusID(long value, int pValue) {
            this.value = new TLong(value);
            this.protocolValue = pValue;
        }
        
        public ConsensusID(TLong value, int pValue) {
            this.value = value;
            this.protocolValue = pValue;
        }
        
        public boolean equals(Object o) {
            if (!(o instanceof ConsensusID))
                return false;

            ConsensusID cID = (ConsensusID) o;
            return ((cID.protocolValue == this.protocolValue) && (cID.value
                    .longValue() == this.value.longValue()));
        }

        public int compareToCompressable(Object o) {
            if (o instanceof ConsensusID) {
                ConsensusID id = (ConsensusID) o;

                if (this.protocolValue != id.protocolValue)
                    return Compressable.NOT_COMPARABLE;
                else
                    return this.value.compareToCompressable(id.value);
            } else {
                return Compressable.NOT_COMPARABLE;
            }
        }

        public String toString() {
            return new String("protocolValue: "+protocolValue+" -> value: "+value);
        }
        
        public int hashCode(){
            return value.hashCode() + protocolValue; 
        }
        
        protected static final int _SIZE = uka.transport.BasicIO.SIZEOF_int;

        /** Used by uka.transport.UnmarshalStream to unmarshal the object */
        public ConsensusID(uka.transport.UnmarshalStream _stream)
                throws java.io.IOException, ClassNotFoundException {
            this(_stream, _SIZE);
            _stream.accept(_SIZE);
        }

        protected ConsensusID(uka.transport.UnmarshalStream _stream, int _size)
                throws java.io.IOException, ClassNotFoundException {
            byte[] _buffer = _stream.getBuffer();
            int _pos = _stream.getPosition();
            protocolValue = uka.transport.BasicIO.extractInt(_buffer, _pos);
            _pos += uka.transport.BasicIO.SIZEOF_int;
        }

        /**
         * Method of interface Transportable, it must be declared public. It is
         * called from within UnmarshalStream after creating the object and
         * assigning a stream reference to it.
         */
        public void unmarshalReferences(uka.transport.UnmarshalStream _stream)
                throws java.io.IOException, ClassNotFoundException {
            this.value = (TLong) _stream.readObject();
        }

        /** Called directly by uka.transport.MarshalStream */
        public void marshal(uka.transport.MarshalStream _stream)
                throws java.io.IOException {
            _stream.reserve(_SIZE);
            byte[] _buffer = _stream.getBuffer();
            int _pos = _stream.getPosition();
            marshalPrimitives(_buffer, _pos);
            _stream.deliver(_SIZE);
            marshalReferences(_stream);
        }

        protected void marshalPrimitives(byte[] _buffer, int _pos)
                throws java.io.IOException {
            _pos = uka.transport.BasicIO.insert(_buffer, _pos, protocolValue);
        }

        protected void marshalReferences(uka.transport.MarshalStream _stream)
                throws java.io.IOException {
            _stream.writeObject(this.value);
        }
        
        public final Object deepClone(uka.transport.DeepClone _helper)
        throws CloneNotSupportedException
        {
        Object _copy = clone();
        _helper.add(this, _copy);
        ((ConsensusID) _copy).deepCloneReferences(_helper);
        return _copy;
        }

        /** Clone all references to other objects. Use the 
        DeepClone to resolve cycles */
        protected void deepCloneReferences(uka.transport.DeepClone _helper)
        throws CloneNotSupportedException
        {
            this.value = (TLong) _helper.doDeepClone(this.value);
        }
    }
    
    // Service provided
    private DynAbcast dynAbcast;

    // Service required
    private RPT2PT rpt2pt;

    private Consensus consensus;

    protected DynAbcastImpl handlers;

    // Timers scheduled
    private Map<Transportable, AtomicTask> timers = null;
    
    // The Executer
    // It ABcasts a message
    protected DynAbcast.Executer dynAbcastExecuter;

    // The Listeners
    // It listen for consensus decision
    protected Consensus.Listener consensusListener;

    // It listen for rpt2pt messages
    protected RPT2PT.Listener rpt2ptListener;

    // Identifier of the protocol
    protected int pValue;
    
    // The COR for dynAbcast call
    private ServiceCallOrResponse dynAbcastCallCOR;
    
    /**
     * Constructor. <br>
     * 
     * @param name
     *            Name of the layer
     * @param stack
     * 			  The stack in which the module will be
     */
    public ProtocolDynAbcast(String name, ProtocolStack stack, DynAbcast dynAbcast, Consensus consensus, RPT2PT rpt2pt) throws AlreadyExistingProtocolModuleException {

        super(name, stack);
        handlers = new DynAbcastImpl(this, stack.getFlowControl(), this, stack.getPID());
        
        this.pValue = nbDynAbcast;
        nbDynAbcast++;
        this.timers = new HashMap<Transportable, AtomicTask>();

        this.dynAbcast = dynAbcast;
        this.consensus = consensus;
        this.rpt2pt = rpt2pt;
        
        this.dynAbcastCallCOR = ServiceCallOrResponse.createServiceCallOrResponse(this.dynAbcast, true);

        LinkedList<ServiceCallOrResponse> initiatedAbcast = new LinkedList<ServiceCallOrResponse>();
        for (int i=0; i<MAX_PROCESSES; i++)
        	initiatedAbcast.add(ServiceCallOrResponse.createServiceCallOrResponse(rpt2pt, true));
        initiatedAbcast.add(ServiceCallOrResponse.createServiceCallOrResponse(consensus, true));
        dynAbcastExecuter = dynAbcast.new Executer(this, initiatedAbcast) {
            public void evaluate(DynAbcastCallParameters params,
                    Message dmessage) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();

                    ga.addLast(params.type);
                    ga.addLast(dmessage.toGroupCommMessage());
                    ga.addLast(params.pid);

                    handlers.handleAbcast(ga);
                }
            }
        };

        LinkedList<ServiceCallOrResponse> initiatedRpt2pt = new LinkedList<ServiceCallOrResponse>();
        for (int i=0; i<MAX_PROCESSES; i++)
        	initiatedRpt2pt.add(ServiceCallOrResponse.createServiceCallOrResponse(rpt2pt, true));
        initiatedRpt2pt.add(ServiceCallOrResponse.createServiceCallOrResponse(consensus, true));
        rpt2ptListener = rpt2pt.new Listener(this, initiatedRpt2pt) {
            public void evaluate(RPT2PTResponseParameters infos,
                    Transportable message) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();

                    ga.addLast(message);
                    ga.addLast(infos.pid);

                    try {
                        handlers.handlePt2PtDeliver(ga);
                    } catch (Exception ex) {
                        throw new RuntimeException("ProtocolDynAbcast: "
                                + "rpt2ptListener: " + ex.getMessage());
                    }
                }
            }
        };

        LinkedList<ServiceCallOrResponse> initiatedConsensus = new LinkedList<ServiceCallOrResponse>();
        for (int i=0; i<MAX_PROCESSES; i++)
        	initiatedConsensus.add(ServiceCallOrResponse.createServiceCallOrResponse(rpt2pt, true));
        initiatedConsensus.add(ServiceCallOrResponse.createServiceCallOrResponse(consensus, true));
        for (int i=0; i<MAX_MESSAGES; i++)
        	initiatedConsensus.add(ServiceCallOrResponse.createServiceCallOrResponse(dynAbcast, false));
        consensusListener = consensus.new Listener(this, initiatedConsensus) {
            public void evaluate(
                    ConsensusResponseParameters infos, Transportable message) {
                synchronized (this.parent) {
                    GroupCommEventArgs ga = new GroupCommEventArgs();

                    ga.addLast(message);
                    ga.addLast(((ConsensusID) infos.id).value);

                    try {
                        handlers.handleDecide(ga);
                    } catch (GroupCommException ex) {
                        throw new RuntimeException("ProtocolDynAbcast: "
                                + "handleDecide: " + ex.getMessage());
                    }
                }
            }
        };
    }

    synchronized public void init() {
        try {
            GroupCommEventArgs initev = new GroupCommEventArgs();
            initev.addLast(stack.getGroup());
            handlers.handleInit(initev);
        } catch (GroupCommException ex) {
            throw new RuntimeException("ProtocolDynAbcast: handleInit: "
                    + ex.getMessage());
        }
        
        super.init();
    }

    synchronized public void dump(OutputStream stream) {
        handlers.dump(stream);
    }

    /**
     * Manage the triggering of the events
     */
    @SuppressWarnings("unchecked")
	public void trigger(int type, GroupCommEventArgs l) {
        switch (type) {
        case Constants.JOINREMOVELIST:
            TSet start = (TSet) l.remove(0);
            TSet stop = (TSet) l.remove(0);

            Iterator itStart = start.iterator();
            while (itStart.hasNext()) {
                RPT2PTCallParameters jparams = new RPT2PTCallParameters(
                        (PID) itStart.next(), new TBoolean(true), new TBoolean(
                                false));

                rpt2pt.call(jparams, null);
            }

            Iterator itStop = stop.iterator();
            while (itStop.hasNext()) {
                RPT2PTCallParameters jparams = new RPT2PTCallParameters(
                        (PID) itStop.next(), new TBoolean(false), new TBoolean(
                                false));

                rpt2pt.call(jparams, null);
            }

            break;

        case Constants.PT2PTSEND:
            Transportable message = l.remove(0);
            PID ppid = (PID) l.remove(0);
            TBoolean promisc = (TBoolean) l.remove(0);
            RPT2PTCallParameters rparams = new RPT2PTCallParameters(ppid,
                    promisc, new TBoolean(true));

            rpt2pt.call(rparams, new Message(message, rpt2ptListener));
            break;

        case Constants.ADELIVER:
            TInteger atype = (TInteger) l.remove(0);
            GroupCommMessage gm = (GroupCommMessage) l.remove(0);
            Message dmessage = new Message(gm);
            PID apid = (PID) l.remove(0);
            DynAbcastResponseParameters infos = new DynAbcastResponseParameters(
                    atype, apid);

            dynAbcast.response(infos, dmessage);
            break;

        case Constants.PROPOSE:
            TList group = (TList) l.remove(0);
            Transportable cmessage = l.remove(0);
            TLong id = (TLong) l.remove(0);
            ConsensusCallParameters cparams = new ConsensusCallParameters(
                    group, new ConsensusID(id, this.pValue));

            consensus.call(cparams, new Message(cmessage, consensusListener));
            break;

        default:
            throw new RuntimeException("Unknow event triggered : " + type);
        }
    }
    // Interface for the timers
    synchronized public void schedule(final Transportable key, boolean periodic,
            int time) {
        if (periodic)
            throw new RuntimeException("ProtocolHB: schedule: Periodic "
                    + "timers not supported. Discarding it.");
        if (!timers.containsKey(key)) {
            // There is no entry in the map
            // Create the entry and start the timer
            AtomicTask trigger = new AtomicTask() {
                public void execute() {
                    timeout(key);
                }

                public ServiceCallOrResponse getCOR() {
                	return dynAbcastCallCOR;
                }
            };

            timers.put(key, trigger);
            stack.getScheduler().schedule(trigger, periodic, time);
        } else {
            throw new RuntimeException("ProtocolHB:schedule: Suspect "
                    + "Task already scheduled!");
        }
    }

    synchronized public void cancel(Transportable key) {
        try{
        	stack.getScheduler().cancel(timers.remove(key));            
        } catch (NotScheduledTaskException ex){
            System.err.println("ProtocolPing: cancel: The task is not"
                    + " currently scheduled");
        }
    }

    synchronized public void reset(Transportable key) {
        try{
        	stack.getScheduler().reset(timers.get(key));            
        } catch (NotScheduledTaskException ex){
            System.err.println("ProtocolPing: reset: The task is not"
                    + " currently scheduled");
        }
    }

    synchronized private void timeout(Object o) {
        GroupCommEventArgs ga = new GroupCommEventArgs();
        final Transportable key = (Transportable) o;

        if (!timers.containsKey(key))
            // Timer already canceled
            return;

        ga.add(key);
        handlers.handleTimeout(ga);
    }
}
