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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;

import javax.swing.JOptionPane;

public class LinksManager {
    
    protected ArrayList<FinalLink> linksList;
    protected FinalLink selectedLink;

    public LinksManager() {
        linksList = new ArrayList<FinalLink>();
    }

    /**
     * draw the link in the canvas
     * 
     * @param g
     */
    public void drawLinks(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setStroke(new BasicStroke(2));

        for (int i = 0; i < linksList.size(); i++)
            ((FinalLink) linksList.get(i)).drawLink(g2d);
    }

    /**
     * add a new link to the stack
     * 
     * @param link
     *            the new link to add
     */
    public void addLink(FinalLink link) {
        linksList.add(link);
    }

    /**
     * remove the link "link" from the stack
     * 
     * @param link
     *            the link to remove
     */
    public void removeLink(FinalLink link) {
        linksList.remove(link);
        if (selectedLink != null)
            if (selectedLink.equals(link))
                selectedLink = null;
    }

    /**
     * check if one the links of the stack contains the mouse coordinates
     * 
     * @param x
     *            the x coordinate of the mouse
     * @param y
     *            the y coordinate of the mouse
     * @return the link selected by the mouse
     */
    public FinalLink linkContainingPoint(int x, int y) {
        for (int j = 0; j < linksList.size(); j++) {
            if (((FinalLink) linksList.get(j)).containsPoint(x, y)) {
                selectedLink = (FinalLink) linksList
                        .get(j);
                selectedLink.selected = true;
                return selectedLink;
            }
        }
       
        if (selectedLink != null) {
            selectedLink.selected = false;
            selectedLink = null;
        }
               
        return null;
    }

    /**
     * check if the a link is valid
     * 
     * @param link
     *            the link to check
     * @return true if the link is valid and false if not
     */
    public boolean linkIsValid(Link link) {
        // check if the link starts from a provided service and ends at a
        // required service
        if (!link.providingProtocol.servicePanelIsAProvidedService(
                link.providedService)
                || link.requiringProtocol.servicePanelIsAProvidedService(
                        link.requiredService)) {

            return false;

        }
        if (linksList.contains(link)) {
            JOptionPane.showMessageDialog(null, "Link already exists", "error",
                    JOptionPane.PLAIN_MESSAGE);
            return false;
        }
        // check that no cycles are created
        if (linkAddCycles(link)) {
            JOptionPane.showMessageDialog(null,
                    "Adding this link will create a cycle", "error",
                    JOptionPane.PLAIN_MESSAGE);
            // return false;
        }
        return true;
    }

    /**
     * chack if a adding a certain link would create a cycle in the stack
     * 
     * @param link
     *            the link to check
     * @return true if adding this link would create a cycle in the graph of the
     *         stack and false if not
     */
    private boolean linkAddCycles(Link link) {
        ArrayList<ProtocolPanel> protocolsList = new ArrayList<ProtocolPanel>();
        protocolsList.add(link.requiringProtocol);
        while (protocolsList.size() > 0) {
            for (int i = 0; i < protocolsList.size(); i++) {
                if (((ProtocolPanel) protocolsList.get(i)).getName()
                        .equals(link.providingProtocol.getName()))
                    return true;
            }

            protocolsList = getProtocolsReceivingServicesFrom(protocolsList);
        }
        return false;
    }

    private ArrayList<ProtocolPanel> getProtocolsReceivingServicesFrom(ArrayList<ProtocolPanel> providingProtocols) {
        ArrayList<ProtocolPanel> protocolsList = new ArrayList<ProtocolPanel>();
        for (int j = 0; j < providingProtocols.size(); j++)
            for (int i = 0; i < linksList.size(); i++) {
                if (linksList.get(i) instanceof Link) {
                    Link link = (Link) linksList.get(i);
                    if (((ProtocolPanel) providingProtocols.get(j))
                            .getName().equals(
                                    link.providingProtocol.getName()))
                        protocolsList.add(link.requiringProtocol);
                }
            }
        return protocolsList;
    }
}
