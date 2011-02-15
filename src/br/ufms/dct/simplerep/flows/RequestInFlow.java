package br.ufms.dct.simplerep.flows;

import java.util.ArrayList;
import org.apache.axiom.soap.SOAPEnvelope;

import br.ufms.dct.simplerep.ar.MessageContext;
import br.ufms.dct.simplerep.handlers.AbstractHandler;
import br.ufms.dct.simplerep.handlers.AddressingRequestHandler;

public class RequestInFlow implements AbstractFlow {
	public ArrayList<AbstractHandler> handlers;

	/**
	 * Defines the set of handlers which handle incoming messages from the client
	 */
	public RequestInFlow() {
		handlers.add(new AddressingRequestHandler());
	}
	
	public void invoke(MessageContext context) {
		for (AbstractHandler handler : handlers) {
			handler.invoke(context);
		}
	}
}
