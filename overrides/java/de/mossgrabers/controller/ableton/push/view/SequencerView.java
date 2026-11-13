// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.view;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.clip.INoteClip;
import de.mossgrabers.framework.daw.clip.NotePosition;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.view.Views;
import de.mossgrabers.framework.view.AbstractDrumView;
import de.mossgrabers.framework.daw.clip.IStepInfo;

import java.util.List;
import de.mossgrabers.framework.view.sequencer.AbstractNoteSequencerView;


/**
 * The Sequencer view.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class SequencerView extends AbstractNoteSequencerView<PushControlSurface, PushConfiguration>
{
    /**
     * When enabled, keep the visible page synchronized with the playing page.
     * A manual page selection disables following until the playing page is
     * selected again. This mirrors Push 1's page-lock/auto-follow behavior.
     */
    private boolean followPlayhead = true;


    /**
     * Constructor.
     *
     * @param surface The surface
     * @param model The model
     */
    public SequencerView (final PushControlSurface surface, final IModel model)
    {
        super (Views.NAME_SEQUENCER, surface, model, true);
    }


    /**
     * Keep the visible sequencer page locked to Bitwig's live playhead while
     * auto-follow is enabled. The inherited renderer paints the moving step
     * and the top-row page indicators using the existing Push 1 LED colors.
     */
    /**
     * Render the moving playhead as a continuous dark-orange column across
     * every note row, including the lowest/root-note row. The shared renderer
     * otherwise leaves empty pads in their scale color while highlighting
     * note-bearing pads, which makes the playhead appear interrupted.
     *
     * @param stepInfo The information about the step
     * @param highlight True when this step is the current playhead position
     * @param channel The MIDI channel
     * @param step The step in the clip
     * @param pad The displayed note row
     * @param note The mapped MIDI note
     * @param editNotes The currently edited notes
     * @return The LED color
     */
    @Override
    protected String getStepColor (final IStepInfo stepInfo, final boolean highlight, final int channel, final int step, final int pad, final int note, final List<NotePosition> editNotes)
    {
        if (highlight)
            return AbstractDrumView.COLOR_PAD_MUTED;
        return super.getStepColor (stepInfo, false, channel, step, pad, note, editNotes);
    }


    @Override
    public void drawGrid ()
    {
        if (this.followPlayhead && this.isActive () && this.model.getTransport ().isPlaying ())
        {
            final INoteClip clip = this.getClip ();
            // The note-clip adapter remains available for empty slots. Do not
            // ask it to follow a playhead until Bitwig reports a real clip.
            if (clip.doesExist ())
            {
                final int stepsPerPage = this.numDisplayCols;
                final int currentStep = clip.getCurrentStep ();
                if (stepsPerPage > 0 && currentStep >= 0)
                {
                    final int playheadPage = currentStep / stepsPerPage;
                    if (playheadPage != clip.getEditPage ())
                        clip.scrollToPage (playheadPage);
                }
            }
        }
        super.drawGrid ();
    }


    /**
     * The upstream renderer treats the clip's total step count as the width
     * of one visible page. Push 1 displays only numDisplayCols steps, so use
     * the physical grid width when deciding whether the global playhead is
     * inside the currently selected page.
     *
     * @param step The global playback step
     * @return True if the step belongs to the visible Push 1 page
     */
    @Override
    protected boolean isInXRange (final int step)
    {
        final int stepsPerPage = this.numDisplayCols;
        if (stepsPerPage <= 0)
            return false;

        final int pageStart = this.getClip ().getEditPage () * stepsPerPage;
        return step >= pageStart && step < pageStart + stepsPerPage;
    }


    /**
     * A single page tap selects an edit page. Selecting a page other than the
     * playing page locks the view there; selecting the playing page restores
     * auto-follow. A two-pad gesture changes the loop and keeps auto-follow.
     *
     * @param pad The page pad
     * @param velocity The pad velocity
     */
    @Override
    protected void handleLoopArea (final int pad, final int velocity)
    {
        final int pressedPage = this.loopPadPressed;
        super.handleLoopArea (pad, velocity);

        // The base handler stores the first pressed pad and processes gestures
        // on release. Do not alter follow state on pad-down or stray releases.
        if (velocity > 0 || pressedPage < 0)
            return;

        if (pad != pressedPage)
        {
            // A two-page loop gesture changes the loop range, not the page lock.
            this.followPlayhead = true;
            return;
        }

        final INoteClip clip = this.getClip ();
        if (!clip.doesExist ())
        {
            this.followPlayhead = false;
            return;
        }

        final int stepsPerPage = this.numDisplayCols;
        final int currentStep = clip.getCurrentStep ();
        this.followPlayhead = stepsPerPage > 0 && currentStep >= 0 && currentStep / stepsPerPage == pad;
    }


    /** Manual page navigation must not be immediately undone by auto-follow. */
    @Override
    public void onLeft (final ButtonEvent event)
    {
        this.followPlayhead = false;
        super.onLeft (event);
    }


    /** Manual page navigation must not be immediately undone by auto-follow. */
    @Override
    public void onRight (final ButtonEvent event)
    {
        this.followPlayhead = false;
        super.onRight (event);
    }


    /** {@inheritDoc} */
    @Override
    public void onGridNoteLongPress (final int note)
    {
        if (!this.isActive ())
            return;

        final int index = note - 36;
        this.surface.getButton (ButtonID.get (ButtonID.PAD1, index)).setConsumed ();

        final int y = index / 8;
        if (y >= this.numSequencerRows)
            return;

        final NotePosition notePosition = new NotePosition (this.configuration.getMidiEditChannel (), index % 8, this.keyManager.map (y));
        this.editNote (this.getClip (), notePosition, false);
    }


    /** {@inheritDoc} */
    @Override
    protected boolean handleSequencerAreaButtonCombinations (final INoteClip clip, final NotePosition notePosition, final int row, final int velocity)
    {
        final boolean isSelectPressed = this.surface.isSelectPressed ();

        if (this.surface.isShiftPressed ())
        {
            if (velocity > 0)
                this.handleSequencerAreaRepeatOperator (clip, notePosition, velocity, !isSelectPressed);
            return true;
        }

        if (isSelectPressed)
        {
            this.editNote (clip, notePosition, true);
            return true;
        }

        return super.handleSequencerAreaButtonCombinations (clip, notePosition, row, velocity);
    }
}
