package br.ufms.dct.simplerep.samoa.runners;

import java.util.concurrent.BlockingQueue;

import org.apache.log4j.Logger;

import framework.libraries.serialization.TLinkedList;
import framework.libraries.serialization.TString;

import br.ufms.dct.simplerep.SimpleRepConfiguration;
import br.ufms.dct.simplerep.exceptions.SimpleRepConfException;
import br.ufms.dct.simplerep.samoa.Pt2Pt2Parameter;
import br.ufms.dct.simplerep.samoa.SimpleRepUdpCallback;

import seqSamoa.api.ApiSamoaAbcastStack;
import seqSamoa.services.udp.UDPCallParameters;

public class Pt2PtRunner implements Runnable {

	static Logger logger = Logger.getLogger(Pt2PtRunner.class.getName());

	/**
	 * This queue stores the parameters needed to send direct messages to the
	 * original Replication Agents
	 */
	private BlockingQueue<Pt2Pt2Parameter> outQueue;

	/**
	 * The stack through which the thread is going to send the messages
	 */
	ApiSamoaAbcastStack stack;

	public Pt2PtRunner(ApiSamoaAbcastStack samoaStack,
			BlockingQueue<Pt2Pt2Parameter> q) {
		outQueue = q;
		stack = samoaStack;
	}

	public void run() {
		if (outQueue == null || stack == null) {
			String nullComponent = "outQueue";

			if (stack == null)
				nullComponent = "stack";

			logger.error(nullComponent + " is null!");
			return;
		}

		while (true) {
			try {
				String localhost = "";
				
				SimpleRepConfiguration conf = SimpleRepConfiguration.getConfiguration();
				localhost = conf.getFrameworkLocalHost() + ":" + conf.getFrameworkLocalPort();

				Pt2Pt2Parameter msgAndTarget = outQueue.take();
				UDPCallParameters params = msgAndTarget.getTarget();

				TLinkedList toSend = new TLinkedList();
				toSend.add(msgAndTarget.getEnvelope());
				toSend.add(msgAndTarget.getMsgId());
				toSend.add(new TString(localhost));

				logger.debug("Sending my local response to the original RA ("
						+ params.pid + ") via Samoa.");
				stack.serviceCall("udp", params, toSend);
			} catch (InterruptedException e) {
				logger.error("Pt2Pt2Parameter could not be taken from the outQueue!");
				return;
			}
			
			logger.info("");
			logger.info("===== End of interaction ====");
			logger.info("");
			logger.info("");
			logger.info("");
		}
	}

}
