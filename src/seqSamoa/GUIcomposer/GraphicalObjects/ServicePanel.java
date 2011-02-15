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
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;

import seqSamoa.GUIcomposer.XMLProtocolAndServiceDatabase.ServiceXML;

public class ServicePanel {
    protected ServiceXML serviceXML;
    
    protected int width = 62;
    protected int height = 12;

    protected Position pos = new Position(0, 0);

    public ServicePanel (Position pos, ServiceXML serviceXML) {
        this.pos = pos;
        this.serviceXML = serviceXML;        
    }

    /**
     * draw the service panel
     * 
     * @param g
     * @param w
     *            width of the convas
     * @param h
     *            height of the canvas
     */
    public void drawPanel(Graphics g, int w, int h) {
        g.setColor(serviceXML.color);
        int[] vectorX = { pos.x - (int) (width / 2), pos.x - (int) (width / 2.1),
                pos.x + (int) (width / 2.1), pos.x + (int) (width / 2) };
        int[] vectorY = { pos.y, pos.y - height, pos.y - height, pos.y};
        g.fillPolygon(vectorX, vectorY, 4);

        g.setFont(new Font("Arial", 0, 10));
        g.setColor(Color.white);
        g.drawString(serviceXML.diminutive, pos.x - (int) (width / 2.1), pos.y - 3);
        if (serviceXML.className.equals("Network")) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setStroke(new BasicStroke(2));
            g2d.setColor(Color.black);
            g2d.drawLine(pos.x, pos.y, pos.x, h - 20);
        }
    }

    /**
     * Move the service by a certain distance
     * 
     * @param dx
     *            the number of pixels by which the service will be moved
     *            horizontally
     * @param dy
     *            the number of pixels by which the service will be moved
     *            vertically
     */
    public void moveBy(int dx, int dy) {
        this.pos.move(dx, dy);
    }

    /**
     * check if the service contains the mouse coordinates
     * 
     * @param x
     *            the x coordinates of the mouse
     * @param y
     *            the y coordinates of the mouse
     * @return true if the service is selected by the mouse and false if not
     */
    public boolean containsPoint(int x, int y) {
        int x1 = x - pos.x;
        int y1 = pos.y - y;
        if ((y1 >= 0) && (y1 <= height)) {
            if (Math.abs(x1) <= (width / 4)) {
                return true;
            }
        }
        return false;
    }
}
