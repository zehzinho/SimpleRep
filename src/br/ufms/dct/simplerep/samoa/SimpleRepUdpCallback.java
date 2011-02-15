package br.ufms.dct.simplerep.samoa;

import java.util.HashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import framework.libraries.serialization.TLinkedList;
import framework.libraries.serialization.TString;
import seqSamoa.Callback;
import uka.transport.Transportable;

/**
 * Producer for the Transport Proxy
 */

public class SimpleRepUdpCallback implements Callback {
	
	static Logger logger = Logger.getLogger(SimpleRepUdpCallback.class.getName());
	
	/**
	 * The Transport Proxy waits on this queue to send responses to clients
	 */
	HashMap<String, SynchronousQueue<String>> outQueues;
	
	public void serviceCallback(Object infos, Transportable message) {
		TLinkedList l = (TLinkedList) message;
		
		String envelope = ((TString) l.get(0)).toString();
		String queueId = ((TString) l.get(1)).toString();
		String from = ((TString) l.get(2)).toString();;
		
		logger.debug("Received a response from \"" + from + "\" via Samoa. Putting in the HTTP Proxy Queue.");
		SynchronousQueue<String> outQueue = outQueues.get(queueId);
		
		if (outQueue != null) {
			synchronized (outQueue) {
				outQueue.offer(envelope);
				outQueue.notifyAll();
			}
		}
	}
	
	public SimpleRepUdpCallback(HashMap<String, SynchronousQueue<String>> out) {
		this.outQueues = out;
	}

}
