package br.ufms.dct.simplerep;

public class Host {
	private String host;
	private int port;
	
	public Host(String host, int port) {
		this.host = host;
		this.port = port;
	}
	
	public void setHost(String host) {
		this.host = host;
	}
	
	public String getHost() {
		return host;
	}
	
	public void setPort(int port) {
		this.port = port;
	}
	
	public int getPort() {
		return port;
	}
}
