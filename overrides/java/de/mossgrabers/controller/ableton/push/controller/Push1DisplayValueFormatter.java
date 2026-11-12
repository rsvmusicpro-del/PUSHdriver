// Written by PUSHdriver maintainers.
// Licensed under the same LGPL-3.0 terms as the pinned DrivenByMoss sources.

package de.mossgrabers.controller.ableton.push.controller;


/**
 * Bounds the Push 1's eight-cell value bars before drawing them.
 *
 * @author PUSHdriver maintainers
 */
public final class Push1DisplayValueFormatter
{
    private static final int BAR_CELLS = 8;
    private static final int HALF_STEPS = BAR_CELLS * 2;


    private Push1DisplayValueFormatter ()
    {
    }


    /**
     * Format a parameter value as eight cells.
     *
     * @param value The value
     * @param maxParam The maximum value
     * @param barsNon The empty-cell glyph
     * @param barsOne The half-cell glyph
     * @param barsTwo The full-cell glyph
     * @return Eight display cells
     */
    public static String formatValue (final int value, final int maxParam, final String barsNon, final String barsOne, final String barsTwo)
    {
        return String.join ("", fillFields (value, maxParam, barsNon, barsOne, barsTwo));
    }


    /**
     * Format a parameter value with its modulated position marker.
     *
     * @param modulated The modulated value
     * @param value The base value
     * @param maxParam The maximum value
     * @param barsNon The empty-cell glyph
     * @param barsOne The half-cell glyph
     * @param barsTwo The full-cell glyph
     * @param barsOneLeft The left-half marker glyph
     * @return Eight display cells
     */
    public static String formatModulatedValue (final int modulated, final int value, final int maxParam, final String barsNon, final String barsOne, final String barsTwo, final String barsOneLeft)
    {
        final String [] fields = fillFields (value, maxParam, barsNon, barsOne, barsTwo);
        if (maxParam <= 0)
            return String.join ("", fields);

        final int noOfBarsModulated = toHalfSteps (modulated, maxParam);
        final int pos = noOfBarsModulated / 2;
        if (noOfBarsModulated % 2 == 0)
        {
            if (pos > 0)
            {
                final int previous = pos - 1;
                if (fields[previous].equals (barsNon))
                    fields[previous] = barsOneLeft;
                else if (pos < BAR_CELLS && fields[pos].equals (barsOne))
                    fields[previous] = barsTwo;
                else if (pos == BAR_CELLS && fields[previous].equals (barsOne))
                    fields[previous] = barsTwo;
            }
        }
        else if (pos < BAR_CELLS)
        {
            if (fields[pos].equals (barsNon))
                fields[pos] = barsOne;
            else
                fields[pos] = barsTwo;
        }

        return String.join ("", fields);
    }


    private static String [] fillFields (final int value, final int maxParam, final String barsNon, final String barsOne, final String barsTwo)
    {
        final String [] fields = new String [BAR_CELLS];
        java.util.Arrays.fill (fields, barsNon);
        if (maxParam <= 0)
            return fields;

        final int boundedValue = clamp (value, 0, maxParam);
        final int noOfBars = toHalfSteps (boundedValue, maxParam);
        int count;
        for (count = 0; count < noOfBars / 2 && count < BAR_CELLS; count++)
            fields[count] = barsTwo;
        if (noOfBars % 2 == 1 && count < BAR_CELLS)
            fields[count] = barsOne;

        return fields;
    }


    private static int toHalfSteps (final int value, final int maxParam)
    {
        if (maxParam <= 0)
            return 0;
        final double scaled = HALF_STEPS * (double) value / maxParam;
        if (scaled <= 0)
            return 0;
        if (scaled >= HALF_STEPS)
            return HALF_STEPS;
        return (int) Math.round (scaled);
    }


    private static int clamp (final int value, final int minimum, final int maximum)
    {
        return Math.max (minimum, Math.min (maximum, value));
    }
}
