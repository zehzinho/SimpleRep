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
import java.awt.Graphics2D;

import org.jdom.Element;

public class FinalLink {
    protected ProtocolPanel providingProtocol;

    protected ServicePanel providedService;

    protected boolean selected = false;

    public FinalLink(ProtocolPanel providingProtocol,
            String serviceClassName) {
        this.providingProtocol = providingProtocol;
        this.providedService = providingProtocol.getServicePanelByServiceClassName(serviceClassName);
    }

    /**
     * create an Element representing the service
     * 
     * @return XML Element representing the service
     */
    public Element getServiceElement() {
        // the Class implementing the protocol
        Element service = new Element("Service");
        Element serviceClass = new Element("Class");
        Element serviceClassName = new Element("Name");
        serviceClassName.setText(providedService.serviceXML.className);
        Element servicePackageName = new Element("Package");
        servicePackageName.setText(providedService.serviceXML.packageName);
        serviceClass.addContent(serviceClassName);
        serviceClass.addContent(servicePackageName);
        Element serviceName = new Element("Name");
        serviceName.setText(providingProtocol.getName());
        Element serviceFinal = new Element("ServiceFinal");
        serviceFinal.setText("true");
        Element serviceProvided = new Element("ServiceProvided");
        serviceProvided.setText("true");
        
        service.addContent(serviceClass);
        service.addContent(serviceName);
        service.addContent(serviceFinal);
        service.addContent(serviceProvided);
        
        return service;
    }
    
    /**
     * draw the link in the canvas
     */
    public void drawLink(Graphics2D g2d) {
        if (selected)
            g2d.setColor(Color.blue);
        else
            g2d.setColor(Color.black);
        g2d.drawLine(providedService.pos.x, providedService.pos.y
                - providedService.height, providedService.pos.x, 20);
    }

    /**
     * check if the link contains the coordinates of the mouse
     * 
     * @param x
     *            the x coordinate of the mouse.
     * @param y
     *            the y coordinate of the mouse
     * @return true of the point (x,y) is contained in the link.
     */
    public boolean containsPoint(int x, int y) {
        if ((Math.abs(x - providedService.pos.x) <= 1)
                && ((y > 20) && (y < providedService.pos.y
                        - providedService.height)))
            return true;
        return false;
    }
        
    public boolean equals(Object o){
        if (!(o instanceof FinalLink))
            return false;
        
        FinalLink fl = (FinalLink) o;
        
        return providingProtocol.getName().equals(fl.providingProtocol.getName());
    }
    
    /**
     * get the ServicePanel representing the provided service in the protocol
     * providing the service
     * 
     * @return a ProtocolPanel representing the provided service in the protocol
     *         providing the service
     */
//    protected ServicePanel getServicePanelFromProvidingProtocol() {
//        return providedService;
//    }
//    
//    /**
//     * get the type of service represented by the link
//     * 
//     * @return the type of service provided by the providing protocol and
//     *         required by the requiring protocol
//     */
//    protected String getServiceClassName() {
//        return providedService.serviceXML.className;
//    }
    
    // These method are not used for Final Link
    public void moveSelectedPart(int dx, int dy) {
    }
    public void mouseDoubleClicked(int x, int y) {       
    }
}
