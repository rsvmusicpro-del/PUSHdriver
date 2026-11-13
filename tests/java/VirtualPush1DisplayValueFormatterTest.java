// Copyright (C) 2026 PUSHdriver contributors
// Licensed under the same LGPLv3 terms as the adapted DrivenByMoss controller code.

import de.mossgrabers.controller.ableton.push.controller.Push1DisplayValueFormatter;


/** Regression tests for bounded Push 1 LCD value and pan bars. */
public final class VirtualPush1DisplayValueFormatterTest
{
    private static final String EMPTY = "-";
    private static final String HALF = ">";
    private static final String FULL = "#";
    private static final String LEFT_HALF = "<";


    private VirtualPush1DisplayValueFormatterTest ()
    {
    }


    public static void main (final String [] args)
    {
        check (Push1DisplayValueFormatter.formatValue (0, 127, EMPTY, HALF, FULL), "--------",
            "minimum value must produce eight empty cells");
        check (Push1DisplayValueFormatter.formatValue (127, 127, EMPTY, HALF, FULL), "########",
            "maximum value must produce eight full cells");
        check (Push1DisplayValueFormatter.formatValue (-100, 127, EMPTY, HALF, FULL), "--------",
            "negative values must clamp safely");
        check (Push1DisplayValueFormatter.formatValue (Integer.MAX_VALUE, 127, EMPTY, HALF, FULL), "########",
            "values above maximum must clamp safely");
        check (Push1DisplayValueFormatter.formatValue (127, 0, EMPTY, HALF, FULL), "--------",
            "zero maximum must produce an empty bar");

        check (Push1DisplayValueFormatter.formatPan (63, 127, EMPTY, HALF, FULL, LEFT_HALF), "--------",
            "lower center value in an odd range must remain centered");
        check (Push1DisplayValueFormatter.formatPan (64, 127, EMPTY, HALF, FULL, LEFT_HALF), "--------",
            "upper center value in an odd range must remain centered");
        check (Push1DisplayValueFormatter.formatPan (0, 127, EMPTY, HALF, FULL, LEFT_HALF), "####----",
            "minimum pan must fill the left edge");
        check (Push1DisplayValueFormatter.formatPan (1, 127, EMPTY, HALF, FULL, LEFT_HALF), "####----",
            "near-minimum pan must match the near-maximum endpoint");
        check (Push1DisplayValueFormatter.formatPan (126, 127, EMPTY, HALF, FULL, LEFT_HALF), "----####",
            "penultimate pan value must fill the right edge");
        check (Push1DisplayValueFormatter.formatPan (127, 127, EMPTY, HALF, FULL, LEFT_HALF), "----####",
            "maximum pan must fill the right edge");
        check (Push1DisplayValueFormatter.formatPan (-100, 127, EMPTY, HALF, FULL, LEFT_HALF), "####----",
            "negative pan values must clamp to the left edge");
        check (Push1DisplayValueFormatter.formatPan (Integer.MAX_VALUE, 127, EMPTY, HALF, FULL, LEFT_HALF), "----####",
            "pan values above maximum must clamp to the right edge");
        check (Push1DisplayValueFormatter.formatPan (127, 0, EMPTY, HALF, FULL, LEFT_HALF), "--------",
            "zero pan maximum must be safe");
        check (Push1DisplayValueFormatter.formatPan (0, 1, EMPTY, HALF, FULL, LEFT_HALF), "####----",
            "single-step range minimum must be left aligned");
        check (Push1DisplayValueFormatter.formatPan (1, 1, EMPTY, HALF, FULL, LEFT_HALF), "----####",
            "single-step range maximum must be right aligned");
        check (Push1DisplayValueFormatter.formatPan (0, Integer.MAX_VALUE, EMPTY, HALF, FULL, LEFT_HALF), "####----",
            "extreme pan range minimum must remain bounded");
        check (Push1DisplayValueFormatter.formatPan (Integer.MAX_VALUE, Integer.MAX_VALUE, EMPTY, HALF, FULL, LEFT_HALF), "----####",
            "extreme pan range maximum must remain bounded");

        checkLength (Push1DisplayValueFormatter.formatModulatedValue (Integer.MAX_VALUE, Integer.MIN_VALUE,
            Integer.MAX_VALUE, EMPTY, HALF, FULL, LEFT_HALF), 8, "modulated value overflow");
        check (Push1DisplayValueFormatter.formatModulatedValue (127, 127, 0, EMPTY, HALF, FULL, LEFT_HALF),
            "--------", "zero maximum modulation must be safe");

        System.out.println ("Virtual Push 1 display value formatter passed: bounded values, modulation, symmetric pan and integer extremes.");
    }


    private static void check (final String actual, final String expected, final String label)
    {
        if (!expected.equals (actual))
            throw new AssertionError (label + ": expected '" + expected + "', got '" + actual + "'");
    }


    private static void checkLength (final String actual, final int expected, final String label)
    {
        if (actual.length () != expected)
            throw new AssertionError (label + ": expected " + expected + " cells, got " + actual.length ());
    }
}
