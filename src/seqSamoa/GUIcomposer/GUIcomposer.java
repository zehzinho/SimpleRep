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

/**
 * Allows ot build protocol stack with a Graphical Interface. The
 * protocol stacks can be saved in a XML file that can be later
 * given as a parameter of the {@link seqSamoa.ProtocolStack stack}
 * constructor.
 *
 * @author  nacereddine, orutti
 */
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import seqSamoa.GUIcomposer.XMLProtocolAndServiceDatabase.ParameterXML;
import seqSamoa.GUIcomposer.XMLProtocolAndServiceDatabase.ProtocolXML;
import seqSamoa.GUIcomposer.XMLProtocolAndServiceDatabase.ServiceXML;
import seqSamoa.GUIcomposer.handlers.ParametersListHandler;
import seqSamoa.GUIcomposer.handlers.ServicesListHandler;

@SuppressWarnings("serial")
public class GUIcomposer extends JFrame implements MouseListener,
        ListSelectionListener, ActionListener {
	
	
	
    private JToolBar toolBar = new JToolBar();

    private JButton newButton = new JButton(
            new ImageIcon(
                    "/home/orutti/Crystall/workspace/Samoa/src/seqSamoa/GUIcomposer/images/new.gif"));

    private JButton openSchemaButton = new JButton(
            new ImageIcon(
                    "/home/orutti/Crystall/workspace/Samoa/src/seqSamoa/GUIcomposer/images/open_file.gif"));

    private JButton deleteButton = new JButton(
            new ImageIcon(
                    "/home/orutti/Crystall/workspace/Samoa/src/seqSamoa/GUIcomposer/images/cancel.gif"));

    private JButton saveButton = new JButton(
            new ImageIcon(
                    "/home/orutti/Crystall/workspace/Samoa/src/seqSamoa/GUIcomposer/images/save.gif"));

    private JButton saveasButton = new JButton(
            new ImageIcon(
                    "/home/orutti/Crystall/workspace/Samoa/src/seqSamoa/GUIcomposer/images/save_as.gif"));

    private JButton quitButton = new JButton(
            new ImageIcon(
                    "/home/orutti/Crystall/workspace/Samoa/src/seqSamoa/GUIcomposer/images/quit.gif"));

    private JComboBox filterByList = new JComboBox();

    private JList allProtocolsList = new JList();

    private JButton addButton = new JButton("add");

    private JLabel allProtocolsLabel = new JLabel();

    private JTextArea descriptionTextArea = new JTextArea();

    private JLabel descriptionLabel = new JLabel();

    private JCanvas canvas;

    private JLabel perametersLabel = new JLabel();

    private JList parametersList = new JList();

    private JLabel providedServiceLabel = new JLabel();

    private JList providedServicesList = new JList();

    private JLabel requiredServicesLabel = new JLabel();

    private JList requiredServicesList = new JList();

    private JScrollPane scrollPane1 = new JScrollPane();

    private JScrollPane scrollPane2 = new JScrollPane();

    private JScrollPane scrollPane3 = new JScrollPane();

    private JScrollPane scrollPane4 = new JScrollPane();

    private JSeparator separator = new JSeparator();

    private JSeparator serarator2 = new JSeparator();

    private JTabbedPane tab1 = new JTabbedPane();

    private JPanel panel1 = new JPanel();

    private JTabbedPane tab2 = new JTabbedPane();

    private JPanel panel2 = new JPanel();

    private JButton submitButton = new JButton("submit");

    private ArrayList<JLabel> parameterslabelsList = new ArrayList<JLabel>();

    private ArrayList<JTextField> parameterstextFieldsList = new ArrayList<JTextField>();

    private JTabbedPane tab3 = new JTabbedPane();

    private JPanel panel3 = new JPanel();

    private XMLProtocolAndServiceDatabase readerXML;

    private File schemaFile;

    private JFileChooser chooser = new JFileChooser();

    public GUIcomposer() {
        readerXML = new XMLProtocolAndServiceDatabase("SAMOA.xml");
        chooser.addChoosableFileFilter(new ExtentionFilterXML());
        chooser.setAcceptAllFileFilterUsed(false);
        initComponents();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     */
    private void initComponents() {
        this.setTitle("Graphic Tool for Composing Protocols");
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int width = screen.width;
        int height = screen.height - 50;
        this.setSize(width, height);
        this.setLayout(null);

        // the toolbar
        toolBar.add(newButton);
        toolBar.add(openSchemaButton);
        toolBar.add(saveButton);
        toolBar.add(saveasButton);
        toolBar.add(deleteButton);
        toolBar.add(quitButton);
        newButton.addActionListener(this);
        openSchemaButton.addActionListener(this);
        saveButton.addActionListener(this);
        saveasButton.addActionListener(this);
        deleteButton.addActionListener(this);
        quitButton.addActionListener(this);
        this.add(toolBar);
        toolBar.setBounds(0, 0, width, 30);

        panel1.setLayout(null);

        tab1.addTab("untitled.xml", panel1);

        this.add(tab1);
        tab1.setBounds(5, 40, (int) (0.66 * width), height - 80);

        panel2.setLayout(null);
        tab2.addTab("Protocol Properties", panel2);
        submitButton.addActionListener(this);

        this.add(tab2);
        tab2.setBounds((int) (0.66 * width) + 10, 40, (int) (0.15 * width),
                height - 80);

        panel3.setLayout(null);

        this.add(tab3);
        tab3.setBounds((int) (0.81 * width) + 15, 40, (int) (0.17 * width),
                height - 80);

        tab3.addTab("All Protocols List", panel3);

        scrollPane1.setViewportView(allProtocolsList);
        allProtocolsList.setListData(readerXML.getAllProtocolsVector());
        allProtocolsList.addListSelectionListener(this);
        allProtocolsList.addMouseListener(this);

        JLabel filterbyJLabel = new JLabel("Filter By :");
        panel3.add(filterbyJLabel);
        filterbyJLabel.setBounds(10, 10, 90, 20);
        filterByList.addItem("          ");
        for (int i = 0; i < readerXML.getAllModels().size(); i++) {
            filterByList.addItem(readerXML.getAllModels().get(i));
        }
        panel3.add(filterByList);
        filterByList.setBounds(10, 30, tab3.getWidth() - 20, 20);
        filterByList.addActionListener(this);

        panel3.add(scrollPane1);
        scrollPane1.setBounds(10, 75, tab3.getWidth() - 20, 240);

        addButton.addActionListener(this);

        panel3.add(addButton);
        addButton.setBounds(30, 325, 60, 25);

        // allProtocolsLabel.setFont(new java.awt.Font("Dialog", 1, 14));
        allProtocolsLabel.setText("Protocols List");
        panel3.add(allProtocolsLabel);
        allProtocolsLabel.setBounds(10, 55, 100, 20);

        providedServiceLabel.setText("Protocol Provided Services");
        panel3.add(providedServiceLabel);
        providedServiceLabel.setBounds(10, 360, 160, 20);

        scrollPane4.setViewportView(providedServicesList);
        providedServicesList.addListSelectionListener(new ServicesListHandler(
                descriptionTextArea, readerXML));

        panel3.add(scrollPane4);
        scrollPane4.setBounds(10, 380, tab3.getWidth() - 20, 100);

        perametersLabel.setText("Protocol Parameters");
        panel3.add(perametersLabel);
        perametersLabel.setBounds(10, 490, 130, 15);

        scrollPane2.setViewportView(parametersList);
        parametersList.addListSelectionListener(new ParametersListHandler(
                descriptionTextArea, readerXML));

        panel3.add(scrollPane2);
        scrollPane2.setBounds(10, 510, tab3.getWidth() - 20, 100);

        requiredServicesLabel.setText("Protocol Required Services");
        panel3.add(requiredServicesLabel);
        requiredServicesLabel.setBounds(10, 620, 170, 15);

        scrollPane3.setViewportView(requiredServicesList);
        requiredServicesList.addListSelectionListener(new ServicesListHandler(
                descriptionTextArea, readerXML));

        panel3.add(scrollPane3);
        scrollPane3.setBounds(10, 640, tab3.getWidth() - 20, 100);

        panel3.add(separator);
        separator.setBounds(20, 355, tab3.getWidth() - 40, 10);

        panel3.add(serarator2);
        serarator2.setBounds(20, 745, tab3.getWidth() - 40, 10);

        descriptionLabel.setText("Description");
        panel3.add(descriptionLabel);
        descriptionLabel.setBounds(10, 750, 80, 15);

        descriptionTextArea.setBackground(new java.awt.Color(238, 238, 238));
        descriptionTextArea.setColumns(20);
        descriptionTextArea.setRows(5);
        panel3.add(descriptionTextArea);
        descriptionTextArea.setBounds(10, 770, tab3.getWidth() - 20, 100);

        canvas = new JCanvas(readerXML);
        panel1.add(canvas);
        canvas.setBounds(0, 0, tab1.getWidth(), tab1.getHeight() - 50);
        canvas.addMouseListener(this);
    }

    public void actionPerformed(ActionEvent evt) {
        String command = evt.getActionCommand();
        if (command.equals("add")) {
            if (allProtocolsList.getSelectedValue() != null) {
                String protocolName = allProtocolsList.getSelectedValue()
                        .toString().trim();
                ProtocolXML protXML = readerXML.getProtocol(protocolName);
                canvas.addProtocol(protXML);
            }
        } else if (evt.getSource().equals(newButton)) {
            canvas.init();
            tab1.setTitleAt(0, "untitled.xml");
            panel2.removeAll();
        } else if (evt.getSource().equals(saveasButton)) {
            chooser.showSaveDialog(this);
            schemaFile = chooser.getSelectedFile();
            if (schemaFile != null) {
                canvas.saveSchema(schemaFile.getPath());
            }
        } else if (evt.getSource().equals(saveButton)) {
            if (schemaFile == null) {
                chooser.showSaveDialog(this);
                schemaFile = chooser.getSelectedFile();
            }
            if (schemaFile != null) {
                tab1.setTitleAt(0, schemaFile.getName());
                canvas.saveSchema(schemaFile.getPath());
            }
        } else if (evt.getSource().equals(openSchemaButton)) {
            chooser.setAcceptAllFileFilterUsed(false);
            chooser.showOpenDialog(this);
            schemaFile = chooser.getSelectedFile();
            if (schemaFile != null) {
                canvas.openSchema(schemaFile.getPath());
                tab1.setTitleAt(0, schemaFile.getName());
            }
        } else if (evt.getSource().equals(deleteButton)) {
            canvas.deleteSelectedObject();
            panel2.removeAll();
        } else if (evt.getSource().equals(submitButton)) {
            HashMap<ParameterXML, String> parameters = new HashMap<ParameterXML, String>();
            for (int i = 0; i < parameterslabelsList.size(); i++) {
            	ParameterXML pXML = readerXML.getParameter(((JLabel) parameterslabelsList.get(i)).getText());
                parameters.put(pXML,
                        ((JTextField) parameterstextFieldsList.get(i))
                                .getText());
            }
            canvas.modifySelectedProtocolParameters(parameters);
        } else if (evt.getSource().equals(quitButton)) {
            System.exit(0);
        } else if (evt.getSource().equals(filterByList)) {
            if (filterByList.getSelectedItem().equals("          "))
                allProtocolsList.setListData(readerXML.getAllProtocolsVector());
            else
                allProtocolsList.setListData(readerXML
                        .getAllProtocolsVector((String) filterByList
                                .getSelectedItem()));
        }
        repaint();
    }

	public void valueChanged(ListSelectionEvent e) {
        // get the Protocol selected by the user
        String selectedProtocol = (String) allProtocolsList.getSelectedValue();
        if (selectedProtocol != null) {
            String protocolName = selectedProtocol.trim();
            ProtocolXML protXML = readerXML.getProtocol(protocolName);

            // Provided Services
            Vector<String> providedServiceNames = new Vector<String>();
            Iterator<ServiceXML> itProvided = protXML.providedService.iterator();
            while (itProvided.hasNext()) {
                ServiceXML service = (ServiceXML) itProvided.next();
                providedServiceNames.add(service.className);
            }
            providedServicesList.setListData(providedServiceNames);
            // Required Services
            Vector<String> requiredServiceNames = new Vector<String>();
            Iterator<ServiceXML> itRequired = protXML.requiredService.iterator();
            while (itRequired.hasNext()) {
                ServiceXML service = (ServiceXML) itRequired.next();
                requiredServiceNames.add(service.className);
            }
            requiredServicesList.setListData(requiredServiceNames);
            // Parameters
            Vector<String> parameterNames = new Vector<String>();
            Iterator<ParameterXML> itParams = protXML.parameters.iterator();
            while (itParams.hasNext()) {
                ParameterXML params = (ParameterXML) itParams.next();
                parameterNames.add(params.name);
            }
            parametersList.setListData(parameterNames);
        } else {
            providedServicesList.setListData(new Object[] {});
            requiredServicesList.setListData(new Object[] {});
            parametersList.setListData(new Object[] {});
        }
        this.repaint();
    }

    public void mousePressed(MouseEvent evt) {
        showProtocolProperties(canvas.getSelectedProtocolProperties());
        repaint();
    }

    public void mouseReleased(MouseEvent evt) {
    }

    public void mouseExited(MouseEvent evt) {
    }

    public void mouseEntered(MouseEvent evt) {
    }

    // this method is called when mouse is clicked
    public void mouseClicked(MouseEvent evt) {
        if (evt.getClickCount() == 2) {
            if (evt.getSource().equals(allProtocolsList)) {
                ProtocolXML protXML = readerXML
                        .getProtocol(((String) allProtocolsList
                                .getSelectedValue()).trim());
                JOptionPane.showMessageDialog(null, protXML.description,
                        "Description", JOptionPane.PLAIN_MESSAGE);
                // Force a repaint so we can see what's happening.
                repaint();
            }
        }
    }

    /**
     * this method is used to show the parameters of a protocol when this one is
     * selected
     * 
     * @param parameters
     *            list of the values of the parameters of the selected protocol
     */
    public void showProtocolProperties(HashMap<ParameterXML, String> parameters) {
        panel2.removeAll();

        parameterslabelsList = new ArrayList<JLabel>();
        parameterstextFieldsList = new ArrayList<JTextField>();
        if (parameters != null) {
            Iterator<ParameterXML> it = parameters.keySet().iterator();
            while (it.hasNext()) {
            	ParameterXML pXML = it.next();
            	parameterslabelsList.add(new JLabel(pXML.name));
            	parameterstextFieldsList.add(new JTextField(
                            (String) parameters.get(pXML)));
            }
            //
            if (parameterslabelsList.size() == parameterstextFieldsList.size()) {
                for (int k = 0; k < parameterslabelsList.size(); k++) {
                    JLabel label = (JLabel) parameterslabelsList.get(k);
                    panel2.add(label);
                    label.setBounds(10, 5 + (50 * k), (int) panel2.getSize()
                            .getWidth() - 20, 20);
                    JTextField textfield = (JTextField) parameterstextFieldsList
                            .get(k);
                    panel2.add(textfield);
                    textfield.setBounds(10, 25 + (50 * k), (int) panel2
                            .getSize().getWidth() - 20, 20);
                }
            }
            panel2.add(submitButton);
            submitButton.setBounds((int) panel2.getSize().getWidth() - 100,
                    parameterslabelsList.size() * 50 + 20, 80, 25);
        }
        repaint();
    }
    
    /**
     * the main method of the class
     * 
     * @param args
     *            the command line arguments
     */
    public static void main(String args[]) {
        GUIcomposer graphicalTool = new GUIcomposer();
        graphicalTool.setVisible(true);
    }
}
