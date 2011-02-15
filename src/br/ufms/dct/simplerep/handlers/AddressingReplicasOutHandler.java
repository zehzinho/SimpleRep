package br.ufms.dct.simplerep.handlers;

import java.util.ArrayList;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axiom.soap.SOAPHeader;
import org.apache.axiom.soap.SOAPHeaderBlock;

import br.ufms.dct.simplerep.Host;
import br.ufms.dct.simplerep.SimpleRepConfiguration;
import br.ufms.dct.simplerep.ar.MessageContext;
import br.ufms.dct.simplerep.ar.ProcessingStatus;
import br.ufms.dct.simplerep.enums.AddressingConstants;

public class AddressingReplicasOutHandler implements AbstractHandler {
	public ProcessingStatus invoke(MessageContext context) {
		SOAPHeader header = context.getEnvelope().getHeader();
		
		OMNamespace addressingNamespaceObject = header.declareNamespace(AddressingConstants.ADDRESSING_NAMESPACE, AddressingConstants.WSA_DEFAULT_PREFIX);
		
		SOAPHeaderBlock soapHeaderBlock = header.addHeaderBlock("Replicas", addressingNamespaceObject);
		ArrayList<Host> proxies = null;
		SOAPFactory factory = (SOAPFactory) context.getEnvelope().getOMFactory();
		
		Host firstHost = null;
		
		proxies = SimpleRepConfiguration.getConfiguration().getProxies();
		
		for (Host h : proxies) {
			if (firstHost == null) {
				firstHost = h;
			}
			
			OMElement replica = factory.createOMElement("Replica", addressingNamespaceObject);
			OMElement EPRAddress = factory.createOMElement("Address", addressingNamespaceObject);

			EPRAddress.addChild(factory.createOMText(h.getHost() + ":" + h.getPort()));
			replica.addChild(EPRAddress);
			soapHeaderBlock.addChild(replica);
		}
		
		// @TODO check if it's not already present
		soapHeaderBlock = header.addHeaderBlock("From", addressingNamespaceObject);
		
		OMElement EPRAddress = factory.createOMElement("Address", addressingNamespaceObject);
		EPRAddress.addChild(factory.createOMText(firstHost.getHost() + ":" + firstHost.getPort()));
		soapHeaderBlock.addChild(EPRAddress);
		
		return ProcessingStatus.CONTINUE;
	}
}