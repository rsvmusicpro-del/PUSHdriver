// Copyright (C) 2026 PUSHdriver contributors
// Licensed under the same LGPLv3 terms as the adapted DrivenByMoss controller code.
package de.mossgrabers.controller.ableton.push.controller;

/** Fits human-readable labels to the Push 1's fixed-width character cells. */
public final class Push1DisplayText
{
    private Push1DisplayText ()
    {
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
            for (int i = 0; i < value.length (); i++)
            {
                final char c = value.charAt (i);
                if (c == '\n' || c == '\r' || c == '\t')
                    normalized.append (' ');
                else
                    normalized.append (c <= 0x7F ? c : '?');
            }
        }

        String text = normalized.toString ();
        if (text.length () > width)
        {
            if (width >= 6)
            {
                final int available = width - 3;
                final int headLength = (available + 1) / 2;
                final int tailLength = available - headLength;
                final String head = text.substring (0, headLength).stripTrailing ();
                final String tail = tailLength == 0 ? "" : text.substring (text.length () - tailLength).stripLeading ();
                text = head + "..." + tail;
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

    /** @return one exact 17-character physical display block */
    public static String fitBlock (final String value)
    {
        return fit (value, 17);
    }
}
