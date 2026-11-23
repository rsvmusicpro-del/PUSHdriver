import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import de.mossgrabers.controller.ableton.push.controller.Push1DisplayLayout;
import de.mossgrabers.controller.ableton.push.controller.Push1DisplayGlyphs;

/**
 * Hardware-independent Ableton Push 1 endpoint emulator.
 *
 * The class models the physical MIDI surface, not the Bitwig controller
 * semantics. It can be connected to an IMidiInput callback on its input side;
 * outgoing IMidiOutput messages are fed back through receiveShort/receiveSysex
 * to observe LEDs and the 4x68 LCD. It has no native MIDI or USB dependency.
 */
public final class VirtualPush1Device
{
    public interface MidiLink
    {
        void shortMessage (int status, int data1, int data2);
        void sysex (String hexadecimalFrame);
    }

    public interface EventListener
    {
        void onShortMessage (int status, int data1, int data2);
        void onSysex (String hexadecimalFrame);
    }

    public static final int CHANNEL_STATUS = 0xB0;
    public static final int NOTE_ON = 0x90;
    public static final int NOTE_OFF = 0x80;
    public static final int POLY_AFTERTOUCH = 0xA0;
    public static final int PITCH_BEND = 0xE0;
    public static final int PAD_FIRST_NOTE = 36;
    public static final int PAD_COUNT = 64;
    public static final int PAD_VELOCITY_MAX = 127;
    public static final int TAP_TEMPO = 3;
    public static final int METRONOME = 9;
    public static final int LCD_ROWS = 4;
    public static final int LCD_COLUMNS = 68;

    public static final int SHIFT = 49;
    public static final int LEFT = 44;
    public static final int RIGHT = 45;
    public static final int UP = 46;
    public static final int DOWN = 47;
    /** Push 1's In button (CC 62). */
    public static final int IN = 62;
    /** Push 1's Out button (CC 63). */
    public static final int OUT = 63;
    public static final int PLAY = 85;
    public static final int RECORD = 86;
    public static final int NEW = 87;
    public static final int NOTE = 50;
    public static final int SESSION = 51;
    public static final int DEVICE = 110;
    public static final int BROWSE = 111;
    public static final int TRACK = 112;
    public static final int CLIP = 113;
    public static final int VOLUME = 114;
    public static final int PAN_SEND = 115;
    public static final int TOP_ROW_FIRST = 20;
    public static final int MASTER = 28;
    public static final int STOP = 29;
    public static final int BOTTOM_ROW_FIRST = 102;
    public static final int REPEAT_BUTTON_FIRST = 36;
    public static final int SELECT = 48;
    public static final int ADD_EFFECT = 52;
    public static final int ADD_TRACK = 53;
    public static final int OCTAVE_DOWN = 54;
    public static final int OCTAVE_UP = 55;
    public static final int REPEAT = 56;
    public static final int ACCENT = 57;
    public static final int SCALES = 58;
    public static final int USER = 59;
    public static final int MUTE = 60;
    public static final int SOLO = 61;
    public static final int DUPLICATE = 88;
    public static final int AUTOMATION = 89;
    public static final int FIXED_LENGTH = 90;
    public static final int QUANTIZE = 116;
    public static final int DOUBLE = 117;
    public static final int DELETE = 118;
    public static final int UNDO = 119;
    /** Physical Push 1 footswitch 1 MIDI CC. */
    public static final int FOOTSWITCH1 = 64;
    /** Physical Push 1 footswitch 2 MIDI CC. */
    public static final int FOOTSWITCH2 = 69;

    private final MidiLink link;
    private final EventListener observer;
    private final Map<Integer, Integer> noteLeds = new HashMap<> ();
    private final Map<Integer, Integer> ccLeds = new HashMap<> ();
    private final String [] lcd = new String [LCD_ROWS];
    private final boolean [] pressedPads = new boolean [PAD_COUNT];
    private final int [] padPressure = new int [PAD_COUNT];
    private final int [][] padRgb = new int [PAD_COUNT][3];
    private final int [][] bottomRowRgb = new int [8][3];
    private final boolean [] pressedButtons = new boolean [128];
    private final int [] encoderValues = new int [11];

    public VirtualPush1Device (final MidiLink link, final EventListener observer)
    {
        this.link = Objects.requireNonNull (link, "link");
        this.observer = observer;
        Arrays.fill (this.lcd, " ".repeat (LCD_COLUMNS));
    }

    public static int padNote (final int row, final int column)
    {
        if (row < 0 || row >= 8 || column < 0 || column >= 8)
            throw new IllegalArgumentException ("Pad coordinates must be in [0,7]");
        return PAD_FIRST_NOTE + row * 8 + column;
    }

    public static int encoderCC (final int encoder)
    {
        if (encoder < 0 || encoder > 8)
            throw new IllegalArgumentException ("Main encoder index must be in [0,8]");
        return 71 + encoder;
    }

    public void pressButton (final int cc)
    {
        check7Bit (cc, "button CC");
        this.pressedButtons[cc] = true;
        this.sendShort (CHANNEL_STATUS, cc, 127);
    }

    public void releaseButton (final int cc)
    {
        check7Bit (cc, "button CC");
        this.pressedButtons[cc] = false;
        this.sendShort (CHANNEL_STATUS, cc, 0);
    }

    public void tapButton (final int cc)
    {
        this.pressButton (cc);
        this.releaseButton (cc);
    }

    public void pressPad (final int row, final int column, final int velocity)
    {
        if (velocity < 1 || velocity > PAD_VELOCITY_MAX)
            throw new IllegalArgumentException ("Pad velocity must be in [1,127]");
        final int note = padNote (row, column);
        this.pressedPads[note - PAD_FIRST_NOTE] = true;
        this.sendShort (NOTE_ON, note, velocity);
    }

    public void releasePad (final int row, final int column)
    {
        final int note = padNote (row, column);
        this.pressedPads[note - PAD_FIRST_NOTE] = false;
        this.padPressure[note - PAD_FIRST_NOTE] = 0;
        this.sendShort (NOTE_OFF, note, 0);
    }

    /** Sends a raw value for one of the nine Push endless encoders. */
    public void turnEncoder (final int encoder, final int midiValue)
    {
        if (encoder < 0 || encoder > 8)
            throw new IllegalArgumentException ("Main encoder index must be in [0,8]");
        check7Bit (midiValue, "encoder value");
        this.encoderValues[encoder] = midiValue;
        this.sendShort (CHANNEL_STATUS, encoderCC (encoder), midiValue);
    }

    /** Send a signed relative step count using Push 1's two's-complement encoding. */
    public void turnEncoderBy (final int encoder, final int signedSteps)
    {
        if (signedSteps != 0)
            this.turnEncoder (encoder, encodeRelativeSteps (signedSteps));
    }

    /** Small encoders use CC 14 and 15 on Push 1. */
    public void turnSmallEncoder (final int encoder, final int midiValue)
    {
        if (encoder < 0 || encoder > 1)
            throw new IllegalArgumentException ("Small encoder index must be 0 or 1");
        check7Bit (midiValue, "encoder value");
        this.encoderValues[9 + encoder] = midiValue;
        this.sendShort (CHANNEL_STATUS, 14 + encoder, midiValue);
    }

    public void turnSmallEncoderBy (final int encoder, final int signedSteps)
    {
        if (signedSteps != 0)
            this.turnSmallEncoder (encoder, encodeRelativeSteps (signedSteps));
    }

    public void touchEncoder (final int encoder, final boolean touched)
    {
        if (encoder < 0 || encoder > 8)
            throw new IllegalArgumentException ("Main encoder index must be in [0,8]");
        this.sendShort (NOTE_ON, encoder, touched ? 127 : 0);
    }

    public void touchSmallEncoder (final int encoder, final boolean touched)
    {
        if (encoder < 0 || encoder > 1)
            throw new IllegalArgumentException ("Small encoder index must be 0 or 1");
        final int note = encoder == 0 ? 10 : 9;
        this.sendShort (NOTE_ON, note, touched ? 127 : 0);
    }

    public void touchRibbon (final boolean touched)
    {
        this.sendShort (NOTE_ON, 12, touched ? 127 : 0);
    }

    /** Pitch bend value is split into the standard MIDI LSB/MSB pair. */
    public void moveRibbon (final int value14Bit)
    {
        if (value14Bit < 0 || value14Bit > 16383)
            throw new IllegalArgumentException ("Pitch bend must be in [0,16383]");
        this.sendShort (PITCH_BEND, value14Bit & 0x7F, (value14Bit >>> 7) & 0x7F);
    }

    /** Polyphonic aftertouch sent while a physical pad remains depressed. */
    public void pressPadPressure (final int row, final int column, final int pressure)
    {
        if (pressure < 0 || pressure > PAD_VELOCITY_MAX)
            throw new IllegalArgumentException ("Pad pressure must be in [0,127]");
        final int note = padNote (row, column);
        final int index = note - PAD_FIRST_NOTE;
        if (!this.pressedPads[index])
            throw new IllegalStateException ("Pad pressure requires a depressed pad");
        this.padPressure[index] = pressure;
        this.sendShort (POLY_AFTERTOUCH, note, pressure);
    }

    public int getPadPressure (final int row, final int column)
    {
        return this.padPressure[padNote (row, column) - PAD_FIRST_NOTE];
    }

    public int [] getPadRgb (final int row, final int column)
    {
        return this.padRgb[padNote (row, column) - PAD_FIRST_NOTE].clone ();
    }

    public int [] getBottomRowRgb (final int button)
    {
        if (button < 0 || button >= 8)
            throw new IllegalArgumentException ("Bottom row button index must be in [0,7]");
        return this.bottomRowRgb[button].clone ();
    }

    /** Encode signed movement as Push 1 seven-bit two's complement. */
    public static int encodeRelativeSteps (final int signedSteps)
    {
        if (signedSteps < -63 || signedSteps > 63)
            throw new IllegalArgumentException ("Relative encoder movement must be in [-63,63]");
        return signedSteps < 0 ? 128 + signedSteps : signedSteps;
    }

    public void injectSysex (final String hexadecimalFrame)
    {
        validateHexSysex (hexadecimalFrame);
        if (this.observer != null)
            this.observer.onSysex (hexadecimalFrame);
        this.link.sysex (hexadecimalFrame);
    }

    /** Observe a message sent by the extension back to the virtual hardware. */
    public void receiveShort (final int status, final int data1, final int data2)
    {
        validateShort (status, data1, data2);
        final int family = status & 0xF0;
        if (family == POLY_AFTERTOUCH && data1 >= PAD_FIRST_NOTE && data1 < PAD_FIRST_NOTE + PAD_COUNT)
            this.padPressure[data1 - PAD_FIRST_NOTE] = data2;
        else if (family == NOTE_ON || family == NOTE_OFF)
            this.noteLeds.put (data1, family == NOTE_OFF || data2 == 0 ? 0 : data2);
        else if (family == CHANNEL_STATUS)
            this.ccLeds.put (data1, data2);
    }

    /** Decode Push 1 pad/bottom-row RGB frames and four 68-character LCD rows. */
    public void receiveSysex (final String hexadecimalFrame)
    {
        validateHexSysex (hexadecimalFrame);
        final String [] tokens = hexadecimalFrame.trim ().split ("\\s+");
        if (isPadRgbFrame (tokens))
        {
            final int pad = Integer.parseInt (tokens[7], 16);
            if (pad > 71)
                throw new IllegalArgumentException ("Invalid Push 1 RGB LED index");
            final int [] rgb = {
                (parseNibble (tokens[8]) << 4) | parseNibble (tokens[9]),
                (parseNibble (tokens[10]) << 4) | parseNibble (tokens[11]),
                (parseNibble (tokens[12]) << 4) | parseNibble (tokens[13])
            };
            if (pad < PAD_COUNT)
                System.arraycopy (rgb, 0, this.padRgb[pad], 0, 3);
            else
                System.arraycopy (rgb, 0, this.bottomRowRgb[pad - PAD_COUNT], 0, 3);
            return;
        }

        if (tokens.length != 77 || !"F0".equalsIgnoreCase (tokens[0])
                || !"47".equalsIgnoreCase (tokens[1]) || !"7F".equalsIgnoreCase (tokens[2])
                || !"15".equalsIgnoreCase (tokens[3]) || !"00".equalsIgnoreCase (tokens[5])
                || !"45".equalsIgnoreCase (tokens[6]) || !"00".equalsIgnoreCase (tokens[7])
                || !"F7".equalsIgnoreCase (tokens[76]))
            throw new IllegalArgumentException ("Not a Push 1 LCD row or RGB LED frame");
        final int row = Integer.parseInt (tokens[4], 16) - 0x18;
        if (row < 0 || row >= LCD_ROWS)
            throw new IllegalArgumentException ("Invalid Push 1 LCD row selector");
        final StringBuilder text = new StringBuilder (LCD_COLUMNS);
        for (int i = 8; i < 76; i++)
        {
            final int character = Integer.parseInt (tokens[i], 16);
            if (character > 0x7F)
                throw new IllegalArgumentException ("LCD character is not seven-bit ASCII");
            text.append ((char) character);
        }
        this.lcd[row] = text.toString ();
    }

    private static boolean isPadRgbFrame (final String [] tokens)
    {
        return tokens.length == 15 && "F0".equalsIgnoreCase (tokens[0])
            && "47".equalsIgnoreCase (tokens[1]) && "7F".equalsIgnoreCase (tokens[2])
            && "15".equalsIgnoreCase (tokens[3]) && "04".equalsIgnoreCase (tokens[4])
            && "00".equalsIgnoreCase (tokens[5]) && "08".equalsIgnoreCase (tokens[6])
            && "F7".equalsIgnoreCase (tokens[14]);
    }

    private static int parseNibble (final String token)
    {
        final int value = Integer.parseInt (token, 16);
        if (value > 0x0F)
            throw new IllegalArgumentException ("RGB SysEx color components must be 4-bit nibbles");
        return value;
    }

    public boolean isButtonPressed (final int cc) { return this.pressedButtons[cc]; }
    public boolean isPadPressed (final int row, final int column) { return this.pressedPads[padNote (row, column) - PAD_FIRST_NOTE]; }
    public int getNoteLed (final int note) { return this.noteLeds.getOrDefault (note, 0); }
    public int getCCLed (final int cc) { return this.ccLeds.getOrDefault (cc, 0); }
    /** Full logical 68-character row, preserving Push custom glyph codes. */
    public String getLcdRow (final int row) { return checkedLcdRow (row); }

    /** One of the four physical 17-character LCD modules, left to right. */
    public String getLcdModuleRow (final int row, final int module)
    {
        final String line = checkedLcdRow (row);
        final int start = Push1DisplayLayout.blockStartColumn (module);
        return line.substring (start, start + Push1DisplayLayout.MODULE_COLUMNS);
    }

    /** Four rows of one physical LCD module, returned as a defensive snapshot. */
    public String [] getLcdModuleSnapshot (final int module)
    {
        Push1DisplayLayout.blockStartColumn (module);
        final String [] result = new String [Push1DisplayLayout.MODULE_ROWS];
        for (int row = 0; row < result.length; row++)
            result[row] = getLcdModuleRow (row, module);
        return result;
    }

    /** One 17-character logical display block (one physical LCD module). */
    public String getLcdBlock (final int row, final int block)
    {
        return getLcdModuleRow (row, block);
    }

    /** One logical parameter cell; physical widths alternate 9 and 8 characters. */
    public String getLcdCell (final int row, final int cell)
    {
        final String line = checkedLcdRow (row);
        final int start = Push1DisplayLayout.cellStartColumn (cell);
        return line.substring (start, start + Push1DisplayLayout.cellWidth (cell));
    }

    /** Raw seven-bit character code at a global LCD column. */
    public int getLcdCharacter (final int row, final int column)
    {
        final String moduleRow = getLcdModuleRow (row, Push1DisplayLayout.moduleForColumn (column));
        return moduleRow.charAt (Push1DisplayLayout.moduleColumn (column));
    }

    /**
     * Return the exact extracted 5x8 bitmap for a programmable LCD glyph.
     * Standard controller-ROM glyphs return null and remain firmware-owned.
     */
    public int [] getLcdGlyphBitmap (final int row, final int column)
    {
        final int characterCode = getLcdCharacter (row, column);
        return Push1DisplayGlyphs.isKnownCustomGlyph (characterCode)
            ? Push1DisplayGlyphs.bitmapRows (characterCode)
            : null;
    }

    public String [] getLcdSnapshot () { return this.lcd.clone (); }

    private String checkedLcdRow (final int row)
    {
        if (row < 0 || row >= Push1DisplayLayout.ROWS)
            throw new IllegalArgumentException ("Push 1 LCD row must be in [0,3]");
        return this.lcd[row];
    }
    public Map<Integer, Integer> getNoteLedSnapshot () { return Collections.unmodifiableMap (new HashMap<> (this.noteLeds)); }
    public Map<Integer, Integer> getCCLedSnapshot () { return Collections.unmodifiableMap (new HashMap<> (this.ccLeds)); }

    private void sendShort (final int status, final int data1, final int data2)
    {
        validateShort (status, data1, data2);
        if (this.observer != null)
            this.observer.onShortMessage (status, data1, data2);
        this.link.shortMessage (status, data1, data2);
    }

    private static void validateShort (final int status, final int data1, final int data2)
    {
        if (status < 0x80 || status > 0xEF)
            throw new IllegalArgumentException ("Invalid MIDI channel status");
        check7Bit (data1, "MIDI data1");
        check7Bit (data2, "MIDI data2");
    }

    private static void check7Bit (final int value, final String label)
    {
        if (value < 0 || value > 0x7F)
            throw new IllegalArgumentException (label + " must be in [0,127]");
    }

    private static void validateHexSysex (final String frame)
    {
        if (frame == null || !frame.trim ().startsWith ("F0 ") || !frame.trim ().endsWith (" F7"))
            throw new IllegalArgumentException ("Malformed SysEx boundaries");
        for (final String token: frame.trim ().split ("\\s+"))
            if (!token.matches ("(?i)[0-9a-f]{2}"))
                throw new IllegalArgumentException ("Malformed SysEx byte: " + token);
    }
}
