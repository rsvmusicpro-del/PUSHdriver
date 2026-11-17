// Written by PUSHdriver maintainers.
// Licensed under the same LGPL-3.0 terms as the pinned DrivenByMoss sources.

package de.mossgrabers.controller.ableton.push.command.trigger;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.device.DeviceParamsMode;
import de.mossgrabers.framework.command.core.AbstractTriggerCommand;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ICursorDevice;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.view.Views;


/** Push 1 Out control: leave the current Device Chains hierarchy level. */
public class PageRightCommand extends AbstractTriggerCommand<PushControlSurface, PushConfiguration>
{
    public PageRightCommand (final IModel model, final PushControlSurface surface)
    {
        super (model, surface);
    }


    /** {@inheritDoc} */
    @Override
    public void execute (final ButtonEvent event, final int velocity)
    {
        if (event != ButtonEvent.DOWN || this.surface.getViewManager ().isActive (Views.SESSION))
            return;

        final var modeManager = this.surface.getModeManager ();
        if (modeManager.isActive (Modes.DEVICE_CHAINS))
        {
            final var chainsMode = modeManager.getActive ();
            if (chainsMode instanceof final DeviceParamsMode deviceMode)
                deviceMode.onSecondRow (7, ButtonEvent.DOWN);
            return;
        }

        if (!modeManager.isActive (Modes.DEVICE_PARAMS))
            return;

        final ICursorDevice cursorDevice = this.model.getCursorDevice ();
        if (!cursorDevice.doesExist () || !cursorDevice.isNested ())
            return;

        // Upstream's native Up action also handles the asynchronous parent
        // selection and layer-mode restoration. Ensure one Out press leaves
        // the chain even when the parameter-page selector was showing.
        final var activeMode = modeManager.getActive ();
        if (activeMode instanceof final DeviceParamsMode deviceMode)
        {
            deviceMode.setShowDevices (true);
            deviceMode.onSecondRow (7, ButtonEvent.DOWN);
        }
    }
}
