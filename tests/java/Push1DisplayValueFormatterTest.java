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
        check (Push1DisplayValueFormatter.formatValue (Integer.MAX_VALUE, 1, EMPTY, HALF, FULL), "########", "extreme positive base values must clamp without integer overflow");
        check (Push1DisplayValueFormatter.formatValue (10, 0, EMPTY, HALF, FULL), "--------", "zero maximum must be safe");

        check (Push1DisplayValueFormatter.formatModulatedValue (127, 127, 127, EMPTY, HALF, FULL, LEFT_HALF), "########", "maximum modulated and base values must not index past the final cell");
        check (Push1DisplayValueFormatter.formatModulatedValue (127, 120, 127, EMPTY, HALF, FULL, LEFT_HALF), "########", "maximum modulation must fill the final half-cell");
        check (Push1DisplayValueFormatter.formatModulatedValue (127, 0, 127, EMPTY, HALF, FULL, LEFT_HALF), "-------<", "maximum modulation over an empty base must mark the final cell");
        check (Push1DisplayValueFormatter.formatModulatedValue (-1, -1, 127, EMPTY, HALF, FULL, LEFT_HALF), "--------", "negative values must clamp to zero");
        check (Push1DisplayValueFormatter.formatModulatedValue (200, 200, 127, EMPTY, HALF, FULL, LEFT_HALF), "########", "values above maximum must clamp");
        check (Push1DisplayValueFormatter.formatModulatedValue (Integer.MAX_VALUE, 0, 1, EMPTY, HALF, FULL, LEFT_HALF), "-------<", "extreme positive modulation must clamp to the final cell without integer overflow");
        check (Push1DisplayValueFormatter.formatModulatedValue (127, 127, 0, EMPTY, HALF, FULL, LEFT_HALF), "--------", "zero maximum must be safe");

        check (Push1DisplayValueFormatter.formatPan (63, 127, EMPTY, HALF, FULL, LEFT_HALF), "--------", "lower center pan value must remain empty");
        check (Push1DisplayValueFormatter.formatPan (64, 127, EMPTY, HALF, FULL, LEFT_HALF), "--------", "upper center pan value must remain empty for an odd range");
        check (Push1DisplayValueFormatter.formatPan (0, 1, EMPTY, HALF, FULL, LEFT_HALF), "####----", "minimum pan must remain visible for a two-value range");
        check (Push1DisplayValueFormatter.formatPan (1, 1, EMPTY, HALF, FULL, LEFT_HALF), "----####", "maximum pan must remain visible for a two-value range");
        check (Push1DisplayValueFormatter.formatPan (0, 127, EMPTY, HALF, FULL, LEFT_HALF), "####----", "minimum pan must fill the left edge symmetrically");
        check (Push1DisplayValueFormatter.formatPan (1, 127, EMPTY, HALF, FULL, LEFT_HALF), "####----", "near-minimum pan must match the near-maximum endpoint");
        check (Push1DisplayValueFormatter.formatPan (126, 127, EMPTY, HALF, FULL, LEFT_HALF), "----####", "penultimate pan value must reach the right edge");
        check (Push1DisplayValueFormatter.formatPan (127, 127, EMPTY, HALF, FULL, LEFT_HALF), "----####", "maximum pan must not produce a ninth display cell");
        check (Push1DisplayValueFormatter.formatPan (-100, 127, EMPTY, HALF, FULL, LEFT_HALF), "####----", "negative pan values must clamp to the full left edge");
        check (Push1DisplayValueFormatter.formatPan (Integer.MAX_VALUE, 127, EMPTY, HALF, FULL, LEFT_HALF), "----####", "pan values above maximum must clamp to the right edge");
        check (Push1DisplayValueFormatter.formatPan (127, 0, EMPTY, HALF, FULL, LEFT_HALF), "--------", "zero pan maximum must be safe");
        check (Push1DisplayValueFormatter.formatPan (Integer.MAX_VALUE, Integer.MAX_VALUE, EMPTY, HALF, FULL, LEFT_HALF), "----####", "extreme pan maximum must remain bounded");

        for (int maximum = 1; maximum <= 256; maximum++)
            for (int pan = -1; pan <= maximum + 1; pan++)
            {
                final String rendered = Push1DisplayValueFormatter.formatPan (pan, maximum, EMPTY, HALF, FULL, LEFT_HALF);
                if (rendered.length () != 8)
                    throw new AssertionError ("Pan bar width changed for pan=" + pan + ", maximum=" + maximum + ": " + rendered);

                final String mirrored = Push1DisplayValueFormatter.formatPan (maximum - pan, maximum, EMPTY, HALF, FULL, LEFT_HALF);
                final String normalized = rendered.replace (LEFT_HALF, HALF);
                final String normalizedMirror = mirrored.replace (LEFT_HALF, HALF);
                if (!normalized.equals (new StringBuilder (normalizedMirror).reverse ().toString ()))
                    throw new AssertionError ("Pan bar is asymmetric for pan=" + pan + ", maximum=" + maximum
                        + ": " + rendered + " vs mirrored " + mirrored);
            }

        // Exhaustively exercise every base/modulated pair over representative
        // small and full MIDI parameter ranges. Every output must stay eight cells.
        for (int maximum = 1; maximum <= 127; maximum++)
            for (int value = -1; value <= maximum + 1; value++)
            {
                final String base = Push1DisplayValueFormatter.formatValue (value, maximum, EMPTY, HALF, FULL);
                if (base.length () != 8)
                    throw new AssertionError ("Base value bar width changed for value=" + value + ", maximum=" + maximum);
                for (int modulated = -1; modulated <= maximum + 1; modulated++)
                {
                    final String rendered = Push1DisplayValueFormatter.formatModulatedValue (
                        modulated, value, maximum, EMPTY, HALF, FULL, LEFT_HALF);
                    if (rendered.length () != 8)
                        throw new AssertionError ("Modulated value bar width changed for value=" + value
                            + ", modulation=" + modulated + ", maximum=" + maximum);
                }
            }

        System.out.println ("Push 1 display value-bar boundary tests passed.");
    }
}
