package mars.mips.hardware;

import mars.Globals;
import mars.assembler.SymbolTable;
import mars.mips.instructions.Instruction;
import mars.util.Binary;

import java.util.Observer;

/*
Copyright (c) 2003-2008,  Pete Sanderson and Kenneth Vollmar

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
 * Represents the collection of MIPS registers.
 *
 * @author Jason Bumgarner, Jason Shrewsbury
 * @version June 2003
 */
public class RegisterFile {
    public static final int GLOBAL_POINTER = 28;
    public static final int STACK_POINTER = 29;
    public static final int PROGRAM_COUNTER = 32;
    public static final int HIGH_ORDER = 33;
    public static final int LOW_ORDER = 34;
    public static final int USER_REGISTER_COUNT = 32;

    private static final Register[] registers = {
        new Register("$zero", 0, 0),
        new Register("$at", 1, 0),
        new Register("$v0", 2, 0),
        new Register("$v1", 3, 0),
        new Register("$a0", 4, 0),
        new Register("$a1", 5, 0),
        new Register("$a2", 6, 0),
        new Register("$a3", 7, 0),
        new Register("$t0", 8, 0),
        new Register("$t1", 9, 0),
        new Register("$t2", 10, 0),
        new Register("$t3", 11, 0),
        new Register("$t4", 12, 0),
        new Register("$t5", 13, 0),
        new Register("$t6", 14, 0),
        new Register("$t7", 15, 0),
        new Register("$s0", 16, 0),
        new Register("$s1", 17, 0),
        new Register("$s2", 18, 0),
        new Register("$s3", 19, 0),
        new Register("$s4", 20, 0),
        new Register("$s5", 21, 0),
        new Register("$s6", 22, 0),
        new Register("$s7", 23, 0),
        new Register("$t8", 24, 0),
        new Register("$t9", 25, 0),
        new Register("$k0", 26, 0),
        new Register("$k1", 27, 0),
        new Register("$gp", GLOBAL_POINTER, Memory.globalPointer),
        new Register("$sp", STACK_POINTER, Memory.stackPointer),
        new Register("$fp", 30, 0),
        new Register("$ra", 31, 0),
        // These are internal registers which are not accessible directly by the user
        new Register("pc", PROGRAM_COUNTER, Memory.textBaseAddress),
        new Register("hi", HIGH_ORDER, 0),
        new Register("lo", LOW_ORDER, 0),
    };

    /**
     * Update the register value whose number is given, unless it is {@code $zero}.
     * Also handles the internal lo and hi registers.
     *
     * @param number Register to set the value of.
     * @param value The desired value for the register.
     * @return The previous value of the register.
     */
    public static int updateRegister(int number, int value) {
        int previousValue;
        // The $zero register cannot be updated
        if (0 < number && number < USER_REGISTER_COUNT) {
            // Originally, this used a linear search to figure out which register to update.
            // Since all registers 0-31 are present in order, a simple array access should work.
            // - Sean Clarke 03/2024
            previousValue = registers[number].setValue(value);
        }
        else {
            // $zero or invalid register, do nothing
            return 0;
        }

        if (Globals.getSettings().getBackSteppingEnabled()) {
            return Globals.program.getBackStepper().addRegisterFileRestore(number, previousValue);
        }
        else {
            return previousValue;
        }
    }

    /**
     * Returns the value of the register whose number is given.
     *
     * @param number The register number.
     * @return The value of the given register.
     */
    public static int getValue(int number) {
        return registers[number].getValue();
    }

    /**
     * Get register number corresponding to given name.
     *
     * @param name The string formatted register name to look for, in $zero format.
     * @return The number of the register represented by the string,
     *     or -1 if no match was found.
     */
    public static int getNumber(String name) {
        // check for register mnemonic $zero thru $ra
        // just do linear search; there aren't that many registers
        for (Register register : registers) {
            if (register.getName().equals(name)) {
                return register.getNumber();
            }
        }
        return -1;
    }

    /**
     * Get the set of accessible registers, not including pc, hi, or lo.
     *
     * @return The set of registers.
     */
    public static Register[] getRegisters() {
        return registers;
    }

    /**
     * Get the register object corresponding to a given name.
     *
     * @param name The register name, either in $0 or $zero format.
     * @return The register object, or null if not found.
     */
    public static Register getUserRegister(String name) {
        if (name.isEmpty() || name.charAt(0) != '$') {
            return null;
        }
        try {
            // Check for register number 0-31
            return registers[Binary.stringToInt(name.substring(1))]; // KENV 1/6/05
        }
        catch (Exception e) {
            // Handles both NumberFormat and ArrayIndexOutOfBounds
            // Check for register mnemonic $zero thru $ra
            // Just do linear search; there aren't that many registers
            for (Register register : registers) {
                if (register.getName().equals(name)) {
                    return register;
                }
            }
            return null;
        }
    }

    /**
     * Initialize the Program Counter.  Do not use this to implement jumps and
     * branches, as it will NOT record a backstep entry with the restore value.
     * If you need backstepping capability, use {@link #setProgramCounter(int)} instead.
     *
     * @param value The value to set the Program Counter to.
     */
    public static void initializeProgramCounter(int value) {
        registers[PROGRAM_COUNTER].setValue(value);
    }

    /**
     * Will initialize the Program Counter to either the default reset value, or the address
     * associated with source program global label "main", if it exists as a text segment label
     * and the global setting is set.
     *
     * @param startAtMain If true, will set program counter to address of statement labeled
     *                    'main' (or other defined start label) if defined.  If not defined, or if parameter false,
     *                    will set program counter to default reset value.
     */
    public static void initializeProgramCounter(boolean startAtMain) {
        int mainAddr = Globals.symbolTable.getAddress(SymbolTable.getStartLabel());
        if (startAtMain && mainAddr != SymbolTable.NOT_FOUND && (Memory.inTextSegment(mainAddr) || Memory.inKernelTextSegment(mainAddr))) {
            initializeProgramCounter(mainAddr);
        }
        else {
            initializeProgramCounter(registers[PROGRAM_COUNTER].getDefaultValue());
        }
    }

    /**
     * Set the value of the program counter.  Note that an ordinary PC update should be done using
     * the {@link #incrementPC()} method; use this only when processing jumps and branches.
     *
     * @param value The value to set the Program Counter to.
     * @return The previous program counter value.
     */
    public static int setProgramCounter(int value) {
        int previousValue = registers[PROGRAM_COUNTER].setValue(value);
        if (Globals.getSettings().getBackSteppingEnabled()) {
            Globals.program.getBackStepper().addPCRestore(previousValue);
        }
        return previousValue;
    }

    /**
     * Get the current program counter value.
     *
     * @return The program counter value as an int.
     */
    public static int getProgramCounter() {
        return registers[PROGRAM_COUNTER].getValue();
    }

    /**
     * Get the Register object for program counter.  Use with caution.
     *
     * @return The program counter register.
     */
    public static Register getProgramCounterRegister() {
        return registers[PROGRAM_COUNTER];
    }

    /**
     * Get the program counter's initial (reset) value.
     *
     * @return The program counter's initial value.
     */
    public static int getInitialProgramCounter() {
        return registers[PROGRAM_COUNTER].getDefaultValue();
    }

    /**
     * Reinitialize the values of the registers.
     * <b>NOTE:</b> Should <i>not</i> be called from command-mode MARS because this
     * this method uses global settings from the registry.  Command-mode must operate
     * using only the command switches, not registry settings.  It can be called
     * from tools running stand-alone, and this is done in
     * {@link mars.tools.AbstractMarsToolAndApplication}.
     */
    public static void resetRegisters() {
        for (Register register : registers) {
            register.resetValueToDefault();
        }
        initializeProgramCounter(Globals.getSettings().startAtMain.get()); // replaces "programCounter.resetValue()", DPS 3/3/09
    }

    /**
     * Increment the Program counter in the general case (not a jump or branch).
     */
    public static void incrementPC() {
        registers[PROGRAM_COUNTER].setValue(registers[PROGRAM_COUNTER].getValue() + Instruction.INSTRUCTION_LENGTH_BYTES);
    }

    /**
     * Each individual register is a separate object and Observable.  This handy method
     * will add the given Observer to each one.  Currently does not apply to Program
     * Counter.
     */
    public static void addRegistersObserver(Observer observer) {
        for (Register register : registers) {
            if (register.getNumber() != PROGRAM_COUNTER) {
                register.addObserver(observer);
            }
        }
    }

    /**
     * Each individual register is a separate object and Observable.  This handy method
     * will delete the given Observer from each one.  Currently does not apply to Program
     * Counter.
     */
    public static void deleteRegistersObserver(Observer observer) {
        for (Register register : registers) {
            if (register.getNumber() != PROGRAM_COUNTER) {
                register.deleteObserver(observer);
            }
        }
    }
}
