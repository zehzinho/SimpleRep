package br.ufms.dct.simplerep.handlers;

import br.ufms.dct.simplerep.ar.MessageContext;
import br.ufms.dct.simplerep.ar.ProcessingStatus;

public class AddressingInHandler implements AbstractHandler {
	public ProcessingStatus invoke(MessageContext context) {
		return ProcessingStatus.CONTINUE;
	}

}
