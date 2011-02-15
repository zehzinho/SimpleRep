package br.ufms.dct.simplerep.handlers;

// let's start using the seqSamoa version
import java.util.concurrent.BlockingQueue;

import br.ufms.dct.simplerep.ar.MessageContext;
import br.ufms.dct.simplerep.ar.SystemContext;
import br.ufms.dct.simplerep.kernels.SamoaKernel;


public class ABCastInHandler implements AbstractHandler {

	private BlockingQueue<MessageContext> inQueue;
	private BlockingQueue<MessageContext> outQueue;
	
	public ABCastInHandler(SystemContext sysContext) {
		inQueue = (BlockingQueue<MessageContext>) sysContext.get(SamoaKernel.SAMOA_IN_QUEUE);
		outQueue = (BlockingQueue<MessageContext>) sysContext.get(SamoaKernel.SAMOA_OUT_QUEUE);
	}
	
	public void invoke(MessageContext context) {
		System.out.println("[ABCastInHandler] invoke");

		// producing...
	/*	try {
			System.out.println("ABCastInHandler is enqueuing a message for samoa...");
			inQueue.put(context);
			
			synchronized(inQueue) {
				// waiting for the ABcast, then releasing resources
				inQueue.wait();
			}
			
		} catch (InterruptedException e1) {
			Thread.currentThread().interrupt();
		}
		
		// receiving feedback from samoa
		try {
			MessageContext broadcastMessage = outQueue.take();
			System.out.println("Message received in the ABCastInHandler from SAMOA: " + broadcastMessage.getEnvelope().toString());
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
   */
	}
}