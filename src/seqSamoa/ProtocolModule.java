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

import java.io.OutputStream;
import java.util.LinkedList;

import seqSamoa.exceptions.AlreadyBoundServiceException;
import seqSamoa.exceptions.AlreadyExistingProtocolModuleException;

/**
 * <CODE>ProtocolModule</CODE> is the base class for all protocols. It contains
 * original methods, called {@link seqSamoa.Service.Executer Executers},
 * {@link seqSamoa.Service.Interceptor Interceptors}and
 * {@link seqSamoa.Service.Listener Listeners}.
 * 
 * The {@link seqSamoa.Service.Executer Executers}contained in the <CODE>
 * Protocol</CODE> determine the {@link seqSamoa.Service Services}it provides.
 * 
 * @see Service
 * @see Service.Listener
 * @see Service.Executer
 * @see Service.Interceptor
 */
public class ProtocolModule {
	// List of Listeners contained inside the microprotocol
	@SuppressWarnings("unchecked")
	protected LinkedList<Service.Listener> allListeners;

	// List of Executers contained inside the microprotocol
	@SuppressWarnings("unchecked")
	protected LinkedList<Service.Executer> allExecuters;

	// List of Interceptors contained inside the microprotocol
	@SuppressWarnings("unchecked")
	protected LinkedList<Service.Interceptor> allInterceptors;

	/**
	 * <CODE>name</CODE> is a unique identifier.
	 */
	protected String name;

	/* The name of the stack that contains the module */
	protected ProtocolStack stack;

	/**
	 * Constructor.
	 * 
	 * @param name
	 *            name of the protocol (unique ID)
	 * @param stack
	 * 			  the {@link seqSamoa.ProtocolStack stack} to which the module belongs to
	 */
	@SuppressWarnings("unchecked")
	public ProtocolModule(String name, ProtocolStack stack)
			throws AlreadyExistingProtocolModuleException {
		allListeners = new LinkedList<Service.Listener>();
		allExecuters = new LinkedList<Service.Executer>();
		allInterceptors = new LinkedList<Service.Interceptor>();
		this.name = name;
		this.stack = stack;

		// Registration of the protocol in table
		stack.registerProtocolModule(this);
	}

	/**
	 * Return the name of the protocol
	 * 
	 * @return name of the protocol
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Return the {@link seqSamoa.ProtocolStack stack} of the protocol
	 * 
	 * @return {@link seqSamoa.ProtocolStack stack} of the protocol
	 */
	public ProtocolStack getStack() {
		return stack;
	}
	
	/**
	 * Return true if the object in parameter is this protocol, 
	 *  and false otherwise
	 * 
	 * @param  the object to be compared with this protocol  
	 * @return true if the paramter is this protocol
	 */
	public boolean equals(Object o) {
		if (o instanceof ProtocolModule) {
			ProtocolModule p = (ProtocolModule) o;
			
			return this.name.equals(p.name);
		} else {
			return false;
		}
	}

	/**
	 * Link all the {@link seqSamoa.Service.Executer executers} contained in
	 * this Protocol to the service they provide.
	 */
	final public void linkToService() throws AlreadyBoundServiceException {
		int size = allExecuters.size();
		for (int i = 0; i < size; i++)
			allExecuters.get(i).link();
	}

	/**
	 * Unlink all the {@link seqSamoa.Service.Executer executers} contained in
	 * this Protocol from the service they provide.
	 */
	final public void unlinkFromService() {
		int size = allExecuters.size();
		for (int i = 0; i < size; i++)
			allExecuters.get(i).unlink();
	}

	/**
	 * Bind all the {@link seqSamoa.Service.Interceptor interceptors} contained
	 * in this Protocol to the service they provide.
	 */
	final public void bindInterceptors() {
		int size = allInterceptors.size();
		for (int i = 0; i < size; i++)
			allInterceptors.get(i).bind();
	}

	/**
	 * Unbind all the {@link seqSamoa.Service.Interceptor interceptors}
	 * contained in this Protocol from the service they provide.
	 */
	final public void unbindInterceptors() {
		int size = allInterceptors.size();
		for (int i = 0; i < size; i++)
			allInterceptors.get(i).unbind();
	}

	/**
	 * Init the protocol
	 */
	@SuppressWarnings("unchecked")
	public void init() {
		int size = allListeners.size();
		for (int i = 0; i < size; i++) {
			Service.Listener l = allListeners.get(i);
			l.getService().registrateListener(l);
		}
	}

	/**
	 * Close the protocol
	 */
	@SuppressWarnings("unchecked")
	public void close() {
		while (this.allExecuters.size() > 0) {
			Service.Executer e = this.allExecuters.remove(0);
			Service s = e.getService();

			if (s.currentExecuter == e)
				s.currentExecuter = null;
		}

		unbindInterceptors();
		while (this.allInterceptors.size() > 0)
			this.allInterceptors.remove(0);

		while (this.allListeners.size() > 0) {
			Service.Listener l = this.allListeners.remove(0);
			Service s = l.getService();

			s.allListeners.remove(l.key);
		}

		try {
			this.finalize();
		} catch (Throwable e) {
			throw new RuntimeException("Can not delete this microprotocol...");
		}
	}

	/**
	 * Commit the protocol. Do nothing by default
	 */
	public void commit() {		
	}
	
	/**
	 * Dump the protocol. Do nothing by default
	 */
	public void dump(OutputStream stream) {		
	}
	
	/**
	 * Recover the protocol from a file. Do onthing by default
	 * 
	 * @param recovery
	 * 		true, if we recover... false otherwise.
	 */
	public void recovery(boolean recovery) {		
	}
}
