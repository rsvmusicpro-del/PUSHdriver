import de.mossgrabers.controller.ableton.push.controller.Push1DisplayProtocol;

import java.util.ArrayList;
import java.util.List;

/** Deterministic hardware-surface tests; no Push, MIDI driver or DAW required. */
public final class VirtualPush1DeviceTest
{
    public static void main (final String [] args)
    {
        final List<int[]> input = new ArrayList<> ();
        final List<String> sysexInput = new ArrayList<> ();
        final VirtualPush1Device device = new VirtualPush1Device (
            new VirtualPush1Device.MidiLink () {
                @Override public void shortMessage (int status, int data1, int data2)
                { input.add (new int [] { status, data1, data2 }); }
                @Override public void sysex (String frame) { sysexInput.add (frame); }
            }, null);

        // Every physical pad has a stable, unique note identity and velocity.
        for (int row = 0; row < 8; row++)
            for (int col = 0; col < 8; col++)
            {
                final int note = VirtualPush1Device.padNote (row, col);
                device.pressPad (row, col, 100);
                if (!device.isPadPressed (row, col) || input.get (input.size () - 1)[1] != note)
                    throw new AssertionError ("Pad press mapping failed at " + row + "," + col);
                device.pressPadPressure (row, col, 72);
                if (input.get (input.size () - 1)[0] != VirtualPush1Device.POLY_AFTERTOUCH
                        || input.get (input.size () - 1)[1] != note || device.getPadPressure (row, col) != 72)
                    throw new AssertionError ("Pad polyphonic aftertouch mapping failed");
                device.releasePad (row, col);
                if (device.isPadPressed (row, col) || device.getPadPressure (row, col) != 0
                        || input.get (input.size () - 1)[0] != VirtualPush1Device.NOTE_OFF)
                    throw new AssertionError ("Pad release mapping failed at " + row + "," + col);
            }
        if (input.size () != 192)
            throw new AssertionError ("Expected 192 pad note/aftertouch messages");

        // Full hardware button groups follow the independent Push 1 CC map.
        for (int i = 0; i < 8; i++)
        {
            device.tapButton (VirtualPush1Device.TOP_ROW_FIRST + i);
            device.tapButton (VirtualPush1Device.BOTTOM_ROW_FIRST + i);
            device.tapButton (VirtualPush1Device.REPEAT_BUTTON_FIRST + i);
        }
        // Button matrix, Shift chord and transport button are actual CC events.
        if (VirtualPush1Device.IN != 62 || VirtualPush1Device.OUT != 63)
            throw new AssertionError ("Push 1 In/Out CC mapping regression");
        if (VirtualPush1Device.SHIFT != 49 || VirtualPush1Device.LEFT != 44
                || VirtualPush1Device.RIGHT != 45 || VirtualPush1Device.UP != 46
                || VirtualPush1Device.DOWN != 47 || VirtualPush1Device.PLAY != 85
                || VirtualPush1Device.RECORD != 86 || VirtualPush1Device.NEW != 87
                || VirtualPush1Device.DEVICE != 110 || VirtualPush1Device.BROWSE != 111
                || VirtualPush1Device.TRACK != 112 || VirtualPush1Device.CLIP != 113)
            throw new AssertionError ("Push 1 button CC mapping regression");
        if (VirtualPush1Device.encodeRelativeSteps (1) != 1
                || VirtualPush1Device.encodeRelativeSteps (-1) != 127
                || VirtualPush1Device.encodeRelativeSteps (-63) != 65)
            throw new AssertionError ("Relative encoder two's-complement encoding regression");
        expectInvalid (() -> VirtualPush1Device.encodeRelativeSteps (64));
        final int beforeZeroDelta = input.size ();
        device.turnEncoderBy (0, 0);
        device.turnSmallEncoderBy (0, 0);
        if (input.size () != beforeZeroDelta)
            throw new AssertionError ("Zero relative movement must not emit MIDI");
        device.pressButton (VirtualPush1Device.SHIFT);
        device.pressButton (VirtualPush1Device.LEFT);
        if (!device.isButtonPressed (VirtualPush1Device.SHIFT) || !device.isButtonPressed (VirtualPush1Device.LEFT))
            throw new AssertionError ("Shift+Left chord state was not retained");
        device.releaseButton (VirtualPush1Device.LEFT);
        device.releaseButton (VirtualPush1Device.SHIFT);
        device.tapButton (VirtualPush1Device.PLAY);

        // Nine main encoders, two small encoders, touch and ribbon controls.
        for (int i = 0; i < 9; i++)
        {
            device.turnEncoderBy (i, i + 1);
            device.touchEncoder (i, true);
            device.touchEncoder (i, false);
            if (input.get (input.size () - 1)[0] != VirtualPush1Device.NOTE_ON
                    || input.get (input.size () - 1)[1] != i || input.get (input.size () - 1)[2] != 0)
                throw new AssertionError ("Encoder touch release must be Note On velocity zero");
        }
        device.turnSmallEncoder (0, 17);
        device.turnSmallEncoder (1, 18);
        device.touchSmallEncoder (0, true);
        device.touchSmallEncoder (1, true);
        device.touchSmallEncoder (0, false);
        device.touchSmallEncoder (1, false);
        device.touchRibbon (true);
        device.moveRibbon (8192);
        device.touchRibbon (false);
        if (input.get (input.size () - 1)[0] != VirtualPush1Device.NOTE_ON
                || input.get (input.size () - 1)[1] != 12 || input.get (input.size () - 1)[2] != 0)
            throw new AssertionError ("Ribbon touch release must be Note On velocity zero");

        // Use the extension's actual Push 1 LCD encoder, then decode the output.
        for (int row = 0; row < 4; row++)
        {
            final String text = (row == 0 ? "A" : " ").repeat (68);
            device.receiveSysex (Push1DisplayProtocol.encodeLine (row, text));
        }
        if (device.getLcdRow (0).length () != 68 || device.getLcdRow (0).charAt (0) != 'A'
                || device.getLcdRow (3).charAt (0) != ' ')
            throw new AssertionError ("LCD row decoding failed");

        // Push 1 pad and lower-button RGB SysEx: 4-bit components are
        // expanded into the full 8-bit RGB channel values.
        device.receiveSysex ("F0 47 7F 15 04 00 08 00 0F 00 08 00 04 00 F7");
        final int [] rgb = device.getPadRgb (0, 0);
        if (rgb[0] != 240 || rgb[1] != 128 || rgb[2] != 64)
            throw new AssertionError ("Pad RGB SysEx decoding failed");
        device.receiveSysex ("F0 47 7F 15 04 00 08 40 01 02 03 04 05 06 F7");
        final int [] lowerButtonRgb = device.getBottomRowRgb (0);
        if (lowerButtonRgb[0] != 18 || lowerButtonRgb[1] != 52 || lowerButtonRgb[2] != 86)
            throw new AssertionError ("Bottom-row RGB SysEx decoding failed");
        expectInvalid (() -> device.receiveSysex ("F0 47 7F 15 04 00 08 00 10 00 08 00 04 00 F7"));
        expectInvalid (() -> device.receiveSysex ("F0 47 7F 15 5C 00 01 00 F7"));

        // Output note/CC messages update observable hardware LED state.
        device.receiveShort (VirtualPush1Device.NOTE_ON, 36, 5);
        device.receiveShort (VirtualPush1Device.NOTE_OFF, 36, 0);
        device.receiveShort (VirtualPush1Device.CHANNEL_STATUS, 49, 3);
        if (device.getNoteLed (36) != 0 || device.getCCLed (49) != 3)
            throw new AssertionError ("LED feedback capture failed");

        expectInvalid (() -> device.pressPad (0, 0, 128));
        expectInvalid (() -> device.moveRibbon (16384));
        expectInvalid (() -> VirtualPush1Device.padNote (8, 0));
        expectInvalid (() -> device.receiveSysex ("F0 01 F7"));

        System.out.println ("Virtual Push 1 device passed: 64 pads, buttons/Shift, "
            + "11 encoders, touch/ribbon, 4x68 LCD, LED feedback and MIDI transport.");
    }

    private static void expectInvalid (final Runnable action)
    {
        try { action.run (); }
        catch (final IllegalArgumentException expected) { return; }
        throw new AssertionError ("Invalid hardware event was accepted");
    }
}
