package br.ufms.dct.simplerep.ar;

import java.util.HashMap;
import java.util.Iterator;

import javax.xml.namespace.QName;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPHeader;
import org.apache.axiom.soap.SOAPHeaderBlock;

import com.sun.corba.se.impl.orbutil.closure.Constant;

import br.ufms.dct.simplerep.SimpleRepConfiguration;
import br.ufms.dct.simplerep.enums.AddressingConstants;
import br.ufms.dct.simplerep.exceptions.SimpleRepConfException;
import br.ufms.dct.simplerep.xml.SoapHelper;

public class MessageContext {
	protected SOAPEnvelope env;
	protected HashMap<String, Object> properties;
	private SimpleRepConfiguration conf;
	private SystemContext systemContext;
	private OperationContext operationContext;
	private String msgId;
	private int sequenceId;

	public static final String SOURCE_ADDRESS = "simplerep_context_source_address";
	public static final String MESSAGE_ID = "simplerep_context_message_id";

	public MessageContext(SimpleRepConfiguration conf, SOAPEnvelope env,
			SystemContext sysCtxt) {
		this.conf = conf;
		this.env = env;
		this.systemContext = sysCtxt;
		this.properties = new HashMap<String, Object>();
		
		this.sequenceId = -1;
	}

	public MessageContext(SOAPEnvelope env) {
		SimpleRepConfiguration conf = SimpleRepConfiguration.getConfiguration();
		setConf(conf);
		setSystemContext(conf.getSystemContext());
	}

	public MessageContext() {
		this.properties = new HashMap<String, Object>();
	}

	public void setEnvelope(SOAPEnvelope env) {
		this.env = env;
	}

	public SOAPEnvelope getEnvelope() {
		return env;
	}

	public void setProperty(String key, Object obj) {
		properties.put(key, obj);
	}

	public Object getProperty(String key) {
		return properties.get(key);

	}

	public SimpleRepConfiguration getConf() {
		return this.conf;
	}

	public void setConf(SimpleRepConfiguration conf) {
		this.conf = conf;
	}

	/**
	 * Tries to build a SimpleRep MessageContext from a SOAP Envelope string.
	 * 
	 * @param envelope
	 * @return The message context or null in case of error
	 */

	public static MessageContext buildMessageContext(String envelope) {
		try {
			SimpleRepConfiguration conf = SimpleRepConfiguration
					.getConfiguration();
			SOAPEnvelope soapEnvelope = SoapHelper.str2Envelope(envelope);

			if (soapEnvelope == null) {
				return null;
			}

			MessageContext newMsgCtxt = new MessageContext(conf, soapEnvelope,
					conf.getSystemContext());

			QName messageIdQName = new QName(
					"http://www.w3.org/2005/08/addressing", "MessageID", "wsa");
			Iterator it = soapEnvelope.getHeader().getChildrenWithName(
					messageIdQName);

			while (it.hasNext()) {
				OMElement o = (OMElement) it.next();

				// searching for the messageId
				if (o.getLocalName().toLowerCase().equals("messageid")) {
					newMsgCtxt.setMessageId(o.getText());
				}
				
				if (o.getLocalName().toLowerCase().equals("sequenceid")) {
					newMsgCtxt.setSequenceId(Integer.parseInt(o.getText()));
				}
			}

			return newMsgCtxt;

		} catch (Exception ex) {
			return null;
		}
	}

	public void setSystemContext(SystemContext sysContext) {
		this.systemContext = sysContext;
	}

	public SystemContext getSystemContext() {
		return systemContext;
	}

	public String getMessageId() {
		return msgId;
	}

	public void setMessageId(String msgId) {
		this.msgId = msgId;
	}

	public void setOperationContext(OperationContext operationContext) {
		this.operationContext = operationContext;
	}

	public OperationContext getOperationContext() {
		return operationContext;
	}

	public void setSequenceId(int sequenceId) {
		this.sequenceId = sequenceId;
	}

	public int getSequenceId() {
		if (sequenceId >= 0) {
			return sequenceId;
		}
		else {
			sequenceId = extractSequenceId(getEnvelope().getHeader());
			return sequenceId;
		}
	}

	/**
	 * Extracts the wsa:SequenceId from the Envelope
	 * @param context
	 * @return the SequenceId or 0 (resets) or -2 in case of a non-valid sequenceId
	 */
	private int extractSequenceId(SOAPHeader soapHeader) {
		Iterator headers = soapHeader.getHeadersToProcess(null, AddressingConstants.ADDRESSING_NAMESPACE);
		
		// by default the seqId was not found and it's not a reset
		int seqId = -1;
		
		while (headers.hasNext()) {
			SOAPHeaderBlock soapHeaderBlock = (SOAPHeaderBlock)headers.next();
            String localName = soapHeaderBlock.getLocalName();
            
            if (localName.equals("ResetSequenceID")) {
            	// resets are identified by 0
            	return 0;
            }
            
            if (localName.equals("SequenceID")) {
            	try {
            		seqId = Integer.parseInt(soapHeaderBlock.getText());
            	}
            	catch(NumberFormatException ex) {
            		return -2;
            	}
            }
		}
		
		return seqId;
	}
	
	public String getRemoteHostIdentifier() {
		// @TODO adicionar o FROM
		return this.getMessageId();
	}
}
