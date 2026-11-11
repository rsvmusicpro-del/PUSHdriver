import com.bitwig.extension.ExtensionDefinition;
import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.ControllerHost;
import de.mossgrabers.bitwig.framework.daw.HostImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.UUID;
import java.lang.reflect.Proxy;

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
        final ControllerHost host = (ControllerHost) Proxy.newProxyInstance(
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
                if (type == char.class) return '\\0';
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
        final ControllerExtension instance = definition.createInstance(host);
        if (instance == null || !"de.mossgrabers.bitwig.framework.extension.GenericControllerExtension".equals(instance.getClass().getName()))
            throw new AssertionError("Bitwig extension instance construction failed");

        System.out.println("PUSHdriver SPI discovery passed: " + definition.getName()
            + ", API " + definition.getRequiredAPIVersion() + ", UUID " + definition.getId());
    }
}
