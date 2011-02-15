package br.ufms.dct.simplerep.utils;

import javax.xml.namespace.QName;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.SOAPEnvelope;

import br.ufms.dct.simplerep.enums.AddressingConstants;

public class AddressingUtils {

	/**
	 * Extracts the directory portion of a URL (the part after the host and
	 * port)
	 * 
	 * @param fullURL
	 * @return
	 */
	public static String extractPath(String fullURL) {
		int slashIndex = fullURL.indexOf('/', 7);
		
		if (slashIndex >= 0)
			return fullURL.substring(slashIndex);
		else
			return "";
	}
	
	public static String getAddressingTo(SOAPEnvelope env) {
		String ret = "";
		
		try {
			OMElement to = (OMElement) env.getHeader().getFirstChildWithName(new QName(AddressingConstants.ADDRESSING_NAMESPACE, "To", AddressingConstants.WSA_DEFAULT_PREFIX));
			ret = to.getText();
		}
		catch (Exception ex) {
			return "";
		}
		
		return ret;
	}
}
