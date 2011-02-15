package br.ufms.dct.simplerep.handlers;

import org.apache.axiom.soap.SOAPEnvelope;

import br.ufms.dct.simplerep.ar.MessageContext;
import br.ufms.dct.simplerep.ar.ProcessingStatus;

public class AddressingResponseHandler implements AbstractHandler {

	public ProcessingStatus invoke(MessageContext envelope) {
		// TODO check if there is a ReplyTo or a FaultTo header in the original request and the change the
		// current message's To header.
		return ProcessingStatus.CONTINUE;
	}

	
}
