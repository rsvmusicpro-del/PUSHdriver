// Copyright (C) 2026 PUSHdriver contributors
// Licensed under the same LGPLv3 terms as the adapted DrivenByMoss controller code.

import de.mossgrabers.controller.ableton.push.controller.Push1DisplayText;


/** Regression tests for fixed-width Push 1 LCD text composition. */
public final class VirtualPush1DisplayTextTest
{
    private VirtualPush1DisplayTextTest ()
    {
    }


    public static void main (final String [] args)
    {
        check (Push1DisplayText.fit ("A\nB\tC\rD", 8), "A B C D ",
            "line breaks and tabs must become one display cell each");
        check (Push1DisplayText.fit ("A😀B", 4), "A?B ",
            "a supplementary Unicode code point must occupy one replacement cell");
        check (Push1DisplayText.fit ("ABCDEFGHIJKLMNOPQRSTUVWXYZ", 10), "ABCD...XYZ",
            "long labels must retain both ends within the requested width");
        check (Push1DisplayText.fit ("ABCDE", 4), "ABC>",
            "short-width truncation must remain bounded");
        check (Push1DisplayText.fit (null, 5), "     ",
            "null text must become fixed-width blank text");
        check (Push1DisplayText.fitBlock ("Push 1"), "Push 1           ",
            "physical LCD blocks must contain exactly 17 cells");

        final String line = "L".repeat (68);
        final String notification = Push1DisplayText.overlayRightBlock (line, "GO");
        require (notification.length () == 68, "right-block overlay must preserve exact LCD width");
        require (notification.substring (0, 51).equals ("L".repeat (51)),
            "right-block overlay must preserve the left 51 cells");
        require (notification.substring (51).equals ("GO" + " ".repeat (15)),
            "right-block notification must be fitted to the final 17 cells");

        final String normalized = Push1DisplayText.overlayRightBlock ("A😀B", "");
        require (normalized.length () == 68, "Unicode normalization must preserve 68-cell rows");
        require (normalized.substring (0, 3).equals ("A?B"),
            "right-block overlay must normalize Unicode by display code point");

        System.out.println ("Virtual Push 1 display text passed: Unicode cell fitting, truncation and safe right-block overlays.");
    }


    private static void check (final String actual, final String expected, final String label)
    {
        if (!expected.equals (actual))
            throw new AssertionError (label + ": expected '" + expected + "', got '" + actual + "'");
    }


    private static void require (final boolean condition, final String message)
    {
        if (!condition)
            throw new AssertionError (message);
    }
}
