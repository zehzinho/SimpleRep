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
import java.util.ArrayList;

import javax.swing.JOptionPane;

import org.jdom.Element;

public class Link  extends FinalLink {
    protected ProtocolPanel requiringProtocol;

    protected ServicePanel requiredService;

    ArrayList<Position> linkNodes;

    int SelectedPart = -1;

    public Link(ProtocolPanel providingProtocol,
            ProtocolPanel requiringProtocol, String serviceClassName) {
        super (providingProtocol, serviceClassName);
        
        this.requiringProtocol = requiringProtocol;
        this.requiredService = requiringProtocol
                .getServicePanelByServiceClassName(serviceClassName);
        
        initIntermediateNodes();
    }

    private void initIntermediateNodes() {
        linkNodes = new ArrayList<Position>();
        int distanceX = providedService.pos.x
                - requiredService.pos.x;
        linkNodes.add(new Position(providedService.pos.x,
                        providedService.pos.y
                                - providedService.height));
        linkNodes.add(new Position(providedService.pos.x,
                providedService.pos.y - providedService.height
                        - 10));
        linkNodes.add(new Position(providedService.pos.x
                - (distanceX / 2), providedService.pos.y
                - providedService.height - 10));
        linkNodes.add(new Position(providedService.pos.x
                - (distanceX / 2), requiredService.pos.y + 10));
        linkNodes.add(new Position(requiredService.pos.x,
                requiredService.pos.y + 10));
        linkNodes.add(new Position(requiredService.pos.x,
                requiredService.pos.y));
    }
    
    /**
     * create an Element representing the service
     * 
     * @return XML Element representing the service
     */
    public Element getServiceElement() {
        Element service = super.getServiceElement();
        service.getChild("ServiceFinal").setText("false");
        
        return service;
    }
    
    /**
     * draw the link in the canvas
     * 
     * @param g2d
     */
    public void drawLink(Graphics2D g2d) {
        // if providing protocol
        if (providedService.pos.x != ((Position) linkNodes.get(0)).x) {
            ((Position) linkNodes.get(0)).x = providedService.pos.x;
            ((Position) linkNodes.get(1)).x = providedService.pos.x;
        }
        if (providedService.pos.y - providedService.height != ((Position) linkNodes
                .get(0)).y)
            ((Position) linkNodes.get(0)).y = providedService.pos.y
                    - providedService.height;

        // if requiring protocol
        if (requiredService.pos.x != ((Position) linkNodes.get(linkNodes
                .size() - 1)).x) {
            ((Position) linkNodes.get(linkNodes.size() - 1)).x = requiredService.pos.x;
            ((Position) linkNodes.get(linkNodes.size() - 2)).x = requiredService.pos.x;
        }
        if (requiredService.pos.y != ((Position) linkNodes.get(linkNodes
                .size() - 1)).y)
            ((Position) linkNodes.get(linkNodes.size() - 1)).y = requiredService.pos.y;

        if (selected)
            g2d.setColor(Color.blue);
        else
            g2d.setColor(Color.black);

        for (int i = 0; i < linkNodes.size() - 1; i++) {
            g2d.drawLine(((Position) linkNodes.get(i)).x,
                    ((Position) linkNodes.get(i)).y, ((Position) linkNodes
                            .get(i + 1)).x, ((Position) linkNodes
                            .get(i + 1)).y);
        }

        if (selected) {
            g2d.fillRect(((Position) linkNodes.get(SelectedPart)).x - 3,
                    ((Position) linkNodes.get(SelectedPart)).y - 3, 6, 6);
            g2d.fillRect(
                    ((Position) linkNodes.get(SelectedPart + 1)).x - 3,
                    ((Position) linkNodes.get(SelectedPart + 1)).y - 3, 6,
                    6);
        }
    }
    
    /**
     * check if the link contains the coordinates of the mouse
     * 
     * @param x
     *            the x coordinate of the mouse
     * @param y
     *            the y coordinate of the mouse
     * @return true of the link is selected by the mouse and false if not
     */
    public boolean containsPoint(int x, int y) {
        for (int i = 0; i < linkNodes.size() - 1; i++) {
            if (((Position) linkNodes.get(i)).x == ((Position) linkNodes
                    .get(i + 1)).x)
                if ((Math.min(((Position) linkNodes.get(i)).y,
                        ((Position) linkNodes.get(i + 1)).y) < y)
                        && (y < Math.max(((Position) linkNodes.get(i)).y,
                                ((Position) linkNodes.get(i + 1)).y)))
                    if ((Math.abs(((Position) linkNodes.get(i)).x - x) < 2)) {
                        SelectedPart = i;
                        return true;
                    }

            if (((Position) linkNodes.get(i)).y == ((Position) linkNodes
                    .get(i + 1)).y)
                if ((Math.min(((Position) linkNodes.get(i)).x,
                        ((Position) linkNodes.get(i + 1)).x) < x)
                        && (x < Math.max(((Position) linkNodes.get(i)).x,
                                ((Position) linkNodes.get(i + 1)).x)))
                    if ((Math.abs(((Position) linkNodes.get(i)).y - y) < 2)) {
                        SelectedPart = i;
                        return true;
                    }
        }
        return false;
    }
   
    public boolean equals(Object o){
        if (!(o instanceof Link))
            return false;
        
        Link fl = (Link) o;
        
        return ((providingProtocol.getName().equals(fl.providingProtocol.getName())) && (requiringProtocol.getName().equals(fl.requiringProtocol.getName())));
    }

    public void moveSelectedPart(int dx, int dy) {
        if (SelectedPart >= 0) {
            if (((Position) linkNodes.get(SelectedPart)).x == ((Position) linkNodes
                    .get(SelectedPart + 1)).x) {
                ((Position) linkNodes.get(SelectedPart)).move(dx, 0);
                ((Position) linkNodes.get(SelectedPart + 1)).move(dx, 0);
            }

            if (((Position) linkNodes.get(SelectedPart)).y == ((Position) linkNodes
                    .get(SelectedPart + 1)).y) {
                ((Position) linkNodes.get(SelectedPart)).move(0, dy);
                ((Position) linkNodes.get(SelectedPart + 1)).move(0, dy);
            }
        }
    }

    public void mouseDoubleClicked(int x, int y) {
        for (int i = 0; i < linkNodes.size() - 1; i++) {
            if ((((Position) linkNodes.get(i)).x == ((Position) linkNodes
                    .get(i + 1)).x)
                    && (((Position) linkNodes.get(i)).y == ((Position) linkNodes
                            .get(i + 1)).y)) {
                if ((Math.abs(((Position) linkNodes.get(i)).x - x) < 3)
                        && (Math.abs(((Position) linkNodes.get(i)).y - y) < 3)) {
                    int conf = JOptionPane.showConfirmDialog(null,
                            "Do you whant to remove this node from the link",
                            "A Confirm Dialog", JOptionPane.YES_NO_OPTION);
                    if (conf == 0) {
                        linkNodes.remove(i);
                        linkNodes.remove(i);
                    }
                    return;
                }
            }
        }

        if (((Position) linkNodes.get(SelectedPart)).x == ((Position) linkNodes
                .get(SelectedPart + 1)).x) {
            if ((Math.min(((Position) linkNodes.get(SelectedPart)).y,
                    ((Position) linkNodes.get(SelectedPart + 1)).y) + 2 < y)
                    && (y < Math.max(((Position) linkNodes.get(SelectedPart))
                            .y, ((Position) linkNodes
                            .get(SelectedPart + 1)).y) - 2))
                if ((Math.abs(((Position) linkNodes.get(SelectedPart)).x
                        - x) < 2)) {
                    int conf = JOptionPane.showConfirmDialog(null,
                            "Do you whant to add node to the link",
                            "A Confirm Dialog", JOptionPane.YES_NO_OPTION);
                    if (conf == 0) {
                        linkNodes.add(SelectedPart + 1,
                                new Position(((Position) linkNodes
                                        .get(SelectedPart)).x, y));
                        linkNodes.add(SelectedPart + 1,
                                new Position(((Position) linkNodes
                                        .get(SelectedPart)).x, y));
                    }
                    return;
                }
        } else if (((Position) linkNodes.get(SelectedPart)).y == ((Position) linkNodes
                .get(SelectedPart + 1)).y) {
            if ((Math.min(((Position) linkNodes.get(SelectedPart)).x,
                    ((Position) linkNodes.get(SelectedPart + 1)).x) + 2 < x)
                    && (x < Math.max(((Position) linkNodes.get(SelectedPart))
                            .x, ((Position) linkNodes
                            .get(SelectedPart + 1)).x) - 2))
                if (Math.abs(((Position) linkNodes.get(SelectedPart)).y
                        - y) < 2) {
                    int conf = JOptionPane.showConfirmDialog(null,
                            "Do you whant to add node to the link",
                            "A Confirm Dialog", JOptionPane.YES_NO_OPTION);
                    if (conf == 0) {
                        linkNodes.add(SelectedPart + 1,
                                new Position(x, ((Position) linkNodes
                                        .get(SelectedPart)).y));
                        linkNodes.add(SelectedPart + 1,
                                new Position(x, ((Position) linkNodes
                                        .get(SelectedPart)).y));
                    }
                    return;
                }
        }
    }
}
