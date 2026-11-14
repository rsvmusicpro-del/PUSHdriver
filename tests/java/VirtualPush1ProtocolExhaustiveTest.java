import de.mossgrabers.controller.ableton.push.controller.Push1DisplayProtocol;

import java.util.ArrayList;
import java.util.List;

/**
 * Exhaustive deterministic protocol contract for the virtual Push 1.
 * No Bitwig process, native MIDI driver, USB device or physical Push is used.
 */
public final class VirtualPush1ProtocolExhaustiveTest
{
    private static final int [] BUTTON_CCS = {
        3, 9, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
        36, 37, 38, 39, 40, 41, 42, 43,
        44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63,
        85, 86, 87, 88, 89, 90,
        102, 103, 104, 105, 106, 107, 108, 109,
        110, 111, 112, 113, 114, 115, 116, 117, 118, 119
    };

    private VirtualPush1ProtocolExhaustiveTest () { }

    public static void main (final String [] args)
    {
        final List<int[]> input = new ArrayList<> ();
        final VirtualPush1Device device = new VirtualPush1Device (
            new VirtualPush1Device.MidiLink () {
                @Override public void shortMessage (final int status, final int data1, final int data2)
                { input.add (new int [] { status, data1, data2 }); }
                @Override public void sysex (final String frame) { }
            }, null);

        testAllPads (device, input);
        testAllPadVelocityAndPressureRanges (device, input);
        testAllButtons (device, input);
        testAllEncoders (device, input);
        testAllRelativeEncoderSteps (device, input);
        testAllPitchBendValues (device, input);
        testAllRgbAddresses (device);
        testAllLcdRowsAndCharacters (device);
        testMalformedFrames (device);

        System.out.println ("Virtual Push 1 exhaustive protocol contract passed: "
            + "64 pads, " + BUTTON_CCS.length + " buttons, 11 encoders, "
            + "all pad velocity/pressure values, all relative encoder steps, all 16,384 ribbon positions, "
            + "72 RGB addresses, 4x68 LCD and malformed-frame boundaries.");
    }

    private static void testAllPads (final VirtualPush1Device device, final List<int[]> input)
    {
        for (int note = 36; note <= 99; note++)
        {
            final int row = (note - 36) / 8;
            final int col = (note - 36) % 8;
            device.pressPad (row, col, 1 + note % 127);
            requireLast (input, 0x90, note, 1 + note % 127, "pad note-on");
            device.pressPadPressure (row, col, 0);
            requireLast (input, 0xA0, note, 0, "zero pressure");
            device.pressPadPressure (row, col, 127);
            requireLast (input, 0xA0, note, 127, "maximum pressure");
            device.releasePad (row, col);
            requireLast (input, 0x80, note, 0, "pad note-off");
            if (device.isPadPressed (row, col) || device.getPadPressure (row, col) != 0)
                throw new AssertionError ("Pad state did not reset at note " + note);
        }
    }

    /** Exercise every valid velocity and poly-aftertouch value on every physical pad. */
    private static void testAllPadVelocityAndPressureRanges (final VirtualPush1Device device, final List<int[]> input)
    {
        for (int note = 36; note <= 99; note++)
        {
            final int row = (note - 36) / 8;
            final int col = (note - 36) % 8;
            for (int velocity = 1; velocity <= 127; velocity++)
            {
                device.pressPad (row, col, velocity);
                requireLast (input, 0x90, note, velocity, "pad velocity range");
            }
            for (int pressure = 0; pressure <= 127; pressure++)
            {
                device.pressPadPressure (row, col, pressure);
                requireLast (input, 0xA0, note, pressure, "pad pressure range");
            }
            device.releasePad (row, col);
            requireLast (input, 0x80, note, 0, "pad release after full expression range");
        }
    }

    private static void testAllButtons (final VirtualPush1Device device, final List<int[]> input)
    {
        for (final int cc: BUTTON_CCS)
        {
            device.pressButton (cc);
            requireLast (input, 0xB0, cc, 127, "button press CC " + cc);
            if (!device.isButtonPressed (cc))
                throw new AssertionError ("Button state did not latch on CC " + cc);
            device.releaseButton (cc);
            requireLast (input, 0xB0, cc, 0, "button release CC " + cc);
            if (device.isButtonPressed (cc))
                throw new AssertionError ("Button state did not clear on CC " + cc);
        }
    }

    private static void testAllEncoders (final VirtualPush1Device device, final List<int[]> input)
    {
        for (int encoder = 0; encoder < 9; encoder++)
        {
            device.turnEncoderBy (encoder, 63);
            requireLast (input, 0xB0, 71 + encoder, 63, "positive encoder boundary");
            device.turnEncoderBy (encoder, -63);
            requireLast (input, 0xB0, 71 + encoder, 65, "negative encoder boundary");
            device.touchEncoder (encoder, true);
            requireLast (input, 0x90, encoder, 127, "encoder touch");
            device.touchEncoder (encoder, false);
            requireLast (input, 0x90, encoder, 0, "encoder touch release");
        }
        for (int encoder = 0; encoder < 2; encoder++)
        {
            device.turnSmallEncoderBy (encoder, -1);
            requireLast (input, 0xB0, 14 + encoder, 127, "small encoder negative step");
            device.touchSmallEncoder (encoder, true);
            requireLast (input, 0x90, encoder == 0 ? 10 : 9, 127, "small encoder touch");
            device.touchSmallEncoder (encoder, false);
            requireLast (input, 0x90, encoder == 0 ? 10 : 9, 0, "small encoder release");
        }
        device.turnEncoderBy (0, 0);
        device.turnSmallEncoderBy (0, 0);
        if (input.size () != 64 * 4 + 64 * 256 + BUTTON_CCS.length * 2 + 9 * 4 + 2 * 3)
            throw new AssertionError ("Zero-step encoder emitted an unexpected MIDI event");
        device.touchRibbon (true);
        requireLast (input, 0x90, 12, 127, "ribbon touch");
        device.moveRibbon (0);
        requireLast (input, 0xE0, 0, 0, "minimum pitch bend");
        device.moveRibbon (8192);
        requireLast (input, 0xE0, 0, 64, "center pitch bend");
        device.moveRibbon (16383);
        requireLast (input, 0xE0, 127, 127, "maximum pitch bend");
        device.touchRibbon (false);
        requireLast (input, 0x90, 12, 0, "ribbon release");
    }

    /** Walk the complete signed relative-step domain on all eleven encoders. */
    private static void testAllRelativeEncoderSteps (final VirtualPush1Device device, final List<int[]> input)
    {
        for (int encoder = 0; encoder < 11; encoder++)
            for (int steps = -63; steps <= 63; steps++)
            {
                final int before = input.size ();
                if (encoder < 9)
                    device.turnEncoderBy (encoder, steps);
                else
                    device.turnSmallEncoderBy (encoder - 9, steps);
                if (steps == 0)
                {
                    if (input.size () != before)
                        throw new AssertionError ("Zero encoder movement emitted MIDI");
                }
                else
                    requireLast (input, 0xB0, encoder < 9 ? 71 + encoder : 14 + encoder - 9,
                        steps < 0 ? 128 + steps : steps, "complete relative encoder range");
            }
    }

    /** Verify every representable 14-bit ribbon/pitch-bend position. */
    private static void testAllPitchBendValues (final VirtualPush1Device device, final List<int[]> input)
    {
        for (int value = 0; value <= 16383; value++)
        {
            device.moveRibbon (value);
            requireLast (input, 0xE0, value & 0x7F, (value >>> 7) & 0x7F,
                "complete 14-bit ribbon range");
        }
    }

    private static void testAllRgbAddresses (final VirtualPush1Device device)
    {
        for (int address = 0; address < 72; address++)
        {
            final int r = address & 15;
            final int g = (address + 5) & 15;
            final int b = (address + 11) & 15;
            device.receiveSysex (String.format (
                "F0 47 7F 15 04 00 08 %02X %02X %02X %02X %02X %02X %02X F7",
                address, r, r, g, g, b, b));
            final int [] actual = address < 64
                ? device.getPadRgb (address / 8, address % 8)
                : device.getBottomRowRgb (address - 64);
            if (actual[0] != r * 17 || actual[1] != g * 17 || actual[2] != b * 17)
                throw new AssertionError ("RGB address/color mismatch at hardware index " + address);
        }
        expectInvalid (() -> device.receiveSysex (
            "F0 47 7F 15 04 00 08 48 00 00 00 00 00 00 F7"));
    }

    private static void testAllLcdRowsAndCharacters (final VirtualPush1Device device)
    {
        for (int row = 0; row < 4; row++)
        {
            final StringBuilder frame = new StringBuilder ("F0 47 7F 15 ");
            frame.append (String.format ("%02X 00 45 00", 0x18 + row));
            final StringBuilder expected = new StringBuilder ();
            for (int column = 0; column < 68; column++)
            {
                final int value = (row * 68 + column) & 0x7F;
                frame.append (String.format (" %02X", value));
                expected.append ((char) value);
            }
            frame.append (" F7");
            device.receiveSysex (frame.toString ());
            if (!expected.toString ().equals (device.getLcdRow (row)))
                throw new AssertionError ("LCD row/character decoding mismatch at row " + row);
            final String encoded = Push1DisplayProtocol.encodeLine (row, "X".repeat (68));
            if (encoded.split (" ").length != 77)
                throw new AssertionError ("LCD encoder did not produce a complete 77-byte row");
        }
    }

    private static void testMalformedFrames (final VirtualPush1Device device)
    {
        expectInvalid (() -> device.receiveSysex ("F0 47 7F 15 04 00 08 00 10 00 00 00 00 00 F7"));
        expectInvalid (() -> device.receiveSysex ("F0 47 7F 15 04 00 08 00 00 00 00 00 00 00"));
        expectInvalid (() -> device.receiveSysex ("F0 47 7F 15 1C 00 45 00 " + "20 ".repeat (68) + "F7"));
        expectInvalid (() -> device.receiveSysex ("F0 47 7F 15 18 00 45 00 " + "80 ".repeat (68) + "F7"));
        expectInvalid (() -> device.pressPad (0, 0, 0));
        expectIllegalState (() -> device.pressPadPressure (0, 0, 1));
        expectInvalid (() -> device.turnEncoderBy (0, 64));
        expectInvalid (() -> device.moveRibbon (16384));
    }

    private static void requireLast (final List<int[]> messages, final int status,
        final int data1, final int data2, final String label)
    {
        final int[] actual = messages.get (messages.size () - 1);
        if (actual[0] != status || actual[1] != data1 || actual[2] != data2)
            throw new AssertionError (label + " mismatch: expected "
                + status + "/" + data1 + "/" + data2 + " got "
                + actual[0] + "/" + actual[1] + "/" + actual[2]);
    }

    private static void expectInvalid (final Runnable action)
    {
        try { action.run (); }
        catch (final IllegalArgumentException expected) { return; }
        throw new AssertionError ("Invalid hardware protocol input was accepted");
    }

    private static void expectIllegalState (final Runnable action)
    {
        try { action.run (); }
        catch (final IllegalStateException expected) { return; }
        throw new AssertionError ("Invalid hardware state transition was accepted");
    }
}
