import com.bitwig.extension.ExtensionDefinition;
import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;
import de.mossgrabers.bitwig.framework.daw.HostImpl;
import de.mossgrabers.controller.ableton.push.PushControllerDefinition;
import de.mossgrabers.controller.ableton.push.controller.Push1Display;
import de.mossgrabers.framework.utils.OperatingSystem;
import de.mossgrabers.framework.utils.Pair;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import java.util.UUID;

/** Runtime smoke test for the exact SPI discovery path used by Bitwig Studio. */
public final class PushdriverExtensionDiscoveryTest
{
    private static final String EXPECTED_CLASS =
        "de.mossgrabers.bitwig.controller.ableton.push.Push1ControllerExtensionDefinition";
    private static final UUID EXPECTED_ID =
        UUID.fromString("DB830BE9-77B3-46F5-8F97-744901A94B56");

    public static void main(final String[] args)
    {
        // Reproduce HostImpl construction without a Bitwig process. Its constructor
        // eagerly loads device metadata; missing packaged resources caused an NPE here.
        final HostImpl host = new HostImpl(null);
        if (host.getInstrumentMetadata().isEmpty())
            throw new AssertionError("Packaged devices/Instruments.txt was not loaded");
        if (host.getAudioEffectMetadata().isEmpty())
            throw new AssertionError("Packaged devices/AudioEffects.txt was not loaded");

        verifyPackagedPanFormatter();

        // Validate the platform-specific MIDI auto-detection contract independently
        // of the CI runner's actual operating system.
        final PushControllerDefinition push = new PushControllerDefinition();
        if (push.getNumMidiInPorts() != 1 || push.getNumMidiOutPorts() != 1)
            throw new AssertionError("Push 1 must expose exactly one MIDI input and one MIDI output");
        if (push.claimUSBDevice() != null)
            throw new AssertionError("Push 1 MIDI-only discovery must not claim a USB-HID device");

        final List<Pair<String[], String[]>> windowsPairs = push.getMidiDiscoveryPairs(OperatingSystem.WINDOWS);
        final List<Pair<String[], String[]>> macPairs = push.getMidiDiscoveryPairs(OperatingSystem.MAC);
        final List<Pair<String[], String[]>> linuxPairs = push.getMidiDiscoveryPairs(OperatingSystem.LINUX);

        if (windowsPairs.size() != 20 || !containsPair(windowsPairs,
                "MIDIIN2 (Ableton Push)", "MIDIOUT2 (Ableton Push)"))
            throw new AssertionError("Windows Push 1 MIDI port discovery is incomplete or contaminated");
        if (macPairs.size() != 1 || !containsPair(macPairs,
                "Ableton Push User Port", "Ableton Push User Port")
                || containsPair(macPairs, "Push", "Push"))
            throw new AssertionError("macOS Push 1 discovery must contain only its User Port");
        if (linuxPairs.size() != 20 || !containsPair(linuxPairs, "Push", "Push")
                || containsPair(linuxPairs, "Ableton Push User Port", "Ableton Push User Port"))
            throw new AssertionError("Linux Push 1 discovery must contain only ALSA Push port variants");

        final List<ExtensionDefinition> definitions = new ArrayList<>();
        ServiceLoader.load(ExtensionDefinition.class).forEach(definitions::add);

        if (definitions.size() != 1)
            throw new AssertionError("Expected exactly one Bitwig extension provider, found " + definitions.size());

        final ExtensionDefinition definition = definitions.get(0);
        if (!EXPECTED_CLASS.equals(definition.getClass().getName()))
            throw new AssertionError("Unexpected Bitwig extension provider: " + definition.getClass().getName());
        if (!EXPECTED_ID.equals(definition.getId()))
            throw new AssertionError("PUSHdriver extension UUID is incorrect: " + definition.getId());
        if (definition.getRequiredAPIVersion() != 17)
            throw new AssertionError("PUSHdriver must target Bitwig Extension API 17");
        if (!"Push 1".equals(definition.getName()))
            throw new AssertionError("Unexpected controller name: " + definition.getName());
        // Exercise the real ExtensionDefinition.createInstance() construction path.
        // Bitwig owns the live ControllerHost; a dynamic proxy is sufficient here because
        // constructors only retain the host and create the setup/settings adapters.
        final ControllerHost controllerHost = (ControllerHost) Proxy.newProxyInstance(
            ControllerHost.class.getClassLoader(),
            new Class<?>[] { ControllerHost.class },
            (proxy, method, arguments) -> {
                final Class<?> type = method.getReturnType();
                if (type == boolean.class) return false;
                if (type == byte.class) return (byte) 0;
                if (type == short.class) return (short) 0;
                if (type == int.class) return 0;
                if (type == long.class) return 0L;
                if (type == float.class) return 0.0f;
                if (type == double.class) return 0.0d;
                if (type == char.class) return (char) 0;
                if (type == String.class) return "";
                if (type.isInterface())
                    return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                        (nestedProxy, nestedMethod, nestedArguments) -> {
                            final Class<?> nestedType = nestedMethod.getReturnType();
                            if (nestedType == boolean.class) return false;
                            if (nestedType == int.class) return 0;
                            if (nestedType == long.class) return 0L;
                            if (nestedType == String.class) return "";
                            return null;
                        });
                return null;
            });
        final ControllerExtension instance = ((ControllerExtensionDefinition) definition).createInstance(controllerHost);
        if (instance == null || !"de.mossgrabers.bitwig.framework.extension.GenericControllerExtension".equals(instance.getClass().getName()))
            throw new AssertionError("Bitwig extension instance construction failed");

        System.out.println("PUSHdriver SPI discovery passed: " + definition.getName()
            + ", API " + definition.getRequiredAPIVersion() + ", UUID " + definition.getId());

    }

    private static void verifyPackagedPanFormatter()
    {
        try
        {
            final Method formatter = Push1Display.class.getDeclaredMethod("formatPan", int.class, int.class);
            formatter.setAccessible(true);

            final String empty = Push1Display.BARS_NON;
            final String full = Push1Display.BARS_TWO;
            final String leftHalf = Push1Display.BARS_ONE_L;
            final String rightEdge = (String) formatter.invoke(null, 127, 127);
            if (!rightEdge.equals(empty.repeat(4) + full.repeat(4)))
                throw new AssertionError("Packaged Push 1 maximum pan bar is not exactly eight cells");

            final String leftEdge = (String) formatter.invoke(null, 0, 127);
            final String expectedLeft = full.repeat(4) + empty.repeat(4);
            if (!leftEdge.equals(expectedLeft))
                throw new AssertionError("Packaged Push 1 minimum pan bar is not aligned to the left edge");
            final String nearLeftEdge = (String) formatter.invoke(null, 1, 127);
            if (!nearLeftEdge.equals(expectedLeft))
                throw new AssertionError("Packaged Push 1 pan bar is asymmetric near its endpoints");

            final String invalidRange = (String) formatter.invoke(null, Integer.MAX_VALUE, 0);
            if (!invalidRange.equals(empty.repeat(8)))
                throw new AssertionError("Packaged Push 1 pan formatter is not safe for a zero maximum");

            // Exercise the actual assembled Push1Display implementation across
            // every pan value and a range of odd/even parameter maxima, including
            // values immediately outside the legal range.
            for (int maximum = 1; maximum <= 256; maximum++)
            {
                for (int pan = -1; pan <= maximum + 1; pan++)
                {
                    final String rendered = (String) formatter.invoke(null, pan, maximum);
                    if (rendered.length() != 8)
                        throw new AssertionError("Packaged pan formatter returned "
                            + rendered.length() + " cells for pan=" + pan + ", max=" + maximum);
                }

                if (maximum == 1)
                {
                    final String minimum = (String) formatter.invoke(null, 0, maximum);
                    final String maximumValue = (String) formatter.invoke(null, 1, maximum);
                    if (!minimum.equals(full.repeat(4) + empty.repeat(4))
                            || !maximumValue.equals(empty.repeat(4) + full.repeat(4)))
                        throw new AssertionError("Packaged pan formatter must show both endpoints for a two-value range");
                }
                else
                {
                    final String lowerCenter = (String) formatter.invoke(null, maximum / 2, maximum);
                    if (!lowerCenter.equals(empty.repeat(8)))
                        throw new AssertionError("Packaged pan formatter did not center max=" + maximum);
                    if (maximum % 2 == 1)
                    {
                        final String upperCenter = (String) formatter.invoke(null, maximum / 2 + 1, maximum);
                        if (!upperCenter.equals(empty.repeat(8)))
                            throw new AssertionError("Packaged pan formatter must treat both middle values symmetrically for max=" + maximum);
                    }
                }
            }
        }
        catch (final ReflectiveOperationException ex)
        {
            throw new AssertionError("Could not exercise the packaged Push 1 pan formatter", ex);
        }
    }


    private static boolean containsPair(final List<Pair<String[], String[]>> pairs,
                                        final String input, final String output)
    {
        for (final Pair<String[], String[]> pair : pairs)
            if (Arrays.asList(pair.getKey()).contains(input)
                    && Arrays.asList(pair.getValue()).contains(output))
                return true;
        return false;
    }
}
