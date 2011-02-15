package br.ufms.dct.simplerep.handlers;

import java.util.Iterator;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axiom.soap.SOAPHeader;
import org.apache.axiom.soap.SOAPHeaderBlock;

import br.ufms.dct.simplerep.ar.MessageContext;
import br.ufms.dct.simplerep.ar.OperationContext;
import br.ufms.dct.simplerep.ar.ProcessingStatus;
import br.ufms.dct.simplerep.enums.AddressingConstants;
import br.ufms.dct.simplerep.enums.SimpleRepConstants;

public class AddressingOutSeqIdHandler implements AbstractHandler {
	public ProcessingStatus invoke(MessageContext context) {
		OperationContext opContext = context.getOperationContext();
		
		int lastSeqId = -1;
		
		try {
			lastSeqId = Integer.parseInt(opContext.get(SimpleRepConstants.LAST_SEQUENCE_ID).toString());	
		}
		catch (NumberFormatException ex) {
			System.err.println("[AddressingOutSeqIdHandler] LastSequenceID could not be read.");
			return ProcessingStatus.CONTINUE;
		}
		catch (NullPointerException ex) {
			System.err.println("[AddressingOutSeqIdHandler] LastSequenceID was not defined in the OperationContext.");
			return ProcessingStatus.CONTINUE;
		}
		
		
		if (lastSeqId <= 0) {
			// very unlikely, exceptions should be caugth above.
			System.err.println("[AddressingOutSeqIdHandler] LastSequenceID is negative!");
			return ProcessingStatus.CONTINUE;
		}
		
		SOAPFactory factory = (SOAPFactory) context.getEnvelope().getOMFactory();
		
		return ProcessingStatus.CONTINUE;
	}
	
	public ProcessingStatus oldinvoke(MessageContext context) {
		System.out.println("[AddressingOutSeqIdHandler] invoke()");
		SOAPHeader soapHeader = context.getEnvelope().getHeader();

		OMElement refParams = null;
		SOAPHeaderBlock replyToBlock = null;
		OMElement address = null;
		
		// if there is no LastSequenceID in the message,
		// we assume it's the first message
		int lastSeqId = getLastSeqId(context);
		
		if (lastSeqId > 0) 
			lastSeqId++;  // generating the next one
		else 
			lastSeqId = 1;
			
		SOAPFactory factory = (SOAPFactory) context.getEnvelope().getOMFactory();
		
		// The LastSequenceID is transmitted via ReferenceParameters
		// More: http://www.w3.org/TR/2006/REC-ws-addr-soap-20060509
		
		if (refParams == null) {
			refParams = factory.createOMElement("ReferenceParameters", AddressingConstants.ADDRESSING_NAMESPACE, AddressingConstants.WSA_DEFAULT_PREFIX);
		}
		
		OMElement lastSeqIdElement = factory.createOMElement(
				"LastSequenceID", SimpleRepConstants.SIMPLEREP_NAMESPACE,
				SimpleRepConstants.SIMPLEREP_NAMESPACE_PREFIX);
		
		lastSeqIdElement.addChild(factory.createOMText(String.valueOf(lastSeqId)));
		
		refParams.addChild(lastSeqIdElement);
		
		if (replyToBlock == null) {
			// The ReferenceParameter must be encapsulated in an EndpointReference
			replyToBlock = soapHeader.addHeaderBlock(
					"ReplyTo", soapHeader.declareNamespace(
							AddressingConstants.ADDRESSING_NAMESPACE,
							AddressingConstants.WSA_DEFAULT_PREFIX));

			// Axis2 uses the ReplyTo (none) for their ServiceGroupId parameter, we're are doing the same
			// More: http://wso2.org/print/4666
			address = factory.createOMElement("Address", AddressingConstants.ADDRESSING_NAMESPACE, AddressingConstants.WSA_DEFAULT_PREFIX);
			address.addChild(factory.createOMText("http://www.w3.org/2005/08/addressing/none"));
			replyToBlock.addChild(address);
		}
		
		replyToBlock.addChild(refParams);
		
		return ProcessingStatus.CONTINUE;
	}
	
	private SOAPHeaderBlock getReplyTo(MessageContext context) {
		SOAPHeader soapHeader = context.getEnvelope().getHeader();
		
		// checking if the ReplyTo and ReferenceParameters are already present
		// in the message, so we don't override them
		Iterator addressingHeaders = soapHeader.getHeadersToProcess(null, AddressingConstants.ADDRESSING_NAMESPACE);
		
		while (addressingHeaders.hasNext()) {
			SOAPHeaderBlock soapHeaderBlock = (SOAPHeaderBlock) addressingHeaders.next();
			String localName = soapHeaderBlock.getLocalName();

			if (localName.equals("ReplyTo")) {
				return soapHeaderBlock;
			}
		}
		
		return null;
	}
	
	private OMElement getReferenceParametersElement(SOAPHeaderBlock replyToBlock) {
		Iterator replyToIt = replyToBlock.getChildrenWithNamespaceURI(AddressingConstants.ADDRESSING_NAMESPACE);
		
		while (replyToIt.hasNext()) {
			OMElement replyToChildElement = (OMElement) replyToIt.next();
			String replyToChildName = replyToChildElement.getLocalName();
		
			if (replyToChildName.equals("ReferenceParameters")) {
				Iterator refParamsIt = replyToChildElement.getChildrenWithNamespaceURI(SimpleRepConstants.SIMPLEREP_NAMESPACE);
				
				while(refParamsIt.hasNext()) {
					OMElement refParamsChildElement = (OMElement) replyToIt.next();
					if (refParamsChildElement.getLocalName().equals("LastSequenceID")) {
						try {
							return Integer.parseInt(refParamsChildElement.getText());
						}
						catch(NumberFormatException ex) {
							return -1;
						}
					}
				}
			}
		}
		
		return null;
	}
}
