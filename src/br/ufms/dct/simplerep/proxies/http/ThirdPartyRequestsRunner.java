package br.ufms.dct.simplerep.proxies.http;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import seqSamoa.services.udp.UDPCallParameters;
import framework.PID;
import framework.libraries.serialization.TString;
import br.ufms.dct.simplerep.SimpleRepConfiguration;
import br.ufms.dct.simplerep.ar.MessageContext;
import br.ufms.dct.simplerep.ar.SequencedEnvelope;
import br.ufms.dct.simplerep.exceptions.SimpleRepConfException;
import br.ufms.dct.simplerep.kernels.AbstractKernel;
import br.ufms.dct.simplerep.kernels.SamoaKernel;
import br.ufms.dct.simplerep.samoa.Pt2Pt2Parameter;
import br.ufms.dct.simplerep.xml.SoapHelper;

public class ThirdPartyRequestsRunner implements Runnable {

	static Logger logger = Logger.getLogger(ThirdPartyRequestsRunner.class.getName());
	
	BlockingQueue<MessageContext> thirdPartyQueue;
	BlockingQueue<Pt2Pt2Parameter> udpOutQueue;

	public ThirdPartyRequestsRunner(BlockingQueue<MessageContext> thirdPartyQueue, BlockingQueue<Pt2Pt2Parameter> udpOutQueue) {
		this.thirdPartyQueue = thirdPartyQueue;
		this.udpOutQueue = udpOutQueue;
	}

	public void run() {
		SimpleRepConfiguration conf = SimpleRepConfiguration.getConfiguration();
		
		String appServerHost = conf.getAppServerHost();
		int appServerPort = conf.getAppServerPort();

		logger.debug("Running...");
		
		while (true) {
			MessageContext msgContext = null;
			
			try {
				msgContext = thirdPartyQueue.take();
			} catch (InterruptedException e) {
				logger.error("Error while trying to take a msgContext from the queue.");
				continue;				
			}
			
			// invoking
			logger.debug("It's active replication. Invoking my local App Server.");
			
			String originalServicePath = (String) msgContext.getProperty(MessageContext.SOURCE_ADDRESS);
			PID originalRAPID = (PID) msgContext.getProperty(SamoaKernel.ORIGINAL_PID);
			String msgid = (String) msgContext.getProperty(AbstractKernel.MESSAGE_ID);
			
			if (originalServicePath == null || originalRAPID == null || msgid == null) {
				logger.error("Missing parameter. Aborting. ");
				continue;
			}

			String localAppServerServicePath = "http://" + appServerHost + ":" + appServerPort + originalServicePath;

			logger.debug("Local Service Path: " + localAppServerServicePath);
			
			HttpClient httpclient = new DefaultHttpClient();
			HttpPost httppost = new HttpPost(localAppServerServicePath);

			StringEntity requestEntity;

			try {
				requestEntity = new StringEntity(msgContext.getEnvelope().toString());
				httppost.setEntity(requestEntity);
				HttpResponse response = httpclient.execute(httppost);
				HttpEntity entity = response.getEntity();

				String envelope = "";

				if (entity != null) {
					envelope = EntityUtils.toString(entity);
				} else {
					logger.error("Null response from the server!");
					continue;
				}
				
				logger.debug("Response received from the local server: " + envelope);
				
				Pt2Pt2Parameter params = new Pt2Pt2Parameter(new TString(envelope), new UDPCallParameters(originalRAPID));
				params.setMsgId(new TString(msgid));
				
				
				Object leou = msgContext.getSystemContext().get(AbstractKernel.LAST_ENVELOPES_OUT_QUEUE);
				HashMap<String, SequencedEnvelope> lastEnvelopesOutQueue = (HashMap<String, SequencedEnvelope>) leou;
				
				SequencedEnvelope seqEnv = new SequencedEnvelope(msgContext.getSequenceId(), SoapHelper.str2Envelope(envelope));
				logger.debug("Putting the " + msgContext.getSequenceId() + "th envelope in the lastEnvelopesOutQueue for " + msgContext.getRemoteHostIdentifier());
				
				// it must be set here, so we can know in the ElementalReverseProxy
				// if the incoming message (in case of retransmission) has already been processed
				lastEnvelopesOutQueue.put(msgContext.getRemoteHostIdentifier(), seqEnv);
				
				udpOutQueue.offer(params);
				logger.debug("Pt2PtRunner's queue was fed. The local response should soon be sent to the original RA.");

			} catch (ClientProtocolException e) {
				logger.error("The active replication request to my App Server could not be done.");
				continue;
			}
			catch (UnsupportedEncodingException e1) {
				logger.error("The App Server could not be contacted: Envelope could not be turned into an entity.");
				continue;
			}
			catch (IOException e) {
				logger.error("IO Error in the active replication request to my App Server.");
				continue;
			}
		}

	}

}
