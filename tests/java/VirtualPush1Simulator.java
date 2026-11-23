import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import de.mossgrabers.controller.ableton.push.controller.Push1DisplayGlyphs;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * Interactive desktop view of the virtual Push 1, connected to the production
 * controller runtime by VirtualPush1RuntimeTest. Optional; never opened in CI.
 */
public final class VirtualPush1Simulator
{
    private static final Color PANEL = new Color (30, 32, 36);
    private static final Color LCD = new Color (177, 197, 142);
    private static final Font LCD_FONT = new Font (Font.MONOSPACED, Font.BOLD, 13);
    private static final String [] NAMES = {
        "Tap", "Metro", "Repeat 1", "Repeat 2", "Repeat 3", "Repeat 4", "Repeat 5", "Repeat 6",
        "Repeat 7", "Repeat 8", "Left", "Right", "Up", "Down", "Shift", "Note", "Session",
        "Add FX", "Add Track", "Oct -", "Oct +", "Repeat", "Accent", "Scales", "User", "Mute",
        "Solo", "In", "Out", "Device", "Browse", "Track", "Clip", "Volume", "Pan/Send",
        "Master", "Stop", "Play", "Record", "New", "Duplicate", "Automation", "Fixed Len",
        "Quantize", "Double", "Delete", "Undo", "Footswitch 1", "Footswitch 2"
    };
    private static final int [] CCS = {
        3, 9, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 49, 50, 51, 52, 53,
        54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 110, 111, 112, 113, 114, 115,
        28, 29, 85, 86, 87, 88, 89, 90, 116, 117, 118, 119, 64, 69
    };

    private final VirtualPush1MidiBench bench;
    private final VirtualPush1Device device;
    private final Runnable onClose;
    private final JLabel [][] lcd = new JLabel [4][4];
    private final JButton [][] pads = new JButton [8][8];
    private final JButton [] bottom = new JButton [8];
    private final Map<Integer, JButton> controlButtons = new HashMap<> ();
    private final JTextArea log = new JTextArea (8, 72);
    private final JSlider pressure = new JSlider (0, 127, 0);
    private final JSlider velocity = new JSlider (1, 127, 100);
    private final JCheckBox holdPad = new JCheckBox ("Hold selected pad for aftertouch");
    private final JCheckBox [] encoderTouch = new JCheckBox [11];
    private final JLabel selectedPad = new JLabel ("Pad: none");
    private final JLabel status = new JLabel ("Production PushControllerSetup connected");
    private int selectedRow = -1, selectedColumn = -1;
    private int seenInput, seenOutput, seenRaw, seenSysex;
    private boolean ribbonTouched;

    private VirtualPush1Simulator (final VirtualPush1MidiBench bench, final Runnable onClose)
    {
        this.bench = bench;
        this.device = bench.device ();
        this.onClose = onClose;
    }

    /**
     * Headless smoke test for the complete Swing component tree. It constructs,
     * lays out, refreshes and paints the simulator without opening a window.
     */
    public static void smoke (final VirtualPush1MidiBench bench)
    {
        try
        {
            SwingUtilities.invokeAndWait (() -> {
                final VirtualPush1Simulator simulator = new VirtualPush1Simulator (bench, null);
                final JPanel root = new JPanel (new BorderLayout (8, 8));
                root.add (simulator.createDisplay (), BorderLayout.NORTH);
                root.add (simulator.createPads (), BorderLayout.CENTER);
                root.add (simulator.createMonitor (), BorderLayout.SOUTH);
                root.add (simulator.createControls (), BorderLayout.EAST);
                root.setSize (1500, 1000);
                root.doLayout ();
                root.validate ();
                simulator.refreshFeedback ();
                if (simulator.controlButtons.size () < CCS.length
                        || simulator.lcd[3][3] == null || simulator.pads[7][7] == null
                        || simulator.bottom[7] == null)
                    throw new AssertionError ("Swing simulator component tree is incomplete");
                final BufferedImage image = new BufferedImage (1500, 1000, BufferedImage.TYPE_INT_ARGB);
                final Graphics2D graphics = image.createGraphics ();
                try { root.paint (graphics); }
                finally { graphics.dispose (); }
                simulator.releaseAll ();
            });
        }
        catch (final Exception error)
        {
            throw new IllegalStateException ("Virtual Push 1 Swing smoke test failed", error);
        }
    }

    public static void show (final VirtualPush1MidiBench bench, final Runnable onClose)
    {
        SwingUtilities.invokeLater (() -> new VirtualPush1Simulator (bench, onClose).open ());
    }

    private void open ()
    {
        try { UIManager.setLookAndFeel (UIManager.getSystemLookAndFeelClassName ()); }
        catch (final Exception ignored) { }
        final JFrame frame = new JFrame ("PUSHdriver — Push 1 virtual simulator");
        frame.setDefaultCloseOperation (JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout (new BorderLayout (8, 8));
        frame.add (createDisplay (), BorderLayout.NORTH);
        frame.add (createPads (), BorderLayout.CENTER);
        frame.add (createMonitor (), BorderLayout.SOUTH);
        frame.add (createControls (), BorderLayout.EAST);
        frame.setMinimumSize (new Dimension (1180, 780));
        frame.pack ();
        frame.setLocationByPlatform (true);
        final Timer refreshTimer = new Timer (150, event -> refreshFeedback ());
        frame.addWindowListener (new WindowAdapter () {
            @Override public void windowClosed (final WindowEvent event)
            { refreshTimer.stop (); releaseAll (); if (onClose != null) onClose.run (); }
        });
        frame.setVisible (true);
        refreshTimer.start ();
    }

    private JPanel createDisplay ()
    {
        final JPanel root = panel ();
        root.setLayout (new GridLayout (1, 4, 5, 0));
        root.setBorder (BorderFactory.createTitledBorder ("LCD — 4 modules × 4 rows × 17 characters"));
        for (int module = 0; module < 4; module++)
        {
            final JPanel block = panel ();
            block.setLayout (new GridLayout (4, 1, 0, 2));
            block.setBorder (BorderFactory.createTitledBorder ("Module " + (module + 1)));
            for (int row = 0; row < 4; row++)
            {
                final JLabel cell = new JLabel ("                 ");
                cell.setOpaque (true);
                cell.setBackground (LCD);
                cell.setForeground (new Color (35, 48, 30));
                cell.setFont (LCD_FONT);
                cell.setBorder (BorderFactory.createEmptyBorder (3, 4, 3, 4));
                lcd[module][row] = cell;
                block.add (cell);
            }
            root.add (block);
        }
        refreshLcd ();
        return root;
    }

    private JPanel createPads ()
    {
        final JPanel root = panel ();
        root.setLayout (new java.awt.BorderLayout (3, 3));
        final JPanel top = panel ();
        top.setLayout (new FlowLayout (FlowLayout.CENTER, 4, 2));
        for (int cc = 20; cc <= 27; cc++) top.add (makeButton ("Top " + (cc - 19), cc));
        top.add (makeButton ("Master", VirtualPush1Device.MASTER));
        root.add (top, BorderLayout.NORTH);

        final JPanel grid = panel ();
        grid.setLayout (new GridLayout (8, 8, 4, 4));
        grid.setBorder (BorderFactory.createTitledBorder ("64 pads — mouse press = note-on, release = note-off"));
        for (int row = 0; row < 8; row++)
            for (int column = 0; column < 8; column++)
            {
                final int r = row, c = column;
                final JButton pad = new JButton (Integer.toString (VirtualPush1Device.padNote (row, column)));
                pad.setOpaque (true);
                pad.setForeground (Color.WHITE);
                pad.setBackground (new Color (55, 58, 62));
                pad.setPreferredSize (new Dimension (54, 43));
                pad.setToolTipText ("Pad " + (row + 1) + "/" + (column + 1));
                pad.addMouseListener (new MouseAdapter () {
                    @Override public void mousePressed (final MouseEvent event)
                    {
                        if (selectedRow >= 0 && (selectedRow != r || selectedColumn != c)
                                && device.isPadPressed (selectedRow, selectedColumn))
                            device.releasePad (selectedRow, selectedColumn);
                        selectedRow = r; selectedColumn = c;
                        selectedPad.setText ("Pad " + (r + 1) + ", " + (c + 1)
                            + " — MIDI note " + VirtualPush1Device.padNote (r, c));
                        pressure.setValue (0);
                        if (!device.isPadPressed (r, c)) device.pressPad (r, c, velocity.getValue ());
                    }
                    @Override public void mouseReleased (final MouseEvent event)
                    { if (!holdPad.isSelected ()) device.releasePad (r, c); }
                });
                pads[row][column] = pad;
                grid.add (pad);
            }
        root.add (grid, BorderLayout.CENTER);
        final JPanel lower = panel ();
        lower.setLayout (new FlowLayout (FlowLayout.CENTER, 5, 2));
        for (int i = 0; i < 8; i++)
        {
            bottom[i] = makeButton ("Bottom " + (i + 1), 102 + i);
            lower.add (bottom[i]);
        }
        lower.add (makeButton ("Stop", VirtualPush1Device.STOP));
        root.add (lower, BorderLayout.SOUTH);
        return root;
    }

    private JPanel createControls ()
    {
        final JPanel root = panel ();
        root.setLayout (new javax.swing.BoxLayout (root, javax.swing.BoxLayout.Y_AXIS));
        root.setBorder (BorderFactory.createTitledBorder ("Touch, encoders & buttons"));
        for (int encoder = 0; encoder < 11; encoder++)
        {
            final int index = encoder;
            final JPanel line = panel ();
            line.setLayout (new FlowLayout (FlowLayout.LEFT, 3, 2));
            line.add (new JLabel (encoder < 9 ? "Encoder " + (encoder + 1) : "Small " + (encoder - 8)));
            final JButton minus = new JButton ("−"), plus = new JButton ("+");
            final JCheckBox touch = new JCheckBox ("Touch");
            encoderTouch[index] = touch;
            minus.addActionListener (event -> turn (index, -1));
            plus.addActionListener (event -> turn (index, 1));
            touch.addActionListener (event -> {
                if (index < 9) device.touchEncoder (index, touch.isSelected ());
                else device.touchSmallEncoder (index - 9, touch.isSelected ());
            });
            line.add (minus); line.add (plus); line.add (touch);
            root.add (line);
        }

        final JPanel ribbon = panel ();
        ribbon.setLayout (new javax.swing.BoxLayout (ribbon, javax.swing.BoxLayout.Y_AXIS));
        ribbon.setBorder (BorderFactory.createTitledBorder ("Ribbon — touch + 14-bit position"));
        final JSlider ribbonPosition = new JSlider (0, 16383, 8192);
        ribbonPosition.addMouseListener (new MouseAdapter () {
            @Override public void mousePressed (final MouseEvent event) { ribbonTouched = true; device.touchRibbon (true); }
            @Override public void mouseReleased (final MouseEvent event) { ribbonTouched = false; device.touchRibbon (false); }
        });
        ribbonPosition.addChangeListener (event -> device.moveRibbon (ribbonPosition.getValue ()));
        ribbon.add (ribbonPosition);
        root.add (ribbon);

        final JPanel aftertouch = panel ();
        aftertouch.setLayout (new javax.swing.BoxLayout (aftertouch, javax.swing.BoxLayout.Y_AXIS));
        aftertouch.setBorder (BorderFactory.createTitledBorder ("Pad aftertouch"));
        aftertouch.add (selectedPad);
        aftertouch.add (new JLabel ("Strike velocity"));
        velocity.setMajorTickSpacing (32);
        velocity.setPaintTicks (true);
        aftertouch.add (velocity);
        holdPad.addActionListener (event -> {
            if (selectedRow >= 0)
            {
                if (holdPad.isSelected () && !device.isPadPressed (selectedRow, selectedColumn))
                    device.pressPad (selectedRow, selectedColumn, velocity.getValue ());
                else if (!holdPad.isSelected () && device.isPadPressed (selectedRow, selectedColumn))
                    device.releasePad (selectedRow, selectedColumn);
            }
        });
        aftertouch.add (holdPad);
        pressure.setMajorTickSpacing (32);
        pressure.setPaintTicks (true);
        pressure.addChangeListener (event -> {
            if (selectedRow >= 0 && device.isPadPressed (selectedRow, selectedColumn))
                device.pressPadPressure (selectedRow, selectedColumn, pressure.getValue ());
        });
        aftertouch.add (pressure);
        root.add (aftertouch);

        final JPanel buttons = panel ();
        buttons.setLayout (new GridLayout (0, 3, 3, 3));
        buttons.setBorder (BorderFactory.createTitledBorder ("Push controls"));
        for (int i = 0; i < CCS.length; i++) buttons.add (makeButton (NAMES[i], CCS[i]));
        root.add (new JScrollPane (buttons));
        root.setPreferredSize (new Dimension (320, 570));
        return root;
    }

    private JPanel createMonitor ()
    {
        final JPanel root = panel ();
        root.setLayout (new BorderLayout (4, 4));
        root.setBorder (BorderFactory.createTitledBorder ("MIDI monitor — device input and extension output"));
        log.setEditable (false);
        log.setFont (new Font (Font.MONOSPACED, Font.PLAIN, 11));
        root.add (new JScrollPane (log), BorderLayout.CENTER);
        final JPanel footer = panel ();
        footer.add (status);
        final JButton clear = new JButton ("Clear log");
        clear.addActionListener (event -> log.setText (""));
        footer.add (clear);
        root.add (footer, BorderLayout.SOUTH);
        return root;
    }

    private JButton makeButton (final String label, final int cc)
    {
        final JButton button = new JButton (label);
        button.setMargin (new java.awt.Insets (3, 5, 3, 5));
        button.putClientProperty ("baseBackground", button.getBackground ());
        controlButtons.put (cc, button);
        button.addMouseListener (new MouseAdapter () {
            @Override public void mousePressed (final MouseEvent event) { device.pressButton (cc); }
            @Override public void mouseReleased (final MouseEvent event) { device.releaseButton (cc); }
        });
        return button;
    }

    private void releaseAll ()
    {
        for (int row = 0; row < 8; row++)
            for (int column = 0; column < 8; column++)
                if (device.isPadPressed (row, column)) device.releasePad (row, column);
        for (int cc = 0; cc < 128; cc++)
            if (device.isButtonPressed (cc)) device.releaseButton (cc);
        for (int i = 0; i < 9; i++)
            if (encoderTouch[i].isSelected ()) device.touchEncoder (i, false);
        for (int i = 9; i < 11; i++)
            if (encoderTouch[i].isSelected ()) device.touchSmallEncoder (i - 9, false);
        device.touchRibbon (false);
    }

    private void turn (final int index, final int direction)
    {
        if (index < 9) device.turnEncoderBy (index, direction);
        else device.turnSmallEncoderBy (index - 9, direction);
    }

    private void refreshFeedback ()
    {
        refreshLcd ();
        for (int row = 0; row < 8; row++)
            for (int column = 0; column < 8; column++)
            {
                final int [] rgb = device.getPadRgb (row, column);
                if (rgb[0] + rgb[1] + rgb[2] > 0)
                    pads[row][column].setBackground (new Color (rgb[0], rgb[1], rgb[2]));
                else
                    pads[row][column].setBackground (device.isPadPressed (row, column)
                        ? new Color (90, 90, 90) : new Color (55, 58, 62));
            }
        for (int i = 0; i < 8; i++)
        {
            final int [] rgb = device.getBottomRowRgb (i);
            bottom[i].setBackground (new Color (rgb[0], rgb[1], rgb[2]));
        }
        for (final Map.Entry<Integer, JButton> entry: controlButtons.entrySet ())
        {
            final int value = device.getCCLed (entry.getKey ());
            entry.getValue ().setBackground (value > 0
                ? new Color (0, Math.min (255, value * 2), 0)
                : (Color) entry.getValue ().getClientProperty ("baseBackground"));
        }

        final List<int[]> inputs = bench.capturedDeviceInput ();
        final List<String> outputs = bench.capturedOutput ();
        final List<int[]> raw = bench.capturedRawInputOutput ();
        final List<String> sysex = bench.capturedDeviceSysex ();
        while (seenInput < inputs.size ())
        {
            final int[] m = inputs.get (seenInput++);
            append ("DEVICE IN  " + hex (m[0]) + " " + hex (m[1]) + " " + hex (m[2]));
        }
        while (seenOutput < outputs.size ()) append ("EXTENSION OUT  " + outputs.get (seenOutput++));
        while (seenSysex < sysex.size ()) append ("DEVICE SYSEX IN  " + sysex.get (seenSysex++));
        while (seenRaw < raw.size ())
        {
            final int[] m = raw.get (seenRaw++);
            append ("HOST RAW  " + hex (m[0]) + " " + hex (m[1]) + " " + hex (m[2]));
        }
        status.setText ("MIDI in " + inputs.size () + " | Extension out " + outputs.size ()
            + " | Touch " + bench.getDispatchedTouchEvents () + " | Encoders "
            + bench.getDispatchedEncoderEvents () + " | Ribbon " + (ribbonTouched ? "touched" : "released"));
    }

    private void refreshLcd ()
    {
        for (int module = 0; module < 4; module++)
            for (int row = 0; row < 4; row++)
            {
                final String raw = device.getLcdModuleRow (row, module);
                final StringBuilder visible = new StringBuilder (17);
                for (int i = 0; i < raw.length (); i++)
                {
                    final char ch = raw.charAt (i);
                    visible.append (ch >= 32 && ch <= 126 ? ch : '·');
                }
                lcd[module][row].setText (visible.toString ());
                lcd[module][row].setToolTipText (glyphTooltip (raw));
            }
    }

    private static String glyphTooltip (final String raw)
    {
        final StringBuilder tip = new StringBuilder ("Raw LCD codes / custom 5×8 glyphs");
        for (int i = 0; i < raw.length (); i++)
        {
            final int code = raw.charAt (i);
            if (Push1DisplayGlyphs.isKnownCustomGlyph (code))
                tip.append ("\nColumn ").append (i + 1).append (" — glyph ").append (code)
                    .append ("\n").append (Push1DisplayGlyphs.renderAscii (code));
        }
        return tip.toString ();
    }

    private void append (final String line)
    {
        log.append (line + System.lineSeparator ());
        log.setCaretPosition (log.getDocument ().getLength ());
    }

    private static String hex (final int value) { return String.format ("%02X", value & 0xFF); }

    private static JPanel panel ()
    {
        final JPanel panel = new JPanel ();
        panel.setBackground (PANEL);
        panel.setForeground (Color.WHITE);
        return panel;
    }
}
