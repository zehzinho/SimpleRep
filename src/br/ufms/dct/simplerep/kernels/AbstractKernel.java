package br.ufms.dct.simplerep.kernels;

import br.ufms.dct.simplerep.SimpleRepConfiguration;

public abstract class AbstractKernel {
	
	// The identifier of the queue from which the Transport takes responses and sends them to clients
	public static final String TRANSPORT_OUT_QUEUES = "simplerep_transport_out_queue";
	public static final String LAST_ENVELOPES_OUT_QUEUE = "simplerep_envelopes_in_queue";
	
	public static final String MESSAGE_ID = "simplerep_wsaddressing_messageid";
	public static final String REMOTE_HOST_IDENTIFIER = "simplerep_remote_host_identifier";
	
	public void init(SimpleRepConfiguration conf) { }
	public void shutdown() { }
}
