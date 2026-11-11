import com.bitwig.extension.ExtensionDefinition;

import java.util.ArrayList;
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
        if (definition.getNumMidiInPorts() < 1 || definition.getNumMidiOutPorts() < 1)
            throw new AssertionError("Push 1 must expose MIDI input and output ports");

        System.out.println("PUSHdriver SPI discovery passed: " + definition.getName()
            + ", API " + definition.getRequiredAPIVersion() + ", UUID " + definition.getId());
    }
}
