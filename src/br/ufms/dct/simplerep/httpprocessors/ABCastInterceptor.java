package br.ufms.dct.simplerep.httpprocessors;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;

import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.ParseException;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import br.ufms.dct.simplerep.SimpleRepConfiguration;
import br.ufms.dct.simplerep.ar.MessageContext;
import br.ufms.dct.simplerep.ar.SystemContext;
import br.ufms.dct.simplerep.enums.SimpleRepConstants;
import br.ufms.dct.simplerep.exceptions.SimpleRepConfException;
import br.ufms.dct.simplerep.kernels.AbstractKernel;
import br.ufms.dct.simplerep.kernels.SamoaKernel;

public class ABCastInterceptor implements HttpRequestInterceptor {
	static Logger logger = Logger.getLogger(ABCastInterceptor.class.getName());
	
	// the queue from which the HttpProxy will wait
	// for the response and then send the id to the client
	private BlockingQueue<MessageContext> in;
	
	public static final String AppServerInQueueId = "SIMPLEREP_AppServerQueue";
	HttpEntity entity;

	public ABCastInterceptor(BlockingQueue<MessageContext> inQueue) {
		this.in = inQueue;
	}

	public void process(HttpRequest request, HttpContext context) {
		SystemContext sysContext;

		BasicHttpEntityEnclosingRequest basicRequest = (BasicHttpEntityEnclosingRequest) request;
		
		entity = basicRequest.getEntity();
		
		try {
			sysContext = SimpleRepConfiguration.getConfiguration()
					.getSystemContext();
			BlockingQueue<MessageContext> abcastInQueue = (BlockingQueue<MessageContext>) sysContext
					.get(SamoaKernel.SAMOA_ABCAST_IN_QUEUE);
			
			MessageContext msgContext = MessageContext.buildMessageContext(EntityUtils.toString(entity));

			String messageId = msgContext.getMessageId();
			
			if (messageId == null || messageId.equals("")) {
				System.err.println("[ABCastInterceptor] FATAL ERROR: wsa:MessageID could not be retrieved. Aborting!");				
				return;
			}
			
			context.setAttribute(AbstractKernel.MESSAGE_ID, messageId);
			
			// ABCast output queue
			SynchronousQueue<MessageContext> myQueue = new SynchronousQueue<MessageContext>();
			
			// this one will be read by the Abcast Callback
			sysContext.set(messageId, myQueue);
			
			// The Transport Component will wait on this queue to send the response to the client
			HashMap<String, SynchronousQueue<String>> backChannelQueue = (HashMap<String, SynchronousQueue<String>>) sysContext.get(SamoaKernel.TRANSPORT_OUT_QUEUES);

			// this one will be fed by the producers (UDPCallback and Proxy) and read by the Transport Component
			backChannelQueue.put(msgContext.getMessageId(), new SynchronousQueue<String>());
			
			// this one will be read by the ABCastRunner
			msgContext.setProperty(AppServerInQueueId, messageId);
			
			// TODO find the original URL
			msgContext.setProperty(SimpleRepConstants.ORIGINAL_URL, basicRequest.getRequestLine().getUri());
			
			abcastInQueue.put(msgContext);

			synchronized (myQueue) {
				// waiting for the ABcast, then releasing resources
				logger.debug("Waiting for the abcast and inflow to complete.");
				MessageContext processedContext = myQueue.take();
				logger.debug("Message was abcasted and processed in the inflow. Invoking my AppServer.");
				
				((BasicHttpEntity) entity).setContent(new ByteArrayInputStream(processedContext.getEnvelope().toString().getBytes()));
			}
		} catch (InterruptedException e) {
			logger.error("ABCast could not be done. Error when trying to enqueue the message.");
		} catch (ParseException e) {
			logger.error("ParseException; ABCast could not be done. Envelope could not be parsed.");
		} catch (IOException e) {
			logger.error("IOException; ABCast could not be done. Envelope could not be parsed.");
		}
	}
}
