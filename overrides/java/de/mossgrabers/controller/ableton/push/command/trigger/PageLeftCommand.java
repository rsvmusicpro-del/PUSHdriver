// Written by PUSHdriver maintainers.
// Licensed under the same LGPL-3.0 terms as the pinned DrivenByMoss sources.

package de.mossgrabers.controller.ableton.push.command.trigger;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.command.core.AbstractTriggerCommand;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.constants.Capability;
import de.mossgrabers.framework.daw.data.ICursorDevice;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.utils.ButtonEvent;


/** Push 1 In control: enter the native Device Chains selector. */
public class PageLeftCommand extends AbstractTriggerCommand<PushControlSurface, PushConfiguration>
{
    public PageLeftCommand (final IModel model, final PushControlSurface surface)
    {
        super (model, surface);
    }


    /** {@inheritDoc} */
    @Override
    public void execute (final ButtonEvent event, final int velocity)
    {
        if (event != ButtonEvent.DOWN)
            return;

        final var modeManager = this.surface.getModeManager ();
        if (!modeManager.isActive (Modes.DEVICE_PARAMS))
            return;

        if (!this.model.getHost ().supports (Capability.HAS_SLOT_CHAINS))
            return;

        final ICursorDevice cursorDevice = this.model.getCursorDevice ();
        if (!cursorDevice.doesExist ())
            return;

        final String [] slotChains = cursorDevice.getSlotChains ();
        if (slotChains == null || slotChains.length == 0)
            return;

        modeManager.setActive (Modes.DEVICE_CHAINS);
    }
}
