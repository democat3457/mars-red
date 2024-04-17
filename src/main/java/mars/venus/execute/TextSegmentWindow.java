package mars.venus.execute;

import mars.Application;
import mars.ProgramStatement;
import mars.settings.Settings;
import mars.mips.hardware.*;
import mars.simulator.Simulator;
import mars.simulator.SimulatorNotice;
import mars.util.Binary;
import mars.util.EditorFont;
import mars.venus.MonoRightCellRenderer;
import mars.venus.NumberDisplayBaseChooser;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.*;
import java.util.List;

/*
Copyright (c) 2003-2007,  Pete Sanderson and Kenneth Vollmar

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
 * Creates the Text Segment window in the Execute tab of the UI.
 *
 * @author Team JSpim
 */
public class TextSegmentWindow extends JInternalFrame implements Observer {
    private final JPanel programArgumentsPanel; // DPS 17-July-2008
    private final JTextField programArgumentsTextField; // DPS 17-July-2008
    private static final int PROGRAM_ARGUMENT_TEXTFIELD_COLUMNS = 40;
    private JTable table;
    private JScrollPane tableScroller;
    private Object[][] data;
    /*
     * Maintain an int array of code addresses in parallel with ADDRESS_COLUMN,
     * to speed model-row -> text-address mapping.  Maintain a Hashtable of
     * (text-address, model-row) pairs to speed text-address -> model-row mapping.
     * The former is used for breakpoints and changing display base (e.g. base 10
     * to 16); the latter is used for highlighting.  Both structures will remain
     * consistent once set up, since address column is not editable.
     */
    private int[] intAddresses; // Index is table model row, value is text address
    private Hashtable<Integer, Integer> addressRows; // Key is text address, value is table model row
    private Hashtable<Integer, ModifiedCode> executeMods; // Key is table model row, value is original code, basic, source.
    private final Container contentPane;
    private TextTableModel tableModel;
    private boolean codeHighlighting;
    private boolean breakpointsEnabled; // Added 31 Dec 2009
    private int highlightAddress;
    private TableModelListener tableModelListener;
    private boolean inDelaySlot; // Added 25 June 2007

    private static final int BREAKPOINT_COLUMN = 0;
    private static final int ADDRESS_COLUMN = 1;
    private static final int CODE_COLUMN = 2;
    private static final int BASIC_COLUMN = 3;
    private static final int SOURCE_COLUMN = 4;
    private static final String[] COLUMN_NAMES = {
        "Bkpt",
        "Address",
        "Code",
        "Basic",
        "Source",
    };

    /**
     * Displayed in the Basic and Source columns if existing code is overwritten
     * using the self-modifying code feature.
     */
    private static final String MODIFIED_CODE_MARKER = " ------ ";

    /**
     * Constructor, sets up a new JInternalFrame.
     */
    public TextSegmentWindow() {
        super("Text Segment", true, false, true, false);
        this.setFrameIcon(null);

        Simulator.getInstance().addObserver(this);
        Application.getSettings().addObserver(this);
        contentPane = this.getContentPane();
        codeHighlighting = true;
        breakpointsEnabled = true;
        programArgumentsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        programArgumentsPanel.add(new JLabel("Program Arguments: "));
        programArgumentsTextField = new JTextField(PROGRAM_ARGUMENT_TEXTFIELD_COLUMNS);
        programArgumentsTextField.setToolTipText("Arguments provided to program at runtime via $a0 (argc) and $a1 (argv)");
        programArgumentsPanel.add(programArgumentsTextField);
    }

    /**
     * Method to be called once the user compiles the program.
     * Should convert the lines of code over to the table rows and columns.
     */
    public void setupTable() {
        int addressBase = Application.getGUI().getMainPane().getExecuteTab().getAddressDisplayBase();
        codeHighlighting = true;
        breakpointsEnabled = true;
        List<ProgramStatement> statements = Application.program.getMachineStatements();
        data = new Object[statements.size()][COLUMN_NAMES.length];
        intAddresses = new int[data.length];
        addressRows = new Hashtable<>(data.length);
        executeMods = new Hashtable<>(data.length);
        // Get highest source line number to determine # of leading spaces so line numbers will vertically align.
        // In multi-file situation, this will not necessarily be the last line b/c statements contains
        // source lines from all files.  DPS 3-Oct-10
        int maxSourceLineNumber = 0;
        for (ProgramStatement statement : statements) {
            if (statement.getSourceLine() > maxSourceLineNumber) {
                maxSourceLineNumber = statement.getSourceLine();
            }
        }
        int maxSourceLineDigits = Integer.toUnsignedString(maxSourceLineNumber).length();
        int leadingSpaces;
        int lastLine = -1;
        for (int row = 0; row < statements.size(); row++) {
            ProgramStatement statement = statements.get(row);
            intAddresses[row] = statement.getAddress();
            addressRows.put(intAddresses[row], row);
            data[row][BREAKPOINT_COLUMN] = Boolean.FALSE;
            data[row][ADDRESS_COLUMN] = NumberDisplayBaseChooser.formatUnsignedInteger(statement.getAddress(), addressBase);
            data[row][CODE_COLUMN] = NumberDisplayBaseChooser.formatNumber(statement.getBinaryStatement(), 16);
            data[row][BASIC_COLUMN] = statement.getPrintableBasicAssemblyStatement();
            String sourceString = "";
            if (!statement.getSource().isEmpty()) {
                String sourceLineString = Integer.toUnsignedString(statement.getSourceLine());
                leadingSpaces = maxSourceLineDigits - sourceLineString.length();
                String lineNumber = "          ".substring(0, leadingSpaces) + sourceLineString + ": ";
                if (statement.getSourceLine() == lastLine) {
                    lineNumber = "          ".substring(0, maxSourceLineDigits) + "  ";
                }
                sourceString = lineNumber + EditorFont.substituteSpacesForTabs(statement.getSource());
            }
            data[row][SOURCE_COLUMN] = sourceString;
            lastLine = statement.getSourceLine();
        }
        contentPane.removeAll();
        tableModel = new TextTableModel(data);
        if (tableModelListener != null) {
            tableModel.addTableModelListener(tableModelListener);
            // Initialize listener
            tableModel.fireTableDataChanged();
        }
        table = new TextSegmentTable(tableModel);

        // Prevents cells in row from being highlighted when user clicks on breakpoint checkbox
        table.setRowSelectionAllowed(false);

        table.getColumnModel().getColumn(BREAKPOINT_COLUMN).setMinWidth(40);
        table.getColumnModel().getColumn(BREAKPOINT_COLUMN).setMaxWidth(50);
        table.getColumnModel().getColumn(BREAKPOINT_COLUMN).setPreferredWidth(40);
        table.getColumnModel().getColumn(BREAKPOINT_COLUMN).setCellRenderer(new CheckBoxTableCellRenderer());

        CodeCellRenderer codeCellRenderer = new CodeCellRenderer();

        table.getColumnModel().getColumn(ADDRESS_COLUMN).setMinWidth(80);
        table.getColumnModel().getColumn(ADDRESS_COLUMN).setMaxWidth(100);
        table.getColumnModel().getColumn(ADDRESS_COLUMN).setPreferredWidth(90);
        table.getColumnModel().getColumn(ADDRESS_COLUMN).setCellRenderer(codeCellRenderer);

        table.getColumnModel().getColumn(CODE_COLUMN).setMinWidth(80);
        table.getColumnModel().getColumn(CODE_COLUMN).setMaxWidth(100);
        table.getColumnModel().getColumn(CODE_COLUMN).setPreferredWidth(90);
        table.getColumnModel().getColumn(CODE_COLUMN).setCellRenderer(codeCellRenderer);

        table.getColumnModel().getColumn(BASIC_COLUMN).setMinWidth(120);
        table.getColumnModel().getColumn(BASIC_COLUMN).setPreferredWidth(120);
        table.getColumnModel().getColumn(BASIC_COLUMN).setCellRenderer(codeCellRenderer);

        table.getColumnModel().getColumn(SOURCE_COLUMN).setMinWidth(120);
        table.getColumnModel().getColumn(SOURCE_COLUMN).setPreferredWidth(400);
        table.getColumnModel().getColumn(SOURCE_COLUMN).setCellRenderer(codeCellRenderer);

        // Re-order columns according to current preference...
        reorderColumns();
        // Add listener to catch column re-ordering for updating settings.
        table.getColumnModel().addColumnModelListener(new MyTableColumnMovingListener());

        tableScroller = new JScrollPane(table, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        contentPane.add(tableScroller);
        if (Application.getSettings().useProgramArguments.get()) {
            addProgramArgumentsPanel();
        }

        deleteAsTextSegmentObserver();
        if (Application.getSettings().selfModifyingCodeEnabled.get()) {
            addAsTextSegmentObserver();
        }
    }

    /**
     * Get program arguments from text field in south border of text segment window.
     *
     * @return String containing program arguments
     */
    // Support for program arguments added DPS 17-July-2008
    public String getProgramArguments() {
        return programArgumentsTextField.getText();
    }

    public void addProgramArgumentsPanel() {
        // Don't add it if text segment window blank (file closed or no assemble yet)
        if (contentPane != null && contentPane.getComponentCount() > 0) {
            contentPane.add(programArgumentsPanel, BorderLayout.NORTH);
            contentPane.validate();
        }
    }

    public void removeProgramArgumentsPanel() {
        if (contentPane != null) {
            contentPane.remove(programArgumentsPanel);
            contentPane.validate();
        }
    }

    /**
     * Remove all components.
     */
    public void clearWindow() {
        contentPane.removeAll();
    }

    /**
     * Assign listener to Table model.  Used for breakpoints, since that is the only editable
     * column in the table.  Since table model objects are transient (get a new one with each
     * successful assemble), this method will simply keep the identity of the listener then
     * add it as a listener each time a new table model object is created.  Limit 1 listener.
     */
    public void registerTableModelListener(TableModelListener tml) {
        tableModelListener = tml;
    }

    /**
     * Redisplay the addresses.  This should only be done when address display base is
     * modified (e.g. between base 16 hex and base 10 dec).
     */
    public void updateCodeAddresses() {
        if (contentPane.getComponentCount() == 0) {
            // No content to change
            return;
        }
        int addressBase = Application.getGUI().getMainPane().getExecuteTab().getAddressDisplayBase();
        String formattedAddress;
        for (int i = 0; i < intAddresses.length; i++) {
            formattedAddress = NumberDisplayBaseChooser.formatUnsignedInteger(intAddresses[i], addressBase);
            table.getModel().setValueAt(formattedAddress, i, ADDRESS_COLUMN);
        }
    }

    /**
     * Redisplay the basic statements.  This should only be done when address or value display base is
     * modified (e.g. between base 16 hex and base 10 dec).
     */
    public void updateBasicStatements() {
        if (contentPane.getComponentCount() == 0) {
            // No content to change
            return;
        }
        List<ProgramStatement> statements = Application.program.getMachineStatements();
        for (int row = 0; row < statements.size(); row++) {
            // Loop has been extended to cover self-modifying code.  If code at this memory location has been
            // modified at runtime, construct a ProgramStatement from the current address and binary code
            // then display its basic code.  DPS 11-July-2013
            if (executeMods.get(row) == null) {
                // Not modified, so use original logic
                ProgramStatement statement = statements.get(row);
                table.getModel().setValueAt(statement.getPrintableBasicAssemblyStatement(), row, BASIC_COLUMN);
            }
            else {
                try {
                    ProgramStatement statement = new ProgramStatement(Binary.stringToInt((String) table.getModel().getValueAt(row, CODE_COLUMN)), Binary.stringToInt((String) table.getModel().getValueAt(row, ADDRESS_COLUMN)));
                    table.getModel().setValueAt(statement.getPrintableBasicAssemblyStatement(), row, BASIC_COLUMN);
                }
                catch (NumberFormatException exception) {
                    // Should never happen, but just in case...
                    table.getModel().setValueAt("", row, BASIC_COLUMN);
                }
            }
        }
    }

    /**
     * Required by Observer interface.  Called when notified by an Observable that we are registered with.
     * The Observable here is a delegate of the Memory object, which lets us know of memory operations.
     * More precisely, memory operations only in the text segment, since that is the only range of
     * addresses we're registered for.  And we're only interested in write operations.
     *
     * @param observable The Observable object who is notifying us
     * @param obj        Auxiliary object with additional information.
     */
    @Override
    public void update(Observable observable, Object obj) {
        if (observable == Simulator.getInstance()) {
            SimulatorNotice notice = (SimulatorNotice) obj;
            if (notice.action() == SimulatorNotice.SIMULATOR_START) {
                // Simulated MIPS execution starts.  Respond to text segment changes only if self-modifying code
                // enabled.  I commented out conditions that would further limit it to running in timed or stepped mode.
                // Seems reasonable for text segment display to be accurate in cases where existing code is overwritten
                // even when running at unlimited speed.  DPS 10-July-2013
                deleteAsTextSegmentObserver();
                if (Application.getSettings().selfModifyingCodeEnabled.get()) { // && (notice.getRunSpeed() != RunSpeedPanel.UNLIMITED_SPEED || notice.getMaxSteps()==1)) {
                    addAsTextSegmentObserver();
                }
            }
        }
        else if (observable == Application.getSettings()) {
            deleteAsTextSegmentObserver();
            if (Application.getSettings().selfModifyingCodeEnabled.get()) {
                addAsTextSegmentObserver();
            }
        }
        else if (obj instanceof MemoryAccessNotice access) {
            // NOTE: observable != Memory.getInstance() because Memory class delegates notification duty.
            // This will occur only if running program has written to text segment (self-modifying code)
            if (access.getAccessType() == AccessNotice.WRITE) {
                int address = access.getAddress();
                int value = access.getValue();
                String strValue = Binary.intToHexString(access.getValue());
                String strBasic = MODIFIED_CODE_MARKER;
                String strSource = MODIFIED_CODE_MARKER;
                // Translate the address into table model row and modify the values in that row accordingly.
                int row;
                try {
                    row = findRowForAddress(address);
                }
                catch (IllegalArgumentException exception) {
                    // Address modified is outside the range of original program, ignore
                    return;
                }
                ModifiedCode modification = executeMods.get(row);
                if (modification == null) {
                    // Not already modified and new code is same as original, so do nothing
                    if (tableModel.getValueAt(row, CODE_COLUMN).equals(strValue)) {
                        return;
                    }
                    modification = new ModifiedCode(row, tableModel.getValueAt(row, CODE_COLUMN), tableModel.getValueAt(row, BASIC_COLUMN), tableModel.getValueAt(row, SOURCE_COLUMN));
                    executeMods.put(row, modification);
                    // Make a ProgramStatement and get basic code to display in BASIC_COLUMN
                    strBasic = new ProgramStatement(value, address).getPrintableBasicAssemblyStatement();
                }
                else {
                    // If restored to original value, restore the basic and source
                    // This will be the case upon backstepping.
                    if (modification.code().equals(strValue)) {
                        strBasic = (String) modification.basic();
                        strSource = (String) modification.source();
                        // Remove from executeMods since we are back to original
                        executeMods.remove(row);
                    }
                    else {
                        // Make a ProgramStatement and get basic code to display in BASIC_COLUMN
                        strBasic = new ProgramStatement(value, address).getPrintableBasicAssemblyStatement();
                    }
                }
                // For the code column, we don't want to do the following:
                //       tableModel.setValueAt(strValue,  row, CODE_COLUMN)
                // because that method will write to memory using Memory.setRawWord() which will
                // trigger notification to observers, which brings us back to here!!!  Infinite
                // indirect recursion results.  Neither fun nor productive.  So what happens is
                // this: (1) change to memory cell causes setValueAt() to be automatically be
                // called.  (2) it updates the memory cell which in turn notifies us which invokes
                // the update() method - the method we're in right now.  All we need to do here is
                // update the table model then notify the controller/view to update its display.
                data[row][CODE_COLUMN] = strValue;
                tableModel.fireTableCellUpdated(row, CODE_COLUMN);
                // The other columns do not present a problem since they are not editable by user.
                tableModel.setValueAt(strBasic, row, BASIC_COLUMN);
                tableModel.setValueAt(strSource, row, SOURCE_COLUMN);
                // Let's update the value displayed in the DataSegmentWindow too.  But it only observes memory while
                // the MIPS program is running, and even then only in timed or step mode.  There are good reasons
                // for that.  So we'll pretend to be Memory observable and send it a fake memory write update.
                try {
                    Application.getGUI().getMainPane().getExecuteTab().getDataSegmentWindow().update(Memory.getInstance(), new MemoryAccessNotice(AccessNotice.WRITE, address, value));
                }
                catch (Exception e) {
                    // Not sure if anything bad can happen in this sequence, but if anything does we can let it go.
                }
            }
        }
    }

    /**
     * Called by RunResetAction to restore display of any table rows that were
     * overwritten due to self-modifying code feature.
     */
    public void resetModifiedSourceCode() {
        if (executeMods != null && !executeMods.isEmpty()) {
            for (Enumeration<ModifiedCode> elements = executeMods.elements(); elements.hasMoreElements(); ) {
                ModifiedCode mc = elements.nextElement();
                tableModel.setValueAt(mc.code(), mc.row(), CODE_COLUMN);
                tableModel.setValueAt(mc.basic(), mc.row(), BASIC_COLUMN);
                tableModel.setValueAt(mc.source(), mc.row(), SOURCE_COLUMN);
            }
            executeMods.clear();
        }
    }

    /**
     * Return code address as an int, for the specified row of the table.  This should only
     * be used by the code renderer so I will not verify row.
     */
    int getIntCodeAddressAtRow(int row) {
        return intAddresses[row];
    }

    /**
     * Returns number of breakpoints currently set.
     *
     * @return number of current breakpoints
     */
    public int getBreakpointCount() {
        int breakpointCount = 0;
        for (Object[] datum : data) {
            if ((Boolean) datum[BREAKPOINT_COLUMN]) {
                breakpointCount++;
            }
        }
        return breakpointCount;
    }

    /**
     * Returns array of current breakpoints, each represented by a MIPS program counter address.
     * These are stored in the BREAK_COLUMN of the table model.
     *
     * @return int array of breakpoints, sorted by PC address, or null if there are none.
     */
    public int[] getSortedBreakPointsArray() {
        int breakpointCount = getBreakpointCount();
        if (breakpointCount == 0 || !breakpointsEnabled) { // added second condition 31-dec-09 DPS
            return null;
        }
        int[] breakpoints = new int[breakpointCount];
        breakpointCount = 0;
        for (int i = 0; i < data.length; i++) {
            if ((Boolean) data[i][BREAKPOINT_COLUMN]) {
                breakpoints[breakpointCount++] = intAddresses[i];
            }
        }
        Arrays.sort(breakpoints);
        return breakpoints;
    }

    /**
     * Clears all breakpoints that have been set since last assemble, and
     * updates the display of the breakpoint column.
     */
    public void clearAllBreakpoints() {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if ((Boolean) data[i][BREAKPOINT_COLUMN]) {
                // must use this method to assure display updated and listener notified
                tableModel.setValueAt(Boolean.FALSE, i, BREAKPOINT_COLUMN);
            }
        }
        // Handles an obscure situation: if you click to set some breakpoints then "immediately" clear them
        // all using the shortcut (CTRL-K), the last checkmark set is not removed even though the breakpoint
        // is removed (tableModel.setValueAt(Boolean.FALSE, i, BREAK_COLUMN)) and all the other checkmarks
        // are removed.  The checkmark remains although if you subsequently run the program it will blow
        // through because the data model cell really has been cleared (contains false).  Occurs only when
        // the last checked breakpoint check box still has the "focus".  There is but one renderer and editor
        // per column.  Getting the renderer and setting it "setSelected(false)" will not work.  You have
        // to get the editor instead.  (PS, 7 Aug 2006)
        ((JCheckBox) ((DefaultCellEditor) table.getCellEditor(0, BREAKPOINT_COLUMN)).getComponent()).setSelected(false);
    }

    /**
     * Highlights the source code line whose address matches the current
     * program counter value.  This is used for stepping through code
     * execution and when reaching breakpoints.
     */
    public void highlightStepAtPC() {
        highlightStepAtAddress(RegisterFile.getProgramCounter(), false);
    }

    /**
     * Highlights the source code line whose address matches the current
     * program counter value.  This is used for stepping through code
     * execution and when reaching breakpoints.
     *
     * @param inDelaySlot Set true if delayed branching is enabled and the
     *                    instruction at this address is executing in the delay slot, false
     *                    otherwise.
     */
    public void highlightStepAtPC(boolean inDelaySlot) {
        highlightStepAtAddress(RegisterFile.getProgramCounter(), inDelaySlot);
    }

    /**
     * Highlights the source code line whose address matches the given
     * text segment address.
     *
     * @param address text segment address of instruction to be highlighted.
     */
    public void highlightStepAtAddress(int address) {
        highlightStepAtAddress(address, false);
    }

    /**
     * Highlights the source code line whose address matches the given
     * text segment address.
     *
     * @param address     Text segment address of instruction to be highlighted.
     * @param inDelaySlot Set true if delayed branching is enabled and the
     *                    instruction at this address is executing in the delay slot, false
     *                    otherwise.
     */
    public void highlightStepAtAddress(int address, boolean inDelaySlot) {
        highlightAddress = address;
        // Scroll if necessary to assure highlighted row is visible.
        int row;
        try {
            row = findRowForAddress(address);
        }
        catch (IllegalArgumentException exception) {
            return;
        }
        table.scrollRectToVisible(table.getCellRect(row, 0, true));
        this.inDelaySlot = inDelaySlot;// Added 25 June 2007
        // Trigger highlighting, which is done by the column's cell renderer.
        // IMPLEMENTATION NOTE: Pretty crude implementation; mark all rows
        // as changed so assure that the previously highlighted row is
        // unhighlighted.  Would be better to keep track of previous row
        // then fire two events: one for it and one for the new row.
        table.tableChanged(new TableModelEvent(tableModel));
        //this.inDelaySlot = false;// Added 25 June 2007
    }

    /**
     * Used to enable or disable source code highlighting.  If true (normally while
     * stepping through execution) then MIPS statement at current program counter
     * is highlighted.  The code column's cell renderer tests this variable.
     *
     * @param highlightSetting true to enable highlighting, false to disable.
     */
    public void setCodeHighlighting(boolean highlightSetting) {
        codeHighlighting = highlightSetting;
    }

    /**
     * Get code highlighting status.
     *
     * @return true if code highlighting currently enabled, false otherwise.
     */
    public boolean getCodeHighlighting() {
        return codeHighlighting;
    }

    /**
     * If any steps are highlighted, this erases the highlighting.
     */
    public void unhighlightAllSteps() {
        boolean saved = this.getCodeHighlighting();
        this.setCodeHighlighting(false);
        table.tableChanged(new TableModelEvent(tableModel, 0, data.length - 1, ADDRESS_COLUMN));
        table.tableChanged(new TableModelEvent(tableModel, 0, data.length - 1, CODE_COLUMN));
        table.tableChanged(new TableModelEvent(tableModel, 0, data.length - 1, BASIC_COLUMN));
        table.tableChanged(new TableModelEvent(tableModel, 0, data.length - 1, SOURCE_COLUMN));
        this.setCodeHighlighting(saved);
    }

    /**
     * Scroll the viewport so the step (table row) at the given text segment address
     * is visible, vertically centered if possible, and selected.
     * Developed July 2007 for new feature that shows source code step where
     * label is defined when that label is clicked on in the Label Window.
     *
     * @param address text segment address of source code step.
     */
    public void selectStepAtAddress(int address) {
        int addressRow;
        try {
            addressRow = findRowForAddress(address);
        }
        catch (IllegalArgumentException e) {
            return;
        }
        // Scroll to assure desired row is centered in view port.
        int addressSourceColumn = table.convertColumnIndexToView(SOURCE_COLUMN);
        Rectangle sourceCell = table.getCellRect(addressRow, addressSourceColumn, true);
        double cellHeight = sourceCell.getHeight();
        double viewHeight = tableScroller.getViewport().getExtentSize().getHeight();
        int numberOfVisibleRows = (int) (viewHeight / cellHeight);
        int newViewPositionY = Math.max((int) ((addressRow - (numberOfVisibleRows / 2)) * cellHeight), 0);
        tableScroller.getViewport().setViewPosition(new Point(0, newViewPositionY));
        // Select the source code cell for this row by generating a fake Mouse Pressed event
        // and explicitly invoking the table's mouse listener.
        MouseEvent fakeMouseEvent = new MouseEvent(table, MouseEvent.MOUSE_PRESSED, new Date().getTime(), MouseEvent.BUTTON1_DOWN_MASK, (int) sourceCell.getX() + 1, (int) sourceCell.getY() + 1, 1, false);
        MouseListener[] mouseListeners = table.getMouseListeners();
        for (MouseListener mouseListener : mouseListeners) {
            mouseListener.mousePressed(fakeMouseEvent);
        }
    }

    /**
     * Enable or disable all items in the Breakpoints column.
     */
    public void toggleBreakpoints() {
        // Already programmed to toggle by clicking on column header, so we'll create
        // a fake mouse event with coordinates on that header then generate the fake
        // event on its mouse listener.
        Rectangle rect = ((TextSegmentTable) table).getRectForColumnIndex(BREAKPOINT_COLUMN);
        MouseEvent fakeMouseEvent = new MouseEvent(table, MouseEvent.MOUSE_CLICKED, new Date().getTime(), MouseEvent.BUTTON1_DOWN_MASK, (int) rect.getX(), (int) rect.getY(), 1, false);
        MouseListener[] mouseListeners = ((TextSegmentTable) table).tableHeader.getMouseListeners();
        for (MouseListener mouseListener : mouseListeners) {
            mouseListener.mouseClicked(fakeMouseEvent);
        }
    }

    /**
     * Little convenience method to add this as observer of text segment
     */
    private void addAsTextSegmentObserver() {
        try {
            Memory.getInstance().addObserver(this, Memory.textBaseAddress, Memory.dataSegmentBaseAddress);
        }
        catch (AddressErrorException aee) {
            // No action
        }
    }

    /**
     * Little convenience method to remove this as observer of text segment
     */
    private void deleteAsTextSegmentObserver() {
        Memory.getInstance().deleteObserver(this);
    }

    /**
     * Re-order the Text segment columns according to saved preferences.
     */
    private void reorderColumns() {
        TableColumnModel oldModel = table.getColumnModel();
        TableColumnModel newModel = new DefaultTableColumnModel();
        // Apply ordering only if correct number of columns.
        Integer[] savedColumnOrder = getSavedColumnOrder();
        if (savedColumnOrder.length == table.getColumnCount()) {
            for (int column : savedColumnOrder) {
                newModel.addColumn(oldModel.getColumn(column));
            }
            table.setColumnModel(newModel);
        }
    }

    private Integer[] getSavedColumnOrder() {
        String columnOrder = Application.getSettings().textSegmentColumnOrder.get();
        return Arrays.stream(columnOrder.split("\\s+"))
            .map(Integer::valueOf)
            .toArray(Integer[]::new);
    }

    /**
     * Helper method to find the table row corresponding to the given
     * text segment address.  This method is called by
     * a couple different public methods.
     *
     * @param address The address to find the row for.
     * @return The table row corresponding to this address.
     */
    private int findRowForAddress(int address) throws IllegalArgumentException {
        try {
            return addressRows.get(address);
        }
        catch (NullPointerException e) {
            throw new IllegalArgumentException(); // Address not found in map
        }
    }

    /**
     * Inner class to implement the Table model for this JTable.
     */
    private static class TextTableModel extends AbstractTableModel {
        private final Object[][] data;

        public TextTableModel(Object[][] data) {
            this.data = data;
        }

        @Override
        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }

        @Override
        public int getRowCount() {
            return data.length;
        }

        @Override
        public String getColumnName(int col) {
            return COLUMN_NAMES[col];
        }

        @Override
        public Object getValueAt(int row, int col) {
            return data[row][col];
        }

        /**
         * JTable uses this method to determine the default renderer/
         * editor for each cell.  If we didn't implement this method,
         * then the break column would contain text ("true"/"false"),
         * rather than a check box.
         */
        @Override
        public Class<?> getColumnClass(int col) {
            return getValueAt(0, col).getClass();
        }

        /**
         * Only Column #1, the Breakpoint, can be edited.
         */
        @Override
        public boolean isCellEditable(int row, int col) {
            // Note that the data/cell address is constant, no matter where the cell appears onscreen.
            return col == BREAKPOINT_COLUMN || (col == CODE_COLUMN && Application.getSettings().selfModifyingCodeEnabled.get());
        }

        /**
         * Set cell contents in the table model. Overrides inherited empty method.
         * Straightforward process except for the Code column.
         */
        @Override
        public void setValueAt(Object value, int row, int col) {
            if (col != CODE_COLUMN) {
                data[row][col] = value;
                fireTableCellUpdated(row, col);
                return;
            }
            // Handle changes in the Code column
            int address = 0;
            if (value.equals(data[row][col])) {
                return;
            }
            int intValue;
            try {
                intValue = Binary.stringToInt((String) value);
            }
            catch (NumberFormatException exception) {
                data[row][col] = "INVALID";
                fireTableCellUpdated(row, col);
                return;
            }
            // Calculate address from row and column
            try {
                address = Binary.stringToInt((String) data[row][ADDRESS_COLUMN]);
            }
            catch (NumberFormatException exception) {
                // Can't really happen since memory addresses are completely under
                // the control of the software.
            }
            // Assures that if changed during MIPS program execution, the update will
            // occur only between MIPS instructions.
            synchronized (Application.MEMORY_AND_REGISTERS_LOCK) {
                try {
                    Application.memory.setRawWord(address, intValue);
                }
                catch (AddressErrorException exception) {
                    // Somehow, user was able to display out-of-range address.  Most likely to occur between
                    // stack base and kernel.
                }
            }
        }
    }

    private record ModifiedCode(int row, Object code, Object basic, Object source) {
    }

    /**
     * A custom table cell renderer that we'll use to highlight the current line of
     * source code when executing using Step or breakpoint.
     */
    private class CodeCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            TextSegmentWindow textSegment = Application.getGUI().getMainPane().getExecuteTab().getTextSegmentWindow();
            Settings settings = Application.getSettings();

            this.setHorizontalAlignment(SwingConstants.LEFT);
            if (textSegment.getCodeHighlighting() && textSegment.getIntCodeAddressAtRow(row) == highlightAddress) {
                if (Simulator.isInDelaySlot() || textSegment.inDelaySlot) {
                    this.setBackground(settings.getColorSettingByPosition(Settings.TEXTSEGMENT_DELAYSLOT_HIGHLIGHT_BACKGROUND));
                    this.setForeground(settings.getColorSettingByPosition(Settings.TEXTSEGMENT_DELAYSLOT_HIGHLIGHT_FOREGROUND));
                }
                else {
                    this.setBackground(settings.getColorSettingByPosition(Settings.TEXTSEGMENT_HIGHLIGHT_BACKGROUND));
                    this.setForeground(settings.getColorSettingByPosition(Settings.TEXTSEGMENT_HIGHLIGHT_FOREGROUND));
                }
            }
            else {
                this.setBackground(null);
                this.setForeground(null);
            }

            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            this.setFont(MonoRightCellRenderer.MONOSPACED_PLAIN_12POINT);

            return this;
        }
    }


    /**
     * Cell renderer for Breakpoint column.  We can use this to enable/disable breakpoint checkboxes with
     * a single action.  This class blatantly copied/pasted from
     * http://www.javakb.com/Uwe/Forum.aspx/java-gui/1451/Java-TableCellRenderer-for-a-boolean-checkbox-field.
     * Slightly customized.
     *
     * @author DPS 31-Dec-2009
     */
    class CheckBoxTableCellRenderer extends JCheckBox implements TableCellRenderer {
        Border noFocusBorder;
        Border focusBorder;

        public CheckBoxTableCellRenderer() {
            super();
            setContentAreaFilled(true);
            setBorderPainted(true);
            setHorizontalAlignment(SwingConstants.CENTER);
            setVerticalAlignment(SwingConstants.CENTER);

            /*
             * Use this if you want to add "instant" recognition of breakpoint changes
             * during simulation run.  Currently, the simulator gets array of breakpoints
             * only when "Go" is selected.  Thus the system does not respond to breakpoints
             * added/removed during unlimited/timed execution.  In order for it to do so,
             * we need to be informed of such changes and the ItemListener below will do this.
             * Then the item listener needs to inform the SimThread object so it can request
             * a fresh breakpoint array.  That would make SimThread an observer.  Synchronization
             * will come into play in the SimThread class?  It could get complicated, which
             * is why I'm dropping it for release 3.8.  DPS 31-dec-2009
             *
             * addItemListener(
             * new ItemListener(){
             * public void itemStateChanged(ItemEvent e) {
             * String what = "state changed";
             * if (e.getStateChange()==ItemEvent.SELECTED) what = "selected";
             * if (e.getStateChange()==ItemEvent.DESELECTED) what = "deselected";
             * System.out.println("Item "+what);
             * }});
             *
             * For a different approach, see RunClearBreakpointsAction.java.  This menu item registers
             * as a TableModelListener by calling the TextSegmentWindow's registerTableModelListener
             * method.  Then it is notified when the table model changes, and this occurs whenever
             * the user clicks on a breakpoint checkbox!  Using this approach, the SimThread registers
             * similarly.  A "GUI guard" is not needed in SimThread because it extends SwingWorker and
             * thus is only invoked when the IDE is present (never when running MARS in command mode).
             */
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            if (table != null) {
                if (isSelected) {
                    setForeground(table.getSelectionForeground());
                    setBackground(table.getSelectionBackground());
                }
                else {
                    setForeground(table.getForeground());
                    setBackground(table.getBackground());
                }

                setEnabled(table.isEnabled() && breakpointsEnabled);
                setComponentOrientation(table.getComponentOrientation());

                if (hasFocus) {
                    if (focusBorder == null) {
                        focusBorder = UIManager.getBorder("Table.focusCellHighlightBorder");
                    }
                    setBorder(focusBorder);
                }
                else {
                    if (noFocusBorder == null) {
                        if (focusBorder == null) {
                            focusBorder = UIManager.getBorder("Table.focusCellHighlightBorder");
                        }
                        if (focusBorder != null) {
                            Insets insets = focusBorder.getBorderInsets(this);
                            noFocusBorder = new EmptyBorder(insets);
                        }
                    }
                    setBorder(noFocusBorder);
                }
                setSelected(Boolean.TRUE.equals(value));
            }
            return this;
        }
    }

    ///////////////////////////////////////////////////////////////////
    //
    // JTable subclass to provide custom tool tips for each of the
    // text table column headers. From Sun's JTable tutorial.
    // http://java.sun.com/docs/books/tutorial/uiswing/components/table.html
    //
    private class TextSegmentTable extends JTable {
        private JTableHeader tableHeader;

        public TextSegmentTable(TextTableModel model) {
            super(model);
        }

        private static final String[] COLUMN_TOOL_TIPS = {
            "If checked, will set an execution breakpoint. Click header to disable/enable breakpoints", // Break
            "Text segment address of binary instruction code", // Address
            "32-bit binary MIPS instruction", // Code
            "Basic assembler instruction", // Basic
            "Source code line", // Source
        };

        // Implement table header tool tips.
        @Override
        protected JTableHeader createDefaultTableHeader() {
            return tableHeader = new TextTableHeader(columnModel);
        }

        /**
         * Given the model index of a column header, will return rectangle
         * rectangle of displayed header (may be in different position due to
         * column re-ordering).
         */
        public Rectangle getRectForColumnIndex(int realIndex) {
            for (int i = 0; i < columnModel.getColumnCount(); i++) {
                if (columnModel.getColumn(i).getModelIndex() == realIndex) {
                    return tableHeader.getHeaderRect(i);
                }
            }
            return tableHeader.getHeaderRect(realIndex);
        }

        /**
         * Customized table header that will both display tool tip when
         * mouse hovers over each column, and also enable/disable breakpoints
         * when mouse is clicked on breakpoint column.  Both are
         * customized based on the column under the mouse.
         */
        private class TextTableHeader extends JTableHeader {
            public TextTableHeader(TableColumnModel cm) {
                super(cm);
                this.addMouseListener(new TextTableHeaderMouseListener());
            }

            @Override
            public String getToolTipText(MouseEvent e) {
                Point p = e.getPoint();
                int index = columnModel.getColumnIndexAtX(p.x);
                int realIndex = columnModel.getColumn(index).getModelIndex();
                return COLUMN_TOOL_TIPS[realIndex];
            }

            // When user clicks on breakpoint column header, breakpoints are
            // toggled (enabled/disabled).  DPS 31-Dec-2009
            private class TextTableHeaderMouseListener implements MouseListener {
                @Override
                public void mouseClicked(MouseEvent e) {
                    Point p = e.getPoint();
                    int index = columnModel.getColumnIndexAtX(p.x);
                    int realIndex = columnModel.getColumn(index).getModelIndex();
                    if (realIndex == BREAKPOINT_COLUMN) {
                        JCheckBox check = ((JCheckBox) ((DefaultCellEditor) table.getCellEditor(0, index)).getComponent());
                        breakpointsEnabled = !breakpointsEnabled;
                        check.setEnabled(breakpointsEnabled);
                        table.tableChanged(new TableModelEvent(tableModel, 0, data.length - 1, BREAKPOINT_COLUMN));
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                }

                @Override
                public void mouseExited(MouseEvent e) {
                }

                @Override
                public void mousePressed(MouseEvent e) {
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                }
            }
        }
    }

    /**
     * Will capture movement of text columns.  This info goes into persistent store.
     */
    private class MyTableColumnMovingListener implements TableColumnModelListener {
        // Don't care about these events but no adapter provided so...
        @Override
        public void columnAdded(TableColumnModelEvent e) {
        }

        @Override
        public void columnRemoved(TableColumnModelEvent e) {
        }

        @Override
        public void columnMarginChanged(ChangeEvent e) {
        }

        @Override
        public void columnSelectionChanged(ListSelectionEvent e) {
        }

        // When column moves, save the new column order.
        @Override
        public void columnMoved(TableColumnModelEvent e) {
            Integer[] columnOrder = new Integer[table.getColumnCount()];
            for (int i = 0; i < columnOrder.length; i++) {
                columnOrder[i] = table.getColumnModel().getColumn(i).getModelIndex();
            }
            // If movement is slow, this event may fire multiple times w/o
            // actually changing the column order.  If new column order is
            // same as previous, do not save changes to persistent store.
            Integer[] savedColumnOrder = getSavedColumnOrder();
            for (int i = 0; i < columnOrder.length; i++) {
                if (!Objects.equals(savedColumnOrder[i], columnOrder[i])) {
                    // Join column numbers into a space-separated string
                    String columnOrderString = String.join(" ", Arrays.stream(columnOrder).map(Object::toString).toList());
                    Application.getSettings().textSegmentColumnOrder.set(columnOrderString);
                    break;
                }
            }
        }
    }
}