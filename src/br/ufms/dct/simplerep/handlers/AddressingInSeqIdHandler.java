package br.ufms.dct.simplerep.handlers;

import java.util.Iterator;
import org.apache.axiom.soap.SOAPHeader;
import org.apache.axiom.soap.SOAPHeaderBlock;
import br.ufms.dct.simplerep.ar.MessageContext;
import br.ufms.dct.simplerep.ar.ProcessingStatus;
import br.ufms.dct.simplerep.enums.AddressingConstants;
import br.ufms.dct.simplerep.enums.SimpleRepConstants;

public class AddressingInSeqIdHandler implements AbstractHandler {
	public ProcessingStatus invoke(MessageContext context) {
		System.out.println("[AddressingInSeqIdHandler] invoke()");
		
		SOAPHeader soapHeader = context.getEnvelope().getHeader();
		int seqId = -1;
		boolean isReset = false;
		
		Iterator headers = soapHeader.getHeadersToProcess(null, AddressingConstants.ADDRESSING_NAMESPACE);
		
		while (headers.hasNext()) {
			SOAPHeaderBlock soapHeaderBlock = (SOAPHeaderBlock)headers.next();
            String localName = soapHeaderBlock.getLocalName();
            
            if (localName.equals("SequenceID")) {
            	seqId = Integer.parseInt(soapHeaderBlock.getText());
            }
            
            else if (localName.equals("ResetSequenceID")) {
            	isReset = true;
            }
		}
		
		if (seqId < 0) {
			// SequenceId was not received. 
			// The client does not use the Addressing Extension
			return ProcessingStatus.CONTINUE;
		}
		
		// we're going to need the sequence id in the future, right?
		context.getOperationContext().set(SimpleRepConstants.RECEIVED_SEQUENCE_ID, new Integer(seqId));
		
		int lastSeqId = -1; 
		
		if (isReset) {
			// assuming there always is a RESET
			// and RESETs always come with a SequenceID = 1, nothing else to do
			lastSeqId = 1;
		}
		else {
			lastSeqId = getLastSeqId(soapHeader);
			
			if (lastSeqId <= 0) {
				// it's not a RESET and the client is using the extension 
				// but we couldn't find the lastSeqId, something went REALLY wrong
				// it's VERY likely that the client is not sending the ReferenceParameters back
				return ProcessingStatus.CONTINUE; // there's nothing to do in this case
			}
		}
		
		if (seqId == lastSeqId + 1) {
			// yes, this is what we usually expect
			context.getOperationContext().set(SimpleRepConstants.LAST_SEQUENCE_ID, lastSeqId);
		}
		else if (seqId <= lastSeqId) { 
			// the message has already been processed
			// as the user knows the lastSeqId, I can't figure out how this could have happened
			System.err.println("[AddressingInSeqIdHandler] Anomaly: seqId <= lastSeqId");
    	}
		else if (seqId > lastSeqId + 1) {
			System.err.println("[AddressingInSeqIdHandler] seqId > lastSeqId + 1. Someone is generating SequenceIDs too fast.");
		}
		
		return ProcessingStatus.CONTINUE;
	}
	
	private int getLastSeqId(SOAPHeader soapHeader) {
		Iterator headers = soapHeader.getHeadersToProcess(null, SimpleRepConstants.SIMPLEREP_NAMESPACE);
		
		while (headers.hasNext()) {
			SOAPHeaderBlock soapHeaderBlock = (SOAPHeaderBlock)headers.next();
            String localName = soapHeaderBlock.getLocalName();
            
            if (localName.equals("SequenceID")) {
            	try {
            		return Integer.parseInt(soapHeaderBlock.getText());
            	}
            	catch(NumberFormatException ex) {
            		return -1;
            	}
            }
		}
		
		return -1;
	}
}