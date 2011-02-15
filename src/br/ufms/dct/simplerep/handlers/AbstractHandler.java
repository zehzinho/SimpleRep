package br.ufms.dct.simplerep.handlers;

import br.ufms.dct.simplerep.ar.MessageContext;
import br.ufms.dct.simplerep.ar.ProcessingStatus;

public interface AbstractHandler {
	ProcessingStatus invoke(MessageContext context);
}
