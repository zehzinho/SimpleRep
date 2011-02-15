package br.ufms.dct.simplerep.ar;

import java.io.IOException;

import br.ufms.dct.simplerep.SimpleRepConfiguration;
import br.ufms.dct.simplerep.enums.SupportedProtocols;
import br.ufms.dct.simplerep.exceptions.ProxyCouldNotBeStarted;
import br.ufms.dct.simplerep.kernels.AbstractKernel;
import br.ufms.dct.simplerep.kernels.SamoaKernel;
import br.ufms.dct.simplerep.proxies.AbstractProxy;
import br.ufms.dct.simplerep.proxies.TCPProxy;
import br.ufms.dct.simplerep.proxies.UDPProxy;
import br.ufms.dct.simplerep.proxies.http.HTTPProxy;

public class Listener {

	protected static final int TCP = 1;
	
	private int localPort;
	private int targetPort;
	private String targetHost;
	SupportedProtocols protocol;

	private SimpleRepConfiguration conf;
	
	/**
	 * @param args
	 * @throws ProxyCouldNotBeStarted 
	 */
	
	public Listener(SupportedProtocols protocol, String targetHost, int targetPort, int localPort) {
		this.targetHost = targetHost;
		this.targetPort = targetPort;
		this.localPort = localPort;
		this.protocol = protocol;
	}
	
	public Listener(SimpleRepConfiguration conf) {
		this.conf = conf;
		this.targetHost = conf.getAppServerHost();
		this.targetPort = conf.getAppServerPort();
		this.localPort = conf.getProxyPort();
		this.protocol = conf.getTransportProtocol();
	}
	
	public void start() throws ProxyCouldNotBeStarted {
		try {
			
			// starting the samoa thread
			AbstractKernel kernel = new SamoaKernel();
			kernel.init(conf);
			
			AbstractProxy proxy = null; 
			
			if (protocol == SupportedProtocols.HTTP) {
				proxy = new HTTPProxy(this.conf);
			}
			else if (protocol == SupportedProtocols.TCP) {
				proxy = new TCPProxy(this.targetHost, this.targetPort, this.localPort);			
			}
			else {
				proxy = new UDPProxy(this.targetHost, this.targetPort, this.localPort);
			}
			
			proxy.start();
		}
		catch (IOException e) {
			System.out.println("[ERROR] SimpleRep aborting: could not listen on port " + this.localPort + ".");
		}
	}

}
