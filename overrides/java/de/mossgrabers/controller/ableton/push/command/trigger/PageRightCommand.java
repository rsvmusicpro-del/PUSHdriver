// Written by PUSHdriver maintainers.
// Licensed under the same LGPL-3.0 terms as the pinned DrivenByMoss sources.

package de.mossgrabers.controller.ableton.push.command.trigger;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.device.DeviceParamsMode;
import de.mossgrabers.framework.command.core.AbstractTriggerCommand;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.featuregroup.IMode;
import de.mossgrabers.framework.featuregroup.ViewManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.view.Views;
import de.mossgrabers.framework.view.sequencer.AbstractSequencerView;


/** Push 1 Out control: return to the parent device in the current chain. */
public class PageRightCommand extends AbstractTriggerCommand<PushControlSurface, PushConfiguration>
{
    public PageRightCommand (final IModel model, final PushControlSurface surface)
    {
        super (model, surface);
    }

    private static long navigationGeneration;


    /** Cancel a delayed parent completion when the user starts a newer action. */
    public static void invalidatePendingNavigation ()
    {
        ++navigationGeneration;
    }


    /** Finish the asynchronous cursor update after a direct selectParent call. */
    public static void scheduleParentCompletion (final IModel model, final PushControlSurface surface)
    {
        final var modeManager = surface.getModeManager ();
        final DeviceParamsMode deviceParamsMode = (DeviceParamsMode) modeManager.get (Modes.DEVICE_PARAMS);
        final long generation = ++navigationGeneration;
        model.getHost ().scheduleTask ( () -> {
            if (generation != navigationGeneration || !modeManager.isActive (Modes.DEVICE_PARAMS))
                return;
            final var cursorDevice = model.getCursorDevice ();
            if (!cursorDevice.doesExist ())
                return;
            if (cursorDevice.hasLayers ())
                modeManager.setActive (surface.getConfiguration ().getCurrentLayerMixMode ());
            else
                modeManager.setActive (Modes.DEVICE_PARAMS);
            deviceParamsMode.setShowDevices (false);
            cursorDevice.selectChannel ();
        }, 75);
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
                final DeviceParamsMode deviceParamsMode = (DeviceParamsMode) modeManager.get (Modes.DEVICE_PARAMS);
                if (modeManager.isActive (Modes.DEVICE_CHAINS))
                {
                    modeManager.setActive (Modes.DEVICE_PARAMS);
                    deviceParamsMode.setShowDevices (true);
                }
                final var cursorDevice = this.model.getCursorDevice ();
                if (cursorDevice.doesExist () && cursorDevice.isNested ())
                {
                    cursorDevice.selectParent ();
                    scheduleParentCompletion (this.model, this.surface);
                }
            }
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
