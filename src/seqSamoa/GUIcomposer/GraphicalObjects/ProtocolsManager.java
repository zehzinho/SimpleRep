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
import java.awt.Graphics;
import java.util.ArrayList;

public class ProtocolsManager {
    // list of all the protocols added to the graph
    protected ArrayList<ProtocolPanel> allProtocols;

    protected ProtocolPanel selectedProtocol;

    public ProtocolsManager() {
        allProtocols = new ArrayList<ProtocolPanel>();
    }

    /**
     * add a new protocol to the list
     * 
     * @param protocolPanel
     *            the protocol to add
     */
    public void addProtocolPanel(ProtocolPanel protocolPanel) {
        allProtocols.add(protocolPanel);
    }

    /**
     * draw the protocols of the stack in the canvas
     * 
     * @param g
     * @param w
     *            width of the canvas
     * @param h
     *            height of the canvas
     */
    public void drawProtocols(Graphics g, int w, int h) {
        for (int i = 0; i < allProtocols.size(); i++) {
            ((ProtocolPanel) allProtocols.get(i)).drawProtocol(g, w, h);
        }
    }

    /**
     * get the protocolPanel object of a protocol by the protocol's name
     * 
     * @param protocolName
     *            the name of the protocol
     * @return protocolPanel object representing the protocol of name
     *         "protocolName"
     */
    public ProtocolPanel getProtocolPanelByProtocolName(String protocolName) {
        for (int i = 0; i < allProtocols.size(); i++) {
            if (((ProtocolPanel) allProtocols.get(i)).getName()
                    .equals(protocolName)) {
                return (ProtocolPanel) allProtocols.get(i);
            }
        }
        return null;
    }

    /**
     * get the the position of a protocol of name "protocolName" in the canvas
     * 
     * @param protocolName
     *            the name of the protocol
     * @return a vector of length 2 containing the x and y coordinates of the
     *         protocol
     */
    public int[] getProtocolPanelPosition(String protocolName) {
        for (int i = 0; i < allProtocols.size(); i++) {
            if (((ProtocolPanel) allProtocols.get(i)).getName()
                    .equals(protocolName)) {
                int[] position = {
                        ((ProtocolPanel) allProtocols.get(i)).pos.x,
                        ((ProtocolPanel) allProtocols.get(i)).pos.y };
                return position;
            }
        }
        return null;
    }

    /**
     * get the service selected by the mouse
     * 
     * @param x
     *            the x coordinate of the mouse
     * @param y
     *            the y coordinate of the mouse
     * @return a ServicePanel object representing the service containing the
     *         coordinates of mouse
     */
    public ServicePanel servicePanelContainingPoint(int x, int y) {
        for (int i = 0; i < allProtocols.size(); i++) {
            ServicePanel servicePanel = ((ProtocolPanel) allProtocols.get(i))
                    .servicePanelContainingPoint(x, y);
            if (servicePanel != null) {
                return servicePanel;
            }
        }
        return null;
    }

    /**
     * get the protocol selected by the mouse
     * 
     * @param x
     *            the x coordinate of the mouse
     * @param y
     *            the y coordinate of the mouse
     * @return a ProtocolPanel object representing the protocol containing the
     *         coordinates of the mouse
     */
    public ProtocolPanel protocolPanelContainingPoint(int x, int y) {
        for (int i = 0; i < allProtocols.size(); i++) {
            if (((ProtocolPanel) allProtocols.get(i)).containsPoint(x, y)) {
                selectedProtocol = (ProtocolPanel) allProtocols.get(i);
                return selectedProtocol;
            }
        }
        selectedProtocol = null;
        return null;
    }

    /**
     * get the protocol contains the service represented by the ServicePanel
     * "sevicePanel"
     * 
     * @param servicePanel
     *            the ServicePanel representing the service
     * @return a ProtocolPanel object representing the protocol containing the
     *         service
     */
    public ProtocolPanel getProtocolPanelContainingServicePanel(
            ServicePanel servicePanel) {
        for (int i = 0; i < allProtocols.size(); i++) { // check shapes from
                                                            // front to back
            ProtocolPanel protocolPanel = (ProtocolPanel) allProtocols.get(i);
            if (protocolPanel.containsServicePanel(servicePanel))
                return protocolPanel;
        }
        return null;
    }

    /**
     * set the selected protocol with a new vlue
     * 
     * @param protocolPanel
     *            the ProtocolPanel object that will replace the selected
     *            protocol
     */
    public void selectProtocol(ProtocolPanel protocolPanel) {
        if (selectedProtocol != null) {
            selectedProtocol.color = Color.white;
        }

        this.selectedProtocol = protocolPanel;

        if (selectedProtocol != null)
            selectedProtocol.color = Color.lightGray;
    }

    /**
     * remove the protocol represented by "protocolPanel" from the stack
     * 
     * @param protocolPanel
     *            the protocol that will be removed fromthe stack
     */
    public void removeProtocol(ProtocolPanel protocolPanel) {
        allProtocols.remove(protocolPanel);
        if (selectedProtocol.equals(protocolPanel))
            selectedProtocol = null;
    }

    public boolean servicePanelIsAProvidedService(ServicePanel servicePanel) {
        ProtocolPanel protocolPanel = getProtocolPanelContainingServicePanel(servicePanel);
        return protocolPanel.servicePanelIsAProvidedService(servicePanel);
    }
}
