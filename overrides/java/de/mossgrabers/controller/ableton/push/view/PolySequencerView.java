// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.view;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.clip.INoteClip;
import de.mossgrabers.framework.daw.clip.NotePosition;
import de.mossgrabers.framework.daw.clip.StepState;
import de.mossgrabers.framework.daw.constants.Resolution;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.view.AbstractPlayView;
import de.mossgrabers.framework.view.sequencer.AbstractPolySequencerView;

import java.util.Map;


/**
 * Push 1 polyphonic sequencer with persistent note selection and direct step editing.
 *
 * The lower four rows are a scale-aware 32-note keyboard. The upper four rows
 * are the step grid. A note selection is retained until explicitly changed;
 * tapping a step adds that selection without replacing notes already there.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class PolySequencerView extends AbstractPolySequencerView<PushControlSurface, PushConfiguration>
{
    private int editingStep = -1;
    private int durationEditNote = -1;
    private boolean longPressStepPendingRelease = false;


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


    /**
     * Keep selected notes after the physical pad is released. Select+pad toggles
     * a note in the selection without forwarding the pad as a played note.
     * While a step is being edited, ordinary note-pad taps remove that pitch
     * from the step and Select+pad chooses the individual note for duration edit.
     *
     * @param note The hardware note (36-99)
     * @param velocity The pad velocity
     */
    @Override
    protected void handleNoteArea (final int note, final int velocity)
    {
        final int mappedNote = this.keyManager.map (note);
        if (mappedNote < 0)
            return;

        final IHwButton button = this.surface.getButton (ButtonID.get (ButtonID.PAD1, note - 36));
        if (this.editingStep >= 0)
        {
            if (velocity <= 0)
            {
                this.keyManager.setAllKeysPressed (mappedNote, 0);
                return;
            }

            button.setConsumed ();
            this.keyManager.setAllKeysPressed (mappedNote, 0);
            final INoteClip clip = this.getClip ();
            final NotePosition position = new NotePosition (this.configuration.getMidiEditChannel (), this.editingStep, mappedNote);

            if (this.surface.isSelectPressed ())
            {
                if (clip.getStep (position).getState () == StepState.START)
                {
                    this.durationEditNote = mappedNote;
                    this.clearEditNotes ();
                    this.editNote (clip, position, false);
                }
                return;
            }

            if (clip.getStep (position).getState () == StepState.START)
            {
                clip.clearStep (position);
                if (this.durationEditNote == mappedNote)
                    this.durationEditNote = -1;
                this.refreshEditedStep (clip);
            }
            return;
        }

        if (velocity > 0)
        {
            if (this.surface.isSelectPressed ())
            {
                button.setConsumed ();
                final Integer key = Integer.valueOf (mappedNote);
                if (this.noteMemory.containsKey (key))
                    this.noteMemory.remove (key);
                else
                    this.noteMemory.put (key, Integer.valueOf (100));
                return;
            }

            // A normal tap plays through the Push note path and also adds the
            // mapped pitch to the persistent programming selection.
            this.noteMemory.put (Integer.valueOf (mappedNote), Integer.valueOf (velocity));
        }

        this.keyManager.setAllKeysPressed (mappedNote, velocity);
    }


    /**
     * Add all currently selected notes to a step. Existing notes are never
     * toggled off or overwritten by an ordinary step tap.
     *
     * @param x The column
     * @param y The row
     * @param velocity The pad velocity
     */
    @Override
    protected void handleSequencerArea (final int x, final int y, final int velocity)
    {
        if (velocity != 0)
            return;

        final INoteClip clip = this.getClip ();
        final int step = this.numColumns * (this.numRows - 1 - y) + x;
        final int channel = this.configuration.getMidiEditChannel ();

        if (this.handleSequencerAreaButtonCombinations (clip, channel, step))
            return;

        // The release following a long-press is an edit gesture, not a step tap.
        if (this.longPressStepPendingRelease && step == this.editingStep)
        {
            this.longPressStepPendingRelease = false;
            return;
        }
        if (this.longPressStepPendingRelease && step != this.editingStep)
            this.longPressStepPendingRelease = false;

        // A normal tap on another step exits the previous step's note editor.
        if (this.editingStep >= 0 && this.editingStep != step)
        {
            this.editingStep = -1;
            this.durationEditNote = -1;
            this.clearEditNotes ();
        }

        if (this.noteMemory.isEmpty ())
        {
            this.surface.getDisplay ().notify ("Select notes in the lower grid first.");
            return;
        }

        final boolean accent = this.configuration.isAccentActive ();
        boolean changed = false;
        for (final Map.Entry<Integer, Integer> selected: this.noteMemory.entrySet ())
        {
            final NotePosition position = new NotePosition (channel, step, selected.getKey ().intValue ());
            if (clip.getStep (position).getState () != StepState.OFF)
                continue;

            final int velocityValue = accent ? this.configuration.getFixedAccentValue () : selected.getValue ().intValue ();
            clip.toggleStep (position, velocityValue);
            changed = true;
        }

        if (!changed)
            this.surface.getDisplay ().notify ("Selected notes are already present in this step.");
    }


    /**
     * Preserve Push's duplicate, mute, repeat and step-length gestures.
     *
     * @param clip The clip
     * @param channel The MIDI channel
     * @param step The step
     * @return True if the gesture was handled
     */
    @Override
    protected boolean handleSequencerAreaButtonCombinations (final INoteClip clip, final int channel, final int step)
    {
        final boolean isSelectPressed = this.surface.isSelectPressed ();
        final NotePosition notePosition = new NotePosition (channel, step, 0);

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
     * Long-pressing a step opens its notes in the lower grid for direct editing.
     *
     * @param note The hardware note
     */
    @Override
    public void onGridNoteLongPress (final int note)
    {
        if (!this.isActive ())
            return;

        final int index = note - 36;
        this.surface.getButton (ButtonID.get (ButtonID.PAD1, index)).setConsumed ();
        final IHwButton button = this.surface.getButton (ButtonID.get (ButtonID.PAD1, index));

        final int x = index % this.numColumns;
        final int y = index / this.numColumns;
        if (y < this.numRows - this.numSequencerRows)
            return;

        this.editingStep = this.numColumns * (this.numRows - 1 - y) + x;
        this.durationEditNote = -1;
        this.longPressStepPendingRelease = true;
        this.refreshEditedStep (this.getClip ());
    }


    /** Clear step-local editing state without discarding the global note selection. */
    private void clearStepEditor ()
    {
        this.editingStep = -1;
        this.durationEditNote = -1;
        this.longPressStepPendingRelease = false;
        this.clearEditNotes ();
    }


    /** {@inheritDoc} */
    @Override
    public void onLeft (final ButtonEvent event)
    {
        if (event == ButtonEvent.DOWN && this.editingStep >= 0)
            this.clearStepEditor ();
        super.onLeft (event);
    }


    /** {@inheritDoc} */
    @Override
    public void onRight (final ButtonEvent event)
    {
        if (event == ButtonEvent.DOWN && this.editingStep >= 0)
            this.clearStepEditor ();
        super.onRight (event);
    }


    /** {@inheritDoc} */
    @Override
    public void onOctaveDown (final ButtonEvent event)
    {
        if (event == ButtonEvent.DOWN && this.editingStep >= 0)
            this.clearStepEditor ();
        super.onOctaveDown (event);
    }


    /** {@inheritDoc} */
    @Override
    public void onOctaveUp (final ButtonEvent event)
    {
        if (event == ButtonEvent.DOWN && this.editingStep >= 0)
            this.clearStepEditor ();
        super.onOctaveUp (event);
    }


    /** {@inheritDoc} */
    @Override
    public void updateNoteMapping ()
    {
        if (this.editingStep >= 0)
            this.clearStepEditor ();
        super.updateNoteMapping ();
    }


    /**
     * Rebuild the Note-mode selection from the notes currently present in the
     * step being edited.
     *
     * @param clip The clip
     */
    private void refreshEditedStep (final INoteClip clip)
    {
        this.clearEditNotes ();
        if (this.editingStep < 0)
            return;

        final NotePosition position = new NotePosition (this.configuration.getMidiEditChannel (), this.editingStep, 0);
        for (int row = 0; row < 128; row++)
        {
            position.setNote (row);
            if (clip.getStep (position).getState () == StepState.START)
                this.editNote (clip, position, true);
        }
    }


    /**
     * If a note was selected in the lower grid while editing a step, resize only
     * that note. Without an individual note selection, retain the existing
     * Push gesture that changes the duration of every note in the source step.
     *
     * @param clip The clip
     * @param notePosition The endpoint step
     * @return True if a length gesture was handled
     */
    @Override
    protected boolean changeLengthOfNotes (final INoteClip clip, final NotePosition notePosition)
    {
        if (this.durationEditNote < 0)
            return super.changeLengthOfNotes (clip, notePosition);

        final int endStep = notePosition.getStep ();
        for (int sourceStep = endStep - 1; sourceStep >= 0; sourceStep--)
        {
            final int x = sourceStep % this.numColumns;
            final int y = this.numRows - 1 - sourceStep / this.numColumns;
            final int pad = y * this.numColumns + x;
            final IHwButton button = this.surface.getButton (ButtonID.get (ButtonID.PAD1, pad));
            if (!button.isLongPressed ())
                continue;

            button.setConsumed ();
            final NotePosition selected = new NotePosition (notePosition.getChannel (), sourceStep, this.durationEditNote);
            if (clip.getStep (selected).getState () == StepState.START)
            {
                final int length = endStep - sourceStep + 1;
                final double duration = length * Resolution.getValueAt (this.getResolutionIndex ());
                clip.updateStepDuration (selected, duration);
            }
            return true;
        }

        return super.changeLengthOfNotes (clip, notePosition);
    }


    /**
     * Keep the selected-note feedback distinct while always giving the scale
     * root the selected track's color, including when the root is selected or
     * physically held. The scale mapping itself remains owned by Scales.
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
            if (mappedNote >= 0 && Math.floorMod (mappedNote, 12) == this.scales.getScaleOffset ())
                return this.getPadColor (note, this.useDawColors ? track : null);

            if (mappedNote >= 0 && this.noteMemory.containsKey (Integer.valueOf (mappedNote)))
                return isRecording ? AbstractPlayView.COLOR_RECORD : AbstractPlayView.COLOR_PLAY;
        }

        return super.getGridColor (isKeyboardEnabled, isRecording, track, note);
    }
}
