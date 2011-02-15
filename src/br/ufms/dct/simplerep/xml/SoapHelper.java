package br.ufms.dct.simplerep.xml;

import java.util.Iterator;

import javax.xml.stream.XMLStreamException;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.llom.factory.OMXMLBuilderFactory;
import org.apache.axiom.om.impl.llom.util.AXIOMUtil;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axiom.soap.SOAPHeader;
import org.apache.axiom.soap.SOAPHeaderBlock;
import org.apache.axiom.soap.impl.builder.StAXSOAPModelBuilder;

import br.ufms.dct.simplerep.ar.ProcessingStatus;

public class SoapHelper {

	public static SOAPEnvelope str2Envelope(String xmlString) {
		try {
			// TODO: verify the SOAP version in the string
			SOAPFactory soapFactory = OMAbstractFactory.getSOAP12Factory();
			OMElement documentElement = AXIOMUtil.stringToOM(xmlString);
			StAXSOAPModelBuilder builder = OMXMLBuilderFactory.createStAXSOAPModelBuilder(soapFactory, documentElement.getXMLStreamReader());

			return builder.getSOAPEnvelope();
		} catch (XMLStreamException e) {
			return null;
		}
	}
	
	public static int getLastSequenceID(SOAPHeader soapHeader) {
		Iterator headers = soapHeader.getHeadersToProcess(null, "http://www.w3.org/2005/08/addressing");
		
		int seqId = -1;
		int lastSeqId = -1;
		boolean isReset = false;

		SOAPHeaderBlock referenceParametersHeaderBlock = null;
		
		while (headers.hasNext()) {
			SOAPHeaderBlock soapHeaderBlock = (SOAPHeaderBlock)headers.next();
            String localName = soapHeaderBlock.getLocalName();
            
            if (localName.equals("SequenceID")) {
            	try {
            		seqId = Integer.parseInt(soapHeaderBlock.getText());
            	}
            	catch (NumberFormatException e) {
            		// if the client didn't send a SeqID, then there's nothing we can do
            		return -1;
            	}
            	
            	return seqId;
            }
		}
		
		return -1;
	}
}
