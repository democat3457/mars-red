package mars.tools;

import mars.Application;
import mars.ProgramStatement;
import mars.mips.hardware.AccessNotice;
import mars.mips.hardware.AddressErrorException;
import mars.mips.hardware.Memory;
import mars.mips.hardware.MemoryAccessNotice;
import mars.util.SVGIcon;
import mars.venus.VenusUI;
import mars.venus.actions.run.RunAssembleAction;
import mars.venus.actions.run.RunStepBackwardAction;
import mars.venus.actions.run.RunStepForwardAction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.swing.Timer;
import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.IOException;
import java.io.Serial;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;

public class MipsXray extends AbstractMarsToolAndApplication {
    @Serial
    private static final long serialVersionUID = -1L;
    private static final String heading = "MIPS X-Ray - Animation of MIPS Datapath";
    private static final String version = " Version 2.0";

    protected Graphics graphics;
    // Address of instruction in memory
    protected int lastAddress = -1;
    protected JLabel label;
    private final Container painel = this.getContentPane();

    private String instructionBinary;

    private Action runAssembleAction, runStepAction, runBackstepAction;

    private VenusUI gui;
    private JToolBar toolbar;

    public MipsXray(String title, String heading) {
        super(title, heading);
    }

    /**
     * Simple constructor, likely used by the MipsXray menu mechanism
     */
    public MipsXray() {
        super(heading + ", " + version, heading);
    }

    /**
     * Required method to return Tool name.
     *
     * @return Tool name.  MARS will display this in menu item.
     */
    public String getName() {
        return "MIPS X-Ray";
    }

    /**
     * Overrides default method, to provide a Help button for this tool/app.
     */
    @Override
    protected JComponent getHelpComponent() {
        final String helpContent = """
            This plugin is used to visualize the behavior of mips processor using the default datapath.
            It reads the source code instruction and generates an animation representing the inputs and
            outputs of functional blocks and the interconnection between them.  The basic signals
            represented are control signals, opcode bits and data of functional blocks.

            Besides the datapath representation, information for each instruction is displayed below
            the datapath. That display includes opcode value, with the correspondent colors used to
            represent the signals in datapath, mnemonic of the instruction processed at the moment, registers
            used in the instruction and a label that indicates the color code used to represent control signals.

            To see the datapath of register bank and control units, click inside the functional unit.

            Version 2.0
            Developed by M�rcio Roberto, Guilherme Sales, Fabr�cio Vivas, Fl�vio Cardeal and F�bio L�cio
            Contact Marcio Roberto at marcio.rdaraujo@gmail.com with questions or comments.
            """;
        JButton help = new JButton("Help");
        help.addActionListener(event -> JOptionPane.showMessageDialog(window, helpContent));
        return help;
    }

    /**
     * Insert image in the panel and configure the parameters to run animation.
     */
    @Override
    protected JComponent buildMainDisplayArea() {
        return buildMainDisplayArea("datapath.png");
    }

    protected JComponent buildMainDisplayArea(String figure) {
        gui = Application.getGUI();
        this.createActionObjects();
        toolbar = this.setUpToolBar();

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsConfiguration gc = ge.getDefaultScreenDevice().getDefaultConfiguration();
        try {
            BufferedImage im = ImageIO.read(Objects.requireNonNull(getClass().getResource(Application.IMAGES_PATH + figure)));

            int transparency = im.getColorModel().getTransparency();
            BufferedImage datapath = gc.createCompatibleImage(im.getWidth(), im.getHeight(), transparency);

            Graphics2D g2d = datapath.createGraphics();  // graphics context
            g2d.drawImage(im, 0, 0, null);
            g2d.dispose();
        }
        catch (IOException e) {
            System.err.println("Load Image error for " + getClass().getResource(Application.IMAGES_PATH + figure) + ":\n" + e);
            e.printStackTrace(System.err);
        }
        System.setProperty("sun.java2d.translaccel", "true");
        ImageIcon icon = new ImageIcon(Objects.requireNonNull(getClass().getResource(Application.IMAGES_PATH + figure)));
        Image im = icon.getImage();
        icon = new ImageIcon(im);

        JLabel label = new JLabel(icon);
        painel.add(label, BorderLayout.WEST);
        painel.add(toolbar, BorderLayout.NORTH);
        this.setResizable(false);
        return (JComponent) painel;
    }

    @Override
    protected void addAsObserver() {
        addAsObserver(Memory.textBaseAddress, Memory.textLimitAddress);
    }

    /**
     * Function that gets the current instruction in memory and start animation with the selected instruction.
     */
    @Override
    protected void processMIPSUpdate(Observable resource, AccessNotice notice) {
		if (!notice.accessIsFromMIPS()) {
			return;
		}
		if (notice.getAccessType() != AccessNotice.READ) {
			return;
		}
        MemoryAccessNotice man = (MemoryAccessNotice) notice;
        int currentAdress = man.getAddress();

		if (currentAdress == lastAddress) {
			return;
		}
        lastAddress = currentAdress;
        ProgramStatement stmt;

        try {
            stmt = Memory.getInstance().getStatement(currentAdress);
            if (stmt == null) {
                return;
            }

            instructionBinary = stmt.getMachineStatement();

            painel.removeAll();
            // Class panel that runs datapath animation
            DatapathAnimation datapathAnimation = new DatapathAnimation(instructionBinary);
            this.createActionObjects();
            toolbar = this.setUpToolBar();
            painel.add(toolbar, BorderLayout.NORTH);
            painel.add(datapathAnimation, BorderLayout.WEST);
            datapathAnimation.startAnimation(instructionBinary);
        }
        catch (AddressErrorException exception) {
            exception.printStackTrace(System.err);
        }
    }

    @Override
    public void updateDisplay() {
        this.repaint();
    }

    /**
     * Set the tool bar that controls the step in a time instruction running.
     */
    private JToolBar setUpToolBar() {
        JToolBar toolBar = new JToolBar();
        // Components to add menu bar in the plugin window.
        JButton assembleButton = new JButton(runAssembleAction);
        assembleButton.setText("");
        JButton stepBackwardButton = new JButton(runBackstepAction);
        stepBackwardButton.setText("");

        JButton stepForwardButton = new JButton(runStepAction);
        stepForwardButton.setText("");
        toolBar.add(assembleButton);
        toolBar.add(stepForwardButton);

        return toolBar;
    }

    /**
     * Setup actions in the menu bar.
     */
    private void createActionObjects() {
        try {
            runAssembleAction = new RunAssembleAction(gui, "Assemble", SVGIcon.loadSVGActionIcon("assemble.svg", VenusUI.ICON_SIZE), "Assemble the current file and clear breakpoints", KeyEvent.VK_A, KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0));
            runStepAction = new RunStepForwardAction(gui, "Step", SVGIcon.loadSVGActionIcon("step_forward.svg", VenusUI.ICON_SIZE), "Run one step at a time", KeyEvent.VK_T, KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0));
            runBackstepAction = new RunStepBackwardAction(gui, "Backstep", SVGIcon.loadSVGActionIcon("step_backward.svg", VenusUI.ICON_SIZE), "Undo the last step", KeyEvent.VK_B, KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0));
        }
        catch (Exception exception) {
            System.err.println("Internal Error: images folder not found, or other null pointer exception while creating Action objects");
            exception.printStackTrace(System.err);
            System.exit(0);
        }
    }

    public static class Vertex {
        public static final int MOVING_UP = 1;
        public static final int MOVING_DOWN = 2;
        public static final int MOVING_LEFT = 3;
        public static final int MOVING_RIGHT = 4;

        public int index;
        public int init;
        public int end;
        public int current;
        public String name;
        public int oppositeAxis;
        public boolean isMovingHorizontally;
        public Color color;
        public boolean isFirstInteraction;
        public boolean isActive;
        public final boolean isText;
        public final int direction;
        public final ArrayList<Integer> targetVertices;

        public Vertex(int index, int init, int end, String name, int oppositeAxis, boolean isMovingHorizontally, String listOfColors, String listTargetVertex, boolean isText) {
            this.index = index;
            this.init = init;
            this.current = this.init;
            this.end = end;
            this.name = name;
            this.oppositeAxis = oppositeAxis;
            this.isMovingHorizontally = isMovingHorizontally;
            this.isFirstInteraction = true;
            this.isActive = false;
            this.isText = isText;
            this.color = new Color(0, 153, 0);
            if (isMovingHorizontally) {
				if (init < end) {
					direction = MOVING_LEFT;
				}
				else {
					direction = MOVING_RIGHT;
				}
            }
            else {
				if (init < end) {
					direction = MOVING_UP;
				}
				else {
					direction = MOVING_DOWN;
				}
            }
            String[] targetVertex = listTargetVertex.split("#");
            this.targetVertices = new ArrayList<>();
            for (String s : targetVertex) {
                this.targetVertices.add(Integer.parseInt(s));
            }
            String[] color = listOfColors.split("#");
            this.color = new Color(Integer.parseInt(color[0]), Integer.parseInt(color[1]), Integer.parseInt(color[2]));
        }
    }

    public enum DatapathUnit {
        REGISTER,
        CONTROL,
        ALU_CONTROL,
        ALU
    }

    /**
     * Internal class that sets the parameter values, controls the basic behavior of the animation,
     * and executes the animation of the selected instruction in memory.
     */
    class DatapathAnimation extends JPanel implements MouseListener {
        @Serial
        private static final long serialVersionUID = -2681757800180958534L;

        private static final int PERIOD = 5; // Velocity of frames in ms
        private static final int PANEL_WIDTH = 1000;
        private static final int PANEL_HEIGHT = 574;
        private static final Color ORANGE = new Color(255, 102, 0);

        private final GraphicsConfiguration gc;

        private int counter; // Verify then remove.

        private Vector<Vector<Vertex>> outputGraph;
        private final ArrayList<Vertex> vertexList;
        private ArrayList<Vertex> traversedVertices;

        private final HashMap<String, String> opcodeEquivalenceTable;
        private final HashMap<String, String> functionEquivalenceTable;
        private final HashMap<String, String> registerEquivalenceTable;

        private String binaryInstruction;

        private BufferedImage datapathImage;

        public DatapathAnimation(String binaryInstruction) {
            gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();

            setBackground(Color.white);
            setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));

            // load and initialize the images
            initializeImage();

            vertexList = new ArrayList<>();
            counter = 0;
            this.binaryInstruction = binaryInstruction;

            opcodeEquivalenceTable = new HashMap<>();
            functionEquivalenceTable = new HashMap<>();
            registerEquivalenceTable = new HashMap<>();

            loadHashMapValues();
            addMouseListener(this);
        }

        /**
         * Import the binary opcode values for the basic instructions of the MIPS instruction set.
         */
        public void loadHashMapValues() {
            importXMLStringData("/MipsXRayOpcode.xml", opcodeEquivalenceTable, "equivalence", "bits", "mnemonic");
            importXMLStringData("/MipsXRayOpcode.xml", functionEquivalenceTable, "function_equivalence", "bits", "mnemonic");
            importXMLStringData("/MipsXRayOpcode.xml", registerEquivalenceTable, "register_equivalence", "bits", "mnemonic");
            importXMLDatapathMap("/MipsXRayOpcode.xml", "datapath_map");
        }

        /**
         * Import the list of opcodes for the MIPS instruction set.
         */
        public void importXMLStringData(String filename, HashMap<String, String> table, String tagName, String tagId, String tagData) {
            try {
                DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
                builderFactory.setNamespaceAware(false);
                DocumentBuilder documentBuilder = builderFactory.newDocumentBuilder();
                Document document = documentBuilder.parse(Objects.requireNonNull(getClass().getResource(filename)).toString());
                Element root = document.getDocumentElement();
                NodeList bitsList, mnemonic;
                NodeList equivalenceList = root.getElementsByTagName(tagName);
                for (int i = 0; i < equivalenceList.getLength(); i++) {
                    Element equivalenceItem = (Element) equivalenceList.item(i);
                    bitsList = equivalenceItem.getElementsByTagName(tagId);
                    mnemonic = equivalenceItem.getElementsByTagName(tagData);
                    for (int j = 0; j < bitsList.getLength(); j++) {
                        table.put(bitsList.item(j).getTextContent(), mnemonic.item(j).getTextContent());
                    }
                }
            }
            catch (Exception exception) {
                exception.printStackTrace(System.err);
            }
        }

        /**
         * Import the parameters of the datapath animation.
         */
        public void importXMLDatapathMap(String filename, String tagName) {
            try {
                DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
                builderFactory.setNamespaceAware(false);
                DocumentBuilder documentBuilder = builderFactory.newDocumentBuilder();
                Document document = documentBuilder.parse(Objects.requireNonNull(getClass().getResource(filename)).toString());
                Element root = document.getDocumentElement();
                NodeList datapathMapList = root.getElementsByTagName(tagName);
                for (int i = 0; i < datapathMapList.getLength(); i++) {
                    // Extract the vertex of the xml input and encapsulate into the vertex object
                    Element datapathMapItem = (Element) datapathMapList.item(i);
                    NodeList index = datapathMapItem.getElementsByTagName("num_vertex");
                    NodeList name = datapathMapItem.getElementsByTagName("name");
                    NodeList init = datapathMapItem.getElementsByTagName("init");
                    NodeList end = datapathMapItem.getElementsByTagName("end");

                    NodeList color;
                    // Definition of colors line
                    String opcode = binaryInstruction.substring(0, 6);
                    if (opcode.equals("000000")) {
                        // R-type instruction
                        color = datapathMapItem.getElementsByTagName("color_Rtype");
                    }
                    else if (opcode.matches("00001[01]")) {
                        // J-type instruction
                        color = datapathMapItem.getElementsByTagName("color_Jtype");
                    }
                    else if (opcode.matches("100[01][01][01]")) {
                        // LOAD type instruction
                        color = datapathMapItem.getElementsByTagName("color_LOADtype");
                    }
                    else if (opcode.matches("101[01][01][01]")) {
                        // LOAD type instruction
                        color = datapathMapItem.getElementsByTagName("color_STOREtype");
                    }
                    else if (opcode.matches("0001[01][01]")) {
                        // BRANCH type instruction
                        color = datapathMapItem.getElementsByTagName("color_BRANCHtype");
                    }
                    else {
                        // I-type instruction
                        color = datapathMapItem.getElementsByTagName("color_Itype");
                    }

                    NodeList otherAxis = datapathMapItem.getElementsByTagName("other_axis");
                    NodeList isMovingXaxis = datapathMapItem.getElementsByTagName("isMovingXaxis");
                    NodeList targetVertex = datapathMapItem.getElementsByTagName("target_vertex");
                    NodeList isText = datapathMapItem.getElementsByTagName("is_text");

                    for (int j = 0; j < index.getLength(); j++) {
                        Vertex vert = new Vertex(Integer.parseInt(index.item(j).getTextContent()), Integer.parseInt(init.item(j).getTextContent()), Integer.parseInt(end.item(j).getTextContent()), name.item(j).getTextContent(), Integer.parseInt(otherAxis.item(j).getTextContent()), Boolean.parseBoolean(isMovingXaxis.item(j).getTextContent()), color.item(j).getTextContent(), targetVertex.item(j).getTextContent(), Boolean.parseBoolean(isText.item(j).getTextContent()));
                        vertexList.add(vert);
                    }
                }
                //loading matrix of control of vertex.
                outputGraph = new Vector<>();
                traversedVertices = new ArrayList<>();
                for (Vertex vertex : vertexList) {
                    ArrayList<Integer> targetIndices = vertex.targetVertices;
                    Vector<Vertex> targetVertices = new Vector<>();
                    for (int index : targetIndices) {
                        targetVertices.add(vertexList.get(index));
                    }
                    outputGraph.add(targetVertices);
                }

                vertexList.get(0).isActive = true;
                traversedVertices.add(vertexList.get(0));
            }
            catch (Exception exception) {
                exception.printStackTrace(System.err);
            }
        }

        /**
         * Set up the information showed in the screen of the current instruction.
         */
        public void setUpInstructionInfo(Graphics2D graphics2D) {
            FontRenderContext frc = graphics2D.getFontRenderContext();
            Font font = new Font("Digital-7", Font.PLAIN, 15);
            Font fontTitle = new Font("Verdana", Font.PLAIN, 10);

            TextLayout textVariable;
            if (binaryInstruction.startsWith("000000")) {
                // R-type instructions description on screen definition.
                textVariable = new TextLayout("REGISTER TYPE INSTRUCTION", new Font("Arial", Font.BOLD, 25), frc);
                graphics2D.setColor(Color.black);
                textVariable.draw(graphics2D, 280, 30);
                //opcode label
                textVariable = new TextLayout("opcode", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 25, 530);

                //initialize of opcode
                textVariable = new TextLayout(binaryInstruction.substring(0, 6), font, frc);
                graphics2D.setColor(Color.magenta);
                textVariable.draw(graphics2D, 25, 550);

                //rs label
                textVariable = new TextLayout("rs", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 90, 530);

                //initialize of rs
                textVariable = new TextLayout(binaryInstruction.substring(6, 11), font, frc);
                graphics2D.setColor(Color.green);
                textVariable.draw(graphics2D, 90, 550);

                //rt label
                textVariable = new TextLayout("rt", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 150, 530);

                //initialize of rt
                textVariable = new TextLayout(binaryInstruction.substring(11, 16), font, frc);
                graphics2D.setColor(Color.blue);
                textVariable.draw(graphics2D, 150, 550);

                // rd label
                textVariable = new TextLayout("rd", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 210, 530);

                //initialize of rd
                textVariable = new TextLayout(binaryInstruction.substring(16, 21), font, frc);
                graphics2D.setColor(Color.cyan);
                textVariable.draw(graphics2D, 210, 550);

                //shamt label
                textVariable = new TextLayout("shamt", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 270, 530);

                //initialize of shamt
                textVariable = new TextLayout(binaryInstruction.substring(21, 26), font, frc);
                graphics2D.setColor(Color.black);
                textVariable.draw(graphics2D, 270, 550);

                //function label
                textVariable = new TextLayout("function", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 330, 530);

                //initialize of function
                textVariable = new TextLayout(binaryInstruction.substring(26, 32), font, frc);
                graphics2D.setColor(ORANGE);
                textVariable.draw(graphics2D, 330, 550);

                //instruction mnemonic
                textVariable = new TextLayout("Instruction", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 25, 480);

                //instruction name
                textVariable = new TextLayout(functionEquivalenceTable.get(binaryInstruction.substring(26, 32)), font, frc);
                graphics2D.setColor(Color.BLACK);
                textVariable.draw(graphics2D, 25, 500);

                //register in RS
                textVariable = new TextLayout(registerEquivalenceTable.get(binaryInstruction.substring(6, 11)), font, frc);
                graphics2D.setColor(Color.BLACK);
                textVariable.draw(graphics2D, 65, 500);

                //register in RT
                textVariable = new TextLayout(registerEquivalenceTable.get(binaryInstruction.substring(16, 21)), font, frc);
                graphics2D.setColor(Color.BLACK);
                textVariable.draw(graphics2D, 105, 500);

                //register in RD
                textVariable = new TextLayout(registerEquivalenceTable.get(binaryInstruction.substring(11, 16)), font, frc);
                graphics2D.setColor(Color.BLACK);
                textVariable.draw(graphics2D, 145, 500);
            }

            else if (binaryInstruction.substring(0, 6).matches("00001[0-1]")) { //jump intructions
                textVariable = new TextLayout("JUMP TYPE INSTRUCTION", new Font("Verdana", Font.BOLD, 25), frc); //description of instruction code type for jump.
                graphics2D.setColor(Color.black);
                textVariable.draw(graphics2D, 280, 30);

                // label opcode
                textVariable = new TextLayout("opcode", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 25, 530);

                //initialize of opcode
                textVariable = new TextLayout(binaryInstruction.substring(0, 6), font, frc);
                graphics2D.setColor(Color.magenta);
                textVariable.draw(graphics2D, 25, 550);

                //label address
                textVariable = new TextLayout("address", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 95, 530);

                textVariable = new TextLayout("Instruction", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 25, 480);

                //initialize of adress
                textVariable = new TextLayout(binaryInstruction.substring(6, 32), font, frc);
                graphics2D.setColor(Color.orange);
                textVariable.draw(graphics2D, 95, 550);

                //instruction mnemonic
                textVariable = new TextLayout(opcodeEquivalenceTable.get(binaryInstruction.substring(0, 6)), font, frc);
                graphics2D.setColor(Color.cyan);
                textVariable.draw(graphics2D, 65, 500);

                //instruction immediate
                textVariable = new TextLayout("LABEL", font, frc);
                graphics2D.setColor(Color.cyan);
                textVariable.draw(graphics2D, 105, 500);
            }

            else if (binaryInstruction.substring(0, 6).matches("100[0-1][0-1][0-1]")) {//load instruction
                textVariable = new TextLayout("LOAD TYPE INSTRUCTION", new Font("Verdana", Font.BOLD, 25), frc); //description of instruction code type for load.
                graphics2D.setColor(Color.black);
                textVariable.draw(graphics2D, 280, 30);
                //opcode label
                textVariable = new TextLayout("opcode", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 25, 530);

                //initialize of opcode
                textVariable = new TextLayout(binaryInstruction.substring(0, 6), font, frc);
                graphics2D.setColor(Color.magenta);
                textVariable.draw(graphics2D, 25, 550);

                //rs label
                textVariable = new TextLayout("rs", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 90, 530);

                //initialize of rs
                textVariable = new TextLayout(binaryInstruction.substring(6, 11), font, frc);
                graphics2D.setColor(Color.green);
                textVariable.draw(graphics2D, 90, 550);

                //rt label
                textVariable = new TextLayout("rt", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 145, 530);

                //initialize of rt
                textVariable = new TextLayout(binaryInstruction.substring(11, 16), font, frc);
                graphics2D.setColor(Color.blue);
                textVariable.draw(graphics2D, 145, 550);

                // rd label
                textVariable = new TextLayout("Immediate", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 200, 530);

                //initialize of rd
                textVariable = new TextLayout(binaryInstruction.substring(16, 32), font, frc);
                graphics2D.setColor(ORANGE);
                textVariable.draw(graphics2D, 200, 550);

                //instruction mnemonic
                textVariable = new TextLayout("Instruction", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 25, 480);

                textVariable = new TextLayout(opcodeEquivalenceTable.get(binaryInstruction.substring(0, 6)), font, frc);
                graphics2D.setColor(Color.BLACK);
                textVariable.draw(graphics2D, 25, 500);

                textVariable = new TextLayout(registerEquivalenceTable.get(binaryInstruction.substring(6, 11)), font, frc);
                graphics2D.setColor(Color.BLACK);
                textVariable.draw(graphics2D, 65, 500);

                textVariable = new TextLayout("M[ " + registerEquivalenceTable.get(binaryInstruction.substring(16, 21)) + " + " + binaryToDecimal(binaryInstruction.substring(6, 32)) + " ]", font, frc);
                graphics2D.setColor(Color.BLACK);
                textVariable.draw(graphics2D, 105, 500);

                //implement co-processors instruction
            }

            else if (binaryInstruction.substring(0, 6).matches("101[0-1][0-1][0-1]")) {//store instruction
                textVariable = new TextLayout("STORE TYPE INSTRUCTION", new Font("Verdana", Font.BOLD, 25), frc);
                graphics2D.setColor(Color.black);
                textVariable.draw(graphics2D, 280, 30);
                //opcode label
                textVariable = new TextLayout("opcode", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 25, 530);

                //initialize of opcode
                textVariable = new TextLayout(binaryInstruction.substring(0, 6), font, frc);
                graphics2D.setColor(Color.magenta);
                textVariable.draw(graphics2D, 25, 550);

                //rs label
                textVariable = new TextLayout("rs", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 90, 530);

                //initialize of rs
                textVariable = new TextLayout(binaryInstruction.substring(6, 11), font, frc);
                graphics2D.setColor(Color.green);
                textVariable.draw(graphics2D, 90, 550);

                //rt label
                textVariable = new TextLayout("rt", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 145, 530);

                //initialize of rt
                textVariable = new TextLayout(binaryInstruction.substring(11, 16), font, frc);
                graphics2D.setColor(Color.blue);
                textVariable.draw(graphics2D, 145, 550);

                // rd label
                textVariable = new TextLayout("Immediate", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 200, 530);

                //initialize of rd
                textVariable = new TextLayout(binaryInstruction.substring(16, 32), font, frc);
                graphics2D.setColor(ORANGE);
                textVariable.draw(graphics2D, 200, 550);

                //instruction mnemonic
                textVariable = new TextLayout("Instruction", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 25, 480);

                textVariable = new TextLayout(opcodeEquivalenceTable.get(binaryInstruction.substring(0, 6)), font, frc);
                graphics2D.setColor(Color.BLACK);
                textVariable.draw(graphics2D, 25, 500);

                textVariable = new TextLayout(registerEquivalenceTable.get(binaryInstruction.substring(6, 11)), font, frc);
                graphics2D.setColor(Color.BLACK);
                textVariable.draw(graphics2D, 65, 500);

                textVariable = new TextLayout("M[ " + registerEquivalenceTable.get(binaryInstruction.substring(16, 21)) + " + " + binaryToDecimal(binaryInstruction.substring(6, 32)) + " ]", font, frc);
                graphics2D.setColor(Color.BLACK);
                textVariable.draw(graphics2D, 105, 500);
            }

            else if (binaryInstruction.substring(0, 6).matches("0100[0-1][0-1]")) {
                // TODO: Coprocessor 0/1 instruction
            }

            else if (binaryInstruction.substring(0, 6).matches("0001[0-1][0-1]")) { //branch instruction
                textVariable = new TextLayout("BRANCH TYPE INSTRUCTION", new Font("Verdana", Font.BOLD, 25), frc);
                graphics2D.setColor(Color.black);
                textVariable.draw(graphics2D, 250, 30);

                //label opcode
                textVariable = new TextLayout("opcode", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 25, 440);

                textVariable = new TextLayout("opcode", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 25, 530);

                //initialize of opcode
                textVariable = new TextLayout(binaryInstruction.substring(0, 6), font, frc);
                graphics2D.setColor(Color.magenta);
                textVariable.draw(graphics2D, 25, 550);

                //rs label
                textVariable = new TextLayout("rs", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 90, 530);

                //initialize of rs
                textVariable = new TextLayout(binaryInstruction.substring(6, 11), font, frc);
                graphics2D.setColor(Color.green);
                textVariable.draw(graphics2D, 90, 550);

                //rt label
                textVariable = new TextLayout("rt", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 145, 530);

                //initialize of rt
                textVariable = new TextLayout(binaryInstruction.substring(11, 16), font, frc);
                graphics2D.setColor(Color.blue);
                textVariable.draw(graphics2D, 145, 550);

                // rd label
                textVariable = new TextLayout("Immediate", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 200, 530);

                //initialize of immediate
                textVariable = new TextLayout(binaryInstruction.substring(16, 32), font, frc);
                graphics2D.setColor(Color.cyan);
                textVariable.draw(graphics2D, 200, 550);

                //instruction mnemonic
                textVariable = new TextLayout("Instruction", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 25, 480);

                textVariable = new TextLayout(opcodeEquivalenceTable.get(binaryInstruction.substring(0, 6)), font, frc);
                graphics2D.setColor(Color.black);
                textVariable.draw(graphics2D, 25, 500);

                textVariable = new TextLayout(registerEquivalenceTable.get(binaryInstruction.substring(6, 11)), font, frc);
                graphics2D.setColor(Color.black);
                textVariable.draw(graphics2D, 105, 500);

                textVariable = new TextLayout(registerEquivalenceTable.get(binaryInstruction.substring(11, 16)), font, frc);
                graphics2D.setColor(Color.black);
                textVariable.draw(graphics2D, 65, 500);

                textVariable = new TextLayout(binaryToDecimal(binaryInstruction.substring(16, 32)), font, frc);
                graphics2D.setColor(Color.black);
                textVariable.draw(graphics2D, 155, 500);
            }
            else {
                // Immediate instruction
                textVariable = new TextLayout("IMMEDIATE TYPE INSTRUCTION", new Font("Verdana", Font.BOLD, 25), frc);
                graphics2D.setColor(Color.black);
                textVariable.draw(graphics2D, 250, 30);

                //label opcode
                textVariable = new TextLayout("opcode", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 25, 530);

                //initialize of opcode
                textVariable = new TextLayout(binaryInstruction.substring(0, 6), font, frc);
                graphics2D.setColor(Color.magenta);
                textVariable.draw(graphics2D, 25, 550);

                //rs label
                textVariable = new TextLayout("rs", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 90, 530);

                //initialize of rs
                textVariable = new TextLayout(binaryInstruction.substring(6, 11), font, frc);
                graphics2D.setColor(Color.green);
                textVariable.draw(graphics2D, 90, 550);

                //rt label
                textVariable = new TextLayout("rt", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 145, 530);

                //initialize of rt
                textVariable = new TextLayout(binaryInstruction.substring(11, 16), font, frc);
                graphics2D.setColor(Color.blue);
                textVariable.draw(graphics2D, 145, 550);

                // rd label
                textVariable = new TextLayout("Immediate", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 200, 530);

                //initialize of immediate
                textVariable = new TextLayout(binaryInstruction.substring(16, 32), font, frc);
                graphics2D.setColor(Color.cyan);
                textVariable.draw(graphics2D, 200, 550);

                //instruction mnemonic
                textVariable = new TextLayout("Instruction", fontTitle, frc);
                graphics2D.setColor(Color.red);
                textVariable.draw(graphics2D, 25, 480);
                textVariable = new TextLayout(opcodeEquivalenceTable.get(binaryInstruction.substring(0, 6)), font, frc);
                graphics2D.setColor(Color.black);
                textVariable.draw(graphics2D, 25, 500);

                textVariable = new TextLayout(registerEquivalenceTable.get(binaryInstruction.substring(6, 11)), font, frc);
                graphics2D.setColor(Color.black);
                textVariable.draw(graphics2D, 105, 500);

                textVariable = new TextLayout(registerEquivalenceTable.get(binaryInstruction.substring(11, 16)), font, frc);
                graphics2D.setColor(Color.black);
                textVariable.draw(graphics2D, 65, 500);

                textVariable = new TextLayout(binaryToDecimal(binaryInstruction.substring(16, 32)), font, frc);
                graphics2D.setColor(Color.black);
                textVariable.draw(graphics2D, 155, 500);
            }

            // Type of control signal labels
            textVariable = new TextLayout("Control Signals", fontTitle, frc);
            graphics2D.setColor(Color.red);
            textVariable.draw(graphics2D, 25, 440);

            textVariable = new TextLayout("Active", font, frc);
            graphics2D.setColor(Color.red);
            textVariable.draw(graphics2D, 25, 455);

            textVariable = new TextLayout("Inactive", font, frc);
            graphics2D.setColor(Color.gray);
            textVariable.draw(graphics2D, 75, 455);

            textVariable = new TextLayout("To see details of control units and register bank click inside the functional block", font, frc);
            graphics2D.setColor(Color.black);
            textVariable.draw(graphics2D, 400, 550);
        }

        /**
         * Set the initial state of the variables that controls the animation,
         * and start the timer that triggers the animation.
         */
        public void startAnimation(String binaryInstruction) {
            this.binaryInstruction = binaryInstruction;
            // Start the animation timer
            Timer timer = new Timer(PERIOD, event -> repaint());
            timer.start();
        }

        /**
         * Initialize the image of the datapath.
         */
        private void initializeImage() {
            try {
                BufferedImage image = ImageIO.read(Objects.requireNonNull(getClass().getResource(Application.IMAGES_PATH + "datapath.png")));

                int transparency = image.getColorModel().getTransparency();
                datapathImage = gc.createCompatibleImage(image.getWidth(), image.getHeight(), transparency);
                Graphics2D graphics2D = datapathImage.createGraphics();
                graphics2D.drawImage(image, 0, 0, null);
                graphics2D.dispose();
            }
            catch (IOException e) {
                System.out.println("Load Image error for " + getClass().getResource(Application.IMAGES_PATH + "datapath.png") + ":\n" + e);
            }
        }

        @Override
        public void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D graphics2D = (Graphics2D) graphics;
            // Use antialiasing
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Smoother (and slower) image transformations
            graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            drawImage(graphics2D, datapathImage, this, 0, 0, null);
            executeAnimation(graphics2D);
            counter = (counter + 1) % 100;
            graphics2D.dispose();
        }

        static void drawImage(Graphics2D graphics2D, BufferedImage image, ImageObserver observer, int x, int y, Color color) {
			if (image == null) {
				graphics2D.setColor(color);
				graphics2D.fillOval(x, y, 20, 20);
				graphics2D.setColor(Color.black);
				graphics2D.drawString("   ", x, y);
			}
			else {
				graphics2D.drawImage(image, x, y, observer);
			}
        }

        /**
         * Method to draw the lines that run from left to right.
         */
        public static void printTrackLtoR(Graphics2D graphics2D, Vertex vertex) {
            int size = vertex.end - vertex.init;
            int[] track = new int[size];

			for (int i = 0; i < size; i++) {
				track[i] = vertex.init + i;
			}

            if (vertex.isActive) {
                vertex.isFirstInteraction = false;
                for (int i = 0; i < size; i++) {
                    if (track[i] <= vertex.current) {
                        graphics2D.setColor(vertex.color);
                        graphics2D.fillRect(track[i], vertex.oppositeAxis, 3, 3);
                    }
                }
				if (vertex.current == track[size - 1]) {
					vertex.isActive = false;
				}
                vertex.current++;
            }
            else if (!vertex.isFirstInteraction) {
                for (int i = 0; i < size; i++) {
                    graphics2D.setColor(vertex.color);
                    graphics2D.fillRect(track[i], vertex.oppositeAxis, 3, 3);
                }
            }
        }

        /**
         * Method to draw the lines that run from right to left.
         */
        public static void printTrackRtoL(Graphics2D graphics2D, Vertex vertex) {
            int size = vertex.init - vertex.end;
            int[] track = new int[size];

			for (int i = 0; i < size; i++) {
				track[i] = vertex.init - i;
			}

            if (vertex.isActive) {
                vertex.isFirstInteraction = false;
                for (int i = 0; i < size; i++) {
                    if (track[i] >= vertex.current) {
                        graphics2D.setColor(vertex.color);
                        graphics2D.fillRect(track[i], vertex.oppositeAxis, 3, 3);
                    }
                }
				if (vertex.current == track[size - 1]) {
					vertex.isActive = false;
				}

                vertex.current = vertex.current - 1;
            }
            else if (!vertex.isFirstInteraction) {
                for (int i = 0; i < size; i++) {
                    graphics2D.setColor(vertex.color);
                    graphics2D.fillRect(track[i], vertex.oppositeAxis, 3, 3);
                }
            }
        }

        /**
         * Method to draw the lines that run from bottom to top.
         */
        public static void printTrackDtoU(Graphics2D graphics2D, Vertex vertex) {
            int size;
            int[] track;

            if (vertex.init > vertex.end) {
                size = vertex.init - vertex.end;
                track = new int[size];
				for (int i = 0; i < size; i++) {
					track[i] = vertex.init - i;
				}
            }
            else {
                size = vertex.end - vertex.init;
                track = new int[size];
				for (int i = 0; i < size; i++) {
					track[i] = vertex.init + i;
				}
            }

            if (vertex.isActive) {
                vertex.isFirstInteraction = false;
                for (int i = 0; i < size; i++) {
                    if (track[i] >= vertex.current) {
                        graphics2D.setColor(vertex.color);
                        graphics2D.fillRect(vertex.oppositeAxis, track[i], 3, 3);
                    }
                }
				if (vertex.current == track[size - 1]) {
					vertex.isActive = false;
				}
                vertex.current = vertex.current - 1;
            }
            else if (!vertex.isFirstInteraction) {
                for (int i = 0; i < size; i++) {
                    graphics2D.setColor(vertex.color);
                    graphics2D.fillRect(vertex.oppositeAxis, track[i], 3, 3);
                }
            }
        }

        /**
         * Method to draw the lines that run from top to bottom.
         */
        public static void printTrackUtoD(Graphics2D graphics2D, Vertex vertex) {
            int size = vertex.end - vertex.init;
            int[] track = new int[size];

			for (int i = 0; i < size; i++) {
				track[i] = vertex.init + i;
			}

            if (vertex.isActive) {
                vertex.isFirstInteraction = false;
                for (int i = 0; i < size; i++) {
                    if (track[i] <= vertex.current) {
                        graphics2D.setColor(vertex.color);
                        graphics2D.fillRect(vertex.oppositeAxis, track[i], 3, 3);
                    }
                }
				if (vertex.current == track[size - 1]) {
					vertex.isActive = false;
				}
                vertex.current = vertex.current + 1;
            }
            else if (!vertex.isFirstInteraction) {
                for (int i = 0; i < size; i++) {
                    graphics2D.setColor(vertex.color);
                    graphics2D.fillRect(vertex.oppositeAxis, track[i], 3, 3);
                }
            }
        }

        public void printTextDtoU(Graphics2D graphics2D, Vertex vertex) {
            FontRenderContext frc = graphics2D.getFontRenderContext();
            Font font = new Font("Verdana", Font.BOLD, 13);

            TextLayout actionInFunctionalBlock = new TextLayout(vertex.name, font, frc);
            graphics2D.setColor(Color.RED);
            String opcode = binaryInstruction.substring(0, 6);

            if (opcode.matches("101[0-1][0-1][0-1]")) {
                // LOAD type instruction
                actionInFunctionalBlock = new TextLayout(" ", font, frc);
            }
            if (vertex.name.equals("ALUVALUE")) {
				if (opcode.equals("000000")) {
                    // R-type instruction
					actionInFunctionalBlock = new TextLayout(functionEquivalenceTable.get(binaryInstruction.substring(26, 32)), font, frc);
				}
				else {
                    // Other instruction
					actionInFunctionalBlock = new TextLayout(opcodeEquivalenceTable.get(opcode), font, frc);
				}
            }

			if (binaryInstruction.substring(0, 6).matches("0001[0-1][0-1]") && vertex.name.equals("CP+4")) {
                // Branch code
				actionInFunctionalBlock = new TextLayout("PC+OFFSET", font, frc);
			}

            if (vertex.name.equals("WRITING")) {
				if (!binaryInstruction.substring(0, 6).matches("100[0-1][0-1][0-1]")) {
					actionInFunctionalBlock = new TextLayout(" ", font, frc);
				}
            }
            if (vertex.isActive) {
                vertex.isFirstInteraction = false;
                actionInFunctionalBlock.draw(graphics2D, vertex.oppositeAxis, vertex.current);
				if (vertex.current == vertex.end) {
					vertex.isActive = false;
				}
                vertex.current = vertex.current - 1;
            }
        }

        /**
         * Convert binary value to decimal string.
         */
        static String binaryToDecimal(String binary) {
            return Integer.toString(Integer.parseInt(binary, 2));
        }

        //set and execute the information about the current position of each line of information in the animation,
        //verifies the previous status of the animation and increment the position of each line that interconnect the unit function.
        void executeAnimation(Graphics2D graphics2D) {
            setUpInstructionInfo(graphics2D);
            executeAnimation(graphics2D, traversedVertices, outputGraph, this::printTextDtoU);
        }
        static void executeAnimation(Graphics2D graphics2D,
                                     List<Vertex> traversedVertices,
                                     Vector<Vector<Vertex>> outputGraph,
                                     BiConsumer<Graphics2D, Vertex> onTextVertexMovingUp) {
            Vertex vertex;
            for (int i = 0; i < traversedVertices.size(); i++) {
                vertex = traversedVertices.get(i);
                if (vertex.isMovingHorizontally) {
                    if (vertex.direction == Vertex.MOVING_LEFT) {
                        printTrackLtoR(graphics2D, vertex);
                    }
                    else {
                        printTrackRtoL(graphics2D, vertex);
                    }
                    if (!vertex.isActive) {
                        int j = vertex.targetVertices.size();
                        for (int k = 0; k < j; k++) {
                            Vertex tempVertex = outputGraph.get(vertex.index).get(k);
                            boolean hasThisVertex = false;
                            for (Vertex traversedVertex : traversedVertices) {
                                if (tempVertex.index == traversedVertex.index) {
                                    hasThisVertex = true;
                                    break;
                                }
                            }
                            if (!hasThisVertex) {
                                outputGraph.get(vertex.index).get(k).isActive = true;
                                traversedVertices.add(outputGraph.get(vertex.index).get(k));
                            }
                        }
                    }
                }
                else {
                    if (vertex.direction == Vertex.MOVING_DOWN) {
						if (vertex.isText) {
							onTextVertexMovingUp.accept(graphics2D, vertex);
						}
						else {
							printTrackDtoU(graphics2D, vertex);
						}
                    }
                    else {
                        printTrackUtoD(graphics2D, vertex);
                    }
                    if (!vertex.isActive) {
                        int j = vertex.targetVertices.size();
                        for (int k = 0; k < j; k++) {
                            Vertex tempVertex = outputGraph.get(vertex.index).get(k);
                            boolean hasThisVertex = false;
                            for (Vertex traversedVertex : traversedVertices) {
                                if (tempVertex.index == traversedVertex.index) {
                                    hasThisVertex = true;
                                    break;
                                }
                            }
                            if (!hasThisVertex) {
                                outputGraph.get(vertex.index).get(k).isActive = true;
                                traversedVertices.add(outputGraph.get(vertex.index).get(k));
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void mouseClicked(MouseEvent event) {
            // Clear panel image and start functional unit visualization

            if (event.getPoint().getX() > 425 && event.getPoint().getX() < 520 && event.getPoint().getY() > 300 && event.getPoint().getY() < 425) {
                buildMainDisplayArea("register.png");
                FunctionUnitVisualization fu = new FunctionUnitVisualization(instructionBinary, DatapathUnit.REGISTER);
                fu.run();
            }

            if (event.getPoint().getX() > 355 && event.getPoint().getX() < 415 && event.getPoint().getY() > 180 && event.getPoint().getY() < 280) {
                buildMainDisplayArea("control.png");
                FunctionUnitVisualization fu = new FunctionUnitVisualization(instructionBinary, DatapathUnit.CONTROL);
                fu.run();
            }

            if (event.getPoint().getX() > 560 && event.getPoint().getX() < 620 && event.getPoint().getY() > 450 && event.getPoint().getY() < 520) {
                buildMainDisplayArea("ALUcontrol.png");
                FunctionUnitVisualization fu = new FunctionUnitVisualization(instructionBinary, DatapathUnit.ALU_CONTROL);
                fu.run();
            }
        }

        @Override
        public void mouseEntered(MouseEvent event) {}

        @Override
        public void mouseExited(MouseEvent event) {}

        @Override
        public void mousePressed(MouseEvent event) {}

        @Override
        public void mouseReleased(MouseEvent event) {}
    }
}
