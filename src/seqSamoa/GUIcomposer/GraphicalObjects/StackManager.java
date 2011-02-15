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
package seqSamoa.GUIcomposer.GraphicalObjects;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.swing.JOptionPane;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import seqSamoa.GUIcomposer.XMLProtocolAndServiceDatabase;
import seqSamoa.GUIcomposer.XMLProtocolAndServiceDatabase.ParameterXML;
import seqSamoa.GUIcomposer.XMLProtocolAndServiceDatabase.ProtocolXML;

public class StackManager {
    private ProtocolsManager protocolsManager;
    private LinksManager linksManager;

    private ProtocolPanel draggedProtocolPanel;
    private ServicePanel selectedServicePanel;

    private int prevMousePositionX;
    private int prevMousePositionY;
    
    // indicates if the is trying to add a new link
    private boolean userIsDrawingLink;
    private int linkX;
    private int linkY;

    protected XMLProtocolAndServiceDatabase readerXML;

    public StackManager(XMLProtocolAndServiceDatabase readerXML) {
        this.readerXML = readerXML;
        protocolsManager = new ProtocolsManager();
        linksManager = new LinksManager();
    }

    /**
     * draw the stack
     * 
     * @param g
     * @param w
     *            the width of canvas
     * @param h
     *            the height of the cancas
     */
    public void drawStack(Graphics g, int w, int h) {
        // draw the protocols of the stack
        protocolsManager.drawProtocols(g, w, h);
        // draw the links of the stack
        linksManager.drawLinks(g);

        Graphics2D g2d = (Graphics2D) g;
        g2d.setStroke(new BasicStroke(2));

        // if the user is attempting to add a new link
        if (userIsDrawingLink) {
            g2d.setColor(Color.black);
            if (protocolsManager
                    .servicePanelIsAProvidedService(selectedServicePanel))
                g2d.drawLine(selectedServicePanel.pos.x, selectedServicePanel.pos.y
                        - selectedServicePanel.height, linkX, linkY);
            else
                g2d.drawLine(selectedServicePanel.pos.x, selectedServicePanel.pos.y,
                         linkX, linkY);
        }
    }

    public void addLink(Link link) {
        // check if the new link is valid
        if (linksManager.linkIsValid(link))
            linksManager.addLink(link);
    }

    public void createProtocolPanel(ProtocolXML protXML, String name, HashMap<ParameterXML, String> protocolParametersValues) {        
        protocolsManager.addProtocolPanel(new ProtocolPanel(protXML, name, protocolParametersValues));
    }
    
    
    public boolean isNameAlreadyUsed(String name) {
        List<ProtocolPanel> protocolsList = protocolsManager.allProtocols;
                
        for (int i = 0; i < protocolsList.size(); i++) {
            ProtocolPanel pPanel = (ProtocolPanel) protocolsList.get(i);
            
            if (pPanel.getName().equals(name))
                return true;
        }
        return false;
    }
    
    /**
     * get the parameters of the selected protocol
     * 
     * @return a list of elements representing the parameters of the selected
     *         protocol
     */
    public HashMap<ParameterXML, String> getSelectedProtocolParameters() {
        if (protocolsManager.selectedProtocol != null) {
            return protocolsManager.selectedProtocol.parameters;
        }
        return null;
    }

    /**
     * this method is called when the mouse is pressed
     * 
     * @param x
     *            the x coordinate of the mouse
     * @param y
     *            the y coordinate of the mouse
     */
    public void mousePressed(int x, int y) {
        prevMousePositionX = x;
        prevMousePositionY = y;
        
        if (protocolsManager.selectedProtocol != null)
            protocolsManager.selectedProtocol.color = Color.white;

        // check if the user is not clicking on a link
        if (linksManager.linkContainingPoint(x, y) ==  null) {
            // check if the user is not clicking on a ServicePanel
            selectedServicePanel = protocolsManager
                    .servicePanelContainingPoint(x, y);
            if (selectedServicePanel == null) {
                // check if the user is not clicking on a ProtocolPanel
                protocolsManager.protocolPanelContainingPoint(x, y);
                if (protocolsManager.selectedProtocol != null) {
                    draggedProtocolPanel = protocolsManager.selectedProtocol;
                    // prevMousePositionX = x;
                    // prevMousePositionY = y;
                } else {
                    draggedProtocolPanel = null;
                }
            } else {
                protocolsManager.selectProtocol(null);
                draggedProtocolPanel = null;
            }
        } else {
            protocolsManager.selectProtocol(null);
            draggedProtocolPanel = null;
        }
    }

    /**
     * this method is called when the mouse is dragged
     * 
     * @param x
     *            the x coordinate of the mouse
     * @param y
     *            the y coordinate of the mouse
     * @param width
     *            width of the canvas
     * @param height
     *            height of the canvas
     */
    public void mouseDragged(int x, int y, int width, int height) {

        if (linksManager.selectedLink != null) {
            // the user is dragging a ProtocolPanel
            int dx = x - prevMousePositionX;
            int dy = y - prevMousePositionY;
            linksManager.selectedLink.moveSelectedPart(dx, dy);
            prevMousePositionX = x;
            prevMousePositionY = y;
        } else if (selectedServicePanel != null) {
            // the user is trying to draw a link
            userIsDrawingLink = true;
            linkX = x;
            linkY = y;
        } else if (draggedProtocolPanel != null) {
            // the user is dragging a ProtocolPanel
            int dx = x - prevMousePositionX;
            int dy = y - prevMousePositionY;
            if (((draggedProtocolPanel.pos.x < 2) && (dx < 0))
                    || ((draggedProtocolPanel.pos.x > width
                            - draggedProtocolPanel.width) && (dx > 0))
                    || (x < 10) || (x > width))
                dx = 0;

            if (((draggedProtocolPanel.pos.y < 0) && (dy < 0))
                    || ((draggedProtocolPanel.pos.y > height - 5) && (dy > 0))
                    || (y < 12) || (y > height))
                dy = 0;

            draggedProtocolPanel.moveBy(dx, dy);
            prevMousePositionX = x;
            prevMousePositionY = y;

        }
    }

    /**
     * this method is called when the button of the mouse is released
     * 
     * @param x
     *            the x coordinate of the mouse
     * @param y
     *            the y coordinate of the mouse
     */
    public void mouseReleased(int x, int y) {
        userIsDrawingLink = false;
        if (selectedServicePanel != null) {
            ServicePanel selectedServicePanel2 = protocolsManager
                    .servicePanelContainingPoint(x, y);
            if (selectedServicePanel2 != null) {
                // check if the two servicePanels represents the same service
                // type
                if (selectedServicePanel.serviceXML.className.equals(
                        selectedServicePanel2.serviceXML.className)) {
                    ProtocolPanel protocolPanel1 = protocolsManager
                            .getProtocolPanelContainingServicePanel(selectedServicePanel);
                    ProtocolPanel protocolPanel2 = protocolsManager
                            .getProtocolPanelContainingServicePanel(selectedServicePanel2);
                    if (protocolPanel1
                            .servicePanelIsAProvidedService(selectedServicePanel)
                            && !protocolPanel2
                                    .servicePanelIsAProvidedService(selectedServicePanel2)) {
                        addLink(new Link(protocolPanel1, protocolPanel2,
                                selectedServicePanel.serviceXML.className));
                    } else if (protocolPanel2
                            .servicePanelIsAProvidedService(selectedServicePanel2)
                            && !protocolPanel1
                                    .servicePanelIsAProvidedService(selectedServicePanel)) {
                        addLink(new Link(protocolPanel2, protocolPanel1,
                                selectedServicePanel.serviceXML.className));
                    }
                }
            } else {
                if (y < 20)
                    if (protocolsManager
                            .getProtocolPanelContainingServicePanel(
                                    selectedServicePanel)
                            .servicePanelIsAProvidedService(
                                    selectedServicePanel))
                        linksManager
                                .addLink(new FinalLink(
                                        protocolsManager
                                                .getProtocolPanelContainingServicePanel(selectedServicePanel),
                                        selectedServicePanel.serviceXML.className));
            }
        }
    }

    /**
     * this method is called when the mouse is moving
     * 
     * @param x
     *            the x coordinate of the mouse
     * @param y
     *            the y coordinate of the mouse
     */
    public void mouseMoved(int x, int y) {
        protocolsManager.servicePanelContainingPoint(x, y);
    }

    /***************************************************************************
     * saves the constructed schema in the file "fileName"
     * 
     * @param fileName
     *            the name of the XML file in which the schema will be saved
     */
    @SuppressWarnings("unchecked")
	public void saveSchema(String fileName) {
        Element schema = new Element("stack");
        
        // Construct all services
        Element services = new Element("Services");     
        ArrayList<String> tmpServices = new ArrayList<String>();
        ArrayList<FinalLink> allLinks = linksManager.linksList;
        for (int i = 0; i < allLinks.size(); i++) {
            FinalLink link =  allLinks.get(i);
            String  serviceName = link.providingProtocol.getName(); 
            
            if (!tmpServices.contains(serviceName)){
                Element service = link.getServiceElement();
                
                services.addContent(service);
                tmpServices.add(serviceName);
            } else if (!(link instanceof Link)) {
                List servicesList = services.getChildren("Service");
                for (int j = 0; j < servicesList.size(); j++) {
                    Element service = (Element) servicesList.get(j);
                    if (serviceName.equals(service.getChild("Name").getText()))
                        service.getChild("ServiceFinal").setText("true");
                }                
            }
        }        
        
        // Construct all protocols
        Element protocols = new Element("Protocols");
        ArrayList<ProtocolPanel> allProtocols = protocolsManager.allProtocols; 
        for (int i = 0; i < allProtocols.size(); i++) {
            ProtocolPanel pprot = (ProtocolPanel) allProtocols.get(i);
            Element protocol = pprot.getProtocolElement();
            Element providedServices = protocol.getChild("ProvidedServices");
            Element requiredServices = protocol.getChild("RequiredServices");
            
            // Set the service requirements and "providements"
            for (int j = 0; j < allLinks.size(); j++) {
                FinalLink link = (FinalLink) allLinks.get(j);
                String serviceClassName = link.providedService.serviceXML.className;
                String serviceName = link.providingProtocol.getName();
                
                // Provided Services
                if (link.providingProtocol.getName().equals(pprot.getName())) {
                    Iterator itProvided = providedServices.getChildren("ProvidedService").iterator();
                    boolean toBeContinued = true;
                    while (itProvided.hasNext() && toBeContinued) {
                        Element serviceProvided = (Element) itProvided.next();
                    
                        if (serviceProvided.getChildText("ClassName").equals(serviceClassName) && 
                                serviceProvided.getChildText("Name").equals("null")){
                            serviceProvided.getChild("Name").setText(serviceName);
                            toBeContinued = false;
                        }
                    }
                }
                
                // Required Services
                if (link instanceof Link){
                    Link llink = (Link) link;
                    
                    if (llink.requiringProtocol.getName().equals(pprot.getName())) {
                        Iterator itRequired = requiredServices.getChildren("RequiredService").iterator();
                        boolean toBeContinued = true;
                        while (itRequired.hasNext() && toBeContinued) {
                            Element serviceRequired = (Element) itRequired.next();
                        
                            if (serviceRequired.getChildText("ClassName").equals(serviceClassName) && 
                                    serviceRequired.getChildText("Name").equals("null")){
                                serviceRequired.getChild("Name").setText(serviceName);
                                toBeContinued = false;
                            }
                        }
                    }   
                }                
            }
                       
            protocols.addContent(protocol);
            
            // Add Service that has no link
            Iterator itProvided = providedServices.getChildren("ProvidedService").iterator();
            int nbUnNamed = 0;
            while (itProvided.hasNext()) {
                Element protServiceProvided = (Element) itProvided.next();
                if (protServiceProvided.getChildText("Name").equals("null")) {
                    nbUnNamed++;
                    Element service = new Element("Service");
                    Element serviceClass = new Element("Class");
                    String scl = protServiceProvided.getChildText("ClassName");
                    Element serviceClassName = new Element("Name");
                    serviceClassName.setText(scl);
                    Element servicePackageName = new Element("Package");
                    servicePackageName.setText(readerXML.getService(scl).packageName);
                    serviceClass.addContent(serviceClassName);
                    serviceClass.addContent(servicePackageName);
                    Element serviceName = new Element("Name");
                    serviceName.setText(pprot.getName()+"UnNamed"+nbUnNamed);
                    Element serviceFinal = new Element("ServiceFinal");
                    serviceFinal.setText("false");
                    Element serviceProvided = new Element("ServiceProvided");
                    serviceProvided.setText("true");
                    
                    service.addContent(serviceClass);
                    service.addContent(serviceName);
                    service.addContent(serviceFinal);
                    service.addContent(serviceProvided);
                    
                    services.addContent(service);
                    
                    protServiceProvided.getChild("Name").setText(pprot.getName()+"UnNamed"+nbUnNamed);
                }                    
            }

            Iterator itRequired = requiredServices.getChildren("RequiredService").iterator();
            while (itRequired.hasNext()) {
                Element protServiceRequired = (Element) itRequired.next();
                if (protServiceRequired.getChildText("Name").equals("null")
                        && (!protServiceRequired.getChildText("ClassName").equals("Network"))) {
                    nbUnNamed++;
                    Element service = new Element("Service");
                    Element serviceClass = new Element("Class");
                    String scl = protServiceRequired.getChildText("ClassName");
                    Element serviceClassName = new Element("Name");
                    serviceClassName.setText(scl);
                    Element servicePackageName = new Element("Package");
                    servicePackageName.setText(readerXML.getService(scl).packageName);
                    serviceClass.addContent(serviceClassName);
                    serviceClass.addContent(servicePackageName);
                    Element serviceName = new Element("Name");
                    serviceName.setText(pprot.getName()+"UnNamed"+nbUnNamed);
                    Element serviceFinal = new Element("ServiceFinal");
                    serviceFinal.setText("false");
                    Element serviceProvided = new Element("ServiceProvided");
                    serviceProvided.setText("false");
                    
                    service.addContent(serviceClass);
                    service.addContent(serviceName);
                    service.addContent(serviceFinal);
                    service.addContent(serviceProvided);
                    
                    services.addContent(service);
                    
                    protServiceRequired.getChild("Name").setText(pprot.getName()+"UnNamed"+nbUnNamed);
                }                    
            }
        }
        
        schema.addContent(services);
        schema.addContent(protocols);
        Document document = new Document(schema);
        
        try {
            XMLOutputter output = new XMLOutputter(Format.getPrettyFormat());
            output.output(document, new FileOutputStream(fileName));
        } catch (java.io.IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Exception upon file saving: "+e.getMessage());            
        }
    }

    /**
     * opens the saved schema in the file fileName
     * 
     * @param fileName
     *            the name of the XML file containing the saved schema
     */
    @SuppressWarnings("unchecked")
	public void openSchema(String fileName) {
        try {
            SAXBuilder sxb = new SAXBuilder();
            Document document = sxb.build(new File(fileName));
            Element schema_root = (Element) document.getRootElement().clone();
            Element protocolsElement = schema_root.getChild("Protocols");
            Element servicesElement = schema_root.getChild("Services");

            protocolsManager.allProtocols = new ArrayList<ProtocolPanel>();
            linksManager.linksList = new ArrayList<FinalLink>();
            
            // recover the list of all the protocols in the stack
            Iterator itProt = protocolsElement.getChildren("Protocol").iterator();
            while (itProt.hasNext()) {
                Element protocol = (Element) itProt.next();
                int posX = Integer.parseInt(protocol.getChild("Position").getChildText("PositionX"));
                int posY = Integer.parseInt(protocol.getChild("Position").getChildText("PositionY"));
                
                String className = protocol.getChild("Class").getChildText("Name");
                String packageName = protocol.getChild("Class").getChildText("Package");
                String name = protocol.getChildText("Name");
                
                ProtocolXML protXML = readerXML.getProtocol(packageName, className);
                
                HashMap<ParameterXML, String> protParameters = new HashMap<ParameterXML, String>();
                Iterator itParams = protocol.getChild("Parameters").getChildren("Parameter").iterator();
                while (itParams.hasNext()){
                    Element parameter = (Element) itParams.next();
                    
                    String paramName = parameter.getChildText("Name");
                    String paramValue = parameter.getChildText("Value");
                    
                    protParameters.put(readerXML.getParameter(paramName), paramValue);
                }
                
                // Construct the Panel for the protocol 
                ProtocolPanel protocolPanel = new ProtocolPanel(protXML, name, protParameters, posX, posY);
                protocolsManager.addProtocolPanel(protocolPanel);                                
            }
            
            // recover the list of links in the stack
            itProt = protocolsElement.getChildren("Protocol").iterator();
            while (itProt.hasNext()) {
                Element protocol = (Element) itProt.next();
                String  protocolName = protocol.getChildText("Name");                
                ProtocolPanel pRequirer = protocolsManager.getProtocolPanelByProtocolName(protocolName);
                
                Iterator itRequired = protocol.getChild("RequiredServices").getChildren("RequiredService").iterator();
                while (itRequired.hasNext()){
                    Element required = (Element) itRequired.next();
                    String requiredType = required.getChildText("ClassName");
                    String requiredName = required.getChildText("Name");
                    ProtocolPanel pProvider = protocolsManager.getProtocolPanelByProtocolName(requiredName);
                    
                    if (pProvider != null)
                        linksManager.addLink(new Link(pProvider, pRequirer, requiredType));
                }                
            }
            
            Iterator itServices = servicesElement.getChildren("Service").iterator();
            while (itServices.hasNext()) {
                Element service = (Element) itServices.next();
                boolean isFinal = Boolean.parseBoolean(service.getChildText("ServiceFinal"));                
                
                 if (isFinal){
                    String serviceName = service.getChildText("Name");
                    String serviceType = service.getChild("Class").getChildText("Name");
                    ProtocolPanel pProvider = protocolsManager.getProtocolPanelByProtocolName(serviceName);
                    
                    linksManager.addLink(new FinalLink(pProvider, serviceType));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Exception upon file opening: "+e.getMessage());
       }
    }

    /**
     * delete the selected object from the stack
     */
    public void deleteSelectedObject() {
        if (linksManager.selectedLink != null) 
            linksManager.removeLink(linksManager.selectedLink);

        if (protocolsManager.selectedProtocol != null){
            for (int i = 0; i < linksManager.linksList.size(); i++){
                FinalLink fl = (FinalLink) linksManager.linksList.get(i);
                
                if (fl.providingProtocol.getName().equals(protocolsManager.selectedProtocol.getName())){
                    linksManager.removeLink(fl);
                    i--;
                } else if (fl instanceof Link){
                    Link l = (Link) fl;
                    
                    if (l.requiringProtocol.getName().equals(protocolsManager.selectedProtocol.getName())){
                        linksManager.removeLink(l);
                        i--;
                    }
                }
                
            }
            
            protocolsManager.removeProtocol(protocolsManager.selectedProtocol);
        }            
    }

    public void modifySelectedProtocolParameters(HashMap<ParameterXML, String> parameters) {
        if (checkParametersValidity(parameters))
            protocolsManager.selectedProtocol.parameters = parameters;
    }
    
    /**
     * check the validity of un entered parameter
     * 
     * @param parametersNames
     *            the name of the parameter
     * @param parametersValues
     *            the value of the parameter
     * @return true if the entered parameter is valid and false if
     */
    private boolean checkParametersValidity(HashMap<ParameterXML, String> parameters) {
        String parameterName, parameterValue;
        Iterator<ParameterXML> it = parameters.keySet().iterator();
        while (it.hasNext()) {
            parameterName =  it.next().name;
            parameterValue = (String) parameters.get(parameterName);
            if (parameterValue.equals("") || parameterValue.equals(" ")) {
                JOptionPane.showMessageDialog(null,
                        "you have to provide a value for the parameter : "
                                + parameterName, "error",
                        JOptionPane.ERROR_MESSAGE);
                return false;
            }
            if (parameterName.equals("name")) {
                if (!parameterValue.equals(protocolsManager
                        .selectedProtocol.getName())) {
                    for (int i = 0; i < protocolsManager.allProtocols.size(); i++) {
                        if (protocolsManager.allProtocols.get(i).equals(parameterValue)) {
                            JOptionPane.showMessageDialog(null,
                                    "the entered name is already attributed",
                                    "error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }
            }
            if (readerXML.getParameter(parameterName).type.equals("int")) {
                try {
                    Integer.parseInt(parameterValue);
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(null,
                            "the value of the parameter " + parameterName
                                    + " must be an integer", "error",
                            JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
            if (readerXML.getParameter(parameterName).type.equals("long")) {
                try {
                    Long.parseLong(parameterValue);
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(null,
                            "the value of the parameter " + parameterName
                                    + " must be an integer", "error",
                            JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
            if (readerXML.getParameter(parameterName).type.equals("boolean")) {
                if (parameterValue.toLowerCase().equalsIgnoreCase("true")
                        || parameterValue.toLowerCase().equalsIgnoreCase(
                                "false")) {
                    JOptionPane.showMessageDialog(null,
                            "this paramter is a boolean(true or false)",
                            "error", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
        }
        //
        return true;
    }

    public void mouseClicked(int x, int y, int numberOfClicks) {
        if (numberOfClicks == 2)
            if (linksManager.selectedLink != null)
                linksManager.selectedLink.mouseDoubleClicked(x, y);
    }
    
    /**
     * Return all protocols in the stack
     */
    public ArrayList<ProtocolPanel> getAllProtocols(){
        return protocolsManager.allProtocols;
    }   
}
