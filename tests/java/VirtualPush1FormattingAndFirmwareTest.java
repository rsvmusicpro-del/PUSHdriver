import de.mossgrabers.controller.ableton.push.controller.Push1DisplayText;
import de.mossgrabers.controller.ableton.push.controller.Push1DisplayValueFormatter;
import de.mossgrabers.controller.ableton.push.controller.Push1FirmwareVersion;
import de.mossgrabers.framework.daw.midi.DeviceInquiry;

/**
 * Regression coverage for Push 1 display text/value formatting and firmware
 * identity parsing. These are production helpers used by the real extension.
 */
public final class VirtualPush1FormattingAndFirmwareTest
{
    private VirtualPush1FormattingAndFirmwareTest () { }

    public static void main (final String [] args)
    {
        testDisplayText ();
        testValueBars ();
        testPanBars ();
        testFirmwareIdentity ();
        System.out.println ("Virtual Push 1 display formatting and firmware identity tests passed.");
    }

    private static void testDisplayText ()
    {
        require ("".equals (Push1DisplayText.fit ("ignored", 0)), "Zero-width display text must be empty");
        require ("                    ".equals (Push1DisplayText.fit (null, 20)),
            "Null display text must become fixed-width spaces");
        require ("Push 1              ".equals (Push1DisplayText.fit ("Push 1", 20)),
            "Short text must be padded to the exact width");
        require ("ABC...KLM".equals (Push1DisplayText.fit ("ABCDEFGHIJKLM", 9)),
            "Long text must retain both ends around the ellipsis");
        require ("AB>".equals (Push1DisplayText.fit ("ABCDE", 3)),
            "Very narrow text must use the compact truncation marker");
        require ("A ? B  ".equals (Push1DisplayText.fit ("A\n€ B", 7)),
            "Control and non-ASCII characters must be made seven-bit safe");

        final String original = "L".repeat (51) + "R".repeat (17);
        final String overlay = Push1DisplayText.overlayRightBlock (original, "Notice");
        require (overlay.length () == 68, "Notification overlay must retain 68 columns");
        require (overlay.substring (0, 51).equals ("L".repeat (51)),
            "Notification overlay must preserve the first three LCD modules");
        require (overlay.substring (51).equals (Push1DisplayText.fitBlock ("Notice")),
            "Notification overlay must occupy exactly the rightmost 17-character module");
        require (Push1DisplayText.overlayRightBlock (original, "").substring (0, 51).equals ("L".repeat (51)),
            "Clearing a notification must preserve the non-overlay LCD prefix");
        require (Push1DisplayText.fitBlock ("€").equals ("?" + " ".repeat (16)),
            "Physical module text must remain seven-bit safe and exactly 17 columns");
    }

    private static void testValueBars ()
    {
        final String empty = "-";
        final String half = "/";
        final String full = "#";
        require (Push1DisplayValueFormatter.formatValue (0, 127, empty, half, full).equals ("--------"),
            "Zero parameter value must render eight empty cells");
        require (Push1DisplayValueFormatter.formatValue (127, 127, empty, half, full).equals ("########"),
            "Maximum parameter value must render eight full cells");
        require (Push1DisplayValueFormatter.formatValue (-10, 127, empty, half, full).equals ("--------"),
            "Negative parameter values must clamp to zero");
        require (Push1DisplayValueFormatter.formatValue (200, 127, empty, half, full).equals ("########"),
            "Values above the parameter maximum must clamp to full");
        require (Push1DisplayValueFormatter.formatValue (64, 127, empty, half, full).length () == 8,
            "Midpoint parameter bar must have exactly eight cells");
        require (Push1DisplayValueFormatter.formatValue (100, 0, empty, half, full).equals ("--------"),
            "Non-positive parameter ranges must render empty bars");

        for (int value = -16; value <= 143; value++)
        {
            final String formatted = Push1DisplayValueFormatter.formatValue (value, 127, empty, half, full);
            require (formatted.length () == 8, "Value bar width changed at input " + value);
            for (int i = 0; i < formatted.length (); i++)
                require ("-/#".indexOf (formatted.charAt (i)) >= 0,
                    "Unexpected value-bar glyph at input " + value);
            final String modulated = Push1DisplayValueFormatter.formatModulatedValue (
                127 - value, value, 127, empty, half, full, "<");
            require (modulated.length () == 8, "Modulated value bar width changed at input " + value);
        }
    }

    private static void testPanBars ()
    {
        final String empty = "-";
        final String half = "/";
        final String full = "#";
        final String leftHalf = "\\";
        require (Push1DisplayValueFormatter.formatPan (64, 127, empty, half, full, leftHalf).equals ("--------"),
            "The two central values of an odd pan range must render centered");
        require (Push1DisplayValueFormatter.formatPan (0, 127, empty, half, full, leftHalf).length () == 8,
            "Left endpoint pan bar must have eight cells");
        require (Push1DisplayValueFormatter.formatPan (127, 127, empty, half, full, leftHalf).length () == 8,
            "Right endpoint pan bar must have eight cells");
        require (Push1DisplayValueFormatter.formatPan (-20, 127, empty, half, full, leftHalf).length () == 8,
            "Pan below range must clamp without changing width");
        require (Push1DisplayValueFormatter.formatPan (200, 127, empty, half, full, leftHalf).length () == 8,
            "Pan above range must clamp without changing width");
        require (Push1DisplayValueFormatter.formatPan (0, 0, empty, half, full, leftHalf).equals ("--------"),
            "Non-positive pan ranges must render empty cells");
    }

    private static void testFirmwareIdentity ()
    {
        final Push1FirmwareVersion version = Push1FirmwareVersion.parse (
            new DeviceInquiry (validAbletonIdentity (1, 2)));
        require (version != null && version.major () == 1 && version.minor () == 2,
            "Firmware decimal digits must be decoded in the documented positions");

        final Push1FirmwareVersion parsed = Push1FirmwareVersion.parse (
            new DeviceInquiry (validAbletonIdentity (2, 4)));
        require (parsed != null && parsed.major () == 2 && parsed.minor () == 4,
            "Firmware version parsing failed for a second valid response");

        final int [] mutable = validAbletonIdentity (3, 5);
        final DeviceInquiry defensive = new DeviceInquiry (mutable);
        mutable[9] = 9;
        require (defensive.getData ()[9] == 0,
            "DeviceInquiry must defensively copy the caller-owned identity packet");
        final int [] exposed = defensive.getData ();
        exposed[9] = 9;
        require (defensive.getData ()[9] == 0,
            "DeviceInquiry must not expose its internal mutable identity packet");

        final int [] query = DeviceInquiry.createQuery ();
        query[0] = 0;
        require ((DeviceInquiry.createQuery ()[0] & 0xFF) == 0xF0,
            "DeviceInquiry query must be returned as a defensive copy");

        require (Push1FirmwareVersion.parse (null) == null, "Null identity must be rejected");
        require (Push1FirmwareVersion.parse (new DeviceInquiry (new int [] { 0xF0, 0xF7 })) == null,
            "Short malformed identity must be rejected");

        final int [] wrongManufacturer = validAbletonIdentity (1, 2);
        wrongManufacturer[7] = 0x20;
        require (Push1FirmwareVersion.parse (new DeviceInquiry (wrongManufacturer)) == null,
            "A non-Ableton manufacturer identity must be rejected");

        final int [] malformedDigits = validAbletonIdentity (1, 2);
        malformedDigits[9] = 10;
        require (Push1FirmwareVersion.parse (new DeviceInquiry (malformedDigits)) == null,
            "Non-decimal firmware digits must be rejected");

        final int [] malformedData = validAbletonIdentity (1, 2);
        malformedData[13] = 0x80;
        require (Push1FirmwareVersion.parse (new DeviceInquiry (malformedData)) == null,
            "Non-seven-bit identity data must be rejected");

        final int [] badHeader = validAbletonIdentity (1, 2);
        badHeader[3] = 0x05;
        require (Push1FirmwareVersion.parse (new DeviceInquiry (badHeader)) == null,
            "A malformed universal identity header must be rejected");
    }

    private static int [] validAbletonIdentity (final int major, final int minor)
    {
        final int [] data = new int [35];
        data[0] = 0xF0;
        data[1] = 0x7E;
        data[2] = 0x7F;
        data[3] = 0x06;
        data[4] = 0x02;
        data[5] = 0x00;
        data[6] = 0x21;
        data[7] = 0x1D;
        data[8] = 0x01;
        data[9] = major / 10;
        data[10] = major % 10;
        data[11] = minor / 10;
        data[12] = minor % 10;
        data[34] = 0xF7;
        return data;
    }

    private static void require (final boolean condition, final String message)
    {
        if (!condition)
            throw new AssertionError (message);
    }
}
