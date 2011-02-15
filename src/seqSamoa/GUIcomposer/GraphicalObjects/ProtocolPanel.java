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

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.HashMap;

import org.jdom.Element;

import seqSamoa.GUIcomposer.XMLProtocolAndServiceDatabase.ParameterXML;
import seqSamoa.GUIcomposer.XMLProtocolAndServiceDatabase.ProtocolXML;
import seqSamoa.GUIcomposer.XMLProtocolAndServiceDatabase.ServiceXML;

public class ProtocolPanel {
    // unit for provided and required services
    private int unitProvidedServicePanel = 42;
    private int unitRequiredServicePanel;

    protected int width, height = 40;
    protected Position  pos = new Position (10, 30);   
    protected Color color = Color.white;
    
    private ProtocolXML protXML;
    private String name;

    private ArrayList<ServicePanel> providedServicesPanels;
    private ArrayList<ServicePanel> requiredServicesPanels;

    protected HashMap<ParameterXML, String> parameters;

    public ProtocolPanel (ProtocolXML protXML, String name, HashMap<ParameterXML, String> parameters) {
        this.protXML = protXML;
        this.name = name;
        this.parameters = parameters;
        int sizeProvided = protXML.providedService.size();
        int sizeRequired = protXML.requiredService.size();
       
        this.width = Math.max(sizeProvided, sizeRequired) * 67;
        this.unitProvidedServicePanel = width / sizeProvided;
        this.unitRequiredServicePanel = width / sizeRequired;

        // Construct the panels representing the provided services
        providedServicesPanels = new ArrayList<ServicePanel>();
        for (int i = 0; i < sizeProvided; i++) {
            ServiceXML serviceXML = (ServiceXML) protXML.providedService.get(i);            
            Position SPpos = new Position (pos.x + (int) (unitProvidedServicePanel / 2) + unitProvidedServicePanel * i, pos.y);
            
            ServicePanel sp = new ServicePanel(SPpos, serviceXML);
            providedServicesPanels.add(sp);
        }

        // Construct the panels representing the required services
        requiredServicesPanels = new ArrayList<ServicePanel>();
        for (int i = 0; i < sizeRequired; i++) {
            ServiceXML serviceXML = (ServiceXML) protXML.requiredService.get(i);
            Position SPpos = new Position (pos.x + (int) (unitRequiredServicePanel / 2) + unitRequiredServicePanel * i, pos.y + height);
 
            ServicePanel sp = new ServicePanel(SPpos, serviceXML);
            requiredServicesPanels.add(sp);                        
        }
    }

    public ProtocolPanel(ProtocolXML protXML, String name, HashMap<ParameterXML, String> parameters, int posX, int posY) {
        this(protXML, name, parameters);
        
        this.setPosition(new Position(posX, posY));
    }

    /**
     * creat an Element representing the protocol
     * 
     * @return XML Element represeneting the protocol
     */
    public Element getProtocolElement() {
        // the position of the protocol
        Element positionX = new Element("PositionX");
        positionX.setText("" + this.pos.x);
        Element positionY = new Element("PositionY");
        positionY.setText("" + this.pos.y);
        Element position = new Element("Position");
        position.addContent(positionX);
        position.addContent(positionY);

        // The Protocol XML        
        Element className = new Element("Name");
        className.setText(protXML.className);
        Element packageName = new Element("Package");
        packageName.setText(protXML.packageName);
        Element classElement = new Element("Class");
        classElement.addContent(className);
        classElement.addContent(packageName);
        Element nameElement = new Element("Name");
        nameElement.setText(name);
        Element modelElement = new Element("Model");
        modelElement.setText(protXML.model);

        // the value of the protocol parameters
        Element parametersElement = new Element("Parameters");
        int sizeParameters = protXML.parameters.size();
        for (int i=0; i<sizeParameters; i++) {
        	ParameterXML pXML = protXML.parameters.get(i);
            Element parameterName = new Element("Name");
            parameterName.setText(pXML.name);
            Element parameterType = new Element("Type");
            parameterType.setText(pXML.type);
            Element parameterValue = new Element("Value");
            if (parameters.get(pXML) != null)
                parameterValue.setText(parameters.get(pXML));
            else
            	parameterValue.setText(pXML.defaultValue);

            Element parameter = new Element("Parameter");
            parameter.addContent(parameterName);
            parameter.addContent(parameterType);
            parameter.addContent(parameterValue);
            parametersElement.addContent(parameter);        	       	
        }
        
        Element providedServices = new Element("ProvidedServices");
        for (int i = 0; i< protXML.providedService.size(); i++){
            Element providedService = new Element("ProvidedService");
            Element providedServiceClassName = new Element("ClassName");
            Element providedServiceName = new Element("Name");
            
            providedServiceClassName.setText(((ServiceXML) protXML.providedService.get(i)).className);
            providedServiceName.setText(new String("null"));
            providedService.addContent(providedServiceClassName);
            providedService.addContent(providedServiceName);
            providedServices.addContent(providedService);
        }
        
        Element requiredServices = new Element("RequiredServices");
        for (int i = 0; i< protXML.requiredService.size(); i++){
            Element requiredService = new Element("RequiredService");
            Element requiredServiceClassName = new Element("ClassName");
            Element requiredServiceName = new Element("Name");
            
            if (!((ServiceXML) protXML.requiredService.get(i)).className.equals("Network")) {
                requiredServiceClassName.setText(((ServiceXML) protXML.requiredService.get(i)).className);
                requiredServiceName.setText(new String("null"));
                requiredService.addContent(requiredServiceClassName);
                requiredService.addContent(requiredServiceName);
                requiredServices.addContent(requiredService);
            }
        }
        
        Element protocol = new Element("Protocol");
        protocol.addContent(position);
        protocol.addContent(nameElement);
        protocol.addContent(classElement);
        protocol.addContent(modelElement);
        protocol.addContent(parametersElement);
        protocol.addContent(providedServices);
        protocol.addContent(requiredServices);
        return protocol;
    }

    /**
     * draw the protocol in the convas
     * 
     * @param g
     * @param w
     *            width of the canvas
     * @param h
     *            height of the canvas
     */
    public void drawProtocol(Graphics g, int w, int h) {
        //
        g.setColor(Color.black);
        g.fillRect(pos.x, pos.y, width, height);
        g.setColor(color);
        g.fillRect(pos.x + 2, pos.y + 2, width - 4, height - 4);

        //
        g.setColor(Color.black);
        g.setFont(new Font("Arial", 0, 13));
        g.drawString(protXML.diminutive, pos.x + (width / 2) - (protXML.diminutive.length() / 2)
                * 7, pos.y + height / 2);

        // draw the prvided services panels
        for (int j = 0; j < providedServicesPanels.size(); j++) {
            ((ServicePanel) providedServicesPanels.get(j)).drawPanel(g, w, h);
        }

        // draw the required services panels
        for (int i = 0; i < requiredServicesPanels.size(); i++) {
            ((ServicePanel) requiredServicesPanels.get(i)).drawPanel(g, w, h);
        }
    }

//    public Hashtable getProtocolParameters() {
//        return parameters;
//    }

    /**
     * set the position of the protocol
     * 
     * @param pos
     *            the new position of the protocol
     */
    public void setPosition(Position pos) {
        this.pos = pos;
        
        // modify the prositions of the povided services panels
        for (int j = 0; j < providedServicesPanels.size(); j++) {
            ((ServicePanel) providedServicesPanels.get(j)).pos = new Position(pos.x
                    + (int) (unitProvidedServicePanel / 2) + unitProvidedServicePanel * j, pos.y + 2);
        }

        // modify the positions of the required services panels
        for (int i = 0; i < requiredServicesPanels.size(); i++) {
            ((ServicePanel) requiredServicesPanels.get(i)).pos = new Position(pos.x
                    + (int) (unitRequiredServicePanel / 2) + unitRequiredServicePanel * i, pos.y + height);
        }
    }

    /**
     * move the protocol by a certain pixels
     * 
     * @param dx
     *            the number of pixels by which the protocol will be moved
     *            horizontally
     * @param dy
     *            the number of pixels by which the protocol will be moved
     *            vertically
     */
    public void moveBy(int dx, int dy) {
        this.pos.move(dx, dy);
        
        // move the provided services panels
        for (int j = 0; j < providedServicesPanels.size(); j++) {
            ((ServicePanel) providedServicesPanels.get(j)).moveBy(dx, dy);
        }

        // move the required services panels
        for (int i = 0; i < requiredServicesPanels.size(); i++) {
            ((ServicePanel) requiredServicesPanels.get(i)).moveBy(dx, dy);
        }
    }

    /**
     * check if the protocol contains the coordinates of the mouse
     * 
     * @param x
     *            the x coordinate of the mouse
     * @param y
     *            the y coordinate of the mouse
     * @return true if the protocol contains the coordinates of the mouse and
     *         false if not
     */
    public boolean containsPoint(int x, int y) {
        if ((x >= pos.x) && (x < (pos.x + width))
                && (y >= pos.y) && (y < (pos.y + height))) {
            this.color = Color.lightGray;
            return true;
        } else {
            return false;
        }
    }

    /**
     * get the service containing the mouse coordinates
     * 
     * @param x
     *            the x coordinate of the mouse
     * @param y
     *            the y coordinate of the mouse
     * @return a ServicePanel representing the service selected by the mouse
     */
    public ServicePanel servicePanelContainingPoint(int x, int y) {
        // check the provided services panels
        for (int j = 0; j < providedServicesPanels.size(); j++) {
            if (((ServicePanel) providedServicesPanels.get(j)).containsPoint(x,
                    y)) {
                return (ServicePanel) providedServicesPanels.get(j);
            }
        }

        // check the required services panels
        for (int i = 0; i < requiredServicesPanels.size(); i++) {
            if (((ServicePanel) requiredServicesPanels.get(i)).containsPoint(x,
                    y)) {
                return (ServicePanel) requiredServicesPanels.get(i);
            }
        }

        // if no service panel contains the coordinates return null
        return null;
    }

    /**
     * get the ServicePanel of a service by the name of the service type
     * 
     * @param ServiceClassName
     *            the type of service to look for
     * @return a ServicePanel representing the service of type "ServiceClassName"
     */
    public ServicePanel getServicePanelByServiceClassName(String serviceClassName) {
        for (int j = 0; j < providedServicesPanels.size(); j++) {
            if (((ServicePanel) providedServicesPanels.get(j)).serviceXML.className
                    .equals(serviceClassName))
                return (ServicePanel) providedServicesPanels.get(j);
        }

        for (int i = 0; i < requiredServicesPanels.size(); i++) {
            if (((ServicePanel) requiredServicesPanels.get(i)).serviceXML.className
                    .equals(serviceClassName))
                return (ServicePanel) requiredServicesPanels.get(i);
        }

        return null;
    }

    /**
     * check if the service represented by servicePanel is attached to this
     * protocol
     * 
     * @param servicePanel
     *            the service to look for
     * @return true if the ServicePanel object is attached to the protocol and
     *         false if not
     */
    public boolean containsServicePanel(ServicePanel servicePanel) {
        for (int j = 0; j < providedServicesPanels.size(); j++) {
            if (providedServicesPanels.get(j).equals(servicePanel))
                return true;
        }

        for (int i = 0; i < requiredServicesPanels.size(); i++) {
            if (requiredServicesPanels.get(i).equals(servicePanel))
                return true;
        }

        return false;
    }

    public boolean servicePanelIsAProvidedService(ServicePanel servicePanel) {
        for (int i = 0; i < providedServicesPanels.size(); i++) {
            if (providedServicesPanels.get(i).equals(servicePanel))
                return true;
        }
        return false;
    }

    /**
     * get the name of the protocol
     * 
     * @return the name of the protocol
     */
    public String getName() {
        return name;
    }
}
