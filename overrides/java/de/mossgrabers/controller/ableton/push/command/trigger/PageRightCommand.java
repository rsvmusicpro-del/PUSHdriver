// Written by PUSHdriver maintainers.
// Licensed under the same LGPL-3.0 terms as the pinned DrivenByMoss sources.

package de.mossgrabers.controller.ableton.push.command.trigger;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.device.DeviceParamsMode;
import de.mossgrabers.controller.ableton.push.mode.device.DeviceLayerMode;
import de.mossgrabers.framework.command.core.AbstractTriggerCommand;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.featuregroup.IMode;
import de.mossgrabers.framework.featuregroup.ViewManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.view.Views;
import de.mossgrabers.framework.view.sequencer.AbstractSequencerView;


/** Push 1 Out control: leave the current device hierarchy. */
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
        final ViewManager viewManager = this.surface.getViewManager ();
        final var modeManager = this.surface.getModeManager ();
        final IMode activeMode = modeManager.getActive ();

        if (modeManager.isActive (Modes.BROWSER))
        {
            if (event == ButtonEvent.DOWN && activeMode != null)
                activeMode.selectPreviousItemPage ();
            return;
        }

        if (modeManager.isActive (Modes.DEVICE_CHAINS) || modeManager.isActive (Modes.DEVICE_PARAMS))
        {
            if (event == ButtonEvent.DOWN)
            {
                // Reuse DrivenByMoss' own ROW2-UP state machine. It exits the
                // Chains chooser, unwinds one hierarchy level, and finally
                // returns to Track using the upstream timing and mode rules.
                final DeviceParamsMode deviceParamsMode = (DeviceParamsMode) modeManager.get (Modes.DEVICE_PARAMS);
                deviceParamsMode.onSecondRow (7, ButtonEvent.DOWN);
            }
            return;
        }

        if (activeMode instanceof final DeviceLayerMode layerMode)
        {
            if (event == ButtonEvent.DOWN)
                layerMode.moveUpFromOut ();
            return;
        }

        if (Modes.isDeviceMode (modeManager.getActiveID ()))
        {
            if (event == ButtonEvent.DOWN && activeMode != null)
                activeMode.selectPreviousItemPage ();
            return;
        }

        if (viewManager.isActive (Views.SESSION))
        {
            if (event == ButtonEvent.DOWN)
                this.model.getCurrentTrackBank ().selectNextPage ();
            return;
        }

        final var activeView = viewManager.getActive ();
        if (activeView instanceof final AbstractSequencerView<?, ?> sequencerView)
            sequencerView.onRight (event);
    }
}
