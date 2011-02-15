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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.LinkedList;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;

import seqSamoa.GUIcomposer.GraphicalObjects.StackManager;
import seqSamoa.GUIcomposer.XMLProtocolAndServiceDatabase.ParameterXML;
import seqSamoa.GUIcomposer.XMLProtocolAndServiceDatabase.ProtocolXML;

@SuppressWarnings("serial")
public class AddProtocolGUI extends javax.swing.JFrame implements
        ActionListener {
    private JLabel description = new JLabel();

    private JButton next = new JButton();

    private JButton back = new JButton();

    private JButton abort = new JButton();

    private JLabel label1 = new JLabel();

    private JLabel label2 = new JLabel();

    private JLabel label3 = new JLabel();

    private JPanel panel1 = new JPanel();

    private JPanel panel2 = new JPanel();

    private JLabel parameterName = new JLabel();

    private JTextField parameterValue = new JTextField();

    private JSeparator separator = new JSeparator();

    // ReaderXML xmlBrowser;
    HashMap<ParameterXML, String> protocolParametersValues;
    
    String name;

    private int indexPointer = 0;

    private LinkedList<Integer> parameterIndexes;

    // private int pIndex;
    private StackManager stackManager;

    private ProtocolXML protXML;

    public AddProtocolGUI(StackManager stackManager, ProtocolXML protXML) {
        this.stackManager = stackManager;
        this.protXML = protXML;

        // Initialize parameters values
        protocolParametersValues = new HashMap<ParameterXML, String>();
        parameterIndexes = new LinkedList<Integer>();

        // Initialize Graphical Components of the GUI
        this.setLayout(null);
        this.setSize(490, 300);

        panel1.setLayout(null);
        label1.setFont(new java.awt.Font("Dialog", 1, 18));
        label1.setText(protXML.packageName + protXML.className);
        panel1.add(label1);
        label1.setBounds(180, 20, 225, 22);

        this.add(panel1);
        panel1.setBounds(0, 0, 490, 60);

        panel2.setLayout(null);
        panel2.setBounds(0, 60, 490, 170);

        panel2.add(separator);
        separator.setBounds(40, 70, 400, 10);

        // Get the index of the parameter that must be provided
        for (int i = 0; i < protXML.parameters.size(); i++)
            parameterIndexes.add(i);

        // Firstly ask the name
        indexPointer = -1;
        parameterName.setText("Name of the Protocol");
        panel2.add(parameterName);
        parameterName.setBounds(40, 40, 100, 20);

        panel2.add(parameterValue);
        parameterValue.setBounds(150, 40, 280, 19);
        parameterValue.addActionListener(this);

        label2.setFont(new java.awt.Font("Dialog", 1, 14));
        label2.setText("Enter The Following Parameter :");
        panel2.add(label2);
        label2.setBounds(40, 10, 231, 17);

        label3.setText("Description :");
        panel2.add(label3);
        label3.setBounds(40, 80, 79, 15);

        description.setText("The name of the protocol (should be unique)");
        panel2.add(description);
        description.setBounds(40, 100, 400, 60);

        this.add(panel2);

        next.setText("Next");
        if (indexPointer == (parameterIndexes.size() - 1))
            next.setText("End");
        this.add(next);
        next.setBounds(410, 240, 62, 20);
        next.addActionListener(this);

        back.setText("Back");
        this.add(back);
        back.setBounds(30, 240, 63, 20);
        back.addActionListener(this);

        abort.setText("Abort");
        this.add(abort);
        abort.setBounds(210, 240, 69, 20);
        abort.addActionListener(this);
    }

    public void actionPerformed(ActionEvent e) {
        Object o = e.getSource();
        if (o.getClass().getName().contains("JButton")) {
            JButton clickedButton = (JButton) o;
            if (clickedButton.getText().equals("Next")) {
                if (parameterIsValid())
                    next();
            } else if (clickedButton.getText().equals("End")) {
                if (parameterIsValid()) {
                    end();
                    return;
                }
            }
            if (clickedButton.getText().equals("Back")) {
                if (indexPointer > 0)
                    indexPointer--;
                if (indexPointer < (parameterIndexes.size() - 1))
                    next.setText("Next");
            }
            if (clickedButton.getText().equals("Abort")){
                this.setVisible(false);
                return;
            }

        } else if (o.equals(parameterValue)) {
            if (parameterIsValid())
                if (next.getText().equals("Next"))
                    next();
                else if (next.getText().equals("End")){
                    end();
                    return;
                }
        }

        int parameterIndex = parameterIndexes.get(indexPointer);
        ParameterXML param = protXML.parameters.get(parameterIndex);

        parameterName.setText(param.name);
        description.setText(param.description);
        if (param.defaultValue != null)
            parameterValue.setText(param.defaultValue);
        else
            parameterValue.setText("");
    }

    /**
     * pass to the next parameter
     */
    void next() {
    	if (indexPointer == -1)
    		this.name = parameterValue.getText();
    	else
    		protocolParametersValues.put(protXML.parameters.get(parameterIndexes.get(indexPointer)), parameterValue
    				.getText());
        if (indexPointer < parameterIndexes.size())
            indexPointer++;
        if (indexPointer == (parameterIndexes.size() - 1))
            next.setText("End");
    }

    /**
     * end the procedure
     */
    void end() {
    	if (indexPointer == -1)
    		this.name = parameterValue.getText();
    	else   	
    		protocolParametersValues.put(protXML.parameters.get(parameterIndexes.get(indexPointer)), parameterValue.getText());
        stackManager.createProtocolPanel(protXML, name, protocolParametersValues);
        this.setVisible(false);
    }

    /**
     * this methode checks if the value entered by the user for the current
     * parameter is valid or not.
     * 
     * @return true if the value if the value is a valid one, and false if it's
     *         not valid
     */
    boolean parameterIsValid() {
		if (parameterValue.getText().equals("")
				|| parameterValue.getText().equals(" ")) {
			JOptionPane.showMessageDialog(null,
					"you have to provide a value for this parameter", "error",
					JOptionPane.ERROR_MESSAGE);
			return false;
		}

		if (indexPointer == -1) {
			if (stackManager.isNameAlreadyUsed(parameterValue.getText())) {
				JOptionPane.showMessageDialog(null,
						"this name is already attributed", "error",
						JOptionPane.ERROR_MESSAGE);
						return false;
			}
		} else {

			int parameterIndex = (Integer) parameterIndexes.get(indexPointer);
			ParameterXML currentParam = (ParameterXML) protXML.parameters
					.get(parameterIndex);

			if (currentParam.type.equals("int")) {
				try {
					Integer.parseInt(parameterValue.getText());
				} catch (NumberFormatException e) {
					JOptionPane.showMessageDialog(null,
							"this paramter is an integer", "error",
							JOptionPane.ERROR_MESSAGE);
					return false;
				}
			}
			if (currentParam.type.equals("long")) {
				try {
					Long.parseLong(parameterValue.getText());
				} catch (NumberFormatException e) {
					JOptionPane.showMessageDialog(null,
							"this paramter is an integer", "error",
							JOptionPane.ERROR_MESSAGE);
					return false;
				}
			}
			if (currentParam.type.equals("boolean")) {
				if (!parameterValue.getText().toLowerCase().equals("true")
						&& !parameterValue.getText().toLowerCase().equals(
								"false")) {
					JOptionPane.showMessageDialog(null,
							"this paramter is a boolean(true or false)",
							"error", JOptionPane.PLAIN_MESSAGE);
					return false;
				}
			}
		}
		return true;
	}
}
