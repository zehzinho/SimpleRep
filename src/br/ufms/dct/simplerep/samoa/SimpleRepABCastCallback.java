package br.ufms.dct.simplerep.samoa;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import br.ufms.dct.simplerep.SimpleRepConfiguration;
import br.ufms.dct.simplerep.ar.MessageContext;
import br.ufms.dct.simplerep.ar.RequestProcessor;
import br.ufms.dct.simplerep.ar.SequencedEnvelope;
import br.ufms.dct.simplerep.ar.SystemContext;
import br.ufms.dct.simplerep.exceptions.SimpleRepConfException;
import br.ufms.dct.simplerep.kernels.AbstractKernel;
import br.ufms.dct.simplerep.kernels.SamoaKernel;

import seqSamoa.Callback;
import seqSamoa.api.ApiSamoaAbcastStack;
import uka.transport.Transportable;
import framework.PID;
import framework.libraries.serialization.TLinkedList;
import framework.libraries.serialization.TString;

public class SimpleRepABCastCallback implements Callback {
	static Logger logger = Logger.getLogger(SimpleRepABCastCallback.class.getName());
	
	SimpleRepConfiguration conf;
	ApiSamoaAbcastStack stack;
	BlockingQueue<Pt2Pt2Parameter> udpOutQueue;
	
	// in active replication, we have a thread to send requests to the local servers
	// when we are not the original AR
	BlockingQueue<MessageContext> thirdPartyQueue;
	
	public SimpleRepABCastCallback(BlockingQueue<Pt2Pt2Parameter> udpOutQueue, BlockingQueue<MessageContext> thirdPartyQueue) {
		this.thirdPartyQueue = thirdPartyQueue;
		this.udpOutQueue = udpOutQueue; 
	}
	
	public void setStack(ApiSamoaAbcastStack stack) {
		this.stack = stack;
	}
	
	public void serviceCallback(Object infos, Transportable message) {
		// message just arrived via ABcast
		TLinkedList msgs = (TLinkedList) message;
		
		String envelopeStr = ((TString) msgs.getFirst()).toString();
		String waitingQueueId = ((TString) msgs.get(1)).toString();
		String originalUrl = ((TString) msgs.get(2)).toString();
		PID originalRAPID = ((PID) msgs.get(3));
		String msgid = ((TString) msgs.get(4)).toString();
		
		if (envelopeStr == null || envelopeStr.length() <= 0) {
			logger.fatal("The received envelope is empty!");
			return;
		}
		
		if (waitingQueueId == null || waitingQueueId.length() <= 0) {
			logger.fatal("The Waiting Queue Id could not be retrieved!");
			return;
		}
		
		logger.debug("Received envelope: " + envelopeStr);
		
		MessageContext inMsgContext = MessageContext.buildMessageContext(envelopeStr);
		
		// our handlers are prepared to handle only valid MessageContexts
		RequestProcessor requestProcessor = RequestProcessor.getProcessor();
		
		if (inMsgContext != null) {
			requestProcessor.inFlow(inMsgContext);
		}
		
		conf = SimpleRepConfiguration.getConfiguration();

		SystemContext sysContext = conf.getSystemContext();
		
		// if there is a waitingQueue, it's because we're in the 
		// host which received the client's request
		SynchronousQueue<MessageContext> waitingQueue = (SynchronousQueue<MessageContext>) sysContext.remove(waitingQueueId);
		
		if (conf.getReplicationStyle().equals("active")) {
			if (waitingQueue != null) {
				// letting the flow go
				try {
					waitingQueue.put(inMsgContext);
				} catch (InterruptedException e) {
					logger.error("The msgContext could not be put into the waitingQueue.");
				}
				
				logger.debug("ABCastInterceptor's queue has one more element.");
			}
			else {
				inMsgContext.setProperty(MessageContext.SOURCE_ADDRESS, originalUrl);
				inMsgContext.setProperty(SamoaKernel.ORIGINAL_PID, originalRAPID);
				inMsgContext.setProperty(AbstractKernel.MESSAGE_ID, msgid);
				
				try {
					thirdPartyQueue.put(inMsgContext);
				} catch (InterruptedException e) {
					logger.error("The msgContext could not be put into the thirdPartyQueue.");
				}
				
				logger.debug("The WaitingQueue couldn't be retrieved, this host is not the primary. ThirdPartyRequestsRunner has one more request to make now.");
			}
		}
		else if (conf.getReplicationStyle().equals("passive")) {
			
		}
	}
}
