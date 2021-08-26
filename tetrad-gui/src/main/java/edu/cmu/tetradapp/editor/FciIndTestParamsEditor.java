///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.text.NumberFormat;


/**
 * Edits the properties of a measurement params.
 *
 * @author Joseph Ramsey
 */
class FciIndTestParamsEditor extends JComponent {

    /**
     * The parameters object being edited.
     */
    private Parameters params = null;

    private final DoubleTextField alphaField;

    private final IntTextField depthField;

    private final JCheckBox completeRuleSetCheckBox;

    /**
     * A checkbox to allow the user to specify whether possible DSEP should be done.
     */
    private JCheckBox possibleDsepCheckBox;

    /**
     * An int field to specify the maximum length of reachable undirectedPaths (in discriminating path orientation and
     * possible dsep).
     */
    private final IntTextField maxReachablePathLengthField;

    /**
     * A checkbox to allow the user to specify whether to use RFCI
     */
    private JCheckBox RFCI_CheckBox;

    /**
     * Constructs a dialog to edit the given gene simulation parameters object.
     */
    public FciIndTestParamsEditor(Parameters params) {
        this.params = params;

        NumberFormat smallNumberFormat = new DecimalFormat("0E00");

        // set up text and ties them to the parameters object being edited.
        alphaField = new DoubleTextField(params().getDouble(Params.ALPHA), 8,
                new DecimalFormat("0.0########"), smallNumberFormat, 1e-4);
        alphaField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    params().set("alpha", 0.001);
                    return value;
                } catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        depthField = new IntTextField(params().getInt("depth", -1), 5);
        depthField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    params().set("depth", value);
                    return value;
                } catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        completeRuleSetCheckBox = new JCheckBox();
        final boolean completeRuleSetUsed = params().getBoolean("completeRuleSetUsed", false);
        completeRuleSetCheckBox.setSelected(completeRuleSetUsed);
        completeRuleSetCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                if (RFCI_CheckBox.isSelected()) {
                    params().set("completeRuleSetUsed", true);
                }
                else {
                    params().set("completeRuleSetUsed", completeRuleSetCheckBox.isSelected());
                }

                completeRuleSetCheckBox.setSelected(completeRuleSetUsed);
            }
        });

        RFCI_CheckBox = new JCheckBox();
        RFCI_CheckBox.setSelected(params().getBoolean("rfciUsed", false));
        RFCI_CheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JCheckBox source = (JCheckBox) actionEvent.getSource();
                boolean selected = source.isSelected();
                params().set("rfciUsed", selected);
                params().set("completeRuleSetUsed", true);
                params().set("possibleDsepDone", false);

                if (selected) {

                    // keep completeRuleSetCheckBox checked if RFCI is used
                    params().set("completeRuleSetUsed", true);
                    completeRuleSetCheckBox.setSelected(completeRuleSetUsed);
                    possibleDsepCheckBox.setSelected(params().getBoolean("possibleDsepDone", true));
                } else {
                    params().set("rfciUsed", false);
                }
            }
        });

        possibleDsepCheckBox = new JCheckBox();
        possibleDsepCheckBox.setSelected(params().getBoolean("possibleDsepDone", true));
        possibleDsepCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                if (RFCI_CheckBox.isSelected()) {
                    params().set("possibleDsepDone", false);
                    possibleDsepCheckBox.setSelected(false);
                    return;
                }
                JCheckBox source = (JCheckBox) actionEvent.getSource();
                params().set("possibleDsepDone", source.isSelected());
            }
        });

        maxReachablePathLengthField = new IntTextField(params().getInt("maxReachablePathLength", -1), 3);
        maxReachablePathLengthField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    params().set("maxReachablePathLength", value);
                    return value;
                } catch (Exception e) {
                    return oldValue;
                }
            }
        });


        buildGui();
    }

    /**
     * Constructs the Gui used to edit properties; called from each constructor.
     * Constructs labels and text fields for editing each property and adds
     * appropriate listeners.
     */
    private void buildGui() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        if (alphaField != null) {
            Box b1 = Box.createHorizontalBox();
            b1.add(new JLabel("Alpha:"));
            b1.add(Box.createHorizontalStrut(10));
            b1.add(Box.createHorizontalGlue());
            b1.add(alphaField);
            add(b1);
        }

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Depth:"));
        b2.add(Box.createHorizontalStrut(10));
        b2.add(Box.createHorizontalGlue());
        b2.add(depthField);
        add(b2);

        Box b3 = Box.createHorizontalBox();
        b3.add(new JLabel("Use complete rule set: "));
        b3.add(completeRuleSetCheckBox);
        add(b3);

        Box b4 = Box.createHorizontalBox();
        b4.add(new JLabel("Do possible DSEP search: "));
        b4.add(possibleDsepCheckBox);
        add(b4);

        Box b5 = Box.createHorizontalBox();
        b5.add(new JLabel("Max reachable path length: "));
        b5.add(maxReachablePathLengthField);
        add(b5);

        Box b6 = Box.createHorizontalBox();
        b6.add(new JLabel("Use RFCI (complete rule set): "));
        b6.add(RFCI_CheckBox);
        add(b6);

        add(Box.createHorizontalGlue());
    }

    /**
     * @return the getMappings object being edited. (This probably should not be
     * public, but it is needed so that the textfields can edit the model.).
     */
    private Parameters params() {
        return params;
    }
}


