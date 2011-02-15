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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

import org.jdom.Element;

import seqSamoa.Message;
import seqSamoa.ProtocolModule;
import seqSamoa.ProtocolStack;
import seqSamoa.ServiceCallOrResponse;
import seqSamoa.exceptions.AlreadyBoundServiceException;
import seqSamoa.exceptions.AlreadyExistingProtocolModuleException;
import seqSamoa.exceptions.AlreadyExistingServiceException;
import seqSamoa.exceptions.SamoaClassException;
import seqSamoa.services.abcast.DynAbcast;
import seqSamoa.services.abcast.DynAbcastCallParameters;
import seqSamoa.services.abcast.DynAbcastResponseParameters;
import seqSamoa.services.replacement.ReplaceProtocol;
import seqSamoa.services.replacement.ReplaceProtocolCallParameters;
import seqSamoa.services.replacement.ReplaceProtocolResponseParameters;
import uka.transport.Transportable;
import framework.Constants;
import framework.PID;
import framework.libraries.DefaultSerialization;
import framework.libraries.serialization.TElement;
import framework.libraries.serialization.TGLinkedList;
import framework.libraries.serialization.TInteger;
import framework.libraries.serialization.TString;

/**
 * This class implement a Meta-protocol for dynamic replacement of abcast
 * protocol
 */
public class DynAbcastReplacer extends ProtocolModule {
    final static private int MAX_MESSAGES_BUFFERED = 50; 

    // Service provide
    private ReplaceProtocol replaceDynAbcast;

    // Service required
    private DynAbcast dynAbcast;

    // Class for finishing the current abcast
    @SuppressWarnings("serial")
	public static class Terminate implements Transportable {
        protected static final int _SIZE = 0;

        public TString name;

        public TGLinkedList<TElement> newFeatures;

        public Terminate(TString name, TGLinkedList<TElement> newFeatures) {
            super();

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
        	newFeatures = (TGLinkedList<TElement>) _stream.readObject();
            name = (TString) _stream.readObject();
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
            this.newFeatures = (TGLinkedList<TElement>) _helper
                    .doDeepClone(this.newFeatures);
            this.name = (TString) _helper.doDeepClone(this.name);
        }
    }

    @SuppressWarnings("serial")
	public static class SequencedMessage implements Transportable {
        protected static final int _SIZE = 0;

        private Transportable content;

        private TInteger seqNumber;

        public SequencedMessage(Transportable content, TInteger seqNumber) {
            super();

            this.content = content;
            this.seqNumber = seqNumber;
        }

        public boolean equals(Object obj) {
            return ((obj instanceof SequencedMessage)
                    && ((SequencedMessage) obj).content.equals(this.content) && ((SequencedMessage) obj).seqNumber
                    .equals(this.seqNumber));
        }

        /**
         * Method defined by Transportable Interface
         */

        /** Used by uka.transport.UnmarshalStream to unmarshal the object */
        public SequencedMessage(uka.transport.UnmarshalStream _stream)
                throws java.io.IOException, ClassNotFoundException {
            this(_stream, _SIZE);
            _stream.accept(_SIZE);
        }

        protected SequencedMessage(uka.transport.UnmarshalStream _stream,
                int _size) throws java.io.IOException, ClassNotFoundException {
            _stream.request(_size);
        }

        /**
         * Method of interface Transportable, it must be declared public. It is
         * called from within UnmarshalStream after creating the object and
         * assigning a stream reference to it.
         */
        public void unmarshalReferences(uka.transport.UnmarshalStream _stream)
                throws java.io.IOException, ClassNotFoundException {
            content = (Transportable) _stream.readObject();
            seqNumber = (TInteger) _stream.readObject();
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
            _stream.writeObject(content);
            _stream.writeObject(seqNumber);
        }

        public final Object deepClone(uka.transport.DeepClone _helper)
                throws CloneNotSupportedException {
            Object _copy = clone();
            _helper.add(this, _copy);
            ((SequencedMessage) _copy).deepCloneReferences(_helper);
            return _copy;
        }

        /**
         * Clone all references to other objects. Use the DeepClone to resolve
         * cycles
         */
        protected void deepCloneReferences(uka.transport.DeepClone _helper)
                throws CloneNotSupportedException {
            this.content = (Transportable) _helper.doDeepClone(this.content);
            this.seqNumber = (TInteger) _helper.doDeepClone(this.seqNumber);
        }
    }

    // The current ID of the Abcast protocol
    protected int sendSeqNumber;

    protected int recvSeqNumber;

    // The list of messages undelivered and abcasted locally
    protected ArrayList<Message> undeliveredMessages;

    protected ArrayList<TInteger> undeliveredType;

    protected ArrayList<PID> undeliveredPID;

    // The list of messages delivered by an abcast protocol not yet bound
    protected ArrayList<Message> bufferized;

    // The Executer
    protected ReplaceProtocol.Executer replaceDynAbcastExecuter;

    // The Interceptor
    // It listen to DynABCAST calls and responses
    protected DynAbcast.Interceptor dynAbcastInterceptor;

    /**
     * Constructor. <br>
     * 
     * @param name
     *            Name of the layer
     * @param stack
     * 			  The stack in which the module will be
     */
    public DynAbcastReplacer(String name, ProtocolStack stack,
            ReplaceProtocol replaceProtocol, DynAbcast dynAb) throws AlreadyExistingProtocolModuleException {

        super(name, stack);

        this.replaceDynAbcast = replaceProtocol;
        this.dynAbcast = dynAb;

        // Init the current abcast and some data
        sendSeqNumber = 0;
        recvSeqNumber = 0;
        undeliveredMessages = new ArrayList<Message>();
        undeliveredType = new ArrayList<TInteger>();
        undeliveredPID = new ArrayList<PID>();
        bufferized = new ArrayList<Message>();

        LinkedList<ServiceCallOrResponse> initiatedReplace = new LinkedList<ServiceCallOrResponse>();
        initiatedReplace.add(ServiceCallOrResponse.createServiceCallOrResponse(dynAbcast, true));
        replaceDynAbcastExecuter = replaceDynAbcast.new Executer(this, initiatedReplace) {
            public void evaluate(ReplaceProtocolCallParameters infos,
                    Message dmessage) {
                synchronized (this.parent) {
                    Terminate term = new Terminate(infos.name, infos.newFeatures);
                    SequencedMessage seqMessage = new SequencedMessage(term,
                            new TInteger(sendSeqNumber));
                    Message dmes = new Message(seqMessage, null);

                    TInteger atype = new TInteger(Constants.AM);
                    DynAbcastCallParameters aparams = new DynAbcastCallParameters(
                            atype, null);

                    // SEND THE TERMINATE MESSAGE TO ALL
                    dynAbcastInterceptor.forwardCall(aparams, dmes);

                    // GET THE NEW PROTOCOL
                    createFeatures(term.name.toString(), term.newFeatures);
                    sendSeqNumber++;
                }
            }
        };

        LinkedList<ServiceCallOrResponse> initiatedAbcast = new LinkedList<ServiceCallOrResponse>();
        for (int i=0; i<MAX_MESSAGES_BUFFERED; i++)
        	initiatedAbcast.add(ServiceCallOrResponse.createServiceCallOrResponse(dynAbcast, true)); 
        initiatedAbcast.add(ServiceCallOrResponse.createServiceCallOrResponse(replaceDynAbcast, false));
        dynAbcastInterceptor = dynAbcast.new Interceptor(this, initiatedAbcast) {
            public void interceptCall(DynAbcastCallParameters params,
                    Message dmessage) {
                synchronized (this.parent) {
                    try {
                        dmessage.content = new SequencedMessage(
                                dmessage.content, new TInteger(sendSeqNumber));
                        undeliveredMessages.add((Message) deepClone(dmessage));
                        undeliveredType.add(params.type);
                        undeliveredPID.add(params.pid);
                        forwardCall(params, dmessage);
                    } catch (ClassNotFoundException ex) {
                        throw new RuntimeException(
                                "Can not abcast this message!");
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        throw new RuntimeException(
                                "Can not abcast this message!"
                                        + ex.getMessage());
                    }
                }
            }

            public void interceptResponse(DynAbcastResponseParameters infos,
                    Message dmessage) {
                synchronized (this.parent) {
                    SequencedMessage seqMessage = (SequencedMessage) dmessage.content;

                    if (seqMessage.seqNumber.intValue() == recvSeqNumber) {
                        // We can deliver this message
                        if (seqMessage.content instanceof Terminate) {
                            // Terminate message -> switch protocol
                            Terminate term = (Terminate) seqMessage.content;

                            // Increase seqNumber and
                            // create the new protocol if necessary
                            recvSeqNumber++;
                            if (sendSeqNumber < recvSeqNumber) {
                                // Create the new protocol
                        		sendSeqNumber++;
                                createFeatures(term.name.toString(), term.newFeatures);
                            }

                            // Deliver messages in bufferized
                            int pointer = 0;
                            while (bufferized.size() > 0
                                    && pointer < bufferized.size()) {
                                Message dmessageTmp = bufferized.get(pointer);
                                SequencedMessage seqMessageTmp = (SequencedMessage) dmessageTmp.content;

                                if (seqMessageTmp.seqNumber.intValue() == recvSeqNumber) {
                                    removeFromUndelivered(seqMessageTmp);
                                    dmessageTmp.content = seqMessageTmp.content;
                                    forwardResponse(infos, dmessageTmp);
                                    bufferized.remove(pointer);
                                } else
                                    pointer++;
                            }

                            // Send Messages in Undelivered again
                            int undeliveredSize = undeliveredMessages.size();
                            for (int i = 0; i < undeliveredSize; i++) {
                                Message dmessageTmp = undeliveredMessages
                                        .get(i);
                                SequencedMessage seqMessageTmp = (SequencedMessage) dmessageTmp.content;

                                if (seqMessageTmp.seqNumber.intValue() < sendSeqNumber) {
                                    DynAbcastCallParameters params = new DynAbcastCallParameters(
                                            undeliveredType.get(i),
                                            undeliveredPID.get(i));

                                    seqMessageTmp.seqNumber = new TInteger(
                                            sendSeqNumber);
                                    try {
                                        forwardCall(
                                                params,
                                                (Message) deepClone(dmessageTmp));
                                    } catch (ClassNotFoundException ex) {
                                        throw new RuntimeException(
                                                "Can not abcast "
                                                        + "this message!");
                                    } catch (IOException ex) {
                                        throw new RuntimeException(
                                                "Can not abcast "
                                                        + "this message!");
                                    }
                                }
                            }
                        } else {
                            // Normal Abcast message
                            removeFromUndelivered(seqMessage);
                            dmessage.content = seqMessage.content;
                            forwardResponse(infos, dmessage);
                        }
                    } else if (seqMessage.seqNumber.intValue() > recvSeqNumber) {
                        // This message should be delivered later
                        bufferized.add(dmessage);
                        removeFromUndelivered(seqMessage);
                    }
                }
            }
        };
    }

    // Remove the message from undelivered
    private void removeFromUndelivered(SequencedMessage message) {
        int undelSize = undeliveredMessages.size();
        for (int i = 0; i < undelSize; i++) {
            try {
                if (message.equals(undeliveredMessages.get(i).content)) {
                    undeliveredMessages.remove(i);
                    undeliveredType.remove(i);
                    undeliveredPID.remove(i);
                    return;
                }
            } catch (IndexOutOfBoundsException ex) {
                throw new RuntimeException("There is some bug!!!!");
            }
        }
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
				dynAbcast.unlinkExecuter();
				newP.linkToService();
				
				// Issue a response informing of the change of protocol
				ReplaceProtocolResponseParameters nparams = new ReplaceProtocolResponseParameters(
						dynAbcast, newP);
				replaceDynAbcast.response(nparams, null);
			} else
				System.err.println("The protocol with name "+name+" does not exist!");
		} catch (AlreadyBoundServiceException ex) {
			throw new RuntimeException("Error in conception. Should not be possible!");
		}		
	}

    // Makes a complete deep copy of the parameter o
    private Transportable deepClone(Transportable o) throws IOException,
            ClassNotFoundException {
        // TODO: There have to be better ways to do deep-clone!!!
        return DefaultSerialization
                .unmarshall(DefaultSerialization.marshall(o));
    }

}
