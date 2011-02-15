package br.ufms.dct.simplerep.samoa.runners;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.BlockingQueue;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.log4j.Logger;

import br.ufms.dct.simplerep.SimpleRepConfiguration;
import br.ufms.dct.simplerep.ar.MessageContext;
import br.ufms.dct.simplerep.enums.SimpleRepConstants;
import br.ufms.dct.simplerep.exceptions.SimpleRepConfException;
import br.ufms.dct.simplerep.httpprocessors.ABCastInterceptor;
import framework.PID;
import framework.libraries.serialization.TLinkedList;
import framework.libraries.serialization.TString;
import seqSamoa.api.ApiSamoaAbcastStack;

public class ABCastRunner implements Runnable {
	static Logger logger = Logger.getLogger(ABCastRunner.class.getName());
	private static ApiSamoaAbcastStack stack;

	BlockingQueue<MessageContext> in;

	public ABCastRunner(ApiSamoaAbcastStack samoaStack,
			BlockingQueue<MessageContext> inQueue) {
		stack = samoaStack;
		this.in = inQueue;
	}

	public void run() {
		
		PID myPID = null;
		
		try {
			SimpleRepConfiguration conf = SimpleRepConfiguration.getConfiguration();
			myPID = new PID(InetAddress.getByName(conf.getFrameworkLocalHost()), conf.getFrameworkLocalPort(), 0);
		} catch (UnknownHostException e) {
			logger.fatal("The local PID is not a valid host.");
			return;
		}
		
		while (true) {
			logger.trace("still running...");

			MessageContext nextMsgContext = null;

			try {
				// BlockingQueues #ftw
				nextMsgContext = in.take();
			} catch (InterruptedException e) {
				logger.error("The next msgContext could not be retrieved. Problem during in.take()");
				continue;
			}

			SOAPEnvelope soapEnvelope = nextMsgContext.getEnvelope();
			String envelopeString =  new String(soapEnvelope.toString());
			TString envelope = new TString(envelopeString);
			String originalUrl = (String) nextMsgContext.getProperty(SimpleRepConstants.ORIGINAL_URL);
			String msgid = nextMsgContext.getMessageId();
			
			TLinkedList l = new TLinkedList();
			l.add(envelope);
			l.add(new TString((String) nextMsgContext.getProperty(ABCastInterceptor.AppServerInQueueId)));
			l.add(new TString(originalUrl));
			l.add(myPID);
			l.add(new TString(msgid));

			logger.debug("ABcasting msgContext.");
			stack.abcastMessage(l);

			// the rest is handled in the abcast callback
		}

	}
}
