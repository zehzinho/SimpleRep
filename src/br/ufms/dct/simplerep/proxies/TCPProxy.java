package br.ufms.dct.simplerep.proxies;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.axiom.soap.SOAPEnvelope;

import br.ufms.dct.simplerep.SimpleRepConfiguration;
import br.ufms.dct.simplerep.exceptions.ProxyCouldNotBeStarted;

public class TCPProxy implements AbstractProxy {
	
	private ServerSocket serverSocket;

	public TCPProxy(String targetHostname, int port, int localPort) throws IOException {
		serverSocket = new ServerSocket(localPort);
		Socket clientSocket = null;
		clientSocket = serverSocket.accept();
	}
	
	public void run() {
		
	}

	public void start() throws ProxyCouldNotBeStarted {
		// TODO Auto-generated method stub
		
	}
}
