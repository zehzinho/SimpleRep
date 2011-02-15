package br.ufms.dct.simplerep.kernels;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;

import org.apache.log4j.Logger;

import seqSamoa.Callback;
import seqSamoa.SamoaFlowControl;
import seqSamoa.SamoaScheduler;
import seqSamoa.SequentialManager;
import seqSamoa.api.ApiSamoaAbcastStack;
import seqSamoa.exceptions.AlreadyBoundServiceException;
import framework.PID;
import framework.libraries.serialization.TLinkedList;

import br.ufms.dct.simplerep.Host;
import br.ufms.dct.simplerep.SimpleRepConfiguration;
import br.ufms.dct.simplerep.ar.MessageContext;
import br.ufms.dct.simplerep.ar.SequencedEnvelope;
import br.ufms.dct.simplerep.ar.SystemContext;
import br.ufms.dct.simplerep.proxies.http.ThirdPartyRequestsRunner;
import br.ufms.dct.simplerep.samoa.Pt2Pt2Parameter;
import br.ufms.dct.simplerep.samoa.SimpleRepABCastCallback;
import br.ufms.dct.simplerep.samoa.runners.ABCastRunner;
import br.ufms.dct.simplerep.samoa.runners.Pt2PtRunner;

public class SamoaKernel extends AbstractKernel {
	static Logger logger = Logger.getLogger(SamoaKernel.class.getName());
	
	public static final String SAMOA_IN_QUEUE = "simplerep_in_queue";
	public static final String SAMOA_OUT_QUEUE = "simplerep_out_queue";
	public static final String SAMOA_ABCAST_IN_QUEUE = "simplerep_abcast_in_queue";
	public static final String SAMOA_ABCAST_OUT_QUEUE = "simplerep_abcast_out_queue";
	public static final String ORIGINAL_PID = "simplerep_samoa_original_pid";
	
	private static ApiSamoaAbcastStack stack;
	private SimpleRepConfiguration conf;
	
	SynchronousQueue<MessageContext> inQueue;
	SynchronousQueue<MessageContext> outQueue;
	HashMap<String, SynchronousQueue<String>> transportOutQueues;
	HashMap<String, SequencedEnvelope> lastEnvelopesOutQueue;
	
	BlockingQueue<Pt2Pt2Parameter> udpOutQueue;
	BlockingQueue<MessageContext> thirdPartyQueue;
	
	ExecutorService abcastExecutor;
	ExecutorService pt2ptExecutor;
	ExecutorService thirdPartyExecutor;
	
	public void init(SimpleRepConfiguration configuration) {
		conf = configuration;
		
		SystemContext sysContext = conf.getSystemContext();
		
		inQueue = new SynchronousQueue<MessageContext>();
		outQueue = new SynchronousQueue<MessageContext>();
		udpOutQueue = new LinkedBlockingQueue<Pt2Pt2Parameter>();
		thirdPartyQueue = new LinkedBlockingQueue<MessageContext>();
		transportOutQueues = new HashMap<String, SynchronousQueue<String>>();
		// host / <seqId,Envelope>
		lastEnvelopesOutQueue = new HashMap<String, SequencedEnvelope>();
		
		sysContext.set(SAMOA_ABCAST_IN_QUEUE, inQueue);
		sysContext.set(SAMOA_OUT_QUEUE, outQueue);
		sysContext.set(TRANSPORT_OUT_QUEUES, transportOutQueues);
		sysContext.set(LAST_ENVELOPES_OUT_QUEUE, lastEnvelopesOutQueue);
		
		stack = getSamoaStack();
		
		ABCastRunner samoaRunner = new ABCastRunner(stack, inQueue);
		abcastExecutor = Executors.newSingleThreadExecutor();
		abcastExecutor.execute(samoaRunner);
		
		Pt2PtRunner pt2ptRunner = new Pt2PtRunner(stack, udpOutQueue);
		pt2ptExecutor = Executors.newSingleThreadExecutor();
		pt2ptExecutor.execute(pt2ptRunner);
		
		if (conf.getReplicationStyle().equals("active")) {
			// sends requests to the local appServer in the active replication
			ThirdPartyRequestsRunner thirdPartyRunner = new ThirdPartyRequestsRunner(thirdPartyQueue, udpOutQueue);
			thirdPartyExecutor = Executors.newSingleThreadExecutor();
			thirdPartyExecutor.execute(thirdPartyRunner);
		}
	}
	
	public void shutdown(){
		stack.close();
		
		// killing the thread created in the init method
		abcastExecutor.shutdown();
		pt2ptExecutor.shutdown();
		
		if (conf.getReplicationStyle() == "active") {
			thirdPartyExecutor.shutdown();
		}
	}
	
	/**
	 * This method is the responsible for the actual instantiation of the
	 * samoa stack
	 *  
	 * @param localPort
	 * @param frameworkProcesses
	 * @return
	 */
	public synchronized ApiSamoaAbcastStack getSamoaStack() {
		if (stack == null) {
			logger.trace("Loading Samoa... ");
			
			PID myself;
			
			try {
				try {
					myself = new PID(InetAddress.getByName("127.0.0.1"), conf
							.getFrameworkLocalPort(), 0);
				} catch (UnknownHostException e1) {
					logger.fatal("Localhost is down!? Port: " + conf.getFrameworkLocalPort());
					return null;
				}

				SimpleRepABCastCallback callback = new SimpleRepABCastCallback(udpOutQueue, thirdPartyQueue);
				
				Callback udpCallback = new br.ufms.dct.simplerep.samoa.SimpleRepUdpCallback(this.transportOutQueues);

				TLinkedList processes = new TLinkedList();

				for (Host host : conf.getFrameworkProcesses()) {
					try {
						PID pid = new PID(InetAddress.getByName(host
								.getHost()), host.getPort(), 0);
						processes.addLast(pid);
						
						logger.debug("Host "  + pid + " added.");
					} catch (UnknownHostException e) {
						logger.error("The host " + host.getHost() + " does not exist.");
					}
				}
				
				// running the thread?
				stack = new ApiSamoaAbcastStack(myself, processes,
						new SamoaScheduler(new SequentialManager()),
						new SamoaFlowControl(1000), callback, udpCallback, null, conf);
				
				// we need the stack object to send direct messages in the callback
				callback.setStack(stack);

				stack.init();
				
				logger.trace("Samoa Stack up and running!");
			}
			catch (AlreadyBoundServiceException e) {
				logger.fatal("Samoa Stack não pôde ser instanciada.");
				return null;
			}
		}
		else {
			logger.trace("Samoa has already been started.");
		}
	
		return stack;
	}
}