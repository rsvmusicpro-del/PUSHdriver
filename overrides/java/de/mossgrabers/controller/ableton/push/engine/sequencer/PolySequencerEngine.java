// Copyright (c) 2026 PUSHdriver contributors
package de.mossgrabers.controller.ableton.push.engine.sequencer;

/**
 * Push 1 Poly Sequencer's feature-local grid and scale rules.
 * DrivenByMoss remains the sole owner of musical gestures and clip mutations.
 */
public final class PolySequencerEngine extends AbstractSequencerEngine
{
    private static final int MIDI_NOTE_COUNT = 128;
    private static final int NOTES_PER_OCTAVE = 12;

    public boolean isHardwarePadNote (final int note)
    {
        return Push1PadGrid.isHardwarePadNote (note);
    }

    public boolean isHardwarePadIndex (final int index)
    {
        return Push1PadGrid.isValidIndex (index);
    }

    /** Convert a top-down hardware row to the bottom-up Poly clip step. */
    public int stepForPad (final int x, final int y, final int columns, final int rows)
    {
        if (columns <= 0 || rows <= 0 || x < 0 || x >= columns || y < 0 || y >= rows)
            return -1;
        return columns * (rows - 1 - y) + x;
    }

    public boolean isScaleFundamental (final int midiNote, final int scaleOffset)
    {
        return midiNote >= 0 && midiNote < MIDI_NOTE_COUNT
                && scaleOffset >= 0 && scaleOffset < NOTES_PER_OCTAVE
                && Math.floorMod (midiNote, NOTES_PER_OCTAVE) == scaleOffset;
    }
}
