// Copyright (C) 2026 PUSHdriver contributors
// Licensed under the same LGPLv3 terms as the adapted DrivenByMoss controller code.
package de.mossgrabers.controller.ableton.push.controller;

import de.mossgrabers.framework.utils.StringUtils;

/** Fits human-readable labels to the Push 1's fixed-width character cells. */
public final class Push1DisplayText
{
    // Push 1 firmware character table: code 0x1C is the single-cell ellipsis.
    private static final int ELLIPSIS_CHARACTER_CODE = 0x1C;

    private Push1DisplayText ()
    {
    }

    /**
     * Apply the existing DrivenByMoss ASCII transliteration before fitting.
     * This preserves umlaut and symbol handling while deferring truncation
     * until the actual Push 1 cell or block width is known.
     *
     * @param value source text
     * @param width target character-cell width
     * @return fixed-width, transliterated text
     */
    public static String fitAscii (final String value, final int width)
    {
        final StringBuilder ascii = new StringBuilder ();
        if (value != null)
        {
            value.codePoints ().forEach (codePoint -> {
                if (codePoint == 0x2026)
                    ascii.append ((char) ELLIPSIS_CHARACTER_CODE);
                else if (codePoint <= 0x7F)
                    ascii.append ((char) codePoint);
                else if (codePoint <= Character.MAX_VALUE && !Character.isSurrogate ((char) codePoint))
                    ascii.append (StringUtils.fixASCII (Character.toString ((char) codePoint)));
                else
                    ascii.append ('?');
            });
        }
        return fit (ascii.toString (), width);
    }

    /**
     * Fit text to an exact LCD width, retaining both ends of long names.
     * Push-specific seven-bit glyph codes are preserved.
     *
     * @param value source text
     * @param width target character-cell width
     * @return fixed-width, seven-bit-safe text
     */
    public static String fit (final String value, final int width)
    {
        if (width <= 0)
            return "";

        final StringBuilder normalized = new StringBuilder ();
        if (value != null)
        {
            value.codePoints ().forEach (codePoint -> {
                if (codePoint == 0x2026)
                    normalized.append ((char) ELLIPSIS_CHARACTER_CODE);
                else if (codePoint == '\n' || codePoint == '\r' || codePoint == '\t')
                    normalized.append (' ');
                else
                    normalized.append (codePoint <= 0x7F ? (char) codePoint : '?');
            });
        }

        String text = normalized.toString ();
        if (text.length () > width)
        {
            if (width >= 6)
            {
                final int available = width - 1;
                final int headLength = (available + 1) / 2;
                final int tailLength = available - headLength;
                final String head = text.substring (0, headLength).stripTrailing ();
                final String tail = tailLength == 0 ? "" : text.substring (text.length () - tailLength).stripLeading ();
                text = head + (char) ELLIPSIS_CHARACTER_CODE + tail;
            }
            else
                text = text.substring (0, width - 1) + ">";
        }

        final StringBuilder result = new StringBuilder (width);
        result.append (text);
        while (result.length () < width)
            result.append (' ');
        if (result.length () > width)
            result.setLength (width);
        return result.toString ();
    }

    /**
     * Preserve the left side of an LCD row and place a short notification in
     * its rightmost 17-character block.
     *
     * @param line the current 68-character row
     * @param value the notification (or empty text to clear the block)
     * @return one exact 68-character, seven-bit-safe row
     */
    public static String overlayRightBlock (final String line, final String value)
    {
        final int prefixWidth = Push1DisplayLayout.COLUMNS - Push1DisplayLayout.BLOCK_WIDTH;
        final StringBuilder prefix = new StringBuilder (prefixWidth);
        if (line != null)
        {
            line.codePoints ().limit (prefixWidth).forEach (codePoint -> {
                if (codePoint == '\n' || codePoint == '\r' || codePoint == '\t')
                    prefix.append (' ');
                else
                    prefix.append (codePoint <= 0x7F ? (char) codePoint : '?');
            });
        }
        while (prefix.length () < prefixWidth)
            prefix.append (' ');
        return prefix + Push1DisplayLayout.fitBlock (value);
    }


}
