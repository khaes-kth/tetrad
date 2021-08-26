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

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;


/**
 * Edits the parameters for simulating data from SEMs.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class LinearSemImParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The parameters object being edited.
     */
    private Parameters params = null;

    /**
     * Constructs a dialog to edit the given workbench SEM simulation
     * getMappings object.
     */
    public LinearSemImParamsEditor() {
    }

    public void setParams(Parameters params) {
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = params;
    }

    public void setParentModels(Object[] parentModels) {
        // Do nothing.
    }

    /**
     * Constructs the Gui used to edit properties; called from each constructor.
     * Constructs labels and text fields for editing each property and adds
     * appropriate listeners.
     */
    public void setup() {
        setLayout(new BorderLayout());

//        final JCheckBox randomEveryTime = new JCheckBox();
//        randomEveryTime.setSelected(!params.getBoolean("retainPreviousValues", false));
        DecimalFormat decimalFormat = new DecimalFormat("0.0######");

        final DoubleTextField coefLowField = new DoubleTextField(params.getDouble(Params.COEF_LOW),
                6, decimalFormat);

        coefLowField.setFilter((value, oldValue) -> {
            try {
                getParams().set(Params.COEF_LOW, value);
                return value;
            }
            catch (IllegalArgumentException e) {
                return oldValue;
            }
        });


        final DoubleTextField coefHighField = new DoubleTextField(params.getDouble(Params.COEF_HIGH),
                6, decimalFormat);

        coefHighField.setFilter((value, oldValue) -> {
            try {
                getParams().set(Params.COEF_HIGH, value);
                return value;
            }
            catch (IllegalArgumentException e) {
                return oldValue;
            }
        });

        final DoubleTextField covLowField = new DoubleTextField(params.getDouble(Params.COV_LOW),
                6, decimalFormat);

        covLowField.setFilter((value, oldValue) -> {
            try {
                params.set(Params.COV_LOW, value);
                return value;
            }
            catch (IllegalArgumentException e) {
                return oldValue;
            }
        });

        final DoubleTextField covHighField = new DoubleTextField(params.getDouble(Params.COV_HIGH, 0.0),
                6, decimalFormat);

        covHighField.setFilter((value, oldValue) -> {
            try {
                params.set(Params.COV_HIGH, value);
                return value;
            }
            catch (IllegalArgumentException e) {
                return oldValue;
            }
        });

        final DoubleTextField varLowField = new DoubleTextField(params.getDouble(Params.VAR_LOW, 1),
                6, decimalFormat);

        varLowField.setFilter((value, oldValue) -> {
            try {
                params.set(Params.VAR_LOW, value);
                return value;
            }
            catch (IllegalArgumentException e) {
                return oldValue;
            }
        });

        final DoubleTextField varHighField = new DoubleTextField(params.getDouble(Params.VAR_HIGH, 3),
                6, decimalFormat);

        varHighField.setFilter((value, oldValue) -> {
            try {
                params.set(Params.VAR_HIGH, value);
                return value;
            }
            catch (IllegalArgumentException e) {
                return oldValue;
            }
        });

        final JCheckBox coefSymmetric = new JCheckBox("Symmetric about zero.");
        final JCheckBox covSymmetric = new JCheckBox("Symmetric about zero.");

        coefSymmetric.setSelected(params.getBoolean(Params.COEF_SYMMETRIC, true));
        covSymmetric.setSelected(params.getBoolean(Params.COV_SYMMETRIC, true));

        coefSymmetric.addActionListener(e -> {
            JCheckBox checkBox = (JCheckBox) e.getSource();
            params.set(Params.COEF_SYMMETRIC, checkBox.isSelected());
        });

        covSymmetric.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JCheckBox checkBox = (JCheckBox) e.getSource();
                params.set("covSymmetric", checkBox.isSelected());
            }
        });

        // continue workbench construction.
        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel(
                "Unfixed parameter values for this SEM IM are drawn as follows:"));
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);
        b1.add(Box.createVerticalStrut(5));

        Box b4a = Box.createHorizontalBox();
        b4a.add(new JLabel("Coefficient values are drawn from "));
        b4a.add(new BigLabel("("));
        b4a.add(coefLowField);
        b4a.add(new BigLabel(", "));
        b4a.add(coefHighField);
        b4a.add(new BigLabel(") "));
        b4a.add(coefSymmetric);
        b4a.add(Box.createHorizontalGlue());
        b1.add(b4a);

        Box b4b = Box.createHorizontalBox();
        b4b.add(new JLabel("Error covariance values are drawn from "));
        b4b.add(new BigLabel("("));
        b4b.add(covLowField);
        b4b.add(new BigLabel(", "));
        b4b.add(covHighField);
        b4b.add(new BigLabel(") "));
        b4b.add(covSymmetric);
        b4b.add(Box.createHorizontalGlue());
        b1.add(b4b);

        Box b4c = Box.createHorizontalBox();
        b4c.add(new JLabel("Error standard deviation values are drawn from "));
        b4c.add(new BigLabel("("));
        b4c.add(varLowField);
        b4c.add(new BigLabel(", "));
        b4c.add(varHighField);
        b4c.add(new BigLabel(")"));
        b4c.add(new JLabel("."));
        b4c.add(Box.createHorizontalGlue());
        b1.add(b4c);

        b1.add(Box.createHorizontalGlue());
        add(b1, BorderLayout.CENTER);
        setBorder(new EmptyBorder(5, 5, 5, 5));
    }

    public boolean mustBeShown() {
        return false;
    }

    /**
     * @return the getMappings object being edited. (This probably should not be
     * public, but it is needed so that the textfields can edit the model.)
     */
    private synchronized Parameters getParams() {
        return this.params;
    }

    final static class BigLabel extends JLabel {
        private static final Font FONT = new Font("Dialog", Font.BOLD, 20);

        public BigLabel(String text) {
            super(text);
            setFont(FONT);
        }
    }
}


