// Copyright (C) 2026 PUSHdriver contributors
// Licensed under the same LGPLv3 terms as the adapted DrivenByMoss controller code.
package de.mossgrabers.controller.ableton.push.controller;

/**
 * Reverse-engineered 5x8 dot patterns for the eight Push 1 programmable LCD
 * characters (character codes 0..7). Rows are stored as five right-aligned
 * bits, matching the HD44780 custom-character data extracted from firmware.
 *
 * Standard ROM glyphs (including degree, division and arrow symbols) are not
 * duplicated here; the LCD controller owns those glyphs.
 *
 * Reverse-engineered bitmap source: https://gist.github.com/eeropic/cf679d65d3b7299668e1281ad0efcf5c
 */
public final class Push1DisplayGlyphs
{
    public static final int WIDTH = 5;
    public static final int HEIGHT = 8;

    private static final int [][] CUSTOM =
    {
        { 0x00, 0x04, 0x0E, 0x15, 0x04, 0x04, 0x00, 0x00 }, // 0: arrow up
        { 0x00, 0x04, 0x04, 0x15, 0x0E, 0x04, 0x00, 0x00 }, // 1: arrow down
        { 0x00, 0x1F, 0x00, 0x1F, 0x00, 0x1F, 0x00, 0x00 }, // 2: three rows
        { 0x18, 0x18, 0x18, 0x1B, 0x18, 0x18, 0x18, 0x00 }, // 3: slider left
        { 0x03, 0x03, 0x03, 0x1B, 0x03, 0x03, 0x03, 0x00 }, // 4: slider right
        { 0x1B, 0x1B, 0x1B, 0x1B, 0x1B, 0x1B, 0x1B, 0x00 }, // 5: slider center
        { 0x00, 0x00, 0x00, 0x1B, 0x00, 0x00, 0x00, 0x00 }, // 6: slider empty
        { 0x00, 0x18, 0x1F, 0x11, 0x11, 0x1F, 0x00, 0x00 }  // 7: folder
    };

    private Push1DisplayGlyphs ()
    {
    }

    public static boolean isKnownCustomGlyph (final int characterCode)
    {
        return characterCode >= 0 && characterCode < CUSTOM.length;
    }

    /** @return eight five-bit row masks, returned as a defensive copy */
    public static int [] bitmapRows (final int characterCode)
    {
        if (!isKnownCustomGlyph (characterCode))
            throw new IllegalArgumentException ("No extracted Push 1 custom glyph for character code " + characterCode);
        return CUSTOM[characterCode].clone ();
    }

    public static boolean isPixelOn (final int characterCode, final int x, final int y)
    {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT)
            throw new IllegalArgumentException ("Push 1 custom glyph pixel must be inside 5x8 bounds");
        return (bitmapRows (characterCode)[y] & (1 << (WIDTH - 1 - x))) != 0;
    }

    /** @return an eight-line diagnostic bitmap using '#' for lit and '.' for dark */
    public static String renderAscii (final int characterCode)
    {
        final int [] rows = bitmapRows (characterCode);
        final StringBuilder result = new StringBuilder (HEIGHT * (WIDTH + 1));
        for (int y = 0; y < HEIGHT; y++)
        {
            for (int x = 0; x < WIDTH; x++)
                result.append ((rows[y] & (1 << (WIDTH - 1 - x))) != 0 ? '#' : '.');
            if (y + 1 < HEIGHT)
                result.append ('\n');
        }
        return result.toString ();
    }
}
