// Copyright (c) 2026 PUSHdriver contributors
package de.mossgrabers.controller.ableton.push.engine.sequencer;

/** The physical Push 1 8x8 MIDI pad grid and its index contract. */
public final class Push1PadGrid
{
    public static final int FIRST_PAD_NOTE = 36;
    public static final int PAD_COUNT = 64;

    private Push1PadGrid ()
    {
    }

    public static boolean isHardwarePadNote (final int note)
    {
        return note >= FIRST_PAD_NOTE && note < FIRST_PAD_NOTE + PAD_COUNT;
    }

    public static int indexForNote (final int note)
    {
        return isHardwarePadNote (note) ? note - FIRST_PAD_NOTE : -1;
    }

    public static boolean isValidIndex (final int index)
    {
        return isValidIndex (index, PAD_COUNT);
    }

    public static boolean isValidIndex (final int index, final int count)
    {
        return count > 0 && count <= PAD_COUNT && index >= 0 && index < count;
    }
}
