// Copyright (c) 2026 PUSHdriver contributors
package de.mossgrabers.controller.ableton.push.engine;

/** Stateless address and coordinate contract for the physical Push 1 8x8 pad grid. MIDI notes ascend bottom-to-top; coordinates are top-down. */
public final class Push1PadGrid
{
    public static final int FIRST_NOTE = 36;
    public static final int PAD_COUNT = 64;
    public static final int COLUMNS = 8;
    public static final int ROWS = 8;

    private Push1PadGrid ()
    {
        // Utility class.
    }

    /** Return the top-down, row-major hardware index, or -1 for a non-pad MIDI note. */
    public static int indexForNote (final int note)
    {
        final int offset = note - FIRST_NOTE;
        if (!isValidIndex (offset))
            return -1;
        final int rowFromBottom = offset / COLUMNS;
        final int column = offset % COLUMNS;
        return indexForCoordinate (column, ROWS - 1 - rowFromBottom);
    }

    /** Return the MIDI note for a hardware index, or -1 when the index is invalid. */
    public static int noteForIndex (final int index)
    {
        if (!isValidIndex (index))
            return -1;
        final int rowFromTop = index / COLUMNS;
        final int column = index % COLUMNS;
        return FIRST_NOTE + (ROWS - 1 - rowFromTop) * COLUMNS + column;
    }

    public static boolean isValidIndex (final int index)
    {
        return index >= 0 && index < PAD_COUNT;
    }

    public static int indexForCoordinate (final int column, final int row)
    {
        if (column < 0 || column >= COLUMNS || row < 0 || row >= ROWS)
            return -1;
        return row * COLUMNS + column;
    }

    public static int columnForIndex (final int index)
    {
        return isValidIndex (index) ? index % COLUMNS : -1;
    }

    public static int rowForIndex (final int index)
    {
        return isValidIndex (index) ? index / COLUMNS : -1;
    }
}
