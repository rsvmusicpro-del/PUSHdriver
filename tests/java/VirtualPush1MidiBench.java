import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.midi.IMidiOutput;
import de.mossgrabers.framework.daw.midi.MidiShortCallback;
import de.mossgrabers.framework.daw.midi.MidiSysExCallback;
import de.mossgrabers.framework.controller.hardware.BindType;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwContinuousControl;
import de.mossgrabers.framework.controller.hardware.IHwRelativeKnob;
import de.mossgrabers.framework.controller.hardware.IHwFader;
import de.mossgrabers.framework.controller.valuechanger.RelativeEncoding;
import de.mossgrabers.framework.utils.ButtonEvent;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * API-17-compatible virtual Push 1 MIDI transport.
 *
 * It substitutes IMidiInput/IMidiOutput at the framework boundary, allowing
 * deterministic short-message and SysEx tests without a physical controller.
 * It intentionally does not pretend to emulate Bitwig's model or every mode.
 */
public final class VirtualPush1MidiBench
{
    private final List<String> output = new ArrayList<> ();
    private final List<int[]> rawInputOutput = new ArrayList<> ();
    private final List<ButtonBinding> buttonBindings = new ArrayList<> ();
    private final List<ContinuousBinding> continuousBindings = new ArrayList<> ();
    private final List<TouchBinding> touchBindings = new ArrayList<> ();
    private final List<int[]> rawDeviceInput = new ArrayList<> ();
    private int dispatchedButtonEvents;
    private int dispatchedEncoderEvents;
    private int dispatchedTouchEvents;
    private int dispatchedPitchbendEvents;
    private MidiShortCallback shortCallback;
    private MidiSysExCallback sysexCallback;
    private final IMidiInput input;
    private final IMidiOutput midiOutput;
    private final VirtualPush1Device device;

    public VirtualPush1MidiBench ()
    {
        this.input = (IMidiInput) Proxy.newProxyInstance (
            IMidiInput.class.getClassLoader (), new Class<?> [] { IMidiInput.class },
            (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "setMidiCallback" -> this.shortCallback = (MidiShortCallback) args[0];
                    case "setSysexCallback" -> this.sysexCallback = (MidiSysExCallback) args[0];
                    case "sendRawMidiEvent" -> this.rawInputOutput.add (new int [] {
                        (int) args[0], (int) args[1], (int) args[2] });
                    case "bind" -> {
                        if (args[0] instanceof IHwButton button)
                            this.buttonBindings.add (new ButtonBinding (button, (BindType) args[1],
                                (int) args[2], (int) args[3], args.length > 4 && args[4] instanceof Integer value ? value : -1));
                        else if (args[0] instanceof IHwContinuousControl control)
                            this.continuousBindings.add (new ContinuousBinding (control, (BindType) args[1],
                                (int) args[2], (int) args[3]));
                    }
                    case "bindTouch" -> {
                        if (args[0] instanceof IHwContinuousControl control)
                            this.touchBindings.add (new TouchBinding (control, (BindType) args[1],
                                (int) args[2], (int) args[3]));
                    }
                    case "unbind" -> {
                        this.buttonBindings.removeIf (binding -> binding.button () == args[0]);
                        this.continuousBindings.removeIf (binding -> binding.control () == args[0]);
                        this.touchBindings.removeIf (binding -> binding.control () == args[0]);
                    }
                    case "toString" -> { return "Virtual Push 1 MIDI input"; }
                    default -> { return defaultValue (method.getReturnType ()); }
                }
                return null;
            });
        this.midiOutput = (IMidiOutput) Proxy.newProxyInstance (
            IMidiOutput.class.getClassLoader (), new Class<?> [] { IMidiOutput.class },
            (proxy, method, args) -> {
                if (method.getName ().startsWith ("send"))
                {
                    this.output.add (method.getName () + " " + Arrays.toString (args));
                    this.routeOutputToDevice (method.getName (), args);
                }
                return defaultValue (method.getReturnType ());
            });
        this.device = new VirtualPush1Device (new VirtualPush1Device.MidiLink () {
            @Override public void shortMessage (int status, int data1, int data2)
            {
                final int family = status & 0xF0;
                if (family == 0xB0 || family == 0xE0)
                    VirtualPush1MidiBench.this.dispatchBindings (status, data1, data2);
                else
                {
                    if (VirtualPush1MidiBench.this.shortCallback == null)
                        throw new IllegalStateException ("No short-message callback is registered");
                    VirtualPush1MidiBench.this.shortCallback.handleMidi (status, data1, data2);
                    VirtualPush1MidiBench.this.dispatchBindings (status, data1, data2);
                }
            }
            @Override public void sysex (String frame)
            {
                if (VirtualPush1MidiBench.this.sysexCallback == null)
                    throw new IllegalStateException ("No SysEx callback is registered");
                VirtualPush1MidiBench.this.sysexCallback.handleMidi (frame);
            }
        }, new VirtualPush1Device.EventListener () {
            @Override public void onShortMessage (int status, int data1, int data2)
            { VirtualPush1MidiBench.this.rawDeviceInput.add (new int [] { status, data1, data2 }); }
            @Override public void onSysex (String frame) { }
        });
    }

    public IMidiInput input () { return this.input; }
    public IMidiOutput output () { return this.midiOutput; }
    public VirtualPush1Device device () { return this.device; }

    private record ButtonBinding (IHwButton button, BindType type, int channel, int control, int value) { }
    private record ContinuousBinding (IHwContinuousControl control, BindType type, int channel, int controlNumber) { }
    private record TouchBinding (IHwContinuousControl control, BindType type, int channel, int controlNumber) { }

    /** Deliver MIDI through the actual IMidiInput hardware bindings. */
    private void dispatchBindings (final int status, final int data1, final int data2)
    {
        final int family = status & 0xF0;
        final int channel = status & 0x0F;
        if (family == 0xB0)
        {
            for (final ButtonBinding binding: List.copyOf (this.buttonBindings))
            {
                if (binding.type () != BindType.CC || binding.channel () != channel
                        || binding.control () != data1 || binding.value () >= 0 && binding.value () != data2)
                    continue;
                final boolean pressed = data2 > 0;
                binding.button ().trigger (pressed ? ButtonEvent.DOWN : ButtonEvent.UP, data2 / 127.0);
                this.dispatchedButtonEvents++;
            }
            for (final ContinuousBinding binding: List.copyOf (this.continuousBindings))
            {
                if (!(binding.control () instanceof IHwRelativeKnob knob)
                        || binding.type () != BindType.CC || binding.channel () != channel
                        || binding.controlNumber () != data1)
                    continue;
                final int steps = data2 == 64 ? 0 : data2 > 64 ? data2 - 128 : data2;
                if (steps != 0)
                {
                    knob.handleValue (steps / 61.0);
                    this.dispatchedEncoderEvents++;
                }
            }
        }
        else if (family == 0xE0)
        {
            for (final ContinuousBinding binding: List.copyOf (this.continuousBindings))
                if (binding.control () instanceof IHwFader && binding.type () == BindType.PITCHBEND
                        && binding.channel () == channel && binding.control ().getPitchbendCommand () != null)
                {
                    binding.control ().getPitchbendCommand ().onPitchbend (data1, data2);
                    this.dispatchedPitchbendEvents++;
                }
        }
        else if (family == 0x80 || family == 0x90)
        {
            final boolean pressed = family == 0x90 && data2 > 0;
            for (final ButtonBinding binding: List.copyOf (this.buttonBindings))
                if (binding.type () == BindType.NOTE && binding.channel () == channel && binding.control () == data1
                        && (binding.value () < 0 || binding.value () == data2))
                {
                    binding.button ().trigger (pressed ? ButtonEvent.DOWN : ButtonEvent.UP, data2 / 127.0);
                    this.dispatchedButtonEvents++;
                }
            for (final TouchBinding binding: List.copyOf (this.touchBindings))
                if (binding.type () == BindType.NOTE && binding.channel () == channel && binding.controlNumber () == data1)
                {
                    binding.control ().triggerTouch (pressed);
                    this.dispatchedTouchEvents++;
                }
        }
    }

    private void routeOutputToDevice (final String method, final Object [] args)
    {
        switch (method)
        {
            case "sendCC" -> this.device.receiveShort (0xB0, (int) args[0], (int) args[1]);
            // DrivenByMoss channel arguments are zero-based (0..15).
            case "sendCCEx" -> this.device.receiveShort (0xB0 | (int) args[0], (int) args[1], (int) args[2]);
            case "sendNote" -> this.device.receiveShort (0x90, (int) args[0], (int) args[1]);
            case "sendNoteEx" -> this.device.receiveShort (0x90 | (int) args[0], (int) args[1], (int) args[2]);
            case "sendPitchbend" -> {
                if (args.length == 2)
                    this.device.receiveShort (0xE0, (int) args[0], (int) args[1]);
                else
                    this.device.receiveShort (0xE0 | (int) args[0], (int) args[1], (int) args[2]);
            }
            case "sendSysex" -> {
                final String frame = args[0] instanceof String text
                    ? text
                    : args[0] instanceof byte [] bytes ? toHex (bytes) : null;
                if (frame != null && isPush1LcdFrame (frame))
                    this.device.receiveSysex (frame);
            }
            default -> { /* Other output types remain visible in capturedOutput(). */ }
        }
    }

    private static boolean isPush1LcdFrame (final String frame)
    {
        final String [] tokens = frame.trim ().split ("\\s+");
        return tokens.length == 77 && tokens[0].equalsIgnoreCase ("F0")
            && tokens[1].equalsIgnoreCase ("47") && tokens[2].equalsIgnoreCase ("7F")
            && tokens[3].equalsIgnoreCase ("15")
            && (tokens[4].equalsIgnoreCase ("18") || tokens[4].equalsIgnoreCase ("19")
                || tokens[4].equalsIgnoreCase ("1A") || tokens[4].equalsIgnoreCase ("1B"));
    }

    private static byte [] hexToBytes (final String frame)
    {
        final String [] tokens = frame.trim ().split ("\\s+");
        final byte [] bytes = new byte [tokens.length];
        for (int i = 0; i < tokens.length; i++)
            bytes[i] = (byte) Integer.parseInt (tokens[i], 16);
        return bytes;
    }

    private static String toHex (final byte [] bytes)
    {
        final StringBuilder frame = new StringBuilder (bytes.length * 3);
        for (final byte value: bytes)
        {
            if (frame.length () > 0)
                frame.append (' ');
            frame.append (String.format ("%02X", value & 0xFF));
        }
        return frame.toString ();
    }

    /** Inject one MIDI 1.0 short message as the Bitwig callback would deliver it. */
    public void inject (final int status, final int data1, final int data2)
    {
        if (status < 0x80 || status > 0xEF)
            throw new IllegalArgumentException ("Expected a channel MIDI status byte");
        if (data1 < 0 || data1 > 0x7F || data2 < 0 || data2 > 0x7F)
            throw new IllegalArgumentException ("MIDI data bytes must be seven-bit values");
        if (this.shortCallback == null)
            throw new IllegalStateException ("No short-message callback is registered");
        this.shortCallback.handleMidi (status, data1, data2);
    }

    /** Inject a complete hexadecimal SysEx frame. */
    public void injectSysex (final String frame)
    {
        if (frame == null || !frame.startsWith ("F0 ") || !frame.endsWith (" F7"))
            throw new IllegalArgumentException ("Malformed SysEx boundaries");
        if (this.sysexCallback == null)
            throw new IllegalStateException ("No SysEx callback is registered");
        this.sysexCallback.handleMidi (frame);
    }

    public List<String> capturedOutput () { return List.copyOf (this.output); }
    public List<int[]> capturedRawInputOutput () { return List.copyOf (this.rawInputOutput); }
    public List<int[]> capturedDeviceInput () { return List.copyOf (this.rawDeviceInput); }
    public int getDispatchedButtonEvents () { return this.dispatchedButtonEvents; }
    public int getDispatchedEncoderEvents () { return this.dispatchedEncoderEvents; }
    public int getDispatchedTouchEvents () { return this.dispatchedTouchEvents; }
    public int getDispatchedPitchbendEvents () { return this.dispatchedPitchbendEvents; }

    private static Object defaultValue (final Class<?> type)
    {
        if (!type.isPrimitive () || type == void.class) return null;
        if (type == boolean.class) return Boolean.FALSE;
        if (type == byte.class) return Byte.valueOf ((byte) 0);
        if (type == short.class) return Short.valueOf ((short) 0);
        if (type == int.class) return Integer.valueOf (0);
        if (type == long.class) return Long.valueOf (0);
        if (type == float.class) return Float.valueOf (0);
        if (type == double.class) return Double.valueOf (0);
        if (type == char.class) return Character.valueOf ((char) 0);
        return null;
    }

    public static void main (final String [] args)
    {
        final VirtualPush1MidiBench bench = new VirtualPush1MidiBench ();
        final List<int[]> received = new ArrayList<> ();
        final List<String> receivedSysex = new ArrayList<> ();
        bench.input ().setMidiCallback ((status, data1, data2) ->
            received.add (new int [] { status, data1, data2 }));
        bench.input ().setSysexCallback (receivedSysex::add);

        final List<ButtonEvent> buttonEvents = new ArrayList<> ();
        final IHwButton button = (IHwButton) Proxy.newProxyInstance (
            IHwButton.class.getClassLoader (), new Class<?> [] { IHwButton.class },
            (proxy, method, invocationArgs) -> {
                if (method.getName ().equals ("trigger"))
                    buttonEvents.add ((ButtonEvent) invocationArgs[0]);
                return defaultValue (method.getReturnType ());
            });
        bench.input ().bind (button, BindType.CC, 0, VirtualPush1Device.IN);
        bench.device ().pressButton (VirtualPush1Device.IN);
        bench.device ().releaseButton (VirtualPush1Device.IN);
        bench.device ().pressPad (0, 0, 100);
        bench.device ().releasePad (0, 0);
        final List<int[]> rawInput = bench.capturedDeviceInput ();
        if (rawInput.size () != 4 || rawInput.get (0)[0] != 0xB0
                || rawInput.get (0)[1] != VirtualPush1Device.IN || rawInput.get (0)[2] != 127
                || rawInput.get (1)[2] != 0 || rawInput.get (2)[1] != 36
                || rawInput.get (2)[2] != 100 || rawInput.get (3)[0] != 0x80)
            throw new AssertionError ("Raw Push 1 MIDI input delivery/order regression");
        if (received.size () != 2 || received.get (0)[1] != 36 || received.get (0)[2] != 100
                || received.get (1)[0] != 0x80)
            throw new AssertionError ("Note callback delivery regression");
        if (!buttonEvents.equals (List.of (ButtonEvent.DOWN, ButtonEvent.UP)))
            throw new AssertionError ("MIDI input did not trigger the bound hardware button");

        expectInvalid (() -> bench.inject (0xF0, 0, 0));
        expectInvalid (() -> bench.inject (0xB0, 128, 0));

        final String lcd = "F0 47 7F 15 18 00 45 00 "
            + "20 ".repeat (67) + "20 F7";
        bench.injectSysex (lcd);
        if (receivedSysex.size () != 1 || !receivedSysex.get (0).equals (lcd))
            throw new AssertionError ("SysEx input delivery regression");

        bench.output ().sendCC (49, 3);
        bench.output ().sendCCEx (15, 49, 4);
        bench.output ().sendNote (36, 100);
        bench.output ().sendNoteEx (15, 37, 101);
        bench.output ().sendPitchbend (0, 64);
        bench.output ().sendPitchbend (15, 1, 65);
        bench.output ().sendSysex (lcd);
        bench.output ().sendSysex (hexToBytes (lcd));
        // Startup/pressure-mode SysEx shares the manufacturer prefix but is
        // not an LCD row and must not be decoded as one.
        bench.output ().sendSysex ("F0 47 7F 15 5C 00 01 00 F7");
        if (bench.device ().getCCLed (49) != 4 || bench.device ().getNoteLed (36) != 100
                || bench.device ().getNoteLed (37) != 101 || bench.device ().getLcdRow (0).length () != 68)
            throw new AssertionError ("MIDI output did not update virtual Push 1 LEDs/LCD");
        final List<String> sent = bench.capturedOutput ();
        if (sent.size () != 9 || !sent.get (0).startsWith ("sendCC ")
                || !sent.get (6).startsWith ("sendSysex ") || !sent.get (7).startsWith ("sendSysex ")
                || !sent.get (8).startsWith ("sendSysex "))
            throw new AssertionError ("MIDI output capture regression");

        System.out.println ("Virtual Push 1 MIDI bench passed: "
            + received.size () + " input messages, " + receivedSysex.size ()
            + " SysEx input, " + sent.size () + " output messages.");
    }

    private static void expectInvalid (final Runnable action)
    {
        try { action.run (); }
        catch (final IllegalArgumentException expected) { return; }
        throw new AssertionError ("Invalid MIDI message was accepted");
    }
}
