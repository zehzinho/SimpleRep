package br.ufms.dct.simplerep;

import org.apache.log4j.Logger;

import br.ufms.dct.simplerep.ar.*;
import br.ufms.dct.simplerep.exceptions.ProxyCouldNotBeStarted;
import br.ufms.dct.simplerep.exceptions.SimpleRepConfException;

public class Main {
	static Logger logger = Logger.getLogger(Main.class.getName());
	
	public static void main(String args[]) {
		try {
			SimpleRepConfiguration conf = SimpleRepConfiguration.getConfiguration();
			
			logger.info("SimpleRep will be listening on port: " + conf.getProxyPort());
				
			Listener listener = new Listener(conf);
			listener.start();
		}
		catch (ProxyCouldNotBeStarted ex) {
			logger.fatal("Listener could not be started.");
		}
	}
}
