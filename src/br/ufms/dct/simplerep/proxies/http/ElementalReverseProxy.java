/*
 * $HeadURL: http://svn.apache.org/repos/asf/httpcomponents/httpcore/trunk/module-main/src/examples/org/apache/http/examples/ElementalHttpServer.java $
 * $Revision: 702589 $
 * $Date: 2008-10-07 21:13:28 +0200 (Tue, 07 Oct 2008) $
 *
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package br.ufms.dct.simplerep.proxies.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;

import org.apache.axiom.soap.SOAPHeader;
import org.apache.axiom.soap.SOAPHeaderBlock;
import org.apache.http.ConnectionClosedException;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpServerConnection;
import org.apache.http.ProtocolVersion;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import br.ufms.dct.simplerep.SimpleRepConfiguration;
import br.ufms.dct.simplerep.ar.MessageContext;
import br.ufms.dct.simplerep.ar.OperationContext;
import br.ufms.dct.simplerep.ar.ProcessingStatus;
import br.ufms.dct.simplerep.ar.RequestProcessor;
import br.ufms.dct.simplerep.ar.SequencedEnvelope;
import br.ufms.dct.simplerep.ar.SystemContext;
import br.ufms.dct.simplerep.enums.AddressingConstants;
import br.ufms.dct.simplerep.httpprocessors.ABCastInterceptor;
import br.ufms.dct.simplerep.kernels.AbstractKernel;
import br.ufms.dct.simplerep.utils.HttpUtils;

/**
 * Rudimentary HTTP/1.1 reverse proxy.
 * <p>
 * Please note the purpose of this application is demonstrate the usage of
 * HttpCore APIs. It is NOT intended to demonstrate the most efficient way of
 * building an HTTP reverse proxy.
 * 
 * 
 * @version $Revision: $
 */
public class ElementalReverseProxy {
	
	static Logger logger = Logger.getLogger(ElementalReverseProxy.class.getName());

	private static final String HTTP_IN_CONN = "http.proxy.in-conn";
	private static final String HTTP_OUT_CONN = "http.proxy.out-conn";
	private static final String HTTP_CONN_KEEPALIVE = "http.proxy.conn-keepalive";

	public static void run(SimpleRepConfiguration conf) throws Exception {
		Thread t = new RequestListenerThread(conf);
		t.setDaemon(false);
		t.start();
	}

	static class ProxyHandler implements HttpRequestHandler {

		static Logger logger = Logger.getLogger(ProxyHandler.class.getName());
		
		private final HttpHost target;
		private final HttpProcessor httpproc;
		private final HttpRequestExecutor httpexecutor;
		private final ConnectionReuseStrategy connStrategy;
		private Socket inSocket;

		public ProxyHandler(final HttpHost target,
				final HttpProcessor httpproc,
				final HttpRequestExecutor httpexecutor) {
			super();
			this.target = target;
			this.httpproc = httpproc;
			this.httpexecutor = httpexecutor;
			this.connStrategy = new DefaultConnectionReuseStrategy();
		}

		public void handle(final HttpRequest request,
				final HttpResponse response, final HttpContext context)
				throws HttpException, IOException {

			HttpClientConnection conn = (HttpClientConnection) context
					.getAttribute(HTTP_OUT_CONN);

			context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
			context.setAttribute(ExecutionContext.HTTP_TARGET_HOST,this.target);
			
			System.out.println(">> Request URI: "
					+ request.getRequestLine().getUri());

			// Remove hop-by-hop headers
			request.removeHeaders(HTTP.CONTENT_LEN);
			request.removeHeaders(HTTP.TRANSFER_ENCODING);
			request.removeHeaders(HTTP.CONN_DIRECTIVE);
			request.removeHeaders("Keep-Alive");
			request.removeHeaders("Proxy-Authenticate");
			request.removeHeaders("TE");
			request.removeHeaders("Trailers");
			request.removeHeaders("Upgrade");

			this.httpexecutor.preProcess(request, this.httpproc, context);

			// BEGIN(@JRS)
			SynchronousQueue<String> myOutQueue = this.getMyOutQueue(context);
			HttpResponse targetResponse = null;
			
			if (myOutQueue != null) {
				ExecutorService localInvocationExecutor = Executors.newSingleThreadExecutor();
				BasicHttpEntityEnclosingRequest realRequest = (BasicHttpEntityEnclosingRequest) request;
				SystemContext sysContext = SimpleRepConfiguration.getConfiguration().getSystemContext();

				String incomingEnvelope = EntityUtils.toString(((BasicHttpEntityEnclosingRequest) request).getEntity());
				MessageContext inEnvelopeContext = MessageContext.buildMessageContext(incomingEnvelope);
				
				// este mesmo remoteHostIdentifier é usado no ThirdPartyRequestsRunner
				String remoteHostIdentifier = inEnvelopeContext.getRemoteHostIdentifier();
				logger.debug("Incoming connection. Remote Host Identifier: " + remoteHostIdentifier);
				
				HashMap<String, SequencedEnvelope> lastEnvelopesOutQueue = (HashMap<String, SequencedEnvelope>) sysContext.get(AbstractKernel.LAST_ENVELOPES_OUT_QUEUE);
				SequencedEnvelope lastSentSequencedEnvelope = lastEnvelopesOutQueue.get(remoteHostIdentifier);
				
				int incomingSequenceId = inEnvelopeContext.getSequenceId();
				
				if (logger.isDebugEnabled()) {
					if (incomingSequenceId == 0) {
						logger.debug("ResetSeqId received. It's the first interaction.");
					}
					else if (incomingSequenceId < 0) {
						logger.debug("The seqId could not be retrieved.");
					}
					else if (lastSentSequencedEnvelope == null) {
						logger.debug("No sequenced envelope has been received until now.");
					}
					else {
						logger.debug("Incoming sequenceId: " + incomingSequenceId + ". Last Sequence id: " + lastSentSequencedEnvelope.getSequenceId());
					}
				}
				
				if (lastSentSequencedEnvelope != null && incomingSequenceId == lastSentSequencedEnvelope.getSequenceId()) {
					// we already have the response
					// bypassing
					logger.info("[ProxyHandler] Envelope already processed. Bypassing.");
					targetResponse = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 200, "Success");
					targetResponse.setEntity(new BasicHttpEntity());
					
					((BasicHttpEntity) targetResponse.getEntity()).setContent(new ByteArrayInputStream(lastSentSequencedEnvelope.getEnvelope().toString().getBytes()));
				}
				else {
					// must be shared IN and OUT
					OperationContext operationContext = new OperationContext();
					inEnvelopeContext.setOperationContext(operationContext);
					
					// Putting the message in the inflow
					
					if (RequestProcessor.getProcessor().inFlow(inEnvelopeContext) == ProcessingStatus.ABORT) {
						// Something went wrong in the InFlow
						// we have to restore the original envelope in the request entity (which is read-once, remember?)
						logger.warn("The InFlow has been aborted.");
						realRequest.setEntity(HttpUtils.string2BasicEntity(incomingEnvelope));
					}
					else {
						// everything went well in the InFlow, replacing the original envelope
						// with the new (possibly modified) envelope
						logger.debug("Inflow OK. Sending the following envelope to the appserver: " + inEnvelopeContext.getEnvelope().toString());
						realRequest.setEntity(HttpUtils.string2BasicEntity(inEnvelopeContext.getEnvelope().toString()));
					}
					
					LocalInvocationRunner localInvocationRunner = new LocalInvocationRunner(
							myOutQueue, this.httpexecutor, request, context, conn);
	
					// at this point the response may come from the local app server or from
					// any of the replicas
					String envelope = "";
					MessageContext outMessageContext = null;
	
					try {
						logger.debug("Waiting for some envelope... ");
						
						localInvocationExecutor.execute(localInvocationRunner);
						envelope = myOutQueue.take();
						outMessageContext = MessageContext.buildMessageContext(envelope);
						logger.debug("Unprocessed response: " + outMessageContext.getEnvelope().toString() + ". Putting in the outflow...");
						
						outMessageContext.setOperationContext(operationContext);
						RequestProcessor.getProcessor().outFlow(outMessageContext);
						
						if (incomingSequenceId == 2) {
							System.err.println("Received the SECOND envelope. I'm going to sleep.");
							Thread.sleep(10000);
							System.err.println("Woke up!");
						}
						
						//logger.debug("[ProxyHandler] Processed response: " + outMessageContext.getEnvelope().toString());
						
						// Saving the envelope and sequenceId
						SequencedEnvelope seqEnv = new SequencedEnvelope(incomingSequenceId, outMessageContext.getEnvelope());
						lastEnvelopesOutQueue.put(remoteHostIdentifier, seqEnv);
					} catch (InterruptedException e) {
						logger.fatal("Fatal error when trying to get an envelope from one of the replicas.");
					}
	
					targetResponse = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 200, "Success");
					targetResponse.setEntity(new BasicHttpEntity());
					
					((BasicHttpEntity) targetResponse.getEntity()).setContent(new ByteArrayInputStream(outMessageContext.getEnvelope().toString().getBytes()));
				}
			} else {
				// normal proxy behaviour
				targetResponse = this.httpexecutor.execute(request, conn, context);
			}

			this.httpexecutor.postProcess(response, this.httpproc, context);

			// END(@JRS)

			// Remove hop-by-hop headers
			targetResponse.removeHeaders(HTTP.CONTENT_LEN);
			targetResponse.removeHeaders(HTTP.TRANSFER_ENCODING);
			targetResponse.removeHeaders(HTTP.CONN_DIRECTIVE);
			targetResponse.removeHeaders("Keep-Alive");
			targetResponse.removeHeaders("TE");
			targetResponse.removeHeaders("Trailers");
			targetResponse.removeHeaders("Upgrade");

			response.setStatusLine(targetResponse.getStatusLine());
			response.setHeaders(targetResponse.getAllHeaders());
			response.setEntity(targetResponse.getEntity());

			logger.info("<< Response: " + response.getStatusLine());
			logger.info("");
			logger.info("===== End of interaction ====");
			logger.info("");
			logger.info("");
			logger.info("");

			boolean keepalive = this.connStrategy.keepAlive(response, context);
			context.setAttribute(HTTP_CONN_KEEPALIVE, new Boolean(keepalive));
		}

		private SynchronousQueue<String> getMyOutQueue(HttpContext context) {
			SimpleRepConfiguration conf;
			SystemContext sysCtxt = null;

			sysCtxt = SimpleRepConfiguration.getConfiguration().getSystemContext();

			HashMap<String, SynchronousQueue<String>> transportOutQueues = (HashMap<String, SynchronousQueue<String>>) sysCtxt.get(AbstractKernel.TRANSPORT_OUT_QUEUES);
			String messageId = (String) context.getAttribute(AbstractKernel.MESSAGE_ID);

			if (messageId == null || messageId.equals("")) {
				logger.fatal("wsa:MessageID could not be retrieved. Aborting!");
				return null;
			}

			// This thread is going to wait on this queue for the envelope to be
			// sent to the client
			return transportOutQueues.get(messageId);
		}
		
		public void setInSocket(Socket inSocket) {
			this.inSocket = inSocket;
		}

		public Socket getInSocket() {
			return inSocket;
		}

	}

	static class RequestListenerThread extends Thread {

		private final HttpHost target;
		private final ServerSocket serversocket;
		private final HttpParams params;
		private final HttpService httpService;
		private SimpleRepConfiguration conf;
		private ProxyHandler proxyHandler;

		public RequestListenerThread(SimpleRepConfiguration conf)
				throws IOException {
			this.target = new HttpHost(conf.getAppServerHost(), conf
					.getAppServerPort());
			this.serversocket = new ServerSocket(conf.getProxyPort());
			this.conf = conf;
			this.params = new BasicHttpParams();
			this.params
					.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 5000)
					.setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE,
							8 * 1024)
					.setBooleanParameter(
							CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
					.setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
					.setParameter(CoreProtocolPNames.ORIGIN_SERVER,
							"HttpComponents/1.1");

			// Set up HTTP protocol processor for incoming connections
			BasicHttpProcessor inhttpproc = new BasicHttpProcessor();
			inhttpproc.addInterceptor(new ResponseDate());
			inhttpproc.addInterceptor(new ResponseServer());
			inhttpproc.addInterceptor(new ResponseContent());
			inhttpproc.addInterceptor(new ResponseConnControl());

			// @BEGIN(JRS)
			// inhttpproc.addInterceptor(new SimpleRepInFlowInterceptor()); //
			// old stuff
			BlockingQueue<MessageContext> inQueue = new SynchronousQueue<MessageContext>();

			inhttpproc.addInterceptor(new ABCastInterceptor(inQueue));
			// @END(JRS)

			// Set up HTTP protocol processor for outgoing connections
			BasicHttpProcessor outhttpproc = new BasicHttpProcessor();
			outhttpproc.addInterceptor(new RequestContent());
			outhttpproc.addInterceptor(new RequestTargetHost());
			outhttpproc.addInterceptor(new RequestConnControl());
			outhttpproc.addInterceptor(new RequestUserAgent());
			outhttpproc.addInterceptor(new RequestExpectContinue());

			// Set up outgoing request executor
			HttpRequestExecutor httpexecutor = new HttpRequestExecutor();

			// Set up incoming request handler
			HttpRequestHandlerRegistry reqistry = new HttpRequestHandlerRegistry();
			this.proxyHandler = new ProxyHandler(this.target, outhttpproc, httpexecutor);
			
			reqistry.register("*", this.proxyHandler);

			// Set up the HTTP service
			this.httpService = new HttpService(inhttpproc,
					new DefaultConnectionReuseStrategy(),
					new DefaultHttpResponseFactory());
			this.httpService.setParams(this.params);
			this.httpService.setHandlerResolver(reqistry);
		}

		public void run() {
			logger.info("HTTP Proxy Listening on port "
					+ this.serversocket.getLocalPort());
			while (!Thread.interrupted()) {
				try {
					// Set up incoming HTTP connection
					Socket insocket = this.serversocket.accept();
					DefaultHttpServerConnection inconn = new DefaultHttpServerConnection();
					System.out.println("Incoming connection from "
							+ insocket.getInetAddress());
					this.proxyHandler.setInSocket(insocket);
					
					inconn.bind(insocket, this.params);

					// Set up outgoing HTTP connection
					Socket outsocket = new Socket(this.target.getHostName(),
							this.target.getPort());
					DefaultHttpClientConnection outconn = new DefaultHttpClientConnection();
					outconn.bind(outsocket, this.params);
					System.out.println("Outgoing connection to "
							+ outsocket.getInetAddress());

					// Start worker thread
					Thread t = new ProxyThread(this.httpService, inconn,
							outconn, insocket);
					t.setDaemon(true);
					t.start();
				} catch (InterruptedIOException ex) {
					break;
				} catch (IOException e) {
					System.err
							.println("I/O error initialising connection thread: "
									+ e.getMessage()
									+ ". The application server is down.");
					break;
				}
			}
		}
	}

	static class ProxyThread extends Thread {

		private final HttpService httpservice;
		private final HttpServerConnection inconn;
		private final HttpClientConnection outconn;
		private final Socket inSocket;

		public ProxyThread(final HttpService httpservice,
				final HttpServerConnection inconn,
				final HttpClientConnection outconn,
				final Socket insocket) {
			super();
			this.httpservice = httpservice;
			this.inconn = inconn;
			this.outconn = outconn;
			this.inSocket = insocket;
		}

		public void run() {
			System.out.println("New connection thread");
			HttpContext context = new BasicHttpContext(null);

			// Bind connection objects to the execution context
			context.setAttribute(HTTP_IN_CONN, this.inconn);
			context.setAttribute(HTTP_OUT_CONN, this.outconn);

			try {
				while (!Thread.interrupted()) {
					if (!this.inconn.isOpen()) {
						this.outconn.close();
						break;
					}
					
					this.httpservice.handleRequest(this.inconn, context);

					Boolean keepalive = (Boolean) context
							.getAttribute(HTTP_CONN_KEEPALIVE);
					if (!Boolean.TRUE.equals(keepalive)) {
						this.outconn.close();
						this.inconn.close();
						break;
					}
				}
			} catch (ConnectionClosedException ex) {
				System.err.println("Client closed connection");
			} catch (IOException ex) {
				if (ex.getMessage().equals("Broken pipe")) {
					logger.debug(ex.getMessage() + ". The client has probably failed over.");
				}
				else {
					System.err.println("I/O error: " + ex.getMessage());	
				}
				
			} catch (HttpException ex) {
				System.err.println("Unrecoverable HTTP protocol violation: "
						+ ex.getMessage());
			} finally {
				try {
					this.inconn.shutdown();
				} catch (IOException ignore) {
				}
				try {
					this.outconn.shutdown();
				} catch (IOException ignore) {
				}
			}
		}

	}

}
