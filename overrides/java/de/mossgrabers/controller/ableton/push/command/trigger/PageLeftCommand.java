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
    private static final int CHAIN_CURSOR_POLL_MS = 8;
    private static final int CHAIN_CURSOR_MAX_POLLS = 75;
    private static final Map<DeviceParamsMode, PendingRow1Selection> PENDING_ROW1 = new WeakHashMap<> ();

    private static final class PendingRow1Selection
    {
        private final String parentId;
        private final String parentName;
        private int targetIndex = -1;
        private boolean returnToParent;
        private int polls;
        private boolean scheduled;

        private PendingRow1Selection (final ICursorDevice cursor)
        {
            this.parentId = cursor.getID ();
            this.parentName = cursor.getName ();
        }
    }


    /**
     * Remember the current cursor before a direct slot-chain selection.
     * Polling remains dormant unless a dependent ROW1 gesture arrives.
     */
    public static void prepareSlotChainSelection (final DeviceParamsMode mode, final IModel model,
        final PushControlSurface surface, final String chain)
    {
        if (mode == null)
            return;
        PENDING_ROW1.remove (mode);
        if (chain == null || chain.isBlank ())
            return;
        final ICursorDevice cursor = model.getCursorDevice ();
        if (!cursor.doesExist ())
            return;
        PENDING_ROW1.put (mode, new PendingRow1Selection (cursor));
    }


    /** Return true when ROW1 must wait for the new nested cursor. */
    public static boolean deferRow1Selection (final DeviceParamsMode mode, final IModel model,
        final PushControlSurface surface, final int index)
    {
        final PendingRow1Selection pending = PENDING_ROW1.get (mode);
        if (pending == null)
            return false;
        if (isPublishedChainCursor (model, pending))
        {
            PENDING_ROW1.remove (mode);
            return false;
        }
        pending.targetIndex = index;
        pending.returnToParent = false;
        if (!pending.scheduled)
        {
            pending.scheduled = true;
            scheduleChainCursorProbe (mode, model, surface, pending);
        }
        return true;
    }


    /** Defer native OUT/ROW2-UP until the just-requested chain cursor is available. */
    public static boolean deferOutUntilChainCursor (final DeviceParamsMode mode, final IModel model,
        final PushControlSurface surface)
    {
        final PendingRow1Selection pending = PENDING_ROW1.get (mode);
        if (pending == null)
            return false;
        if (isPublishedChainCursor (model, pending))
        {
            PENDING_ROW1.remove (mode);
            return false;
        }
        pending.targetIndex = -1;
        pending.returnToParent = true;
        if (!pending.scheduled)
        {
            pending.scheduled = true;
            scheduleChainCursorProbe (mode, model, surface, pending);
        }
        return true;
    }


    private static boolean isPublishedChainCursor (final IModel model, final PendingRow1Selection pending)
    {
        final ICursorDevice cursor = model.getCursorDevice ();
        if (!cursor.doesExist () || !cursor.isNested ())
            return false;
        final String id = cursor.getID ();
        if (pending.parentId != null && !pending.parentId.isEmpty () && id != null && !id.isEmpty ()
                && !pending.parentId.equals (id))
            return true;
        final String name = cursor.getName ();
        return name != null && !name.equals (pending.parentName);
    }


    private static void scheduleChainCursorProbe (final DeviceParamsMode mode, final IModel model,
        final PushControlSurface surface, final PendingRow1Selection pending)
    {
        model.getHost ().scheduleTask (() -> {
            if (PENDING_ROW1.get (mode) != pending)
                return;
            pending.scheduled = false;
            if (!surface.getModeManager ().isActive (Modes.DEVICE_PARAMS))
            {
                PENDING_ROW1.remove (mode);
                return;
            }
            if (isPublishedChainCursor (model, pending))
            {
                final int target = pending.targetIndex;
                final boolean returnToParent = pending.returnToParent;
                PENDING_ROW1.remove (mode);
                if (returnToParent)
                    mode.onSecondRow (7, ButtonEvent.DOWN);
                else if (target >= 0)
                    mode.onFirstRow (target, ButtonEvent.UP);
                return;
            }
            if (++pending.polls >= CHAIN_CURSOR_MAX_POLLS)
            {
                PENDING_ROW1.remove (mode);
                return;
            }
            pending.scheduled = true;
            scheduleChainCursorProbe (mode, model, surface, pending);
        }, CHAIN_CURSOR_POLL_MS);
    }

    public PageLeftCommand (final IModel model, final PushControlSurface surface)
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
                        prepareSlotChainSelection (deviceParamsMode, this.model, this.surface, slotChains[0]);
                    }
                    // Match DrivenByMoss: issue the chain selection directly.
                    // Only a dependent ROW1 intent waits for cursor publication.
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
