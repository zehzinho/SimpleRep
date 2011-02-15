package br.ufms.dct.simplerep.xml;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class XmlHelper {

	public static Document getDoc(String xmlFile) throws ParserConfigurationException, SAXException, IOException {
		File file = new File(xmlFile);
		
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document doc = db.parse(file);
		doc.getDocumentElement().normalize();
		
		return doc;
	}
	
	public static String getElementValue(Node element) {
		return getElementValue((Element) element);
	}
	
	public static String getElementValue(Element element) {
		return element.getChildNodes().item(0).getNodeValue();
	}

	/**
	 * Returns the first child element of doc
	 * 
	 * @param string The name of the searched element
	 * @param doc The parent element
	 * @return
	 */
	public static Element getFirstElement(String string, Document doc) {
		return (Element) doc.getElementsByTagName(string).item(0);
	}
	
	/**
	 * Gets the value of the first child element of parent
	 * 
	 * @param childName
	 * @param parent
	 * @return
	 */
	public static String getFirstChildValue(String childName, Element parent) {
		return getElementValue(parent.getElementsByTagName(childName).item(0));
	}
	
	public static String getFirstChildValue(String childName, Document parent) {
		return getElementValue(parent.getElementsByTagName(childName).item(0));
	}
}
