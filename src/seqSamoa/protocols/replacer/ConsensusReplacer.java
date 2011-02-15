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
package seqSamoa.protocols.replacer;

import java.util.Iterator;
import java.util.LinkedList;

import org.jdom.Element;

import seqSamoa.Message;
import seqSamoa.ProtocolModule;
import seqSamoa.ProtocolStack;
import seqSamoa.Service;
import seqSamoa.ServiceCallOrResponse;
import seqSamoa.exceptions.AlreadyBoundServiceException;
import seqSamoa.exceptions.AlreadyExistingProtocolModuleException;
import seqSamoa.exceptions.AlreadyExistingServiceException;
import seqSamoa.exceptions.SamoaClassException;
import seqSamoa.services.abcast.Abcast;
import seqSamoa.services.abcast.AbcastResponseParameters;
import seqSamoa.services.abcast.DynAbcast;
import seqSamoa.services.abcast.DynAbcastCallParameters;
import seqSamoa.services.abcast.DynAbcastResponseParameters;
import seqSamoa.services.consensus.Consensus;
import seqSamoa.services.consensus.ConsensusCallParameters;
import seqSamoa.services.consensus.ConsensusResponseParameters;
import seqSamoa.services.replacement.ReplaceProtocol;
import seqSamoa.services.replacement.ReplaceProtocolCallParameters;
import seqSamoa.services.replacement.ReplaceProtocolResponseParameters;
import uka.transport.Transportable;
import framework.Constants;
import framework.libraries.serialization.TElement;
import framework.libraries.serialization.TGLinkedList;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TString;

/**
 * This class implement a Meta-protocol for dynamic replacement of consensus
 * protocol
 * 
 * Assumptions: The event propose can not be triggered if there is no decide
 * event corresponding to the precedent propose
 */
public class ConsensusReplacer extends ProtocolModule {
    // Service provided
    private ReplaceProtocol replaceConsensus;

    // Service required
    private Consensus consensus = null;

    private DynAbcast dynAbcast = null;
    private Abcast    abcast = null;

    // Class for finishing the current consensus
    @SuppressWarnings("serial")
	public static class Terminate implements Transportable {
        protected static final int _SIZE = 0;

        public Transportable content;

        public TString name;

        public TGLinkedList<TElement> newFeatures;

        Terminate(Transportable value, TString name, TGLinkedList<TElement> newFeatures) {
            super();

            this.content = value;
            this.name = name;
            this.newFeatures = newFeatures;
        }

        /**
         * Method defined by Transportable Interface
         */

        /** Used by uka.transport.UnmarshalStream to unmarshal the object */
        public Terminate(uka.transport.UnmarshalStream _stream)
                throws java.io.IOException, ClassNotFoundException {
            this(_stream, _SIZE);
            _stream.accept(_SIZE);
        }

        protected Terminate(uka.transport.UnmarshalStream _stream, int _size)
                throws java.io.IOException, ClassNotFoundException {
            _stream.request(_size);
        }

        /**
         * Method of interface Transportable, it must be declared public. It is
         * called from within UnmarshalStream after creating the object and
         * assigning a stream reference to it.
         */
        @SuppressWarnings("unchecked")
		public void unmarshalReferences(uka.transport.UnmarshalStream _stream)
                throws java.io.IOException, ClassNotFoundException {
            newFeatures = (TGLinkedList) _stream.readObject();
            name = (TString) _stream.readObject();
            content = (Transportable) _stream.readObject();
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
        }

        protected void marshalReferences(uka.transport.MarshalStream _stream)
                throws java.io.IOException {
            _stream.writeObject(newFeatures);
            _stream.writeObject(name);
            _stream.writeObject(content);
        }

        public final Object deepClone(uka.transport.DeepClone _helper)
                throws CloneNotSupportedException {
            Object _copy = clone();
            _helper.add(this, _copy);
            ((Terminate) _copy).deepCloneReferences(_helper);
            return _copy;
        }

        /**
         * Clone all references to other objects. Use the DeepClone to resolve
         * cycles
         */
        @SuppressWarnings("unchecked")
		protected void deepCloneReferences(uka.transport.DeepClone _helper)
                throws CloneNotSupportedException {
            this.newFeatures = (TGLinkedList) _helper.doDeepClone(this.newFeatures);
            this.name = (TString) _helper.doDeepClone(this.name);
            this.content = (Transportable) _helper.doDeepClone(this.content);
        }
    }

    // Class for sending the name and the class of the Protocol
    @SuppressWarnings("serial")
	public static class RepInfo implements Transportable {
        protected static final int _SIZE = 0;

        public TString name;

        public TGLinkedList<TElement> newFeatures;

        public RepInfo(TString name, TGLinkedList<TElement> newFeatures) {
            super();

            this.name = name;
            this.newFeatures = newFeatures;
        }
               
        public boolean equals(Object o){
        	if (!(o instanceof RepInfo))
        		return false;
        	
        	RepInfo rep = (RepInfo) o;
        	
        	return ((rep.name.equals(this.name)) &&
        			(rep.newFeatures.equals(this.newFeatures)));
        }

        /**
         * Method defined by Transportable Interface
         */

        /** Used by uka.transport.UnmarshalStream to unmarshal the object */
        public RepInfo(uka.transport.UnmarshalStream _stream)
                throws java.io.IOException, ClassNotFoundException {
            this(_stream, _SIZE);
            _stream.accept(_SIZE);
        }

        protected RepInfo(uka.transport.UnmarshalStream _stream, int _size)
                throws java.io.IOException, ClassNotFoundException {
            _stream.request(_size);
        }

        /**
         * Method of interface Transportable, it must be declared public. It is
         * called from within UnmarshalStream after creating the object and
         * assigning a stream reference to it.
         */
        @SuppressWarnings("unchecked")
		public void unmarshalReferences(uka.transport.UnmarshalStream _stream)
                throws java.io.IOException, ClassNotFoundException {
            newFeatures = (framework.libraries.serialization.TGLinkedList) _stream
                    .readObject();
            name = (framework.libraries.serialization.TString) _stream
                    .readObject();
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
        }

        protected void marshalReferences(uka.transport.MarshalStream _stream)
                throws java.io.IOException {
            _stream.writeObject(newFeatures);
            _stream.writeObject(name);
        }

        public final Object deepClone(uka.transport.DeepClone _helper)
                throws CloneNotSupportedException {
            Object _copy = clone();
            _helper.add(this, _copy);
            ((RepInfo) _copy).deepCloneReferences(_helper);
            return _copy;
        }

        /**
         * Clone all references to other objects. Use the DeepClone to resolve
         * cycles
         */
        @SuppressWarnings("unchecked")
		protected void deepCloneReferences(uka.transport.DeepClone _helper)
                throws CloneNotSupportedException {
            this.newFeatures = (framework.libraries.serialization.TGLinkedList) _helper
                    .doDeepClone(this.newFeatures);
            this.name = (framework.libraries.serialization.TString) _helper
                    .doDeepClone(this.name);
        }
    }

    // Lists of replacement
    // replacing(i) contains the ith protocol to install
    protected LinkedList<TString> replacingName;

    protected LinkedList<TGLinkedList<TElement>> replacingNewFeatures;

    // Lists of replacement already effectuated but not received
    protected LinkedList<TString> alreadyName;

    protected LinkedList<TGLinkedList<TElement>> alreadyNewFeatures;

    // The Executer
    protected ReplaceProtocol.Executer replaceConsensusExecuter;

    // The Interceptor
    // It listen to Consensus calls and responses
    protected Consensus.Interceptor consensusInterceptor;

    // The Listener
    // It listen for DynABcast message
    protected DynAbcast.Listener dynAbcastListener;
    protected Abcast.Listener abcastListener;
    
    // Init interceptor
    private void initInterceptor() {
        LinkedList<ServiceCallOrResponse> initiatedConsensus = new LinkedList<ServiceCallOrResponse>();
        initiatedConsensus.add(ServiceCallOrResponse.createServiceCallOrResponse(replaceConsensus, false));
        consensusInterceptor = consensus.new Interceptor(this, initiatedConsensus) {
            public void interceptCall(ConsensusCallParameters params,
                    Message dmessage) {
                synchronized (this.parent) {
                    if (!replacingName.isEmpty()) {
                        // A replacement is required
                        dmessage.content = new Terminate(dmessage.content,
                        		replacingName.getFirst(),
                        		replacingNewFeatures.getFirst());
                    }
                    forwardCall(params, dmessage);
                }
            }

            public void interceptResponse(ConsensusResponseParameters infos,
                    Message dmessage) {
                synchronized (this.parent) {
                    if (dmessage.content instanceof Terminate) {
                        Terminate term = (Terminate) dmessage.content;
                        TString name = term.name;
                        TGLinkedList<TElement> newFeatures = term.newFeatures;
                        int index = replacingName.indexOf(name);
                        
                        // Remove from the list
                        if (index < 0) {
                            alreadyName.add(name);
                            alreadyNewFeatures.add(newFeatures);
                        } else {
                            replacingName.remove(index);
                            replacingNewFeatures.remove(index);
                        }

                        // Create the new protocol
                        createFeatures(term.name.toString(), term.newFeatures);

                        dmessage.content = term.content;
                    }
                    forwardResponse(infos, dmessage);
                }
            }
        };   	
    }

    /**
     * Constructor. <br>
     * 
     * @param name
     *            Name of the layer
     * @param stack
     * 			  The stack in which the module will be
     */
    public ConsensusReplacer(String name, ProtocolStack stack,
            ReplaceProtocol replaceProtocol, Consensus cons, Abcast ab) throws AlreadyExistingProtocolModuleException {

        super(name, stack);

        this.replaceConsensus = replaceProtocol;
        this.consensus = cons;
        this.abcast = ab;

        replacingName = new LinkedList<TString>();
        replacingNewFeatures = new LinkedList<TGLinkedList<TElement>>();
        alreadyName = new LinkedList<TString>();
        alreadyNewFeatures = new LinkedList<TGLinkedList<TElement>>();

        LinkedList<ServiceCallOrResponse> initiatedReplace = new LinkedList<ServiceCallOrResponse>();
        initiatedReplace.add(ServiceCallOrResponse.createServiceCallOrResponse(abcast, true));
        replaceConsensusExecuter = replaceConsensus.new Executer(this, initiatedReplace) {
            @SuppressWarnings("unchecked")
			public void evaluate(ReplaceProtocolCallParameters infos,
                    Message dmessage) {
                synchronized (this.parent) {
                    abcast.call(null, new Message(new RepInfo(infos.name, infos.newFeatures),
                            (Service.Listener) abcastListener));
                }
            }
        };

        LinkedList<ServiceCallOrResponse> initiatedAbcast = new LinkedList<ServiceCallOrResponse>();
        abcastListener = abcast.new Listener(this, initiatedAbcast) {
            public void evaluate(AbcastResponseParameters infos,
                    Transportable message) {
                synchronized (this.parent) {
                    RepInfo repInfo = (RepInfo) message;
                    int index = alreadyName.indexOf(repInfo.name);
                    
                    // Add the replacement in the list except if the replacement
                    // is already achieved
                    if (index < 0) {
                        replacingName.addLast(repInfo.name);
                        replacingNewFeatures.addLast(repInfo.newFeatures);
                    } else {
                        alreadyName.remove(index);
                        alreadyNewFeatures.remove(index);
                    }
                }
            }
        };
        
        initInterceptor();
    }
    
    /**
     * Constructor. <br>
     * 
     * @param name
     *            Name of the layer
     * @param stack
     * 			  The stack in which the module will be
     */
    public ConsensusReplacer(String name, ProtocolStack stack,
            ReplaceProtocol replaceProtocol, Consensus cons, DynAbcast dynAb) throws AlreadyExistingProtocolModuleException {

        super(name, stack);

        this.replaceConsensus = replaceProtocol;
        this.consensus = cons;
        this.dynAbcast = dynAb;

        replacingName = new LinkedList<TString>();
        replacingNewFeatures = new LinkedList<TGLinkedList<TElement>>();
        alreadyName = new LinkedList<TString>();
        alreadyNewFeatures = new LinkedList<TGLinkedList<TElement>>();

        LinkedList<ServiceCallOrResponse> initiatedReplace = new LinkedList<ServiceCallOrResponse>();
        initiatedReplace.add(ServiceCallOrResponse.createServiceCallOrResponse(dynAbcast, true));
        replaceConsensusExecuter = replaceConsensus.new Executer(this, initiatedReplace) {
            @SuppressWarnings("unchecked")
			public void evaluate(ReplaceProtocolCallParameters infos,
                    Message dmessage) {
                synchronized (this.parent) {
                    TInteger atype = new TInteger(Constants.AM);
                    DynAbcastCallParameters aparams = new DynAbcastCallParameters(
                            atype, null);

                    dynAbcast.call(aparams, new Message(new RepInfo(infos.name, infos.newFeatures),
                            (Service.Listener) dynAbcastListener));
                }
            }
        };

        LinkedList<ServiceCallOrResponse> initiatedAbcast = new LinkedList<ServiceCallOrResponse>();
        dynAbcastListener = dynAbcast.new Listener(this, initiatedAbcast) {
            public void evaluate(DynAbcastResponseParameters infos,
                    Transportable message) {
                synchronized (this.parent) {
                    RepInfo repInfo = (RepInfo) message;
                    int index = alreadyName.indexOf(repInfo.name);
                    
                    // Add the replacement in the list except if the replacement
                    // is already achieved
                    if (index < 0) {
                        replacingName.addLast(repInfo.name);
                        replacingNewFeatures.addLast(repInfo.newFeatures);
                    } else {
                        alreadyName.remove(index);
                        alreadyNewFeatures.remove(index);
                    }
                }
            }
        };

        initInterceptor();
    }
    
    // Add all the protocols and services of the list in the stack
	private void createFeatures(String name, TGLinkedList<TElement> newFeatures) {
		Iterator<TElement> it = newFeatures.iterator();
		while (it.hasNext()) {
			Element element = it.next().getElement();
			
			try {
				if (element.getName().equals("Service")) {
					this.stack.newService(element);
				} else if (element.getName().equals("Protocol")) {
					ProtocolModule newProt = this.stack.newProtocolModule(element);
					newProt.init();
					if (!newProt.getName().equals(name))
						newProt.linkToService();
				} else
					throw new RuntimeException("Unknown Element Name: " + element);
			} catch (SamoaClassException e) {
				System.err.println(e.getMessage());
			} catch (AlreadyExistingServiceException e) {
				System.err.println("Service already exist: "+element.getChildText("Name"));
			} catch (AlreadyExistingProtocolModuleException e) {
				System.err.println("Protocol already exist: "+element.getChildText("Name"));
			} catch (AlreadyBoundServiceException e) {
				System.err.println("Service provided by the protocol has already a provider: "+element.getChildText("Name"));				
			}
		}

		// Unlink the old protocol and bound the new one
		try {
			ProtocolModule newP = stack.getProtocol(name); 
			
			if (newP != null) {
				consensus.unlinkExecuter();
				newP.linkToService();
				
				// Issue a response informing of the change of protocol
				ReplaceProtocolResponseParameters nparams = new ReplaceProtocolResponseParameters(
						consensus, newP);
				replaceConsensus.response(nparams, null);
			} else
				System.err.println("The protocol with name "+name+" does not exist!");
		} catch (AlreadyBoundServiceException ex) {
			throw new RuntimeException("Error in conception. Should not be possible!");
		}		
	}
}
