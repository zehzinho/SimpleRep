package br.ufms.dct.simplerep.handlers;

import java.util.Iterator;

import javax.xml.namespace.QName;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.SOAPEnvelope;

import br.ufms.dct.simplerep.ar.MessageContext;
import br.ufms.dct.simplerep.ar.ProcessingStatus;
import br.ufms.dct.simplerep.enums.AddressingConstants;

/**
 * This handler removes the ReplyTo and FaultTo headers from the original request
 * @author José Ricardo
 *
 */
public class AddressingRequestHandler implements AbstractHandler {

	// this implementation works only with WS-Addressing 1.0
	protected static final String addressingNamespace = "http://www.w3.org/2005/08/addressing";
	protected static final String defaultPrefix = "wsa";
	
	public ProcessingStatus invoke(MessageContext context) {
		SOAPEnvelope env = context.getEnvelope();
		
		Iterator it = env.getHeader().getChildrenWithName(new QName(addressingNamespace, "ReplyTo", defaultPrefix ));

		// if some of the headers are present, we have to store them, so the reverse proxy
		// works appropriately according to the WS-addressing 1.0
		OMElement replyTo = null, faultTo = null;
		
		if (it != null && it.hasNext()) {
			replyTo = (OMElement) it.next();
			
			// TODO: check if it is the anonymous address, if it is, we don't have to store it
			context.setProperty(AddressingConstants.replyTo, replyTo.toString());
			
			it.remove();
		}
		
		it = env.getHeader().getChildrenWithName(new QName(addressingNamespace, "FaultTo", defaultPrefix ));
		
		if (it != null && it.hasNext()) {
			faultTo = (OMElement) it.next();
			
			// TODO: check if it is the anonymous address, if it is, we don't have to store it
			context.setProperty(AddressingConstants.faultTo, faultTo.toString());
			
			it.remove();
		}
		
		return ProcessingStatus.CONTINUE;
	}

}
