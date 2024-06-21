package mars.venus.actions.edit;

import mars.venus.actions.VenusAction;
import mars.venus.VenusUI;
import mars.venus.editor.FileStatus;

import javax.swing.*;
import java.awt.event.ActionEvent;

/*
Copyright (c) 2003-2010,  Pete Sanderson and Kenneth Vollmar

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
 * Action for the Edit -> Select All menu item.
 */
public class EditSelectAllAction extends VenusAction {
    public EditSelectAllAction(VenusUI gui, Integer mnemonic, KeyStroke accel) {
        super(gui, "Select All", VenusUI.getSVGActionIcon("select_all.svg"), "Select all text in the current file", mnemonic, accel);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        this.gui.getMainPane().getCurrentEditorTab().selectAllText();
    }

    @Override
    public void update() {
        this.setEnabled(this.gui.getFileStatus() != FileStatus.NO_FILE);
    }
}