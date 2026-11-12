import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.midi.IMidiOutput;
import de.mossgrabers.framework.daw.midi.MidiShortCallback;
import de.mossgrabers.framework.daw.midi.MidiSysExCallback;

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
    private MidiShortCallback shortCallback;
    private MidiSysExCallback sysexCallback;
    private final IMidiInput input;
    private final IMidiOutput midiOutput;

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
                    case "toString" -> { return "Virtual Push 1 MIDI input"; }
                    default -> { return defaultValue (method.getReturnType ()); }
                }
                return null;
            });
        this.midiOutput = (IMidiOutput) Proxy.newProxyInstance (
            IMidiOutput.class.getClassLoader (), new Class<?> [] { IMidiOutput.class },
            (proxy, method, args) -> {
                if (method.getName ().startsWith ("send"))
                    this.output.add (method.getName () + " " + Arrays.toString (args));
                return defaultValue (method.getReturnType ());
            });
    }

    public IMidiInput input () { return this.input; }
    public IMidiOutput output () { return this.midiOutput; }

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

        bench.inject (0xB0, 62, 127); // Push 1 Out button, channel 1.
        bench.inject (0x90, 36, 100); // Pad note-on.
        bench.inject (0x80, 36, 0);   // Pad note-off.
        if (received.size () != 3 || received.get (0)[1] != 62
                || received.get (1)[2] != 100 || received.get (2)[0] != 0x80)
            throw new AssertionError ("Short MIDI input delivery/order regression");

        expectInvalid (() -> bench.inject (0xF0, 0, 0));
        expectInvalid (() -> bench.inject (0xB0, 128, 0));

        final String lcd = "F0 47 7F 15 18 00 45 00 "
            + "20 ".repeat (67) + "20 F7";
        bench.injectSysex (lcd);
        if (receivedSysex.size () != 1 || !receivedSysex.get (0).equals (lcd))
            throw new AssertionError ("SysEx input delivery regression");

        bench.output ().sendCC (62, 127);
        bench.output ().sendNote (36, 100);
        bench.output ().sendPitchbend (0, 64);
        bench.output ().sendSysex (lcd);
        final List<String> sent = bench.capturedOutput ();
        if (sent.size () != 4 || !sent.get (0).startsWith ("sendCC ")
                || !sent.get (3).startsWith ("sendSysex "))
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
