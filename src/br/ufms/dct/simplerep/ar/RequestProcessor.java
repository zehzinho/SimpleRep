package br.ufms.dct.simplerep.ar;

import java.util.ArrayList;
import br.ufms.dct.simplerep.handlers.AbstractHandler;
import br.ufms.dct.simplerep.handlers.AddressingReplicasOutHandler;

/**
 * Mainly responsible for the implementation of the In and Out Flows
 * 
 *
 */
public class RequestProcessor {
	private ArrayList<AbstractHandler> inFlowHandlers;
	private ArrayList<AbstractHandler> outFlowHandlers;
	
	private ProcessingStatus processFlow(MessageContext unprocessedMsg, ArrayList<AbstractHandler> handlers) {
		for (AbstractHandler h : handlers) {
			if (h.invoke(unprocessedMsg) == ProcessingStatus.ABORT) {
				return ProcessingStatus.ABORT;
			}
		}
		
		return ProcessingStatus.CONTINUE;
	}

	public ProcessingStatus inFlow(MessageContext unprocessedMsg) {
		return processFlow(unprocessedMsg, this.inFlowHandlers);
	}
	
	public ProcessingStatus outFlow(MessageContext unprocessedMsg) {
		return processFlow(unprocessedMsg, this.outFlowHandlers);
	}
	
	protected RequestProcessor() {
	}

	private static RequestProcessor singleton = null;

	public static RequestProcessor getProcessor() {
		if (singleton == null) {
			singleton = new RequestProcessor();
			singleton.inFlowHandlers = new ArrayList<AbstractHandler>();
			singleton.outFlowHandlers = new ArrayList<AbstractHandler>();
			
			singleton.outFlowHandlers.add(new AddressingReplicasOutHandler());
		}
		
		return singleton;
	}

}

