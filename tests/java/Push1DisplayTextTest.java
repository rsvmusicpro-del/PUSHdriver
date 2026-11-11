import de.mossgrabers.controller.ableton.push.controller.Push1DisplayText;

public final class Push1DisplayTextTest
{
    private Push1DisplayTextTest ()
    {
    }

    private static void check (final boolean condition, final String message)
    {
        if (!condition)
            throw new AssertionError (message);
    }

    public static void main (final String [] args)
    {
        check (Push1DisplayText.fit ("Bass", 8).equals ("Bass    "), "short labels must be padded");
        check (Push1DisplayText.fit ("Massive Synth Plugin", 16).equals ("Massive...Plugin"), "long labels must retain their prefix and suffix");
        check (Push1DisplayText.fitBlock ("A very long instrument preset name").length () == 17, "blocks must remain exactly 17 characters");
        check (Push1DisplayText.fit ("A\nB", 8).equals ("A B     "), "line breaks must not leak into LCD rows");
        check (Push1DisplayText.fit ("é", 8).equals ("?       "), "unsupported Unicode must become seven-bit-safe");
        check (Push1DisplayText.fit (Character.toString ((char) 6), 8).charAt (0) == 6, "Push hardware glyph codes must be preserved");
        check (Push1DisplayText.fit (null, 8).equals ("        "), "null labels must produce a blank cell");
        check (Push1DisplayText.fit ("anything", 0).isEmpty (), "zero-width cells must be safe");
        System.out.println ("Push 1 display text fitting tests passed.");
    }
}
