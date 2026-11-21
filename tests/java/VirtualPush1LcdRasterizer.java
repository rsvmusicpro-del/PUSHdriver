// Copyright (C) 2026 PUSHdriver contributors
// Licensed under the same LGPLv3 terms as the adapted DrivenByMoss controller code.

import de.mossgrabers.controller.ableton.push.controller.Push1DisplayGlyphs;
import de.mossgrabers.controller.ableton.push.controller.Push1DisplayLayout;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic 5x8 dot-matrix renderer for one physical Push 1 LCD module row.
 *
 * The eight programmable characters are rendered from the extracted Push 1
 * firmware bitmaps. Common ROM characters use a stable 5x7 reference font;
 * undocumented/variant ROM glyphs are explicitly identified as approximations.
 * This class models LCD dots and character pitch, not LCD glass or backlight.
 */
public final class VirtualPush1LcdRasterizer
{
    public static final int DOT_COLUMNS = 5;
    public static final int DOT_ROWS = 8;
    public static final int CHARACTER_PITCH = 6;
    public static final int MODULE_CHARACTERS = Push1DisplayLayout.MODULE_COLUMNS;
    public static final int RASTER_WIDTH = MODULE_CHARACTERS * CHARACTER_PITCH;
    public static final int RASTER_HEIGHT = DOT_ROWS;

    public enum RomProfile
    {
        HD44780U_A00 ("A00"),
        HD44780U_A02 ("A02");

        private final String id;

        RomProfile (final String id) { this.id = id; }
        public String id () { return this.id; }
    }

    private static final int [][] ROM_A00 = loadRom ("A00");
    private static final int [][] ROM_A02 = loadRom ("A02");
    private static final RomProfile ACTIVE_PROFILE = resolveProfile ();

    private VirtualPush1LcdRasterizer ()
    {
    }

    /** ROM profile selected by the optional -Dpush1.lcd.rom=A00|A02 property. */
    public static RomProfile activeRomProfile ()
    {
        return ACTIVE_PROFILE;
    }

    /** Render exactly one 17-character physical module row to lit-dot pixels. */
    public static boolean [][] rasterize (final String moduleRow)
    {
        if (moduleRow == null || moduleRow.length () != MODULE_CHARACTERS)
            throw new IllegalArgumentException ("A Push 1 LCD module row must contain exactly 17 character codes");
        final boolean [][] pixels = new boolean [RASTER_HEIGHT][RASTER_WIDTH];
        for (int column = 0; column < MODULE_CHARACTERS; column++)
        {
            final int [] glyph = glyphRows (moduleRow.charAt (column));
            for (int y = 0; y < DOT_ROWS; y++)
                for (int x = 0; x < DOT_COLUMNS; x++)
                    pixels[y][column * CHARACTER_PITCH + x] = (glyph[y] & (1 << (DOT_COLUMNS - 1 - x))) != 0;
        }
        return pixels;
    }

    /** Return the eight 5-bit row masks used by the virtual LCD renderer. */
    public static int [] glyphRows (final int characterCode)
    {
        return glyphRows (characterCode, ACTIVE_PROFILE);
    }

    /** Render a glyph using an explicitly selected HD44780U ROM profile. */
    public static int [] glyphRows (final int characterCode, final RomProfile profile)
    {
        if (characterCode < 0 || characterCode > 0x7F)
            throw new IllegalArgumentException ("Push 1 LCD character codes must be seven-bit values");
        if (profile == null)
            throw new IllegalArgumentException ("An HD44780U ROM profile is required");
        if (Push1DisplayGlyphs.isKnownCustomGlyph (characterCode))
            return Push1DisplayGlyphs.bitmapRows (characterCode);

        return switch (characterCode)
        {
            case 0x09 -> bitmap ("01100", "10010", "10010", "01100", "00000", "00000", "00000", "00000"); // degree
            case 0x18 -> bitmap ("00000", "00100", "00000", "11111", "00000", "00100", "00000", "00000"); // division
            case 0x1C -> bitmap ("00000", "00000", "00000", "00000", "00000", "10101", "00000", "00000"); // ellipsis
            case 0x1E -> bitmap ("00100", "00010", "11111", "00010", "00100", "00000", "00000", "00000"); // right arrow
            case 0x7F -> bitmap ("10000", "11000", "11100", "11110", "11100", "11000", "10000", "00000"); // selection arrow
            default -> asciiGlyph (characterCode, profile);
        };
    }

    /** True when a character is drawn from a known firmware/custom glyph profile. */
    public static boolean hasReferenceGlyph (final int characterCode)
    {
        return Push1DisplayGlyphs.isKnownCustomGlyph (characterCode)
            || characterCode == 0x09 || characterCode == 0x18 || characterCode == 0x1C
            || characterCode == 0x1E || characterCode == 0x7F
            || (characterCode >= 0x20 && characterCode <= 0x7E);
    }

    /** ASCII diagnostic view; '#' is a lit dot and '.' is a dark dot. */
    public static String renderAscii (final int characterCode)
    {
        final int [] rows = glyphRows (characterCode);
        final StringBuilder result = new StringBuilder (DOT_ROWS * (DOT_COLUMNS + 1));
        for (int y = 0; y < DOT_ROWS; y++)
        {
            for (int x = 0; x < DOT_COLUMNS; x++)
                result.append ((rows[y] & (1 << (DOT_COLUMNS - 1 - x))) != 0 ? '#' : '.');
            if (y + 1 < DOT_ROWS)
                result.append ('\n');
        }
        return result.toString ();
    }

    private static int [] asciiGlyph (final int code, final RomProfile profile)
    {
        if (code < 0x20 || code > 0x7E)
            return bitmap ("01110", "10001", "00001", "00010", "00100", "00000", "00100", "00000");
        return (profile == RomProfile.HD44780U_A00 ? ROM_A00 : ROM_A02)[code].clone ();
    }

    private static RomProfile resolveProfile ()
    {
        final String configured = System.getProperty ("push1.lcd.rom", "A00").trim ().toUpperCase ();
        return switch (configured)
        {
            case "A00" -> RomProfile.HD44780U_A00;
            case "A02" -> RomProfile.HD44780U_A02;
            default -> throw new IllegalArgumentException (
                "Unsupported Push 1 LCD ROM profile '" + configured + "'; choose A00 or A02");
        };
    }

    /**
     * Load the documented HD44780U A00 5x7 character ROM reference bitmap.
     * The source font and its OFL licence are kept as separate test resources.
     */
    private static int [][] loadRom (final String profile)
    {
        final int [][] glyphs = new int [128][];
        try (InputStream stream = VirtualPush1LcdRasterizer.class.getResourceAsStream ("/fonts/hd44780u-" + profile.toLowerCase () + ".chars.txt"))
        {
            if (stream == null)
                throw new IllegalStateException ("Missing HD44780U " + profile + " LCD font resource");
            final String source = new String (stream.readAllBytes (), StandardCharsets.UTF_8);
            final Pattern blockPattern = Pattern.compile (
                "(?m)^STARTCHAR U\\+([0-9A-Fa-f]{4})[^\\r\\n]*\\R([\\s\\S]*?)^ENDCHAR\\s*$");
            final Pattern rowPattern = Pattern.compile ("(?m)^[|+]([ #]{5})[|+]\\s*$");
            final Matcher blocks = blockPattern.matcher (source);
            int loaded = 0;
            while (blocks.find ())
            {
                final int code = Integer.parseInt (blocks.group (1), 16);
                if (code < 0x20 || code > 0x7E)
                    continue;
                final Matcher rows = rowPattern.matcher (blocks.group (2));
                final int [] bitmap = new int [DOT_ROWS];
                int y = 0;
                while (rows.find () && y < 7)
                {
                    final String row = rows.group (1);
                    for (int x = 0; x < DOT_COLUMNS; x++)
                        if (row.charAt (x) == '#')
                            bitmap[y] |= 1 << (DOT_COLUMNS - 1 - x);
                    y++;
                }
                if (y != 7)
                    throw new IllegalStateException ("HD44780U " + profile + " glyph 0x" + Integer.toHexString (code)
                        + " does not contain seven 5-dot rows");
                glyphs[code] = bitmap;
                loaded++;
            }
            if (loaded != 95)
                throw new IllegalStateException ("Expected 95 printable HD44780U " + profile + " glyphs, loaded " + loaded);
            return glyphs;
        }
        catch (final IOException error)
        {
            throw new IllegalStateException ("Cannot read HD44780U " + profile + " LCD font resource", error);
        }
    }

    private static int [] bitmap (final String... rows)
    {
        if (rows.length != DOT_ROWS)
            throw new IllegalArgumentException ("A virtual LCD glyph must contain eight rows");
        final int [] result = new int [DOT_ROWS];
        for (int y = 0; y < DOT_ROWS; y++)
        {
            if (rows[y].length () != DOT_COLUMNS)
                throw new IllegalArgumentException ("A virtual LCD glyph row must contain five dots");
            for (int x = 0; x < DOT_COLUMNS; x++)
            {
                final char pixel = rows[y].charAt (x);
                if (pixel == '1')
                    result[y] |= 1 << (DOT_COLUMNS - 1 - x);
                else if (pixel != '0')
                    throw new IllegalArgumentException ("Invalid virtual LCD bitmap pixel");
            }
        }
        return result;
    }
}
