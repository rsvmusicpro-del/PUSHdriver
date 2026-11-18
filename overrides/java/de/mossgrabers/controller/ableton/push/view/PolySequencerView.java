// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.view;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.engine.Push1PadGrid;
import de.mossgrabers.controller.ableton.push.engine.sequencer.PolySequencerEngine;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.hardware.IHwButton;
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

    /** The engine follows the ViewManager lifecycle, not clip availability. */
    @Override
    public void onActivate ()
    {
        super.onActivate ();
        this.engine.activate ();
    }

    /** Invalidate Poly-local state whenever another view takes ownership. */
    @Override
    public void onDeactivate ()
    {
        this.engine.deactivate ();
        this.clearInteractionState ();
        super.onDeactivate ();
    }

    @Override
    public void drawGrid ()
    {
        super.drawGrid ();
    }

    /** True only while this exact view owns the Push pad grid. */
    boolean isCurrentActiveView ()
    {
        return this.surface.getViewManager ().getActive () == this;
    }

    /** Clear transient note selection and pressed-key state at ownership boundaries. */
    void onTrackSelectionChanged ()
    {
        this.clearInteractionState ();
    }

    private void clearInteractionState ()
    {
        this.keyManager.clearPressedKeys ();
        this.noteMemory.clear ();
        this.copyStep = -1;
        this.lastPressedNoteTime = 0;
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
        PushPolySequencerObservers.register (model.getTrackBank (), this);
    }


    /** {@inheritDoc} */
    @Override
    public void onGridNoteLongPress (final int note)
    {
        if (!this.engine.isActive () || !this.isCurrentActiveView () || !this.engine.isHardwarePadNote (note))
            return;

        final int index = Push1PadGrid.indexForNote (note);
        if (!this.engine.isHardwarePadIndex (index))
            return;
        final IHwButton padButton = this.surface.getButton (ButtonID.get (ButtonID.PAD1, index));
        if (padButton == null)
            return;
        padButton.setConsumed ();

        final int x = Push1PadGrid.columnForIndex (index);
        final int y = Push1PadGrid.rowForIndex (index);

        if (y < this.numRows - this.numSequencerRows)
            return;

        final INoteClip clip = this.getClip ();
        if (!clip.doesExist ())
            return;
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
        if (!this.engine.isActive () || !this.isCurrentActiveView ())
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
