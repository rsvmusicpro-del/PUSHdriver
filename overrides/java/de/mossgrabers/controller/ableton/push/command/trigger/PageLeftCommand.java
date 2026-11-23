// Written by PUSHdriver maintainers.
// Licensed under the same LGPL-3.0 terms as the pinned DrivenByMoss sources.

package de.mossgrabers.controller.ableton.push.command.trigger;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.device.DeviceParamsMode;
import de.mossgrabers.framework.command.core.AbstractTriggerCommand;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ICursorDevice;
import de.mossgrabers.framework.daw.constants.Capability;
import de.mossgrabers.framework.featuregroup.IMode;
import de.mossgrabers.framework.featuregroup.ViewManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.view.Views;
import de.mossgrabers.framework.view.sequencer.AbstractSequencerView;

import java.util.Map;
import java.util.WeakHashMap;


/** Push 1 In control: enter the first available device slot chain. */
public class PageLeftCommand extends AbstractTriggerCommand<PushControlSurface, PushConfiguration>
{
    private static final Map<PushControlSurface, Long> MODE_GENERATIONS = new WeakHashMap<> ();


    /** Return the current activation generation for delayed native UI completions. */
    public static long getModeGeneration (final PushControlSurface surface)
    {
        return MODE_GENERATIONS.getOrDefault (surface, 0L);
    }


    /** Register one weak mode observer per surface to invalidate stale UI callbacks. */
    private static void ensureModeChangeObserver (final PushControlSurface surface)
    {
        if (MODE_GENERATIONS.containsKey (surface))
            return;
        MODE_GENERATIONS.put (surface, 0L);
        final java.lang.ref.WeakReference<PushControlSurface> reference = new java.lang.ref.WeakReference<> (surface);
        surface.getModeManager ().addChangeListener ( (previousID, activeID) -> {
            final PushControlSurface observed = reference.get ();
            if (observed != null)
                MODE_GENERATIONS.put (observed, MODE_GENERATIONS.getOrDefault (observed, 0L) + 1L);
        });
    }

    public PageLeftCommand (final IModel model, final PushControlSurface surface)
    {
        super (model, surface);
        ensureModeChangeObserver (surface);
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
                activeMode.selectNextItemPage ();
            return;
        }

        if (modeManager.isActive (Modes.DEVICE_CHAINS))
            return;

        if (modeManager.isActive (Modes.DEVICE_PARAMS))
        {
            if (event == ButtonEvent.DOWN && this.model.getHost ().supports (Capability.HAS_SLOT_CHAINS))
            {
                final var cursorDevice = this.model.getCursorDevice ();
                if (!cursorDevice.doesExist ())
                    return;

                final String [] slotChains = cursorDevice.getSlotChains ();
                if (slotChains != null && slotChains.length > 0)
                {
                    if (activeMode instanceof final DeviceParamsMode deviceParamsMode)
                    {
                        deviceParamsMode.setShowDevices (true);
                    }
                    // Native Bitwig cursor navigation is immediate. Never poll or queue
                    // input events behind a synthetic cursor-publication barrier.
                    cursorDevice.selectSlotChain (slotChains[0]);
                }
            }
            return;
        }

        if (Modes.isDeviceMode (modeManager.getActiveID ()))
        {
            if (event == ButtonEvent.DOWN && activeMode != null)
                activeMode.selectNextItemPage ();
            return;
        }

        if (viewManager.isActive (Views.SESSION))
        {
            if (event == ButtonEvent.DOWN)
                this.model.getCurrentTrackBank ().selectPreviousPage ();
            return;
        }

        final var activeView = viewManager.getActive ();
        if (activeView instanceof final AbstractSequencerView<?, ?> sequencerView)
            sequencerView.onLeft (event);
    }
}
