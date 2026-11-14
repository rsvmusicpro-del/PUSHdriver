// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.view;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.engine.sequencer.PolySequencerEngine;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.clip.INoteClip;
import de.mossgrabers.framework.daw.clip.NotePosition;
import de.mossgrabers.framework.daw.clip.StepState;
import de.mossgrabers.framework.view.sequencer.AbstractPolySequencerView;
import de.mossgrabers.framework.view.AbstractPlayView;
import de.mossgrabers.framework.daw.data.ITrack;


/**
 * The Poly Sequencer view.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class PolySequencerView extends AbstractPolySequencerView<PushControlSurface, PushConfiguration>
{
    /** The feature-local Push 1 Poly Sequencer engine. */
    private final PolySequencerEngine engine = new PolySequencerEngine ();

    private void synchronizeEngineLifecycle ()
    {
        if (this.isActive ())
            this.engine.activate ();
        else
            this.engine.deactivate ();
    }

    @Override
    public void drawGrid ()
    {
        this.synchronizeEngineLifecycle ();
        super.drawGrid ();
    }


    /**
     * Constructor.
     *
     * @param surface The surface
     * @param model The model
     * @param useTrackColor True to use the color of the current track for coloring the octaves
     */
    public PolySequencerView (final PushControlSurface surface, final IModel model, final boolean useTrackColor)
    {
        super (surface, model, useTrackColor);
    }


    /** {@inheritDoc} */
    @Override
    public void onGridNoteLongPress (final int note)
    {
        this.synchronizeEngineLifecycle ();
        if (!this.engine.isActive () || !this.engine.isHardwarePadNote (note))
            return;

        final int index = note - 36;
        if (!this.engine.isHardwarePadIndex (index))
            return;
        this.surface.getButton (ButtonID.get (ButtonID.PAD1, index)).setConsumed ();

        final int x = index % this.numColumns;
        final int y = index / this.numColumns;

        if (y < this.numRows - this.numSequencerRows)
            return;

        final INoteClip clip = this.getClip ();
        final int step = this.engine.stepForPad (x, y, this.numColumns, this.numRows);
        if (step < 0)
            return;
        final NotePosition notePosition = new NotePosition (this.configuration.getMidiEditChannel (), step, 0);

        this.clearEditNotes ();
        for (int row = 0; row < 128; row++)
        {
            notePosition.setNote (row);
            if (clip.getStep (notePosition).getState () == StepState.START)
                this.editNote (clip, notePosition, true);
        }
    }


    /** {@inheritDoc} */
    @Override
    protected boolean handleSequencerAreaButtonCombinations (final INoteClip clip, final int channel, final int step)
    {
        this.synchronizeEngineLifecycle ();
        if (!this.engine.isActive ())
            return true;

        final boolean isSelectPressed = this.surface.isSelectPressed ();
        final NotePosition notePosition = new NotePosition (channel, step, 0);

        // Change note repeat setting for step
        if (this.surface.isShiftPressed ())
        {
            for (int row = 0; row < 128; row++)
            {
                notePosition.setNote (row);
                if (clip.getStep (notePosition).getState () == StepState.START)
                    this.handleSequencerAreaRepeatOperator (clip, notePosition, 127, !isSelectPressed);
            }
            return true;
        }

        // Add note to edit notes with SELECT
        if (isSelectPressed)
        {
            for (int row = 0; row < 128; row++)
            {
                notePosition.setNote (row);
                if (clip.getStep (notePosition).getState () == StepState.START)
                    this.editNote (clip, notePosition, true);
            }
            return true;
        }

        return super.handleSequencerAreaButtonCombinations (clip, channel, step);
    }
    /**
     * Preserve the selected track's color for the scale fundamental. All
     * other keyboard feedback follows the upstream DrivenByMoss behavior.
     *
     * @param isKeyboardEnabled Whether the track accepts notes
     * @param isRecording Whether recording is active
     * @param track The cursor track
     * @param note The grid note
     * @return The pad color
     */
    @Override
    protected String getGridColor (final boolean isKeyboardEnabled, final boolean isRecording, final ITrack track, final int note)
    {
        if (isKeyboardEnabled)
        {
            final int mappedNote = this.keyManager.map (note);
            if (this.engine.isScaleFundamental (mappedNote, this.scales.getScaleOffset ()))
                return this.getPadColor (note, this.useDawColors ? track : null);
        }

        return super.getGridColor (isKeyboardEnabled, isRecording, track, note);
    }

}
