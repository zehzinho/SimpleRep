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
package seqSamoa;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import seqSamoa.exceptions.AlreadyBoundServiceException;
import seqSamoa.exceptions.AlreadyExistingServiceException;
import seqSamoa.exceptions.NotInAComputationException;
import seqSamoa.exceptions.UnboundServiceException;
import uka.transport.Transportable;
import framework.libraries.serialization.TString;

/**
 * A class <CODE>Service</CODE> represents a service (Reliable send,
 * consensus, ABcast, ..) provided by a {@link seqSamoa.ProtocolModule Protocol}. A
 * <CODE>Service</CODE> is bound to an executer which provide the service and
 * to many listeners that get the response of th service call. A <CODE>Service
 * </CODE> can be subtyped. Imagine a service <t, u> and a service <v, w> such
 * that v is a subtype of t and w a subtype of u. Then, we consider service <v,
 * w> as a subtype of service <t,u>. However, the class v must define a
 * constructor with object of class t as a parameters. An example is available
 * for service RPT2PT which is considered as a subtype of UDP. Take also a look
 * to protocols HB and Ping in util/groupcomm/fd/ to see how implement protocols
 * that can benefit of this feature.
 * 
 * @see ProtocolModule
 */
public class Service<CallParameters, ResponseParameters> {
    /* Class representing a service call */
    protected class ServiceCall extends AtomicTask {
        /* The Service */
        protected Service<CallParameters, ResponseParameters> service;

        /* The parameters */
        protected CallParameters params;

        /* The message */
        protected Message dmessage;
        
        /* The Service Call or Response */
        protected ServiceCallOrResponse cor;

        protected ServiceCall(Service<CallParameters, ResponseParameters> service, CallParameters params,
                Message dmessage) {
            super();

            this.service = service;
            this.params = params;
            this.dmessage = dmessage;
            this.cor = ServiceCallOrResponse.createServiceCallOrResponse(service, true);
        }
         
		@SuppressWarnings("unchecked")
		public void execute(){
            // If there is no currentExecuter, throws Exception
            if (currentExecuter == null)
                throw new UnboundServiceException(this.service);

            // Get the class corresponding CallParameters at runtime
            Method evaluateMethod = this.service.currentExecuter.getClass().getMethods()[0];
            Class classAtRuntime = evaluateMethod.getParameterTypes()[0];

            // Transform the parameter if params.class is a super-type of
            // CallParameters
            if (this.params != null && !this.params.getClass().equals(classAtRuntime)
                    && classAtRuntime.getClass().equals(Object.class)) {
                try {
                    // Class[] contains the class of the params
                    Class[] paramsClass = new Class[1];
                    paramsClass[0] = this.params.getClass();

                    // Object[] contains the params
                    Object[] paramsArray = new Object[1];
                    paramsArray[0] = this.params;

                    Constructor constructor = classAtRuntime.getConstructor(paramsClass);

                    this.params = (CallParameters) constructor.newInstance(paramsArray);
                } catch (NoSuchMethodException ex) {
                    throw new RuntimeException(classAtRuntime
                            + " do not define a constructor with"
                            + this.params.getClass() + " as a parameters!!!");
                } catch (SecurityException ex) {
                    throw new RuntimeException("Security Exception!!!");
                } catch (InstantiationException ex) {
                    throw new RuntimeException(classAtRuntime
                            + " is abstract. Class for parameters"
                            + "should not be abstract!!!");
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException(classAtRuntime
                            + " does not declare all constructors public");
                } catch (IllegalArgumentException ex) {
                    throw new RuntimeException("Illegal Argument!!!");
                } catch (InvocationTargetException ex) {
                    throw new RuntimeException("The underlying constructor of "
                            + classAtRuntime + "throws an exception: "
                            + ex.getMessage());
                }
            }

            // Execute interceptors and the executer
            if (this.service.boundInterceptors.size() > 0) {
            	this.currentModule = this.service.boundInterceptors.get(0).parent;
            	this.service.boundInterceptors.get(0).interceptCall(this.params, this.dmessage);
            	this.currentModule = null;
            } else {
            	this.currentModule = this.service.currentExecuter.parent;
            	this.service.currentExecuter.evaluate(this.params, this.dmessage);
            	this.currentModule = null;
            }
        }
        
        public ServiceCallOrResponse getCOR() {
    		return this.cor;
    	}
        
        public String toString() {
        	return new String(this.cor + ":"+dmessage);
        }
    }

    /* Class representing a service response */
    protected class ServiceResponse extends AtomicTask {

        /* The Service */
        protected Service<CallParameters, ResponseParameters> service;

        /* The Message */
        protected Message dmessage;

        /* The additional ResponseParameters */
        protected ResponseParameters params;
        
        /* The Service Call or Response */
        protected ServiceCallOrResponse cor;
        
        protected ServiceResponse(Service<CallParameters, ResponseParameters> service, ResponseParameters infos,
                Message dmessage) {
            super();

            this.service = service;
            this.dmessage = dmessage;
            this.params = infos;
            this.cor = ServiceCallOrResponse.createServiceCallOrResponse(service, false);
        }
         
        public void execute(){
            int size = this.service.boundInterceptors.size();
            if (size > 0) {
            	this.currentModule = this.service.boundInterceptors.get(size-1).parent;
                this.service.boundInterceptors.get(size - 1).interceptResponse(this.params, this.dmessage);
            	this.currentModule = null;
            } else {
                TString dest;
                Transportable message;

                if (this.dmessage == null) {
                    dest = new TString("NULL");
                    message = null;
                } else {
                    dest = this.dmessage.dest;
                    message = this.dmessage.content;
                }
                
                if (!dest.equals(new TString("NULL"))) {
                    if (this.service.allListeners.containsKey(dest)) {
                        Listener l = this.service.allListeners.get(dest);

                        this.currentModule = l.parent;
                        l.evaluate(this.params, message);
                    } else {
                    	this.service.bufferedMessage.add(this.dmessage);
                        this.service.bufferedResponseParameters.add(this.params);
                    }
                } else {
                    Set<TString> allListenersKeys = this.service.allListeners.keySet();
                    Iterator<TString> it = allListenersKeys.iterator();
                    
                    while (it.hasNext()) {
                        TString key = it.next();
                        Listener l = this.service.allListeners.get(key);

                        this.currentModule = l.parent;
                        l.evaluate(this.params, message);
                    	this.currentModule = null;
                    }
                }
            }
        }
        public ServiceCallOrResponse getCOR() {
    		return this.cor;
    	}
                
        public String toString() {
        	return new String(this.cor +":"+dmessage);
        }
    }
    
    /* Is the corresponding service call critical */
    protected final boolean isCallCritical;
    
    /* Is the corresponding service response critical */
    protected final boolean isResponseCritical;
    
    /* The name of the service instance */
    protected String name;
    
    /* The name of the stack that contains the service */
    protected ProtocolStack stack;
    
    /* The current Executer of the service calls */
    protected Executer currentExecuter;

    /* Map with all Listeners */
    protected HashMap<TString, Listener> allListeners;

    /* List of all bounded Interceptor */
    protected LinkedList<Interceptor> boundInterceptors;

    /* List of all buffered Message */
    protected LinkedList<Message> bufferedMessage;

    /* List of all buffered ResponseParameters */
    protected LinkedList<ResponseParameters> bufferedResponseParameters;

    /**
     * Constructor. By default, both call and responses to/from the service are critical
     * 
     * @param name
     *            name of the service
	 * @param stack
	 * 			  the {@link seqSamoa.ProtocolStack stack} to which the module belongs to
     */
    public Service(String name, ProtocolStack stack) throws AlreadyExistingServiceException {
    	this(name, stack, true, true);
    }

    /**
     * Constructor.
     * 
     * @param name
     *            name of the service
	 * @param stack
	 * 			  the {@link seqSamoa.ProtocolStack stack} to which the module belongs to
     */
    public Service(String name, ProtocolStack stack, boolean isCallCritical, boolean isResponseCritical) throws AlreadyExistingServiceException {
        super();

        this.name = name;
        this.stack = stack;
        this.isCallCritical = isCallCritical;
        this.isResponseCritical = isResponseCritical;
        stack.registerService(this);

        this.allListeners = new HashMap<TString, Listener>();
        this.boundInterceptors = new LinkedList<Interceptor>();
        this.bufferedMessage = new LinkedList<Message>();
        this.bufferedResponseParameters = new LinkedList<ResponseParameters>();
    }
    
	/**
	 * Return the name of the service
	 * 
	 * @return name of the service
	 */
	public String getName() {
		return name;
	}

    /**
     * Call the service
     * 
     * @param params
     *            parameters of the service call
     * @param dmessage
     *            {@link seqSamoa.Message message} with its destination
     * 
     */
    public void call(CallParameters params, Message dmessage) throws NotInAComputationException{
        ServiceCall sc = new ServiceCall(this, params, dmessage);
        try {
        	this.stack.scheduler.addInternalTask(sc);
        } catch (NotInAComputationException ex) {
        	throw new NotInAComputationException(new String("Call to service "+sc.service.name));
        }
    }
    
    /**
     * Externally call the service  
     * 
     * @param params
     *            parameters of the service call
     * @param dmessage
     *            {@link seqSamoa.Message message} with its destination
     *            
     * @return
     * 			the id corresponding to the external call
     */
    public long externalCall(CallParameters params, Message dmessage) {
        ServiceCall sc = new ServiceCall(this, params, dmessage);
        return this.stack.scheduler.addExternalTask(sc);
    }
    
    /**
     * Send the response(s) of the service
     * 
     * @param params
     *            the parameter of the service response
     * @param dmessage
     *            {@link seqSamoa.Message message} with its destination
     * 
     */
    public void response(ResponseParameters params, Message dmessage) throws NotInAComputationException {
        ServiceResponse sr = new ServiceResponse(this, params, dmessage);
        try {
        	this.stack.scheduler.addInternalTask(sr);
        } catch (NotInAComputationException ex) {
        	throw new NotInAComputationException(new String("Response to service "+sr.service.name));
        }
    }
    
    
    /**
     * Externally send the response of the service  
     * 
     * @param params
     *            the parameter of the service response
     * @param dmessage
     *            {@link seqSamoa.Message message} with its destination
     *            
     * @return
     * 			the id corresponding to the external response
     */
    public long externalResponse(ResponseParameters params, Message dmessage) {
        ServiceResponse sr = new ServiceResponse(this, params, dmessage);
        return this.stack.scheduler.addExternalTask(sr);
    }
    
    /**
     * Return the service provider
     * 
     * @return the protocol bound to this service
     */
    public ProtocolModule getProvider() {
        if (currentExecuter != null)
            return currentExecuter.parent;
        else
            return null;
    }

    /**
     * Unlink the {@link seqSamoa.Service.Executer executer} currently linked to the service
     * 
     */
    public void unlinkExecuter() {
        currentExecuter = null;
    }

    // Registrate this Listener and executes all the responses waiting to be
    protected void registrateListener(Listener l) {
        int i = 0;
        while (i < bufferedMessage.size()) {
            Message dmessage = bufferedMessage.get(i);
            ResponseParameters infos = bufferedResponseParameters.get(i);

            if (dmessage.dest.equals(l.key)) {
                l.evaluate(infos, dmessage.content);
                bufferedResponseParameters.remove(i);
                bufferedMessage.remove(i);
            } else {
                i++;
            }
        }
    }
    
    /**
     * A super class for executer, listener and interceptor
     */
    public class Handler {
        /* protocol containing this handler */
        protected ProtocolModule parent;

        /*
         * The list of calls and reponses that may be called
         * upon execution of the handler
         */
        protected LinkedList<ServiceCallOrResponse> initiatedCallsAndResponses;

        protected Handler(ProtocolModule parent, 
        		LinkedList<ServiceCallOrResponse> initiatedCallsAndResponses) {
            super();

            this.parent = parent;
            this.initiatedCallsAndResponses = initiatedCallsAndResponses;
        }

        /**
         * Return the service to which the Executer, listener or interceptor is bound
         * 
         * @return the service to which the Executer, listener or interceptor is bound
         */
        public Service<CallParameters, ResponseParameters> getService() {
            return Service.this;
        }
    }

    /**
     * A class <CODE>Listener</CODE> describe the behaviour of a {@link seqSamoa.ProtocolModule protocol} when
     * he receive a response of a {@link seqSamoa.Service service} call.
     * 
     * @see ProtocolModule
     */
    public abstract class Listener extends Handler {
        /* The string associated to the Listener */
        protected TString key;

        /**
         * Constructor.
         * 
         * @param parent
         *            the {@link seqSamoa.ProtocolModule protocol} containing the
         *            listener
         * @param serviceCalls
         *            the list of {@link simpleSamoa.Service services} that may
         *            be called while executing the listener
         * @param serviceResponses
         *            the list of {@link simpleSamoa.Service services} that we
         *            may respond to while executing the listener
         */
        public Listener(ProtocolModule parent, 
        		LinkedList<ServiceCallOrResponse> initiatedCallsAndResponses) {
            super(parent, initiatedCallsAndResponses);

            parent.allListeners.add(this);
            key = new TString(parent.name + parent.allListeners.size());

            allListeners.put(key, this);
        }
        
        /**
         * Constructor.
         * 
         * @param parentStack
         *            the {@link seqSamoa.ProtocolStack stack} containing the
         *            listener (only if the listener does not belong to a specific {@link seqSamoa.ProtocolModule protocol})
         * @param serviceCalls
         *            the list of {@link simpleSamoa.Service services} that may
         *            be called while executing the listener
         * @param serviceResponses
         *            the list of {@link simpleSamoa.Service services} that we
         *            may respond to while executing the listener
         */
        public Listener(ProtocolStack parentStack, 
        		LinkedList<ServiceCallOrResponse> initiatedCallsAndResponses) {
            super(parentStack.pFake, initiatedCallsAndResponses);

            parentStack.registerFinalListener(this);
            parent.allListeners.add(this);
            key = new TString(parent.name + parent.allListeners.size());

            allListeners.put(key, this);
        }

        /**
         * Define the listener behavior
         * 
         * @param params
         *            the parameter of the {@link seqSamoa.Service service} response
         * @param response
         *            the object of response without destination of the response
         */
        public abstract void evaluate(ResponseParameters params,
                Transportable response);
    }

    /**
     * A class <CODE>Executer</CODE> describe the behaviour of the service.
     * When a service s is called the Executer that is link to s is executed.
     * 
     * @see ProtocolModule
     */
    public abstract class Executer extends Handler{
        /**
         * Constructor.
         * 
         * @param parent
         *            the {@link seqSamoa.ProtocolModule Protocol} containing the
         *            executer
         * @param serviceCalls
         *            the list of {@link simpleSamoa.Service services} that may
         *            be called while executing the executer
         * @param serviceResponses
         *            the list of {@link simpleSamoa.Service services} that we
         *            may respond to while executing the executer
         */
        public Executer(ProtocolModule parent, 
        		LinkedList<ServiceCallOrResponse> initiatedCallsAndResponses) {
            super(parent, initiatedCallsAndResponses);

            parent.allExecuters.add(this);
        }
        
        /**
         * Constructor.
         * 
         * @param parentStack
         *            the {@link seqSamoa.ProtocolStack stack} containing the
         *            executer
         * @param serviceCalls
         *            the list of {@link simpleSamoa.Service services} that may
         *            be called while executing the executer
         * @param serviceResponses
         *            the list of {@link simpleSamoa.Service services} that we
         *            may respond to while executing the executer
         */
        public Executer(ProtocolStack parentStack, 
        		LinkedList<ServiceCallOrResponse> initiatedCallsAndResponses) {
            super(parentStack.pFake, initiatedCallsAndResponses);

            parent.allExecuters.add(this);
        }

        /**
         * Define the executer behavior
         * 
         * @param params
         *            the parameter of the {@link seqSamoa.Service service} call
         * @param dmessage
         *            the message of the {@link seqSamoa.Service service} call
         */
        public abstract void evaluate(CallParameters params, Message dmessage);

        /**
         * Link this executer to the {@link seqSamoa.Service service}
         */
        public void link() throws AlreadyBoundServiceException {
            if (currentExecuter == null)
                currentExecuter = this;
            else
                throw new AlreadyBoundServiceException((Service.this));
        }

        /**
         * Unlink this executer from the {@link seqSamoa.Service service}
         */
        public void unlink() {
            if (currentExecuter == this)
                currentExecuter = null;
        }
    }

    /**
     * A class <CODE>Interceptor</CODE> allow to intercept the call and the
     * response to calls of a {@link seqSamoa.Service service}. A Interceptor effectively intercept calls
     * and response only when it is linked.
     * 
     * @see ProtocolModule
     */
    public abstract class Interceptor extends Handler {
        /**
         * Constructor.
         * 
         * @param parent
         *            the {@link seqSamoa.ProtocolModule Protocol} containing the
         *            interceptor
         * @param serviceCalls
         *            the list of {@link simpleSamoa.Service services} that may
         *            be called while executing the interceptor
         * @param serviceResponses
         *            the list of {@link simpleSamoa.Service services} that we
         *            may respond to while executing the interceptor
         */
        public Interceptor(ProtocolModule parent, 
        		LinkedList<ServiceCallOrResponse> initiatedCallsAndResponses) {
            super(parent, initiatedCallsAndResponses);

            parent.allInterceptors.add(this);
        }
        
        /**
         * Constructor.
         * 
         * @param parentStack
         *            the {@link seqSamoa.ProtocolStack stack} containing the
         *            interceptor
         * @param serviceCalls
         *            the list of {@link simpleSamoa.Service services} that may
         *            be called while executing the interceptor
         * @param serviceResponses
         *            the list of {@link simpleSamoa.Service services} that we
         *            may respond to while executing the interceptor
         */
        public Interceptor(ProtocolStack parentStack, 
        		LinkedList<ServiceCallOrResponse> initiatedCallsAndResponses) {
            super(parentStack.pFake, initiatedCallsAndResponses);

            parent.allInterceptors.add(this);
        }

        /**
         * Define the behavior upon call interception
         * 
         * @param params
         *            the parameter of the {@link seqSamoa.Service service} call
         * @param dmessage
         *            the message of the {@link seqSamoa.Service service} call
         */
        public abstract void interceptCall(CallParameters params,
                Message dmessage);

        /**
         * Forward the call to next interceptor or to the executer
         * 
         * @param params
         *            the parameter of the {@link seqSamoa.Service service} call
         * @param dmessage
         *            the message of the {@link seqSamoa.Service service} call
         */
        public void forwardCall(CallParameters params, Message dmessage) {
            int nextIndex = boundInterceptors.indexOf(this) + 1;
            AtomicTask task = this.parent.stack.scheduler.currentTask();
            
            if (nextIndex < boundInterceptors.size()) {
            	task.currentModule = boundInterceptors.get(nextIndex).parent;
                boundInterceptors.get(nextIndex)
                        .interceptCall(params, dmessage);
                task.currentModule = null;
            } else {
            	task.currentModule = currentExecuter.parent;            	
            	currentExecuter.evaluate(params, dmessage);
            	task.currentModule = null;
            }
        }

        /**
         * Define the behavior upon response interception
         * 
         * @param params
         *            the parameter of the {@link seqSamoa.Service service} response
         * @param dmessage
         *            the message of the {@link seqSamoa.Service service} response
         */
        public abstract void interceptResponse(ResponseParameters params,
                Message dmessage);

        /**
         * Forward the response to next interceptor or to the executer
         * 
         * @param params
         *            the parameter of the {@link seqSamoa.Service service} response
         * @param dmessage
         *            the message of the {@link seqSamoa.Service service} response
         */
        public void forwardResponse(ResponseParameters params, Message dmessage) {
            int nextIndex = boundInterceptors.indexOf(this) - 1;
            AtomicTask task = this.parent.stack.scheduler.currentTask();

            if (nextIndex > 0) {
            	task.currentModule = boundInterceptors.get(nextIndex).parent;
                boundInterceptors.get(nextIndex).interceptResponse(params,
                        dmessage);
                task.currentModule = null;
            } else {
                TString dest;
                Transportable message;

                if (dmessage == null) {
                    dest = new TString("NULL");
                    message = null;
                } else {
                    dest = dmessage.dest;
                    message = dmessage.content;
                }
                
                if (!dest.equals(new TString("NULL"))) {
                    if (allListeners.containsKey(dest)) {
                        Listener l = allListeners.get(dest);

                        task.currentModule = l.parent;
                        l.evaluate(params, message);
                        task.currentModule = null;
                    } else {
                        bufferedMessage.add(dmessage);
                        bufferedResponseParameters.add(params);
                    }
                } else {
                    Set<TString> allListenersKeys = allListeners.keySet();
                    Iterator<TString> it = allListenersKeys.iterator();
                    
                    while (it.hasNext()) {
                        TString key = it.next();
                        Listener l = allListeners.get(key);

                        task.currentModule = l.parent;
                        l.evaluate(params, message);
                        task.currentModule = null;
                    }
                }
            }
        }

        /**
         * Bind this interceptor to the {@link seqSamoa.Service service}. An Interceptor could be link
         * only once to a {@link seqSamoa.Service service}.
         */
        public void bind() {
            if (!boundInterceptors.contains(this))
                boundInterceptors.addLast(this);
        }

        /**
         * Unbind this interceptor to the {@link seqSamoa.Service service}.
         */
        public void unbind() {
            boundInterceptors.remove(this);
        }
    }
}
