package mars.venus;

import mars.Application;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/*
Copyright (c) 2003-2006,  Pete Sanderson and Kenneth Vollmar

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
 * Use this to render Monospaced and right-aligned data in JTables.
 * I am using it to render integer addresses and values that are stored as
 * Strings containing either the decimal or hexadecimal version
 * of the integer value.
 */
public class SimpleCellRenderer extends DefaultTableCellRenderer {
    private final int alignment;

    /**
     * Create a new <code>SimpleCellRenderer</code> with the given text alignment. This cell renderer can be reused
     * anywhere without issue.
     *
     * @param alignment One of the following: {@link SwingConstants#LEFT}, {@link SwingConstants#CENTER},
     *                  {@link SwingConstants#RIGHT}, {@link SwingConstants#LEADING}, or
     *                  {@link SwingConstants#TRAILING}.
     */
    public SimpleCellRenderer(int alignment) {
        this.alignment = alignment;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        this.setFont(Application.getSettings().tableFont.get());
        this.setHorizontalAlignment(this.alignment);

        return this;
    }
}