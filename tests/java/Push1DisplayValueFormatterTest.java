import de.mossgrabers.controller.ableton.push.controller.Push1DisplayValueFormatter;

/** Boundary tests for the Push 1 eight-cell parameter renderer. */
public final class Push1DisplayValueFormatterTest
{
    private static final String EMPTY = "-";
    private static final String HALF = "|";
    private static final String FULL = "#";
    private static final String LEFT_HALF = "<";


    private Push1DisplayValueFormatterTest ()
    {
    }


    private static void check (final String actual, final String expected, final String message)
    {
        if (!expected.equals (actual))
            throw new AssertionError (message + ": expected '" + expected + "', got '" + actual + "'");
    }


    public static void main (final String [] args)
    {
        check (Push1DisplayValueFormatter.formatValue (127, 127, EMPTY, HALF, FULL), "########", "maximum base value");
        check (Push1DisplayValueFormatter.formatValue (0, 127, EMPTY, HALF, FULL), "--------", "minimum base value");
        check (Push1DisplayValueFormatter.formatValue (200, 127, EMPTY, HALF, FULL), "########", "base value above maximum must clamp");
        check (Push1DisplayValueFormatter.formatValue (10, 0, EMPTY, HALF, FULL), "--------", "zero maximum must be safe");

        check (Push1DisplayValueFormatter.formatModulatedValue (127, 127, 127, EMPTY, HALF, FULL, LEFT_HALF), "########", "maximum modulated and base values must not index past the final cell");
        check (Push1DisplayValueFormatter.formatModulatedValue (127, 120, 127, EMPTY, HALF, FULL, LEFT_HALF), "########", "maximum modulation must fill the final half-cell");
        check (Push1DisplayValueFormatter.formatModulatedValue (127, 0, 127, EMPTY, HALF, FULL, LEFT_HALF), "-------<", "maximum modulation over an empty base must mark the final cell");
        check (Push1DisplayValueFormatter.formatModulatedValue (-1, -1, 127, EMPTY, HALF, FULL, LEFT_HALF), "--------", "negative values must clamp to zero");
        check (Push1DisplayValueFormatter.formatModulatedValue (200, 200, 127, EMPTY, HALF, FULL, LEFT_HALF), "########", "values above maximum must clamp");
        check (Push1DisplayValueFormatter.formatModulatedValue (127, 127, 0, EMPTY, HALF, FULL, LEFT_HALF), "--------", "zero maximum must be safe");

        System.out.println ("Push 1 display value-bar boundary tests passed.");
    }
}
