package br.ufms.dct.simplerep.flows;

import br.ufms.dct.simplerep.ar.MessageContext;


public interface AbstractFlow {
	public void invoke(MessageContext context);
}
