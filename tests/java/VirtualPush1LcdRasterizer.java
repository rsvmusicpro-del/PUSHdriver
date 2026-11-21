// Copyright (C) 2026 PUSHdriver contributors
// Licensed under the same LGPLv3 terms as the adapted DrivenByMoss controller code.

import de.mossgrabers.controller.ableton.push.controller.Push1DisplayGlyphs;
import de.mossgrabers.controller.ableton.push.controller.Push1DisplayLayout;

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

    private VirtualPush1LcdRasterizer ()
    {
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
        if (characterCode < 0 || characterCode > 0x7F)
            throw new IllegalArgumentException ("Push 1 LCD character codes must be seven-bit values");
        if (Push1DisplayGlyphs.isKnownCustomGlyph (characterCode))
            return Push1DisplayGlyphs.bitmapRows (characterCode);

        return switch (characterCode)
        {
            case 0x09 -> bitmap ("01100", "10010", "10010", "01100", "00000", "00000", "00000", "00000"); // degree
            case 0x18 -> bitmap ("00000", "00100", "00000", "11111", "00000", "00100", "00000", "00000"); // division
            case 0x1C -> bitmap ("00000", "00000", "00000", "00000", "00000", "10101", "00000", "00000"); // ellipsis
            case 0x1E -> bitmap ("00100", "00010", "11111", "00010", "00100", "00000", "00000", "00000"); // right arrow
            case 0x7F -> bitmap ("10000", "11000", "11100", "11110", "11100", "11000", "10000", "00000"); // selection arrow
            default -> asciiGlyph (characterCode);
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

    private static int [] asciiGlyph (final int code)
    {
        if (code == 0x20)
            return bitmap ("00000", "00000", "00000", "00000", "00000", "00000", "00000", "00000");
        final char c = Character.toUpperCase ((char) code);
        return switch (c)
        {
            case '0' -> bitmap ("01110","10001","10011","10101","11001","10001","01110","00000");
            case '1' -> bitmap ("00100","01100","00100","00100","00100","00100","01110","00000");
            case '2' -> bitmap ("01110","10001","00001","00010","00100","01000","11111","00000");
            case '3' -> bitmap ("11110","00001","00001","01110","00001","00001","11110","00000");
            case '4' -> bitmap ("00010","00110","01010","10010","11111","00010","00010","00000");
            case '5' -> bitmap ("11111","10000","10000","11110","00001","00001","11110","00000");
            case '6' -> bitmap ("01110","10000","10000","11110","10001","10001","01110","00000");
            case '7' -> bitmap ("11111","00001","00010","00100","01000","01000","01000","00000");
            case '8' -> bitmap ("01110","10001","10001","01110","10001","10001","01110","00000");
            case '9' -> bitmap ("01110","10001","10001","01111","00001","00001","01110","00000");
            case 'A' -> bitmap ("01110","10001","10001","11111","10001","10001","10001","00000");
            case 'B' -> bitmap ("11110","10001","10001","11110","10001","10001","11110","00000");
            case 'C' -> bitmap ("01111","10000","10000","10000","10000","10000","01111","00000");
            case 'D' -> bitmap ("11110","10001","10001","10001","10001","10001","11110","00000");
            case 'E' -> bitmap ("11111","10000","10000","11110","10000","10000","11111","00000");
            case 'F' -> bitmap ("11111","10000","10000","11110","10000","10000","10000","00000");
            case 'G' -> bitmap ("01111","10000","10000","10111","10001","10001","01111","00000");
            case 'H' -> bitmap ("10001","10001","10001","11111","10001","10001","10001","00000");
            case 'I' -> bitmap ("01110","00100","00100","00100","00100","00100","01110","00000");
            case 'J' -> bitmap ("00111","00010","00010","00010","10010","10010","01100","00000");
            case 'K' -> bitmap ("10001","10010","10100","11000","10100","10010","10001","00000");
            case 'L' -> bitmap ("10000","10000","10000","10000","10000","10000","11111","00000");
            case 'M' -> bitmap ("10001","11011","10101","10101","10001","10001","10001","00000");
            case 'N' -> bitmap ("10001","11001","10101","10011","10001","10001","10001","00000");
            case 'O' -> bitmap ("01110","10001","10001","10001","10001","10001","01110","00000");
            case 'P' -> bitmap ("11110","10001","10001","11110","10000","10000","10000","00000");
            case 'Q' -> bitmap ("01110","10001","10001","10001","10101","10010","01101","00000");
            case 'R' -> bitmap ("11110","10001","10001","11110","10100","10010","10001","00000");
            case 'S' -> bitmap ("01111","10000","10000","01110","00001","00001","11110","00000");
            case 'T' -> bitmap ("11111","00100","00100","00100","00100","00100","00100","00000");
            case 'U' -> bitmap ("10001","10001","10001","10001","10001","10001","01110","00000");
            case 'V' -> bitmap ("10001","10001","10001","10001","10001","01010","00100","00000");
            case 'W' -> bitmap ("10001","10001","10001","10101","10101","10101","01010","00000");
            case 'X' -> bitmap ("10001","10001","01010","00100","01010","10001","10001","00000");
            case 'Y' -> bitmap ("10001","10001","01010","00100","00100","00100","00100","00000");
            case 'Z' -> bitmap ("11111","00001","00010","00100","01000","10000","11111","00000");
            case '.' -> bitmap ("00000","00000","00000","00000","00000","00110","00110","00000");
            case ',' -> bitmap ("00000","00000","00000","00000","00110","00110","00100","01000");
            case ':' -> bitmap ("00000","00110","00110","00000","00110","00110","00000","00000");
            case ';' -> bitmap ("00000","00110","00110","00000","00110","00110","00100","01000");
            case '-' -> bitmap ("00000","00000","00000","11111","00000","00000","00000","00000");
            case '_' -> bitmap ("00000","00000","00000","00000","00000","00000","11111","00000");
            case '+' -> bitmap ("00000","00100","00100","11111","00100","00100","00000","00000");
            case '=' -> bitmap ("00000","00000","11111","00000","11111","00000","00000","00000");
            case '/' -> bitmap ("00001","00010","00010","00100","01000","01000","10000","00000");
            case '\\' -> bitmap ("10000","01000","01000","00100","00010","00010","00001","00000");
            case '?' -> bitmap ("01110","10001","00001","00010","00100","00000","00100","00000");
            case '!' -> bitmap ("00100","00100","00100","00100","00100","00000","00100","00000");
            case '(' -> bitmap ("00010","00100","01000","01000","01000","00100","00010","00000");
            case ')' -> bitmap ("01000","00100","00010","00010","00010","00100","01000","00000");
            case '[' -> bitmap ("01110","01000","01000","01000","01000","01000","01110","00000");
            case ']' -> bitmap ("01110","00010","00010","00010","00010","00010","01110","00000");
            case '<' -> bitmap ("00010","00100","01000","10000","01000","00100","00010","00000");
            case '>' -> bitmap ("01000","00100","00010","00001","00010","00100","01000","00000");
            case '*' -> bitmap ("00000","10101","01110","11111","01110","10101","00000","00000");
            case '#' -> bitmap ("01010","11111","01010","01010","11111","01010","00000","00000");
            case '%' -> bitmap ("11001","11010","00100","01000","10110","00110","00000","00000");
            case '\'' -> bitmap ("00100","00100","00010","00000","00000","00000","00000","00000");
            case '"' -> bitmap ("01010","01010","00100","00000","00000","00000","00000","00000");
            default -> bitmap ("01110","10001","00001","00010","00100","00000","00100","00000");
        };
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
