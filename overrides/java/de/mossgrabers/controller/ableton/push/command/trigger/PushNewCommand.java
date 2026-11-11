// Written for PUSHdriver.
// Licensed under the same LGPL-3.0 terms as the DrivenByMoss source tree.

package de.mossgrabers.controller.ableton.push.command.trigger;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.command.trigger.clip.NewCommand;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ISlot;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ISlotBank;

import java.util.Optional;


/**
 * Push 1 New behavior adapted to Bitwig's clip launcher.
 *
 * Stop the current cursor-track clip only after confirming that a free slot
 * exists. Then create and start a new note clip using the upstream New
 * command's existing length and overdub rules.
 */
public class PushNewCommand extends NewCommand<PushControlSurface, PushConfiguration>
{
    private final IModel model;


    /**
     * Constructor.
     *
     * @param model The model
     * @param surface The Push 1 control surface
     */
    public PushNewCommand (final IModel model, final PushControlSurface surface)
    {
        super (model, surface);
        this.model = model;
    }


    /** {@inheritDoc} */
    @Override
    public void handleExecute (final boolean enableOverdub)
    {
        final ITrack cursorTrack = this.model.getCursorTrack ();
        if (!cursorTrack.doesExist ())
        {
            super.handleExecute (enableOverdub);
            return;
        }

        final ISlotBank slotBank = cursorTrack.getSlotBank ();
        final Optional<ISlot> selectedSlot = slotBank.getSelectedItem ();
        final int slotIndex = selectedSlot.isEmpty () ? 0 : selectedSlot.get ().getIndex ();
        final boolean selectedClipIsPlaying = selectedSlot.isPresent () && selectedSlot.get ().isPlaying ();
        final Optional<ISlot> slot = slotBank.getEmptySlot (slotIndex);
        if (slot.isEmpty ())
        {
            super.handleExecute (enableOverdub);
            return;
        }

        // Only stop a selected Launcher clip; do not interrupt unrelated Arranger playback.
        if (selectedClipIsPlaying)
            cursorTrack.stop ();
        this.model.createNoteClip (cursorTrack, slot.get (), this.getClipLength (), enableOverdub);
    }
}
