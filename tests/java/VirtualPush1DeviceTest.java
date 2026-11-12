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
                device.releasePad (row, col);
                if (device.isPadPressed (row, col) || input.get (input.size () - 1)[0] != VirtualPush1Device.NOTE_OFF)
                    throw new AssertionError ("Pad release mapping failed at " + row + "," + col);
            }
        if (input.size () != 128)
            throw new AssertionError ("Expected 128 pad down/up messages");

        // Button matrix, Shift chord and transport button are actual CC events.
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
            device.turnEncoder (i, 64 + i);
            device.touchEncoder (i, true);
            device.touchEncoder (i, false);
        }
        device.turnSmallEncoder (0, 17);
        device.turnSmallEncoder (1, 18);
        device.touchSmallEncoder (0, true);
        device.touchSmallEncoder (1, true);
        device.touchRibbon (true);
        device.moveRibbon (8192);
        device.touchRibbon (false);

        // Use the extension's actual Push 1 LCD encoder, then decode the output.
        for (int row = 0; row < 4; row++)
        {
            final String text = (row == 0 ? "A" : " ").repeat (68);
            device.receiveSysex (Push1DisplayProtocol.encodeLine (row, text));
        }
        if (device.getLcdRow (0).length () != 68 || device.getLcdRow (0).charAt (0) != 'A'
                || device.getLcdRow (3).charAt (0) != ' ')
            throw new AssertionError ("LCD row decoding failed");

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
