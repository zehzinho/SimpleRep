package br.ufms.dct.simplerep;

import java.io.IOException;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import br.ufms.dct.simplerep.ar.SystemContext;
import br.ufms.dct.simplerep.enums.SupportedProtocols;
import br.ufms.dct.simplerep.exceptions.SimpleRepConfException;
import br.ufms.dct.simplerep.proxies.http.ElementalReverseProxy;
import br.ufms.dct.simplerep.xml.XmlHelper;

public class SimpleRepConfiguration {

	static Logger logger = Logger.getLogger(SimpleRepConfiguration.class.getName());
	
	private String appServerHost;
	private int proxyPort;
	private int appServerPort; 
	private SupportedProtocols transportProtocol;
	private int frameworkLocalPort;
	private String replicationStyle;
	private int frameworkTimeout;
	private SystemContext systemContext;
	
	/**
	 * Each element is an array of two elements
	 * The first element is the host of the other process
	 * The second element is the port 
	 */
	private ArrayList<Host> otherFrameworkProcesses;
	
	/**
	 * The reverse proxies, which receive client requests
	 */
	private ArrayList<Host> proxies;
	
	private String frameworkLocalHost;
	
	public static SimpleRepConfiguration getConfiguration() {
		if (singleton == null) {
			try {
				singleton = new SimpleRepConfiguration();
			} catch (SimpleRepConfException e) {
				System.out.println("THE CONFIGURATION COULD NOT BE READ. REASON: " + e.getMessage());
				System.exit(-1);
			}
		}
		
		return singleton;
	}
	
	protected SimpleRepConfiguration() throws SimpleRepConfException {
		try {
			this.buildConf();
		} catch (SAXException e) {
			logger.fatal("SAXException during configuration loading.");
		} catch (IOException e) {
			logger.fatal("IOException during configuration loading.");
		} catch (ParserConfigurationException e) {
			logger.fatal("ParseConfigurationException during configuration loading.");
		}
	}
	
	/**
	 * This methods is responsible for reading the configuration file
	 * and setting the respective properties
	 * 
	 * @throws SimpleRepConfException
	 * @throws IOException 
	 * @throws SAXException 
	 * @throws ParserConfigurationException 
	 */
	protected void buildConf() throws SimpleRepConfException, SAXException, IOException, ParserConfigurationException {
		Document doc = XmlHelper.getDoc("simplerep_conf.xml");
		
		if (doc == null) {
			throw new SimpleRepConfException("Configuration file is missing.");
		}
		
		Element replicasTag = XmlHelper.getFirstElement("replicas", doc);
		
		this.proxyPort = Integer.parseInt(XmlHelper.getFirstChildValue("port", doc));
		String transportProtocol = XmlHelper.getFirstChildValue("transportProtocol", doc);
		
		this.setFrameworkTimeout(Integer.parseInt(XmlHelper.getFirstChildValue("timeout", doc)));
		
		if (transportProtocol.equals("http")) {
			this.transportProtocol = SupportedProtocols.HTTP;
		}
		else {
			// TODO add more protocols
			throw new SimpleRepConfException("The selected Transport Protocol is not supported.");
		}
		
		this.setReplicationStyle(replicasTag.getAttribute("style").trim());
		
		if (this.getReplicationStyle().equals("")) {
			throw new SimpleRepConfException("The replication style (active, passive, etc) must be specified.");
		}
			
		NodeList replicas = replicasTag.getElementsByTagName("replica");
		this.otherFrameworkProcesses = new ArrayList<Host>();
				
		for (int s = 0; s < replicas.getLength(); s++) {
			String replicaAddress =  XmlHelper.getElementValue(replicas.item(s));
		    String[] rawHost = new String[2];
		    
		    if (replicaAddress.contains(":")) {
		    	rawHost[0] = replicaAddress.substring(0, replicaAddress.lastIndexOf(":"));
		    	rawHost[1] = replicaAddress.substring(replicaAddress.lastIndexOf(":") + 1);
		    }
		    else {
		    	throw new SimpleRepConfException("A replica address has not been properly defined (host:port).");
		    }
		    
		    try {
		    	otherFrameworkProcesses.add(new Host(rawHost[0], Integer.parseInt(rawHost[1])));
			}
			catch (NumberFormatException ex) {
				throw new SimpleRepConfException("A replica's port was not properly defined.");
			}
		}
		
		logger.debug(otherFrameworkProcesses.size() + " samoa addresses read.");
		
		Element proxiesTag = XmlHelper.getFirstElement("proxies", doc);
		NodeList proxiesNodes = proxiesTag.getElementsByTagName("proxy");
		
		this.setProxies(new ArrayList<Host>());
				
		for (int s = 0; s < proxiesNodes.getLength(); s++) {
			String proxyAddress =  XmlHelper.getElementValue(proxiesNodes.item(s));
		    String[] rawHost = new String[2];
		    
		    if (proxyAddress.contains(":")) {
		    	rawHost[0] = proxyAddress.substring(0, proxyAddress.lastIndexOf(":"));
		    	rawHost[1] = proxyAddress.substring(proxyAddress.lastIndexOf(":") + 1);
		    }
		    else {
		    	throw new SimpleRepConfException("A proxy address has not been properly defined (host:port).");
		    }
		    
		    try {
		    	this.getProxies().add(new Host(rawHost[0], Integer.parseInt(rawHost[1])));
			}
			catch (NumberFormatException ex) {
				throw new SimpleRepConfException("A proxy's port was not properly defined.");
			}
		}
		
		Element samoaTag = XmlHelper.getFirstElement("samoa", doc);
		
		this.setFrameworkLocalHost(XmlHelper.getFirstChildValue("host", samoaTag));
		this.frameworkLocalPort = Integer.parseInt(XmlHelper.getFirstChildValue("port", samoaTag));
		
		Element appServerTag = XmlHelper.getFirstElement("appserver", doc);
		
		this.appServerHost = XmlHelper.getFirstChildValue("host", appServerTag);
		this.appServerPort = Integer.parseInt(XmlHelper.getFirstChildValue("port", appServerTag));
		
		setSystemContext(new SystemContext());
		
		logger.debug("Simplerep is ready to start the " + this.replicationStyle + " replication!");
	}
	
	public SimpleRepConfiguration(String fileName) {
		// TODO
	}

	public void setAppServerHost(String appServerHost) {
		this.appServerHost = appServerHost;
	}

	public String getAppServerHost() {
		return appServerHost;
	}

	public void setProxyPort(int proxyPort) {
		this.proxyPort = proxyPort;
	}

	public int getProxyPort() {
		return proxyPort;
	}

	public void setAppServerPort(int appServerPort) {
		this.appServerPort = appServerPort;
	}

	public int getAppServerPort() {
		return appServerPort;
	}

	public void setTransportProtocol(SupportedProtocols transportProtocol) {
		this.transportProtocol = transportProtocol;
	}

	public SupportedProtocols getTransportProtocol() {
		return transportProtocol;
	}
	
	private static SimpleRepConfiguration singleton = null;

	public int getFrameworkLocalPort() {
		return this.frameworkLocalPort;
	}
	
	public void setFrameworkLocalPort(int port) {
		this.frameworkLocalPort = port;
	}

	public void setFrameworkProcesses(ArrayList<Host> hosts) {
		this.otherFrameworkProcesses = hosts;
	}

	public ArrayList<Host> getFrameworkProcesses() {
		return this.otherFrameworkProcesses;
	}

	private void setReplicationStyle(String replicationStyle) {
		this.replicationStyle = replicationStyle;
	}

	public String getReplicationStyle() {
		return replicationStyle;
	}

	public void setFrameworkTimeout(int frameworkTimeout) {
		this.frameworkTimeout = frameworkTimeout;
	}

	public int getFrameworkTimeout() {
		return frameworkTimeout;
	}

	public void setSystemContext(SystemContext systemContext) {
		this.systemContext = systemContext;
	}

	public SystemContext getSystemContext() {
		return systemContext;
	}

	public void setFrameworkLocalHost(String frameworkLocalHost) {
		this.frameworkLocalHost = frameworkLocalHost;
	}

	public String getFrameworkLocalHost() {
		return frameworkLocalHost;
	}

	public void setProxies(ArrayList<Host> proxies) {
		this.proxies = proxies;
	}

	public ArrayList<Host> getProxies() {
		return proxies;
	}
}
