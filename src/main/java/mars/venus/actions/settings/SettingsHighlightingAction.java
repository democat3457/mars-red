package mars.venus.actions.settings;

import mars.Application;
import mars.settings.Settings;
import mars.venus.*;
import mars.venus.actions.VenusAction;
import mars.venus.execute.ExecuteTab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;

/*
Copyright (c) 2003-2009,  Pete Sanderson and Kenneth Vollmar

Developed by Pete Sanderson (psanderson@otterbein.edu)
and Kenneth Vollmar (kenvollmar@missouristate.edu)

Permission is hereby granted, free of charge, to any person obtaining 
a copy of this software and associated documentation files (the 
"Software"), to deal in the Software without restriction, including 
without limitation the rights to use, copy, modify, merge, publish, 
distribute, sublicense, and/or sell copies of the Software, and to 
permit persons to whom the Software is furnished to do so, subject 
to the following conditions:

The above copyright notice and this permission notice shall be 
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, 
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF 
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. 
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR 
ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF 
CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION 
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

(MIT license, http://www.opensource.org/licenses/mit-license.html)
*/

/**
 * Action class for the Settings menu item for text editor settings.
 */
public class SettingsHighlightingAction extends VenusAction {
    private JDialog highlightDialog;

    // NOTE: These must follow same sequence and buttons must follow this sequence too!
    private static final int[] backgroundSettingPositions = {
        Settings.TEXTSEGMENT_HIGHLIGHT_BACKGROUND,
        Settings.TEXTSEGMENT_DELAYSLOT_HIGHLIGHT_BACKGROUND,
        Settings.DATASEGMENT_HIGHLIGHT_BACKGROUND,
        Settings.REGISTER_HIGHLIGHT_BACKGROUND,
        Settings.EVEN_ROW_BACKGROUND,
        Settings.ODD_ROW_BACKGROUND,
    };
    private static final int[] foregroundSettingPositions = {
        Settings.TEXTSEGMENT_HIGHLIGHT_FOREGROUND,
        Settings.TEXTSEGMENT_DELAYSLOT_HIGHLIGHT_FOREGROUND,
        Settings.DATASEGMENT_HIGHLIGHT_FOREGROUND,
        Settings.REGISTER_HIGHLIGHT_FOREGROUND,
        Settings.EVEN_ROW_FOREGROUND,
        Settings.ODD_ROW_FOREGROUND,
    };
    private static final int[] fontSettingPositions = {
        Settings.TEXTSEGMENT_HIGHLIGHT_FONT,
        Settings.TEXTSEGMENT_DELAYSLOT_HIGHLIGHT_FONT,
        Settings.DATASEGMENT_HIGHLIGHT_FONT,
        Settings.REGISTER_HIGHLIGHT_FONT,
        Settings.EVEN_ROW_FONT,
        Settings.ODD_ROW_FONT,
    };

    private JButton[] backgroundButtons;
    private JButton[] foregroundButtons;
    private JButton[] fontButtons;
    private JCheckBox[] defaultCheckBoxes;
    private JLabel[] samples;
    private Color[] currentNondefaultBackground, currentNondefaultForeground;
    private Color[] initialSettingsBackground, initialSettingsForeground;
    private Font[] initialFont, currentFont, currentNondefaultFont;
    private JButton dataHighlightButton, registerHighlightButton;
    private boolean currentDataHighlightSetting, initialDataHighlightSetting;
    private boolean currentRegisterHighlightSetting, initialRegisterHighlightSetting;

    private static final int gridVGap = 2;
    private static final int gridHGap = 2;
    // Tool tips for color buttons
    private static final String SAMPLE_TOOL_TIP_TEXT = "Preview based on background and text color settings";
    private static final String BACKGROUND_TOOL_TIP_TEXT = "Click, to select background color";
    private static final String FOREGROUND_TOOL_TIP_TEXT = "Click, to select text color";
    private static final String FONT_TOOL_TIP_TEXT = "Click, to select text font";
    private static final String DEFAULT_TOOL_TIP_TEXT = "Check, to select default color (disables color select buttons)";
    // Tool tips for the control buttons along the bottom
    public static final String OK_TOOL_TIP_TEXT = "Apply current settings and close dialog";
    public static final String APPLY_TOOL_TIP_TEXT = "Apply current settings now and leave dialog open";
    public static final String REVERT_TOOL_TIP_TEXT = "Reset to initial settings without applying";
    public static final String CANCEL_TOOL_TIP_TEXT = "Close dialog without applying current settings";
    // Tool tips for the data and register highlighting enable/disable controls
    private static final String DATA_HIGHLIGHT_ENABLE_TOOL_TIP_TEXT = "Click, to enable or disable highlighting in Data Segment window";
    private static final String REGISTER_HIGHLIGHT_ENABLE_TOOL_TIP_TEXT = "Click, to enable or disable highlighting in Register windows";
    private static final String fontButtonText = "font";

    /**
     * Create a new SettingsEditorAction.  Has all the GuiAction parameters.
     */
    public SettingsHighlightingAction(VenusUI gui, String name, Icon icon, String description, Integer mnemonic, KeyStroke accel) {
        super(gui, name, icon, description, mnemonic, accel);
    }

    /**
     * When this action is triggered, launch a dialog to view and modify
     * editor settings.
     */
    @Override
    public void actionPerformed(ActionEvent event) {
        highlightDialog = new JDialog(Application.getGUI(), "Runtime Table Highlighting Colors and Fonts", true);
        highlightDialog.setContentPane(buildDialogPanel());
        highlightDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        highlightDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent we) {
                closeDialog();
            }
        });
        highlightDialog.pack();
        highlightDialog.setLocationRelativeTo(Application.getGUI());
        highlightDialog.setVisible(true);
    }

    /**
     * Build the dialog box that appears when menu item is selected.
     */
    private JPanel buildDialogPanel() {
        JPanel contents = new JPanel(new BorderLayout(20, 20));
        contents.setBorder(new EmptyBorder(10, 10, 10, 10));
        JPanel patchesPanel = new JPanel(new GridLayout(backgroundSettingPositions.length, 4, gridVGap, gridHGap));
        currentNondefaultBackground = new Color[backgroundSettingPositions.length];
        currentNondefaultForeground = new Color[backgroundSettingPositions.length];
        initialSettingsBackground = new Color[backgroundSettingPositions.length];
        initialSettingsForeground = new Color[backgroundSettingPositions.length];
        initialFont = new Font[backgroundSettingPositions.length];
        currentFont = new Font[backgroundSettingPositions.length];
        currentNondefaultFont = new Font[backgroundSettingPositions.length];

        backgroundButtons = new JButton[backgroundSettingPositions.length];
        foregroundButtons = new JButton[backgroundSettingPositions.length];
        fontButtons = new JButton[backgroundSettingPositions.length];
        defaultCheckBoxes = new JCheckBox[backgroundSettingPositions.length];
        samples = new JLabel[backgroundSettingPositions.length];
        for (int i = 0; i < backgroundSettingPositions.length; i++) {
            backgroundButtons[i] = new ColorSelectButton();
            foregroundButtons[i] = new ColorSelectButton();
            fontButtons[i] = new JButton(fontButtonText);
            defaultCheckBoxes[i] = new JCheckBox();
            samples[i] = new JLabel(" preview ");
            backgroundButtons[i].addActionListener(new BackgroundChanger(i));
            foregroundButtons[i].addActionListener(new ForegroundChanger(i));
            fontButtons[i].addActionListener(new FontChanger(i));
            defaultCheckBoxes[i].addItemListener(new DefaultChanger(i));
            samples[i].setToolTipText(SAMPLE_TOOL_TIP_TEXT);
            backgroundButtons[i].setToolTipText(BACKGROUND_TOOL_TIP_TEXT);
            foregroundButtons[i].setToolTipText(FOREGROUND_TOOL_TIP_TEXT);
            fontButtons[i].setToolTipText(FONT_TOOL_TIP_TEXT);
            defaultCheckBoxes[i].setToolTipText(DEFAULT_TOOL_TIP_TEXT);
        }

        initializeButtonColors();

        for (int i = 0; i < backgroundSettingPositions.length; i++) {
            patchesPanel.add(backgroundButtons[i]);
            patchesPanel.add(foregroundButtons[i]);
            patchesPanel.add(fontButtons[i]);
            patchesPanel.add(defaultCheckBoxes[i]);
        }

        JPanel descriptionsPanel = new JPanel(new GridLayout(backgroundSettingPositions.length, 1, gridVGap, gridHGap));
        // Note the labels have to match buttons by position...
        descriptionsPanel.add(new JLabel("Text Segment highlighting", SwingConstants.RIGHT));
        descriptionsPanel.add(new JLabel("Text Segment Delay Slot highlighting", SwingConstants.RIGHT));
        descriptionsPanel.add(new JLabel("Data Segment highlighting *", SwingConstants.RIGHT));
        descriptionsPanel.add(new JLabel("Register highlighting *", SwingConstants.RIGHT));
        descriptionsPanel.add(new JLabel("Even row normal", SwingConstants.RIGHT));
        descriptionsPanel.add(new JLabel("Odd row normal", SwingConstants.RIGHT));

        JPanel samplesPanel = new JPanel(new GridLayout(backgroundSettingPositions.length, 1, gridVGap, gridHGap));
        for (JLabel sample : samples) {
            samplesPanel.add(sample);
        }

        JPanel instructions = new JPanel(new FlowLayout(FlowLayout.CENTER));
        // Create fake checkbox for illustration purposes
        JCheckBox illustration = new JCheckBox() {
            @Override
            protected void processMouseEvent(MouseEvent event) {}

            @Override
            protected void processKeyEvent(KeyEvent event) {}
        };
        illustration.setSelected(true);
        instructions.add(illustration);
        instructions.add(new JLabel("= use default colors (disables color selection buttons)"));
        int spacer = 10;
        Box mainArea = Box.createHorizontalBox();
        mainArea.add(Box.createHorizontalGlue());
        mainArea.add(descriptionsPanel);
        mainArea.add(Box.createHorizontalStrut(spacer));
        mainArea.add(Box.createHorizontalGlue());
        mainArea.add(Box.createHorizontalStrut(spacer));
        mainArea.add(samplesPanel);
        mainArea.add(Box.createHorizontalStrut(spacer));
        mainArea.add(Box.createHorizontalGlue());
        mainArea.add(Box.createHorizontalStrut(spacer));
        mainArea.add(patchesPanel);

        contents.add(mainArea, BorderLayout.EAST);
        contents.add(instructions, BorderLayout.NORTH);

        // Control highlighting enable/disable for Data Segment window and Register windows
        JPanel dataRegisterHighlightControl = new JPanel(new GridLayout(2, 1));
        dataHighlightButton = new JButton();
        dataHighlightButton.setText(getHighlightControlText(currentDataHighlightSetting));
        dataHighlightButton.setToolTipText(DATA_HIGHLIGHT_ENABLE_TOOL_TIP_TEXT);
        dataHighlightButton.addActionListener(e -> {
            currentDataHighlightSetting = !currentDataHighlightSetting;
            dataHighlightButton.setText(getHighlightControlText(currentDataHighlightSetting));
        });
        registerHighlightButton = new JButton();
        registerHighlightButton.setText(getHighlightControlText(currentRegisterHighlightSetting));
        registerHighlightButton.setToolTipText(REGISTER_HIGHLIGHT_ENABLE_TOOL_TIP_TEXT);
        registerHighlightButton.addActionListener(e -> {
            currentRegisterHighlightSetting = !currentRegisterHighlightSetting;
            registerHighlightButton.setText(getHighlightControlText(currentRegisterHighlightSetting));
        });
        JPanel dataHighlightPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JPanel registerHighlightPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        dataHighlightPanel.add(new JLabel("* Data Segment highlighting is"));
        dataHighlightPanel.add(dataHighlightButton);
        registerHighlightPanel.add(new JLabel("* Register highlighting is"));
        registerHighlightPanel.add(registerHighlightButton);
        dataRegisterHighlightControl.setBorder(new LineBorder(Color.BLACK));
        dataRegisterHighlightControl.add(dataHighlightPanel);
        dataRegisterHighlightControl.add(registerHighlightPanel);

        // Bottom row - the control buttons for Apply&Close, Apply, Cancel
        Box controlPanel = Box.createHorizontalBox();
        JButton okButton = new JButton("OK");
        okButton.setToolTipText(OK_TOOL_TIP_TEXT);
        okButton.addActionListener(event -> {
            setHighlightingSettings();
            closeDialog();
        });
        JButton applyButton = new JButton("Apply");
        applyButton.setToolTipText(APPLY_TOOL_TIP_TEXT);
        applyButton.addActionListener(e -> setHighlightingSettings());
        JButton resetButton = new JButton("Revert");
        resetButton.setToolTipText(REVERT_TOOL_TIP_TEXT);
        resetButton.addActionListener(e -> resetButtonColors());
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setToolTipText(CANCEL_TOOL_TIP_TEXT);
        cancelButton.addActionListener(e -> closeDialog());
        controlPanel.add(Box.createHorizontalGlue());
        controlPanel.add(okButton);
        controlPanel.add(Box.createHorizontalGlue());
        controlPanel.add(applyButton);
        controlPanel.add(Box.createHorizontalGlue());
        controlPanel.add(cancelButton);
        controlPanel.add(Box.createHorizontalGlue());
        controlPanel.add(resetButton);
        controlPanel.add(Box.createHorizontalGlue());

        JPanel allControls = new JPanel(new GridLayout(2, 1));
        allControls.add(dataRegisterHighlightControl);
        allControls.add(controlPanel);
        contents.add(allControls, BorderLayout.SOUTH);
        return contents;
    }

    private String getHighlightControlText(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }

    /**
     * Called once, upon dialog setup.
     */
    private void initializeButtonColors() {
        Settings settings = Application.getSettings();
        LineBorder lineBorder = new LineBorder(Color.BLACK);
        Color backgroundSetting, foregroundSetting;
        Font fontSetting;
        for (int i = 0; i < backgroundSettingPositions.length; i++) {
            backgroundSetting = settings.getColorSettingByPosition(backgroundSettingPositions[i]);
            foregroundSetting = settings.getColorSettingByPosition(foregroundSettingPositions[i]);
            fontSetting = settings.getFontByPosition(fontSettingPositions[i]);
            backgroundButtons[i].setBackground(backgroundSetting);
            foregroundButtons[i].setBackground(foregroundSetting);
            fontButtons[i].setFont(MonoRightCellRenderer.MONOSPACED_PLAIN_12POINT);
            fontButtons[i].setMargin(new Insets(4, 4, 4, 4));
            initialFont[i] = currentFont[i] = fontSetting;
            currentNondefaultBackground[i] = backgroundSetting;
            currentNondefaultForeground[i] = foregroundSetting;
            currentNondefaultFont[i] = fontSetting;
            initialSettingsBackground[i] = backgroundSetting;
            initialSettingsForeground[i] = foregroundSetting;
            samples[i].setOpaque(true); // otherwise, background color will not be rendered
            samples[i].setBorder(lineBorder);
            samples[i].setBackground(backgroundSetting);
            samples[i].setForeground(foregroundSetting);
            samples[i].setFont(fontSetting);
            boolean usingDefaults = backgroundSetting.equals(settings.getDefaultColorSettingByPosition(backgroundSettingPositions[i]))
                && foregroundSetting.equals(settings.getDefaultColorSettingByPosition(foregroundSettingPositions[i]))
                && fontSetting.equals(settings.getDefaultFontByPosition(fontSettingPositions[i]));
            defaultCheckBoxes[i].setSelected(usingDefaults);
            backgroundButtons[i].setEnabled(!usingDefaults);
            foregroundButtons[i].setEnabled(!usingDefaults);
            fontButtons[i].setEnabled(!usingDefaults);
        }
        currentDataHighlightSetting = initialDataHighlightSetting = settings.highlightDataSegment.get();
        currentRegisterHighlightSetting = initialRegisterHighlightSetting = settings.highlightRegisters.get();
    }

    /**
     * Set the color settings according to current button colors.  Occurs when "Apply" selected.
     */
    private void setHighlightingSettings() {
        Settings settings = Application.getSettings();
        for (int i = 0; i < backgroundSettingPositions.length; i++) {
            settings.setColorSettingByPosition(backgroundSettingPositions[i], backgroundButtons[i].getBackground());
            settings.setColorSettingByPosition(foregroundSettingPositions[i], foregroundButtons[i].getBackground());
            settings.setFontByPosition(fontSettingPositions[i], samples[i].getFont());
        }
        settings.highlightDataSegment.set(currentDataHighlightSetting);
        settings.highlightRegisters.set(currentRegisterHighlightSetting);
        RegistersPane registersPane = Application.getGUI().getRegistersPane();
        ExecuteTab executeTab = Application.getGUI().getMainPane().getExecuteTab();
        registersPane.getRegistersWindow().refresh();
        registersPane.getCoprocessor0Window().refresh();
        registersPane.getCoprocessor1Window().refresh();
        // If a successful assembly has occurred, the various panes will be populated with tables
        // and we want to apply the new settings.  If it has NOT occurred, there are no tables
        // in the Data and Text segment windows so we don't want to disturb them.
        // In the latter case, the component count for the Text segment window is 0 (but is 1
        // for Data segment window).
        if (executeTab.getTextSegmentWindow().getContentPane().getComponentCount() > 0) {
            executeTab.getDataSegmentWindow().updateValues();
            executeTab.getTextSegmentWindow().highlightStepAtPC();
        }
    }

    /**
     * Called when Reset selected.
     */
    private void resetButtonColors() {
        Settings settings = Application.getSettings();
        dataHighlightButton.setText(getHighlightControlText(initialDataHighlightSetting));
        registerHighlightButton.setText(getHighlightControlText(initialRegisterHighlightSetting));
        Color backgroundSetting, foregroundSetting;
        Font fontSetting;
        for (int i = 0; i < backgroundSettingPositions.length; i++) {
            backgroundSetting = initialSettingsBackground[i];
            foregroundSetting = initialSettingsForeground[i];
            fontSetting = initialFont[i];
            backgroundButtons[i].setBackground(backgroundSetting);
            foregroundButtons[i].setBackground(foregroundSetting);
            samples[i].setBackground(backgroundSetting);
            samples[i].setForeground(foregroundSetting);
            samples[i].setFont(fontSetting);
            boolean usingDefaults = backgroundSetting.equals(settings.getDefaultColorSettingByPosition(backgroundSettingPositions[i])) && foregroundSetting.equals(settings.getDefaultColorSettingByPosition(foregroundSettingPositions[i])) && fontSetting.equals(settings.getDefaultFontByPosition(fontSettingPositions[i]));
            defaultCheckBoxes[i].setSelected(usingDefaults);
            backgroundButtons[i].setEnabled(!usingDefaults);
            foregroundButtons[i].setEnabled(!usingDefaults);
            fontButtons[i].setEnabled(!usingDefaults);
        }
    }

    // We're finished with this modal dialog.
    private void closeDialog() {
        highlightDialog.setVisible(false);
        highlightDialog.dispose();
    }

    /////////////////////////////////////////////////////////////////
    //
    //  Class that handles click on the background selection button
    //
    private class BackgroundChanger implements ActionListener {
        private final int position;

        public BackgroundChanger(int pos) {
            position = pos;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JButton button = (JButton) e.getSource();
            Color newColor = JColorChooser.showDialog(null, "Set Background Color", button.getBackground());
            if (newColor != null) {
                button.setBackground(newColor);
                currentNondefaultBackground[position] = newColor;
                samples[position].setBackground(newColor);
            }
        }
    }

    /////////////////////////////////////////////////////////////////
    //
    //  Class that handles click on the foreground selection button
    //
    private class ForegroundChanger implements ActionListener {
        private final int position;

        public ForegroundChanger(int pos) {
            position = pos;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JButton button = (JButton) e.getSource();
            Color newColor = JColorChooser.showDialog(null, "Set Text Color", button.getBackground());
            if (newColor != null) {
                button.setBackground(newColor);
                currentNondefaultForeground[position] = newColor;
                samples[position].setForeground(newColor);
            }
        }
    }

    /////////////////////////////////////////////////////////////////
    //
    //  Class that handles click on the font select button
    //
    private class FontChanger implements ActionListener {
        private final int position;

        public FontChanger(int pos) {
            position = pos;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            FontSettingDialog fontDialog = new FontSettingDialog(null, "Select Text Font", samples[position].getFont());
            Font newFont = fontDialog.showDialog();
            if (newFont != null) {
                samples[position].setFont(newFont);
            }
        }
    }

    /////////////////////////////////////////////////////////////////
    //
    // Class that handles action (check, uncheck) on the Default checkbox.
    //
    private class DefaultChanger implements ItemListener {
        private final int position;

        public DefaultChanger(int pos) {
            position = pos;
        }

        @Override
        public void itemStateChanged(ItemEvent e) {
            // If selected: disable buttons, set their bg values from default setting, set sample bg & fg
            // If deselected: enable buttons, set their bg values from current setting, set sample bg & bg
            Color newBackground;
            Color newForeground;
            Font newFont;
            if (e.getStateChange() == ItemEvent.SELECTED) {
                backgroundButtons[position].setEnabled(false);
                foregroundButtons[position].setEnabled(false);
                fontButtons[position].setEnabled(false);
                newBackground = Application.getSettings().getDefaultColorSettingByPosition(backgroundSettingPositions[position]);
                newForeground = Application.getSettings().getDefaultColorSettingByPosition(foregroundSettingPositions[position]);
                newFont = Application.getSettings().getDefaultFontByPosition(fontSettingPositions[position]);
                currentNondefaultBackground[position] = backgroundButtons[position].getBackground();
                currentNondefaultForeground[position] = foregroundButtons[position].getBackground();
                currentNondefaultFont[position] = samples[position].getFont();
            }
            else {
                backgroundButtons[position].setEnabled(true);
                foregroundButtons[position].setEnabled(true);
                fontButtons[position].setEnabled(true);
                newBackground = currentNondefaultBackground[position];
                newForeground = currentNondefaultForeground[position];
                newFont = currentNondefaultFont[position];
            }
            backgroundButtons[position].setBackground(newBackground);
            foregroundButtons[position].setBackground(newForeground);
            //fontButtons[position].setFont(newFont);
            samples[position].setBackground(newBackground);
            samples[position].setForeground(newForeground);
            samples[position].setFont(newFont);
        }
    }

    ///////////////////////////////////////////////////////////////////
    //
    // Modal dialog to set a font.
    //
    private static class FontSettingDialog extends AbstractFontSettingDialog {
        private boolean resultOK;

        public FontSettingDialog(Frame owner, String title, Font currentFont) {
            super(owner, title, true, currentFont);
        }

        private Font showDialog() {
            resultOK = true;
            // Because dialog is modal, this blocks until user terminates the dialog.
            this.setVisible(true);
            return resultOK ? getFont() : null;
        }

        @Override
        protected void closeDialog() {
            this.setVisible(false);
        }

        private void performOK() {
            resultOK = true;
        }

        private void performCancel() {
            resultOK = false;
        }

        // Control buttons for the dialog.
        @Override
        protected Component buildControlPanel() {
            Box controlPanel = Box.createHorizontalBox();
            JButton okButton = new JButton("OK");
            okButton.addActionListener(e -> {
                performOK();
                closeDialog();
            });
            JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(e -> {
                performCancel();
                closeDialog();
            });
            JButton resetButton = new JButton("Reset");
            resetButton.addActionListener(e -> reset());
            controlPanel.add(Box.createHorizontalGlue());
            controlPanel.add(okButton);
            controlPanel.add(Box.createHorizontalGlue());
            controlPanel.add(cancelButton);
            controlPanel.add(Box.createHorizontalGlue());
            controlPanel.add(resetButton);
            controlPanel.add(Box.createHorizontalGlue());
            return controlPanel;
        }

        // required by Abstract super class but not used here.
        @Override
        protected void apply(Font font) {
        }
    }
}