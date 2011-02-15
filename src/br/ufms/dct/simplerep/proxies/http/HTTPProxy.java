package br.ufms.dct.simplerep.proxies.http;

import br.ufms.dct.simplerep.SimpleRepConfiguration;
import br.ufms.dct.simplerep.exceptions.ProxyCouldNotBeStarted;
import br.ufms.dct.simplerep.proxies.AbstractProxy;

public class HTTPProxy implements AbstractProxy {

	protected SimpleRepConfiguration conf;
	
	private String targetHostname;
	private int targetPort;
	private int localPort;

	public HTTPProxy(SimpleRepConfiguration conf) {
		this.conf = conf;
	}

	public void start() throws ProxyCouldNotBeStarted {
		try {
			ElementalReverseProxy.run(this.conf);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}