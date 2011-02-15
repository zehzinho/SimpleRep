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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.HashMap;

import javax.swing.JPanel;

import seqSamoa.GUIcomposer.GraphicalObjects.StackManager;
import seqSamoa.GUIcomposer.XMLProtocolAndServiceDatabase.ParameterXML;
import seqSamoa.GUIcomposer.XMLProtocolAndServiceDatabase.ProtocolXML;

@SuppressWarnings("serial")
public class JCanvas extends JPanel implements MouseListener,
        MouseMotionListener {
    StackManager stackManager;

    boolean drawingLink = false;

    XMLProtocolAndServiceDatabase xmlBrowser;

    public JCanvas(XMLProtocolAndServiceDatabase xmlBrowser) {
        this.xmlBrowser = xmlBrowser;
        stackManager = new StackManager(xmlBrowser);
        addMouseListener(this);
        addMouseMotionListener(this);
    }

    public void init() {
        stackManager = new StackManager(xmlBrowser);
    }

    /**
     * draw the constructed stack
     */
    public void paint(Graphics g) {
        // Background of the canvas
        g.setColor(Color.white);
        g.fillRect(0, 0, this.getSize().width, this.getSize().height);

        //
        stackManager.drawStack(g, this.getWidth(), this.getHeight());

        //
        g.setColor(Color.black);
        g.setFont(new Font("Arial", Font.BOLD, 13));
        g.drawString("Network", (this.getSize().width / 2) - 30,
                this.getSize().height - 5);

        Graphics2D g2d = (Graphics2D) g;
        g2d.setStroke(new BasicStroke(2));

        g2d.drawLine(10, this.getHeight() - 20, this.getWidth() - 20, this
                .getHeight() - 20);
        g2d.drawLine(10, 20, this.getWidth() - 20, 20);

    }

    protected void addProtocol(ProtocolXML protXML) {
        AddProtocolGUI addProtocol = new AddProtocolGUI(stackManager, protXML);
        addProtocol.setVisible(true);
    }

    /***************************************************************************
     * recover the parameters values of the selected protocol
     * 
     * @return a list of the values of the selected protocol's parameters
     */
    HashMap<ParameterXML, String> getSelectedProtocolProperties() {
        return stackManager.getSelectedProtocolParameters();
    }

    /**
     * save the constructed stack in the file of name "fileName"
     * 
     * @param fileName
     *            the name of the XML file in which the stack will be saved
     */
    void saveSchema(String fileName) {
        stackManager.saveSchema(fileName);
    }

    /**
     * open an XML file containing a stack
     * 
     * @param fileName
     *            the name of the XML file
     */
    void openSchema(String fileName) {
        stackManager.openSchema(fileName);
    }

    /**
     * delete the selected object
     */
    void deleteSelectedObject() {
        stackManager.deleteSelectedObject();
    }

    /**
     * modify the values of the selected protocol's parameters
     * 
     * @param parametersNames
     *            the names of the selected protocol
     * @param parametersValues
     *            the new values of the parameters of the selected protocol
     */
    void modifySelectedProtocolParameters(HashMap<ParameterXML, String> parameters) {
        stackManager.modifySelectedProtocolParameters(parameters);
    }

    // this method is called when the mouse is pressed
    synchronized public void mousePressed(MouseEvent evt) {
        // x and y coordinates of point where mouse was clicked
        int x = evt.getX();
        int y = evt.getY();
        stackManager.mousePressed(x, y);
        repaint();
    }

    // this methode is called while the mouse is dragged
    synchronized public void mouseDragged(MouseEvent evt) {
        // x and y coordinates of point where mouse was dragged
        int x = evt.getX();
        int y = evt.getY();
        stackManager.mouseDragged(x, y, this.getWidth(), this.getHeight());
        repaint();
    }

    // this method is called when the mouse is released
    synchronized public void mouseReleased(MouseEvent evt) {
        int x = evt.getX(); // x-coordinate of mouse
        int y = evt.getY(); // y-coordinate of mouse
        stackManager.mouseReleased(x, y);
        repaint();
    }

    public void mouseMoved(MouseEvent evt) {
        int x = evt.getX();
        int y = evt.getY();
        stackManager.mouseMoved(x, y);
        repaint();
    }

    public void mouseEntered(MouseEvent evt) {
    }

    public void mouseExited(MouseEvent evt) {
    }

    public void mouseClicked(MouseEvent evt) {
        stackManager.mouseClicked(evt.getX(), evt.getY(), evt.getClickCount());
    }
}