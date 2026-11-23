// Written by PUSHdriver maintainers.
// Licensed under the same LGPL-3.0 terms as the pinned DrivenByMoss sources.

package de.mossgrabers.controller.ableton.push.command.trigger;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.device.DeviceParamsMode;
import de.mossgrabers.framework.controller.ButtonID;
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
    private static final Map<IModel, PendingRow1Selection> PENDING_ROW1 = new WeakHashMap<> ();
    private static final Map<IModel, PendingParentReturn> PENDING_PARENT_RETURN = new WeakHashMap<> ();
    private static final Map<IModel, Boolean> OBSERVED_MODELS = new WeakHashMap<> ();

    private static final class PendingParentReturn
    {
        private final String childId;
        private final String childFingerprint;
        private final boolean childWasNested;
        private WeakReference<DeviceParamsMode> mode;
        private WeakReference<PushControlSurface> surface;
        private int polls;
        private boolean scheduled;
        private boolean enterFirstChain;
        private String chainToEnter;
        private int queuedUpCount;

        private PendingParentReturn (final ICursorDevice cursor, final String fingerprint,
            final DeviceParamsMode mode, final PushControlSurface surface)
        {
            this.childId = cursor.getID ();
            this.childFingerprint = fingerprint;
            this.childWasNested = cursor.isNested ();
            this.mode = new WeakReference<> (mode);
            this.surface = new WeakReference<> (surface);
        }
    }

    private static final class PendingRow1Selection
    {
        private final String parentId;
        private final String parentName;
        private final String parentFingerprint;
        private final boolean parentWasNested;
        private boolean requireNested = true;
        private WeakReference<DeviceParamsMode> mode;
        private WeakReference<PushControlSurface> surface;
        private int targetIndex = -1;
        private boolean returnToParent;
        private int polls;
        private boolean scheduled;

        private PendingRow1Selection (final ICursorDevice cursor, final String fingerprint,
            final DeviceParamsMode mode, final PushControlSurface surface)
        {
            this.parentId = cursor.getID ();
            this.parentName = cursor.getName ();
            this.parentFingerprint = fingerprint;
            this.parentWasNested = cursor.isNested ();
            this.setOwner (mode, surface);
        }


        private void setOwner (final DeviceParamsMode mode, final PushControlSurface surface)
        {
            this.mode = new WeakReference<> (mode);
            this.surface = new WeakReference<> (surface);
        }
    }


    /**
     * Remember the current cursor before a direct slot-chain selection.
     * The cursor-name observer completes dependent gestures immediately; the
     * bounded probe is armed only if a ROW1/OUT gesture arrives before publication.
     */
    public static void prepareSlotChainSelection (final DeviceParamsMode mode, final IModel model,
        final PushControlSurface surface, final String chain)
    {
        if (mode == null)
            return;
        PENDING_ROW1.remove (model);
        // A new chain selection supersedes any in-flight parent-return
        // completion. The host cursor request itself is not cancellable.
        PENDING_PARENT_RETURN.remove (model);
        if (chain == null || chain.isBlank ())
            return;
        final ICursorDevice cursor = model.getCursorDevice ();
        if (!cursor.doesExist ())
            return;
        PENDING_ROW1.put (model, new PendingRow1Selection (cursor, cursorFingerprint (model), mode, surface));
    }


    /** Track a Device Layer entry before Bitwig publishes the layer's first device. */
    public static void prepareLayerEntry (final DeviceParamsMode mode, final IModel model,
        final PushControlSurface surface)
    {
        if (mode == null)
            return;
        PENDING_ROW1.remove (model);
        PENDING_PARENT_RETURN.remove (model);
        final ICursorDevice cursor = model.getCursorDevice ();
        if (!cursor.doesExist ())
            return;
        final PendingRow1Selection pending = new PendingRow1Selection (cursor, cursorFingerprint (model), mode, surface);
        pending.requireNested = false;
        PENDING_ROW1.put (model, pending);
    }


    /** Defer IN until an already-issued parent return has published its cursor. */
    public static boolean deferInUntilParentReturn (final DeviceParamsMode mode, final IModel model,
        final PushControlSurface surface)
    {
        final PendingParentReturn pending = PENDING_PARENT_RETURN.get (model);
        if (pending == null)
            return false;
        pending.enterFirstChain = true;
        pending.chainToEnter = null;
        pending.queuedUpCount = 0;
        pending.mode = new WeakReference<> (mode);
        pending.surface = new WeakReference<> (surface);
        return true;
    }


    /** Defer a Chains-chooser selection until an in-flight parent return completes. */
    public static boolean deferChainSelectionUntilParentReturn (final DeviceParamsMode mode,
        final IModel model, final PushControlSurface surface, final String chain)
    {
        final PendingParentReturn pending = PENDING_PARENT_RETURN.get (model);
        if (pending == null || chain == null || chain.isBlank ())
            return false;
        pending.enterFirstChain = false;
        pending.chainToEnter = chain;
        pending.queuedUpCount = 0;
        pending.mode = new WeakReference<> (mode);
        pending.surface = new WeakReference<> (surface);
        return true;
    }


    /** Return true when ROW1 must wait for the new nested cursor. */
    public static boolean deferRow1Selection (final DeviceParamsMode mode, final IModel model,
        final PushControlSurface surface, final int index)
    {
        // A fresh sibling gesture supersedes an outstanding parent-return
        // completion; never let its later cursor notification finalize over it.
        PENDING_PARENT_RETURN.remove (model);
        final PendingRow1Selection pending = PENDING_ROW1.get (model);
        if (pending == null)
            return false;
        if (isPublishedChainCursor (model, pending))
        {
            PENDING_ROW1.remove (model);
            return false;
        }
        pending.setOwner (mode, surface);
        pending.targetIndex = index;
        pending.returnToParent = false;
        if (!pending.scheduled)
        {
            pending.scheduled = true;
            scheduleChainCursorProbe (model, pending);
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
        final PendingParentReturn parentReturn = PENDING_PARENT_RETURN.get (model);
        if (parentReturn != null)
        {
            // Keep rapid OUT/ROW2-UP presses ordered behind the in-flight
            // parent change instead of issuing selectParent on the stale child.
            parentReturn.enterFirstChain = false;
            parentReturn.chainToEnter = null;
            parentReturn.queuedUpCount++;
            return true;
        }
        final PendingRow1Selection pending = PENDING_ROW1.get (model);
        if (pending == null)
            return false;
        if (isPublishedChainCursor (model, pending))
        {
            PENDING_ROW1.remove (model);
            return false;
        }
        pending.setOwner (mode, surface);
        pending.targetIndex = -1;
        pending.returnToParent = true;
        if (!pending.scheduled)
        {
            pending.scheduled = true;
            scheduleChainCursorProbe (model, pending);
        }
        return true;
    }


    private static boolean isPublishedChainCursor (final IModel model, final PendingRow1Selection pending)
    {
        final ICursorDevice cursor = model.getCursorDevice ();
        if (!cursor.doesExist () || pending.requireNested && !cursor.isNested ())
            return false;
        // A nesting-state transition is authoritative even when names match.
        if (pending.parentWasNested != cursor.isNested ())
            return true;
        final String id = cursor.getID ();
        if (pending.parentId != null && !pending.parentId.isEmpty () && id != null && !id.isEmpty ()
                && !pending.parentId.equals (id))
            return true;
        final String name = cursor.getName ();
        if (name != null && !name.equals (pending.parentName))
            return true;
        // API 17 exposes no cursor ID. The visible sibling-bank fingerprint
        // distinguishes same-name nested parents from their chain children.
        final String currentFingerprint = cursorFingerprint (model);
        return currentFingerprint != null && !currentFingerprint.equals (pending.parentFingerprint);
    }


    private static String cursorFingerprint (final IModel model)
    {
        final ICursorDevice cursor = model.getCursorDevice ();
        if (!cursor.doesExist ())
            return null;

        final StringBuilder fingerprint = new StringBuilder (256);
        // Cursor position/index and nesting are the only exposed structural
        // identity signals here. Names, capabilities and bank contents can
        // mutate while the same cursor remains selected.
        fingerprint.append (cursor.isNested ()).append ('|')
            .append (cursor.getPosition ()).append ('|')
            .append (cursor.getIndex ());
        return fingerprint.toString ();
    }


    private static void ensureCursorNameObserver (final IModel model)
    {
        if (OBSERVED_MODELS.containsKey (model))
            return;

        OBSERVED_MODELS.put (model, Boolean.TRUE);
        final WeakReference<IModel> modelReference = new WeakReference<> (model);
        model.getCursorDevice ().addNameObserver (name -> {
            final IModel observedModel = modelReference.get ();
            if (observedModel != null)
                onCursorIdentityChanged (observedModel);
        });
        model.getCursorDevice ().addPositionObserver (position -> {
            final IModel observedModel = modelReference.get ();
            if (observedModel != null)
                onCursorIdentityChanged (observedModel);
        });
    }


    private static void onCursorIdentityChanged (final IModel model)
    {
        // There is one latest-wins pending intent for the shared Bitwig model.
        final PendingRow1Selection pending = PENDING_ROW1.get (model);
        if (pending != null)
            completePendingIfReady (model, pending);
        final PendingParentReturn parentReturn = PENDING_PARENT_RETURN.get (model);
        if (parentReturn != null)
            completeParentReturnIfReady (model, parentReturn);
    }


    /**
     * Complete a dependent hierarchy gesture as soon as Bitwig publishes the nested cursor.
     *
     * @return True if the pending intent was completed, invalidated or superseded
     */
    private static boolean completePendingIfReady (final IModel model,
        final PendingRow1Selection pending)
    {
        if (PENDING_ROW1.get (model) != pending)
            return true;
        final DeviceParamsMode mode = pending.mode.get ();
        final PushControlSurface surface = pending.surface.get ();
        if (mode == null || surface == null)
        {
            PENDING_ROW1.remove (model);
            return true;
        }
        final var modeManager = surface.getModeManager ();
        if (!modeManager.isActive (Modes.DEVICE_PARAMS))
        {
            // DeviceChainsMode publishes the new cursor before switching back
            // to Device Params. Keep the intent through that brief hand-off.
            if (modeManager.isActive (Modes.DEVICE_CHAINS))
                return false;
            PENDING_ROW1.remove (model);
            return true;
        }
        if (!isPublishedChainCursor (model, pending))
            return false;

        final int target = pending.targetIndex;
        final boolean returnToParent = pending.returnToParent;
        PENDING_ROW1.remove (model);
        if (returnToParent)
            mode.onSecondRow (7, ButtonEvent.DOWN);
        else if (target >= 0)
            mode.onFirstRow (target, ButtonEvent.UP);
        return true;
    }


    /** Start the native parent return and complete it on cursor publication. */
    public static void beginParentReturn (final DeviceParamsMode mode, final IModel model,
        final PushControlSurface surface)
    {
        final ICursorDevice cursor = model.getCursorDevice ();
        if (!cursor.doesExist ())
            return;
        PENDING_ROW1.remove (model);
        final PendingParentReturn pending = new PendingParentReturn (cursor, cursorFingerprint (model), mode, surface);
        PENDING_PARENT_RETURN.put (model, pending);
        scheduleParentReturnProbe (model, pending);
    }


    private static boolean completeParentReturnIfReady (final IModel model,
        final PendingParentReturn pending)
    {
        if (PENDING_PARENT_RETURN.get (model) != pending)
            return true;
        final DeviceParamsMode mode = pending.mode.get ();
        final PushControlSurface surface = pending.surface.get ();
        if (mode == null || surface == null)
        {
            PENDING_PARENT_RETURN.remove (model);
            return true;
        }
        final var modeManager = surface.getModeManager ();
        if (!modeManager.isActive (Modes.DEVICE_PARAMS))
        {
            // A temporary move into Device Chains may be followed by a
            // selection that must be applied after the parent is published.
            if (modeManager.isActive (Modes.DEVICE_CHAINS))
                return false;
            PENDING_PARENT_RETURN.remove (model);
            return true;
        }

        final ICursorDevice cursor = model.getCursorDevice ();
        final String currentId = cursor.getID ();
        final boolean idChanged = pending.childId != null && !pending.childId.isEmpty ()
            && currentId != null && !currentId.isEmpty () && !pending.childId.equals (currentId);
        final boolean returnedToRoot = pending.childWasNested && !cursor.isNested ();
        final String currentFingerprint = cursorFingerprint (model);
        final boolean identityChanged = currentFingerprint != null
            && !currentFingerprint.equals (pending.childFingerprint);
        // A name observer can also report a rename of the still-selected child.
        // Only cursor ID, nesting or position/index changes can complete this intent.
        if (!cursor.doesExist () || !(idChanged || returnedToRoot || identityChanged))
            return false;
        return finalizeParentReturn (model, pending, mode, surface, cursor);
    }


    private static boolean finalizeParentReturn (final IModel model, final PendingParentReturn pending,
        final DeviceParamsMode mode, final PushControlSurface surface, final ICursorDevice cursor)
    {
        if (PENDING_PARENT_RETURN.get (model) != pending)
            return true;
        final boolean enterFirstChain = pending.enterFirstChain;
        final String chainToEnter = pending.chainToEnter;
        final int queuedUpCount = pending.queuedUpCount;
        PENDING_PARENT_RETURN.remove (model);
        final var modeManager = surface.getModeManager ();
        final boolean hasLayers = cursor.hasLayers ();
        if (hasLayers)
            modeManager.setActive (surface.getConfiguration ().getCurrentLayerMixMode ());
        else
            modeManager.setActive (Modes.DEVICE_PARAMS);
        mode.setShowDevices (false);
        cursor.selectChannel ();

        // IN received while the parent command was in flight belongs to the
        // newly published parent, not to the child cursor being left.
        if (!hasLayers && modeManager.isActive (Modes.DEVICE_PARAMS))
        {
            if (chainToEnter != null)
                selectSpecificChain (mode, model, surface, chainToEnter);
            else if (enterFirstChain)
                selectFirstAvailableChain (mode, model, surface);
            else if (queuedUpCount > 0)
                replayQueuedOut (model, surface, queuedUpCount);
        }
        return true;
    }


    private static void replayQueuedOut (final IModel model, final PushControlSurface surface,
        final int queuedUpCount)
    {
        for (int i = 0; i < queuedUpCount; i++)
        {
            final var modeManager = surface.getModeManager ();
            if (!Modes.isDeviceMode (modeManager.getActiveID ()))
                return;
            // Re-enter through the dedicated OUT command so Device Params,
            // Chains and Layer modes retain their own native exit semantics.
            surface.getButton (ButtonID.PAGE_RIGHT).trigger (ButtonEvent.DOWN);
            final PendingParentReturn next = PENDING_PARENT_RETURN.get (model);
            if (next != null)
            {
                next.queuedUpCount += queuedUpCount - i - 1;
                next.enterFirstChain = false;
                return;
            }
        }
    }


    private static void selectFirstAvailableChain (final DeviceParamsMode mode, final IModel model,
        final PushControlSurface surface)
    {
        if (mode == null || surface == null || !model.getHost ().supports (Capability.HAS_SLOT_CHAINS))
            return;
        final ICursorDevice cursor = model.getCursorDevice ();
        if (!cursor.doesExist ())
            return;
        final String [] slotChains = cursor.getSlotChains ();
        if (slotChains == null || slotChains.length == 0)
            return;
        selectSpecificChain (mode, model, surface, slotChains[0]);
    }


    private static void selectSpecificChain (final DeviceParamsMode mode, final IModel model,
        final PushControlSurface surface, final String chain)
    {
        if (mode == null || surface == null || chain == null || chain.isBlank ()
                || !model.getHost ().supports (Capability.HAS_SLOT_CHAINS))
            return;
        final ICursorDevice cursor = model.getCursorDevice ();
        if (!cursor.doesExist ())
            return;
        final String [] slotChains = cursor.getSlotChains ();
        if (slotChains == null)
            return;
        boolean available = false;
        for (final String availableChain: slotChains)
            if (chain.equals (availableChain))
            {
                available = true;
                break;
            }
        if (!available)
            return;
        mode.setShowDevices (true);
        prepareSlotChainSelection (mode, model, surface, chain);
        cursor.selectSlotChain (chain);
    }


    private static void scheduleParentReturnProbe (final IModel model,
        final PendingParentReturn pending)
    {
        model.getHost ().scheduleTask (() -> {
            if (PENDING_PARENT_RETURN.get (model) != pending)
                return;
            pending.scheduled = false;
            if (completeParentReturnIfReady (model, pending))
                return;
            if (++pending.polls >= CHAIN_CURSOR_MAX_POLLS)
            {
                // Never finalize against an unverified cursor. This bounded
                // fallback avoids a permanent timer if Bitwig publishes no
                // usable identity (for example, same-name parent and child).
                PENDING_PARENT_RETURN.remove (model);
                return;
            }
            pending.scheduled = true;
            scheduleParentReturnProbe (model, pending);
        }, CHAIN_CURSOR_POLL_MS);
    }


    private static void scheduleChainCursorProbe (final IModel model,
        final PendingRow1Selection pending)
    {
        model.getHost ().scheduleTask (() -> {
            if (PENDING_ROW1.get (model) != pending)
                return;
            pending.scheduled = false;
            if (completePendingIfReady (model, pending))
                return;
            if (++pending.polls >= CHAIN_CURSOR_MAX_POLLS)
            {
                PENDING_ROW1.remove (model);
                return;
            }
            pending.scheduled = true;
            scheduleChainCursorProbe (model, pending);
        }, CHAIN_CURSOR_POLL_MS);
    }

    public PageLeftCommand (final IModel model, final PushControlSurface surface)
    {
        super (model, surface);
        // One observer per model serves all Push surfaces. It holds only a
        // weak model reference; pending surface state is weak as well.
        ensureCursorNameObserver (this.model);
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
            if (event == ButtonEvent.DOWN && activeMode instanceof final DeviceParamsMode deviceParamsMode
                    && this.model.getHost ().supports (Capability.HAS_SLOT_CHAINS))
            {
                if (deferInUntilParentReturn (deviceParamsMode, this.model, this.surface))
                    return;
                selectFirstAvailableChain (deviceParamsMode, this.model, this.surface);
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
