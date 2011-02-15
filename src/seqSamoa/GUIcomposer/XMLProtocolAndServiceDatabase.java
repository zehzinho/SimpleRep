/**
 *  SAMOA - PROTOCOL FRAMEWORK
 *  Copyright (C) 2005  Olivier Rütti (EPFL) (olivier.rutti@a3.epfl.ch)
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package seqSamoa.GUIcomposer;

import java.awt.Color;
import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

/**
 * <CODE>XMLProtocolAndServiceDatabase</CODE> represents a
 * database of {@link org.jdom.Element XML descriptions} that
 * represents all the services and the protocols available for
 * dynamic creation.
 */
public class XMLProtocolAndServiceDatabase {
	/**
	 * This class encapsulate a parameter of a {@link seqSamoa.ProtocolModule protocol}
	 * constructor. It has the foollowing field: the name of the parameter, its type,
	 * a brief description of its meaning and its default value. 
	 */
	static public class ParameterXML {
	    public String name;

	    public String type;

	    public String description;

	    public String defaultValue;

	    ParameterXML(String name, String type, String description, String defaultValue) {
	        this.name = name;
	        this.type = type;
	        this.description = description;
	        this.defaultValue = defaultValue;
	    }
	}
	
	/**
	 * This class describes a {@link seqSamoa.Service service}.
	 * It has the foollowing field: a diminutive of the {@link seqSamoa.Service service}
	 * (should be unique), the name of its class, the name of its package,
	 * a brief description and the color by which it is represented with 
	 * the graphical tool. 
	 */
	static public class ServiceXML {
	    public String diminutive;

	    public String className;

	    public String packageName;

	    public String description;

	    public Color color;

	    public ServiceXML(String diminutive, String className, String packageName,
	            String description, Color color) {
	        this.diminutive = diminutive;
	        this.className = className;
	        this.packageName = packageName;
	        this.description = description;
	        this.color = color;
	    }
	}

	/**
	 * This class describes a {@link seqSamoa.ProtocolModule protocol}.
	 * It has the foollowing field:  a diminutive of the {@link seqSamoa.ProtocolModule protocol}
	 * (should be unique), the name of its class, the name of its package,
	 * a brief description of its behavior, the model to which it correspond
	 * (crash-stop or crash-recovery), the list of parameters for the constructor and the
	 * lists of provided and required services.
	 */
	static public class ProtocolXML {
	    public String diminutive;

	    public String className;

	    public String packageName;

	    public String description;

	    public String model;

	    public LinkedList<ParameterXML> parameters;

	    public LinkedList<ServiceXML> providedService;

	    public LinkedList<ServiceXML> requiredService;

	    public ProtocolXML(String diminutive, String className, String packageName,
	            String description, String model, LinkedList<ParameterXML> parameters,
	            LinkedList<ServiceXML> providedService, LinkedList<ServiceXML> requiredService) {
	        this.diminutive = diminutive;
	        this.className = className;
	        this.packageName = packageName;
	        this.description = description;
	        this.model = model;
	        this.parameters = parameters;
	        this.providedService = providedService;
	        this.requiredService = requiredService;
	    }
	}
		
    private HashMap<String, ProtocolXML> allXMLProtocols;

    private HashMap<String, ParameterXML> allXMLParameters;

    private HashMap<String, ServiceXML> allXMLServices;
    
    /**
     * Constructor
     * 
     * @param fileName
     *            the name of the XML file that contains the information related to
     *            {@link seqSamoa.ProtocolModule protocol} and
     *            {@link seqSamoa.Service service}.
     */
    @SuppressWarnings("unchecked")
	public XMLProtocolAndServiceDatabase(String fileName) {
        try {
            SAXBuilder sxb = new SAXBuilder();
            Document document = sxb.build(new File(fileName));
            Element root = document.getRootElement();

            // Create a map of all parameters
            allXMLParameters = new HashMap<String, ParameterXML>();
            Iterator itParams = root.getChild("Parameters").getChildren(
                    "parameter").iterator();
            while (itParams.hasNext()) {
                Element param = (Element) itParams.next();

                String name = param.getChildText("Name");
                String type = param.getChildText("Type");
                String description = param.getChildText("Description");
                String defaultValue = param.getChildText("DefaultValue");

                allXMLParameters.put(name, new ParameterXML(name, type,
                        description, defaultValue));
            }

            // Create a map of all services
            allXMLServices = new HashMap<String, ServiceXML>();
            Iterator itServices = root.getChild("Services").getChildren(
                    "Service").iterator();
            while (itServices.hasNext()) {
                Element service = (Element) itServices.next();

                String diminutive = service.getChildText("Diminutive");
                String className = service.getChild("Class").getChildText(
                        "Name");
                String packageName = service.getChild("Class").getChildText(
                        "Package");
                String description = service.getChildText("Description");

                String color = service.getChildText("AssociatedColor");
                int r = Integer
                        .parseInt(color.substring(0, color.indexOf(" ")));
                int g = Integer.parseInt(color.substring(
                        color.indexOf(" ") + 1, color.lastIndexOf(" ")));
                int b = Integer.parseInt(color.substring(
                        color.lastIndexOf(" ") + 1, color.length()));

                allXMLServices.put(className,
                        new ServiceXML(diminutive, className, packageName,
                                description, new Color(r, g, b)));
            }

            // Create a map of all protocols
            allXMLProtocols = new HashMap<String, ProtocolXML>();
            Iterator itProtocols = root.getChild("Protocols").getChildren(
                    "Protocol").iterator();
            while (itProtocols.hasNext()) {
                Element protocol = (Element) itProtocols.next();

                String diminutive = protocol.getChildText("Diminutive");
                String className = protocol.getChild("Class").getChildText(
                        "Name");
                String packageName = protocol.getChild("Class").getChildText(
                        "Package");
                String description = protocol.getChildText("Description");
                String model = protocol.getChildText("Model");

                LinkedList<ParameterXML> parameters = new LinkedList<ParameterXML>();
                Iterator itParamsProt = protocol.getChild("Parameters")
                        .getChildren("Parameter").iterator();
                while (itParamsProt.hasNext()) {
                    String paramName = ((Element) itParamsProt.next())
                            .getText();
                    parameters.addLast(allXMLParameters.get(paramName));
                }

                LinkedList<ServiceXML> providedServices = new LinkedList<ServiceXML>();
                Iterator itParamsPServ = protocol.getChild("ProvidedServices")
                        .getChildren("ProvidedService").iterator();
                while (itParamsPServ.hasNext()) {
                    String serviceName = ((Element) itParamsPServ.next())
                            .getText();

                    providedServices.addLast(allXMLServices.get(serviceName));
                }

                LinkedList<ServiceXML> requiredServices = new LinkedList<ServiceXML>();
                Iterator itParamsRServ = protocol.getChild("RequiredServices")
                        .getChildren("RequiredService").iterator();
                while (itParamsRServ.hasNext()) {
                    String serviceName = ((Element) itParamsRServ.next())
                            .getText();

                    requiredServices.addLast(allXMLServices.get(serviceName));
                }

                allXMLProtocols.put(new String(packageName + "." + className),
                        new ProtocolXML(diminutive, className, packageName,
                                description, model, parameters,
                                providedServices, requiredServices));
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("READER XML Exception: "
                    + e.getMessage());
        }
    }

    /**
     * This methode returns a Vector containing the names of all the protocols
     * contained in the XML file.
     * 
     * @return  
     *       vector of String with all the names
     */
    public Vector<String> getAllProtocolsVector() {
        Vector<String> allProtocolsVector = new Vector<String>();
        Iterator<String> it = allXMLProtocols.keySet().iterator();
        while (it.hasNext()) {
            String fullName = (String) it.next();
            ProtocolXML protXML = (ProtocolXML) allXMLProtocols.get(fullName);

            allProtocolsVector.add(protXML.diminutive);
        }
        
        return allProtocolsVector;
    }

    /**
     * This methode returns a Vector containing the names of all the protocols
     * contained in the XML file.
     * 
     * @return  
     *       vector of String with all the names
     */
    public Vector<String> getAllProtocolsVector(String protocolModel) {
        Vector<String> allProtocolsVector = new Vector<String>();
        Iterator<String> it = allXMLProtocols.keySet().iterator();
        while (it.hasNext()) {
            String fullName = (String) it.next();
            ProtocolXML protXML = (ProtocolXML) allXMLProtocols.get(fullName);

            if ((protXML.model.equals(protocolModel))
                    || (protXML.model.equals("Both")))
                allProtocolsVector.add(protXML.diminutive);
        }

        return allProtocolsVector;
    }
    
    /**
     * This methode returns a Vector containing all the models considered by the protocols
     * contained in the XML file.
     * 
     * @return  
     *       vector of String with all the names
     */
    public Vector<String> getAllModels() {
        Vector<String> allModels = new Vector<String>();
        Iterator<String> it = allXMLProtocols.keySet().iterator();
        while (it.hasNext()) {
            String fullName = (String) it.next();
            ProtocolXML protXML = (ProtocolXML) allXMLProtocols.get(fullName);

            if (!allModels.contains(protXML.model))
                allModels.add(protXML.model);
        }

        return allModels;
    }

    /**
     * This method returns a parameter given its name
     * 
     * @param name
     *            of the parameter
     *            
     * @return 
     * 			the object representing the parameter
     */
    public ParameterXML getParameter(String name) {
        return allXMLParameters.get(name);
    }

    /**
     * This method returns a service given its name
     * 
     * @param name
     *            of the parameter
	 *
     * @return 
     * 			the object representing the parameter
     */
    public ServiceXML getService(String className) {
        return allXMLServices.get(className);
    }

    /**
     * get the object corresponding to the {@link seqSamoa.ProtocolModule protocol}
     * 
     * @param packageName
     *            the name of the package containing the class implementing the
     *            {@link seqSamoa.ProtocolModule protocol}
     * @param className
     *            the name of the class implementing the {@link seqSamoa.ProtocolModule protocol}
     *            
     * @return 
     * 			 the object corresponding to the {@link seqSamoa.ProtocolModule protocol}
     */
    public ProtocolXML getProtocol(String diminutive) {
        Iterator<String> it = allXMLProtocols.keySet().iterator();
        while (it.hasNext()) {
            String fullName = it.next();
            ProtocolXML protXML = allXMLProtocols.get(fullName);

            if (diminutive.equals(protXML.diminutive))
                return protXML;
        }
        return null;
    }

    /**
     * get the object corresponding to the {@link seqSamoa.ProtocolModule protocol}
     * 
     * @param packageName
     *            the name of the package containing the class implementing the
     *            {@link seqSamoa.ProtocolModule protocol}
     * @param className
     *            the name of the class implementing the {@link seqSamoa.ProtocolModule protocol}
     * @return 
     * 			 the object corresponding to the {@link seqSamoa.ProtocolModule protocol}
     */
    public ProtocolXML getProtocol(String packageName, String className) {
        return allXMLProtocols.get(packageName + "." + className);
    }
}
