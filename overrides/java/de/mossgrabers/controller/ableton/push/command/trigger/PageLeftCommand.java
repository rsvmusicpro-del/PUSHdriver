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

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;


/** Push 1 In control: enter the first available device slot chain. */
public class PageLeftCommand extends AbstractTriggerCommand<PushControlSurface, PushConfiguration>
{
    private static final int CHAIN_CURSOR_POLL_MS = 8;
    private static final int CHAIN_CURSOR_MAX_POLLS = 75;
    // A chain-entry marker is valid only briefly; an abandoned entry must not
    // capture an unrelated ROW1/OUT gesture minutes later.
    private static final int CHAIN_CURSOR_PENDING_TTL_MS = 2000;
    private static final Map<DeviceParamsMode, PendingRow1Selection> PENDING_ROW1 = new WeakHashMap<> ();
    private static final Map<PushControlSurface, Long> MODE_GENERATIONS = new WeakHashMap<> ();


    /** Return the current mode-activation generation for a Push surface. */
    public static long getModeGeneration (final PushControlSurface surface)
    {
        return MODE_GENERATIONS.getOrDefault (surface, 0L);
    }


    /** Check whether a delayed native callback still belongs to the same mode activation. */
    public static boolean isModeGenerationCurrent (final PushControlSurface surface, final long generation)
    {
        return getModeGeneration (surface) == generation;
    }


    /**
     * Track every mode transition independently of the current mode ID. A
     * Device -> Volume -> Device round trip must invalidate callbacks created
     * by the first Device activation even though DEVICE_PARAMS is active again.
     */
    private static void ensureModeChangeObserver (final DeviceParamsMode mode, final PushControlSurface surface)
    {
        if (MODE_GENERATIONS.containsKey (surface))
            return;

        MODE_GENERATIONS.put (surface, 0L);
        final WeakReference<PushControlSurface> surfaceReference = new WeakReference<> (surface);
        final WeakReference<DeviceParamsMode> modeReference = new WeakReference<> (mode);
        surface.getModeManager ().addChangeListener ( (previousID, activeID) -> {
            final PushControlSurface observedSurface = surfaceReference.get ();
            if (observedSurface != null)
                MODE_GENERATIONS.put (observedSurface, MODE_GENERATIONS.getOrDefault (observedSurface, 0L) + 1L);

            // A pending hierarchy gesture belongs only to the Device surface
            // that received it. Leaving the Device family invalidates it even
            // if the user returns before Bitwig publishes the new cursor.
            if (activeID != Modes.DEVICE_PARAMS && activeID != Modes.DEVICE_CHAINS)
            {
                final DeviceParamsMode observedMode = modeReference.get ();
                if (observedMode != null)
                    PENDING_ROW1.remove (observedMode);
            }
        });
    }

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
        final PendingRow1Selection pending = new PendingRow1Selection (cursor);
        PENDING_ROW1.put (mode, pending);
        // Expire even when the user never presses ROW1 or OUT. Without this
        // one-shot cleanup, a failed chain entry can leave a stale intent alive
        // indefinitely and intercept a later, unrelated hierarchy gesture.
        model.getHost ().scheduleTask ( () -> {
            if (PENDING_ROW1.get (mode) == pending)
                PENDING_ROW1.remove (mode);
        }, CHAIN_CURSOR_PENDING_TTL_MS);
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
        // The Chains chooser has its own immediate native Up/exit behavior.
        if (!surface.getModeManager ().isActive (Modes.DEVICE_PARAMS))
            return false;
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
        // A stable cursor ID is the strongest identity signal exposed by
        // the pinned API. If it is available on both sides, a name change
        // alone must never be treated as publication of the requested chain.
        if (pending.parentId != null && !pending.parentId.isEmpty ())
            return id != null && !id.isEmpty () && !pending.parentId.equals (id);

        // Older hosts may not expose cursor IDs. Fall back to the name only
        // when the parent had no usable ID; this is deliberately conservative.
        final String name = cursor.getName ();
        return name != null && !name.isEmpty () && !name.equals (pending.parentName);
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
        final DeviceParamsMode deviceParamsMode = (DeviceParamsMode) surface.getModeManager ().get (Modes.DEVICE_PARAMS);
        if (deviceParamsMode != null)
            ensureModeChangeObserver (deviceParamsMode, surface);
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
