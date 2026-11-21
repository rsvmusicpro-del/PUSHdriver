package de.mossgrabers.controller.ableton.push.command.trigger;

import de.mossgrabers.framework.daw.data.ICursorDevice;

import java.lang.reflect.Proxy;

/** Regression coverage for unambiguous cursor snapshots used by hierarchy confirmation. */
public final class PushDeviceNavigationFingerprintTest
{
    private PushDeviceNavigationFingerprintTest () { }

    public static void main (final String [] args)
    {
        final var first = PushDeviceNavigation.fingerprint (cursor ("device|parent", "child"));
        final var second = PushDeviceNavigation.fingerprint (cursor ("device", "parent|child"));
        require (!first.equals (second),
            "distinct cursor fields containing delimiters must not collapse into the same snapshot");
        require (first.equals (PushDeviceNavigation.fingerprint (cursor ("device|parent", "child"))),
            "identical cursor fields must produce equal snapshots");
        System.out.println ("PushDeviceNavigationFingerprintTest: all checks passed.");
    }

    private static ICursorDevice cursor (final String id, final String name)
    {
        return (ICursorDevice) Proxy.newProxyInstance (ICursorDevice.class.getClassLoader (),
            new Class<?> [] { ICursorDevice.class }, (proxy, method, args) -> switch (method.getName ())
            {
                case "getID" -> id;
                case "getName" -> name;
                case "getPosition", "getIndex" -> 1;
                case "isNested" -> true;
                default -> throw new AssertionError ("Unexpected cursor method: " + method.getName ());
            });
    }

    private static void require (final boolean condition, final String message)
    {
        if (!condition)
            throw new AssertionError (message);
    }
}
