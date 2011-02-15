package br.ufms.dct.simplerep.proxies;

import org.apache.axiom.soap.SOAPEnvelope;

import br.ufms.dct.simplerep.exceptions.ProxyCouldNotBeStarted;

public interface AbstractProxy {
	public void start() throws ProxyCouldNotBeStarted;
}
