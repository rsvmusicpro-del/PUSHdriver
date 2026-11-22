import de.mossgrabers.controller.ableton.push.controller.Push1DisplayComposition;
import de.mossgrabers.controller.ableton.push.controller.Push1DisplayLayout;

/** Contract tests for semantic Push 1 display row composition. */
public final class Push1DisplayCompositionTest
{
    public static void main (final String [] args)
    {
        final String labels = Push1DisplayComposition.encoderLabels ("Track", "Vol", "Pan", "Send A", "Send B", "Mute", "Solo", "Arm");
        check (labels.length () == 68, "encoder labels occupy exactly 68 columns");
        check (labels.substring (0, 9).equals ("Track    "), "first encoder retains its 9-column cell");
        check (labels.substring (9, 17).equals ("Vol     "), "second encoder retains its 8-column cell");
        check (labels.substring (17, 26).equals ("Pan      "), "third encoder starts at the next module");
        check (labels.substring (60, 68).equals ("Arm     "), "eighth encoder ends at column 68");

        final String actions = Push1DisplayComposition.actionHints ("Back", "Select");
        check (actions.length () == Push1DisplayLayout.COLUMNS, "missing actions are padded to the full row");
        check (actions.substring (17).isBlank (), "unused action cells remain blank");

        final String context = Push1DisplayComposition.contextRow ("DEVICE:", "Analog");
        check (context.length () == 68, "context row occupies the full physical display");
        check (context.substring (0, 17).equals ("DEVICE:          "), "context label owns exactly one LCD module");
        check (context.substring (17).startsWith ("Analog"), "context detail starts at module two");
        check (Push1DisplayComposition.contextRow ("", null).length () == 68, "null context detail is safe");

        final String [] graphical = {"\u0005\u0004", "\u0003", "", "", "", "", "", ""};
        check (Push1DisplayComposition.encoderValues (graphical).length () == 68, "graphical value fields share encoder geometry");
        System.out.println ("PASS: semantic display composition, 8/9-cell geometry and full-width context rows");
    }

    private static void check (final boolean condition, final String message)
    {
        if (!condition)
            throw new AssertionError (message);
    }
}
