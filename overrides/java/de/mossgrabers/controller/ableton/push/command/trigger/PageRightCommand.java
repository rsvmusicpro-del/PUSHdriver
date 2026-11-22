// Written by PUSHdriver maintainers.
// Licensed under the same LGPL-3.0 terms as the pinned DrivenByMoss sources.

package de.mossgrabers.controller.ableton.push.command.trigger;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.device.DeviceParamsMode;
import de.mossgrabers.framework.command.core.AbstractTriggerCommand;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ICursorDevice;
import de.mossgrabers.framework.featuregroup.IMode;
import de.mossgrabers.framework.featuregroup.ViewManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.view.Views;
import de.mossgrabers.framework.view.sequencer.AbstractSequencerView;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;


/** Push 1 Out control: return to the parent device in the current chain. */
public class PageRightCommand extends AbstractTriggerCommand<PushControlSurface, PushConfiguration>
{
    private static final long MAX_TRANSITION_WAIT_MS = 2920;
    private static final long [] POLL_DELAYS_MS = { 12, 16, 24, 36, 73 };
    private static final AtomicLong NAVIGATION_GENERATION = new AtomicLong ();
    private static final Map<PushControlSurface, PendingNavigation> PENDING = new WeakHashMap<> ();


    private static final class PendingNavigation
    {
        private final WeakReference<IModel> model;
        private final CursorFingerprint before;
        private final boolean entering;
        private final long generation;
        private int pendingRowIndex = -1;
        private int poll;
        private long waitedMs;

        private PendingNavigation (final IModel model, final CursorFingerprint before,
            final boolean entering, final long generation)
        {
            this.model = new WeakReference<> (model);
            this.before = before;
            this.entering = entering;
            this.generation = generation;
        }
    }


    private record CursorFingerprint (String id, String name, int position, int index, boolean nested)
    {
    }


    public PageRightCommand (final IModel model, final PushControlSurface surface)
    {
        super (model, surface);
    }


    /** Cancel stale navigation reconciliation when a newer ROW2 action takes ownership. */
    public static void invalidatePendingNavigation (final PushControlSurface surface)
    {
        synchronized (PENDING)
        {
            PENDING.remove (surface);
        }
    }


    /**
     * Begin tracking one direct host transition. This is not an action queue:
     * the Bitwig command is dispatched by the caller immediately, and at most
     * the latest ROW1 intent is retained while the cursor publishes that change.
     *
     * @param model The current model
     * @param surface The owning Push surface
     * @param entering True for slot-chain entry, false for parent navigation
     * @return The transition generation token
     */
    public static long beginNavigation (final IModel model, final PushControlSurface surface, final boolean entering)
    {
        final long generation = NAVIGATION_GENERATION.incrementAndGet ();
        final PendingNavigation pending = new PendingNavigation (model, fingerprint (model.getCursorDevice ()), entering, generation);
        synchronized (PENDING)
        {
            PENDING.put (surface, pending);
        }
        return generation;
    }


    /** Start non-blocking cursor reconciliation after the direct host command was sent. */
    public static void scheduleNavigation (final IModel model, final PushControlSurface surface, final long generation)
    {
        final PendingNavigation pending = getPending (surface, model, generation);
        if (pending == null)
            return;
        if (isTransitionComplete (model.getCursorDevice (), pending))
        {
            completeNavigation (surface, model, pending);
            return;
        }
        scheduleProbe (surface, pending);
    }


    /**
     * Retain the latest ROW1 target while a just-issued chain/parent transition
     * is still reflected by the old cursor. If the host has already published
     * the transition, finish reconciliation synchronously and let ROW1 proceed.
     *
     * @return True when this ROW1 event was consumed for deferred replay
     */
    public static boolean queueRowDuringNavigation (final PushControlSurface surface, final IModel model, final int index)
    {
        final PendingNavigation pending;
        synchronized (PENDING)
        {
            pending = PENDING.get (surface);
        }
        if (pending == null || pending.model.get () != model)
            return false;

        final ICursorDevice cursor = model.getCursorDevice ();
        if (isTransitionComplete (cursor, pending))
        {
            // A newer ROW1 tap wins even if the host changed cursor state just
            // before its scheduled probe ran.
            pending.pendingRowIndex = index;
            completeNavigation (surface, model, pending);
            return true;
        }

        if (pending.before.equals (fingerprint (cursor)))
        {
            synchronized (PENDING)
            {
                if (PENDING.get (surface) == pending)
                    pending.pendingRowIndex = index;
            }
            return true;
        }

        // An unrelated cursor move must not replay a stale ROW1 target.
        cancelPending (surface, pending);
        return false;
    }


    private static PendingNavigation getPending (final PushControlSurface surface, final IModel model, final long generation)
    {
        synchronized (PENDING)
        {
            final PendingNavigation pending = PENDING.get (surface);
            return pending != null && pending.generation == generation && pending.model.get () == model ? pending : null;
        }
    }


    private static void cancelPending (final PushControlSurface surface, final PendingNavigation pending)
    {
        synchronized (PENDING)
        {
            if (PENDING.get (surface) == pending)
                PENDING.remove (surface);
        }
    }


    private static CursorFingerprint fingerprint (final ICursorDevice cursor)
    {
        return new CursorFingerprint (cursor.getID (), cursor.getName (), cursor.getPosition (), cursor.getIndex (), cursor.isNested ());
    }


    private static boolean isTransitionComplete (final ICursorDevice cursor, final PendingNavigation pending)
    {
        if (!cursor.doesExist ())
            return false;
        if (pending.before.equals (fingerprint (cursor)))
            return false;
        return !pending.entering || cursor.isNested () && cursor.getIndex () == 0;
    }


    private static void scheduleProbe (final PushControlSurface surface, final PendingNavigation pending)
    {
        final IModel model = pending.model.get ();
        if (model == null)
        {
            cancelPending (surface, pending);
            return;
        }

        final long remaining = MAX_TRANSITION_WAIT_MS - pending.waitedMs;
        if (remaining <= 0)
        {
            cancelPending (surface, pending);
            return;
        }
        final long delay = Math.min (POLL_DELAYS_MS[Math.min (pending.poll, POLL_DELAYS_MS.length - 1)], remaining);
        final WeakReference<PushControlSurface> surfaceReference = new WeakReference<> (surface);
        model.getHost ().scheduleTask (() -> {
            final PushControlSurface currentSurface = surfaceReference.get ();
            final IModel currentModel = pending.model.get ();
            if (currentSurface == null || currentModel == null)
            {
                if (currentSurface != null)
                    cancelPending (currentSurface, pending);
                return;
            }
            if (getPending (currentSurface, currentModel, pending.generation) != pending)
                return;

            pending.waitedMs += delay;
            pending.poll++;
            if (isTransitionComplete (currentModel.getCursorDevice (), pending))
            {
                completeNavigation (currentSurface, currentModel, pending);
                return;
            }
            scheduleProbe (currentSurface, pending);
        }, delay);
    }


    private static void completeNavigation (final PushControlSurface surface, final IModel model, final PendingNavigation pending)
    {
        final int pendingRow = pending.pendingRowIndex;
        cancelPending (surface, pending);

        final var modeManager = surface.getModeManager ();
        if (!modeManager.isActive (Modes.DEVICE_PARAMS))
            return;

        final DeviceParamsMode deviceParamsMode = (DeviceParamsMode) modeManager.get (Modes.DEVICE_PARAMS);
        final ICursorDevice cursorDevice = model.getCursorDevice ();
        if (!pending.entering)
        {
            // A queued ROW1 tap explicitly targets the parent device bank, so
            // it takes precedence over the normal layer-mix destination.
            if (pendingRow < 0 && cursorDevice.hasLayers ())
                modeManager.setActive (surface.getConfiguration ().getCurrentLayerMixMode ());
            else
                modeManager.setActive (Modes.DEVICE_PARAMS);
            deviceParamsMode.setShowDevices (pendingRow >= 0);
            cursorDevice.selectChannel ();
        }
        else
            deviceParamsMode.setShowDevices (true);

        if (pendingRow >= 0 && modeManager.isActive (Modes.DEVICE_PARAMS))
        {
            deviceParamsMode.setShowDevices (true);
            deviceParamsMode.onFirstRow (pendingRow, ButtonEvent.UP);
        }
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
                    modeManager.setActive (Modes.DEVICE_PARAMS);
                deviceParamsMode.setShowDevices (true);
                final var cursorDevice = this.model.getCursorDevice ();
                if (cursorDevice.doesExist () && cursorDevice.isNested ())
                {
                    final long generation = beginNavigation (this.model, this.surface, false);
                    cursorDevice.selectParent ();
                    scheduleNavigation (this.model, this.surface, generation);
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
