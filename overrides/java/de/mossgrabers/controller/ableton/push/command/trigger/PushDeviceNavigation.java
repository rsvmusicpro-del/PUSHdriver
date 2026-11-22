// Written by PUSHdriver maintainers.
// Licensed under the same LGPL-3.0 terms as the pinned DrivenByMoss sources.
package de.mossgrabers.controller.ableton.push.command.trigger;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.constants.Capability;
import de.mossgrabers.framework.daw.data.ICursorDevice;
import de.mossgrabers.framework.daw.data.IDevice;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;


/**
 * Serializes Push 1 hierarchy commands against Bitwig's asynchronous cursor updates.
 *
 * The registry, queued actions and scheduled callbacks deliberately retain neither
 * the controller surface nor the model. A hierarchy callback must likewise refer
 * to its owning mode through a WeakReference.
 */
public final class PushDeviceNavigation
{
    private static final long MAX_WAIT_MS = 2920;
    private static final String TIMING_SETTING = System.getenv ("PUSHDRIVER_NAV_TIMING");
    private static final boolean TIMING_ENABLED = "1".equals (TIMING_SETTING) || Boolean.parseBoolean (TIMING_SETTING);
    private static final int MAX_ACTIONS = 32;
    private static final long [] POLL_BACKOFF_MS = { 4, 8, 12, 20, 40 };

    private static final Map<PushControlSurface, State> STATES = new WeakHashMap<> ();

    private enum Type
    {
        ENTER,
        EXIT,
        SELECT
    }

    private enum Wait
    {
        NONE,
        ENTER,
        EXIT,
        MOVE,
        ROOT_SELECT
    }

    private static final class Action
    {
        private final Type type;
        private final String chain;
        private final long queuedAtNanos = TIMING_ENABLED ? System.nanoTime () : 0;
        private int index;
        private Runnable onReselect;
        private long dispatchedAtNanos;
        private int timingPolls;

        private Action (final Type type, final String chain, final int index)
        {
            this (type, chain, index, null);
        }

        private Action (final Type type, final String chain, final int index, final Runnable onReselect)
        {
            this.type = type;
            this.chain = chain;
            this.index = index;
            this.onReselect = onReselect;
        }
    }

    private static final class State
    {
        private final WeakReference<IModel> model;
        private final ArrayDeque<Action> actions = new ArrayDeque<> ();
        private Wait wait = Wait.NONE;
        private CursorFingerprint before;
        private int expectedIndex;
        private int polls;
        private long waitedMs;
        private long waitStartedAtNanos;
        private long lastPollDelayMs;
        private boolean running;

        private State (final IModel model)
        {
            this.model = new WeakReference<> (model);
        }

        private void cancel ()
        {
            this.actions.clear ();
            this.running = false;
            this.wait = Wait.NONE;
            this.polls = 0;
            this.waitedMs = 0;
            this.waitStartedAtNanos = 0;
            this.lastPollDelayMs = 0;
            this.before = null;
        }
    }


    private PushDeviceNavigation ()
    {
        // Utility class.
    }


    public static void enterFirstSlotChain (final PushControlSurface surface, final IModel model)
    {
        enqueue (surface, model, new Action (Type.ENTER, null, -1));
    }


    public static void selectSlotChain (final PushControlSurface surface, final IModel model, final String chain)
    {
        if (chain != null && !chain.isBlank ())
            enqueue (surface, model, new Action (Type.ENTER, chain, -1));
    }


    public static void exitParent (final PushControlSurface surface, final IModel model)
    {
        final State state = stateFor (surface, model);
        synchronized (state)
        {
            // Out is a navigation escape hatch: keep the host operation already
            // dispatched, but move the parent request ahead of queued ENTER/SELECT
            // intents so stale ROW1 work cannot delay leaving the current chain.
            final ArrayDeque<Action> reordered = new ArrayDeque<> ();
            if (state.running && !state.actions.isEmpty ())
                reordered.addLast (state.actions.removeFirst ());
            reordered.addLast (new Action (Type.EXIT, null, -1));
            reordered.addAll (state.actions);
            while (reordered.size () > MAX_ACTIONS)
                reordered.removeLast ();
            state.actions.clear ();
            state.actions.addAll (reordered);
            startNext (surface, state);
        }
    }


    /**
     * Queue ROW1 while a hierarchy transition or nested cursor move is outstanding.
     *
     * @return false only when upstream must handle an immediate selection; true when
     *         this helper consumed the request, including when queue capacity rejects it.
     */
    public static boolean selectRow (final PushControlSurface surface, final IModel model, final int index)
    {
        return selectRow (surface, model, index, null);
    }


    /**
     * Queue ROW1 and preserve upstream's same-device reselect gesture if the cursor
     * reaches that target first. Callers must not let this callback strongly capture
     * the mode, model or surface; use a WeakReference to its owning mode.
     */
    public static boolean selectRow (final PushControlSurface surface, final IModel model, final int index, final Runnable onReselect)
    {
        final ICursorDevice cursor = model.getCursorDevice ();
        final State state = stateFor (surface, model);
        synchronized (state)
        {
            if (!state.running && state.actions.isEmpty () && (!cursor.isNested () || cursor.getIndex () == index))
                return false;

            // A nested sibling move is already a single, irreversible host
            // command. Retarget its remaining journey to the newest ROW1 intent
            // rather than replaying obsolete intermediate targets afterwards.
            final Action inFlight = state.actions.peekFirst ();
            boolean exitQueued = false;
            if (state.running)
                for (final Action pending: state.actions)
                    if (pending.type == Type.EXIT)
                    {
                        exitQueued = true;
                        break;
                    }
            if (state.running && !exitQueued && state.wait == Wait.MOVE && inFlight != null && inFlight.type == Type.SELECT)
            {
                inFlight.index = index;
                inFlight.onReselect = onReselect;
                return true;
            }

            enqueueLocked (state, new Action (Type.SELECT, null, index, onReselect));
            startNext (surface, state);
            return true;
        }
    }


    public static boolean isEntering (final PushControlSurface surface)
    {
        return isEntering (stateFor (surface));
    }


    public static boolean isEntering (final PushControlSurface surface, final IModel model)
    {
        return isEntering (stateFor (surface, model));
    }


    private static boolean isEntering (final State state)
    {
        if (state == null)
            return false;
        synchronized (state)
        {
            if (state.wait == Wait.ENTER)
                return true;
            for (final Action action: state.actions)
                if (action.type == Type.ENTER)
                    return true;
            return false;
        }
    }


    public static boolean isBusy (final PushControlSurface surface)
    {
        return isBusy (stateFor (surface));
    }


    private static boolean isBusy (final State state)
    {
        if (state == null)
            return false;
        synchronized (state)
        {
            return state.running || !state.actions.isEmpty ();
        }
    }


    private static void enqueue (final PushControlSurface surface, final IModel model, final Action action)
    {
        final State state = stateFor (surface, model);
        synchronized (state)
        {
            enqueueLocked (state, action);
            startNext (surface, state);
        }
    }


    private static void enqueueLocked (final State state, final Action action)
    {
        final Action last = state.actions.peekLast ();
        if (action.type == Type.SELECT && last != null && last.type == Type.SELECT
            && (!state.running || last != state.actions.peekFirst ()))
        {
            last.index = action.index;
            last.onReselect = action.onReselect;
            return;
        }

        if (state.actions.size () >= MAX_ACTIONS)
        {
            // A Bitwig operation already sent to the host cannot be cancelled. Keep
            // the in-flight head and its serialized queue; reject only the newest intent.
            final IModel model = state.model.get ();
            if (model != null)
                model.getHost ().showNotification ("Push Device navigation queue full: newest command ignored");
            return;
        }

        state.actions.addLast (action);
    }


    private static State stateFor (final PushControlSurface surface, final IModel model)
    {
        synchronized (STATES)
        {
            State state = STATES.get (surface);
            if (state == null)
            {
                state = new State (model);
                STATES.put (surface, state);
            }
            else if (state.model.get () != model)
            {
                // A surface can be rebound to a new model while an old host callback
                // is still pending. Cancel the old queue before installing new state.
                synchronized (state)
                {
                    state.cancel ();
                }
                state = new State (model);
                STATES.put (surface, state);
            }
            return state;
        }
    }


    private static State stateFor (final PushControlSurface surface)
    {
        synchronized (STATES)
        {
            return STATES.get (surface);
        }
    }


    private static void startNext (final PushControlSurface surface, final State state)
    {
        if (state.running)
            return;

        final IModel model = state.model.get ();
        if (model == null)
        {
            state.cancel ();
            return;
        }

        final ICursorDevice cursor = model.getCursorDevice ();
        while (!state.actions.isEmpty ())
        {
            final Action action = state.actions.peekFirst ();
            if (!cursor.doesExist ())
            {
                state.cancel ();
                return;
            }

            if (action.type == Type.ENTER)
            {
                if (!model.getHost ().supports (Capability.HAS_SLOT_CHAINS))
                {
                    state.actions.removeFirst ();
                    continue;
                }

                final String [] chains = cursor.getSlotChains ();
                String selected = action.chain;
                if (selected == null)
                    selected = chains == null || chains.length == 0 ? null : chains[0];

                boolean found = false;
                if (selected != null && chains != null)
                    for (final String chain: chains)
                        if (selected.equals (chain))
                        {
                            found = true;
                            break;
                        }

                if (!found)
                {
                    state.actions.removeFirst ();
                    continue;
                }

                state.before = fingerprint (cursor);
                state.expectedIndex = 0;
                beginWait (state, Wait.ENTER);
                markDispatched (action);
                cursor.selectSlotChain (selected);
                schedule (surface, state, model);
                return;
            }

            if (action.type == Type.EXIT)
            {
                if (!cursor.isNested ())
                {
                    state.actions.removeFirst ();
                    continue;
                }

                state.before = fingerprint (cursor);
                beginWait (state, Wait.EXIT);
                markDispatched (action);
                cursor.selectParent ();
                schedule (surface, state, model);
                return;
            }

            final IDevice target = cursor.getDeviceBank ().getItem (action.index);
            if (target == null || !target.doesExist ())
            {
                state.actions.removeFirst ();
                continue;
            }

            final int current = cursor.getIndex ();
            if (current == action.index)
            {
                state.actions.removeFirst ();
                runReselect (model, action);
                continue;
            }

            if (cursor.isNested ())
            {
                if (action.index > current)
                {
                    if (!cursor.canSelectNext ())
                    {
                        state.actions.removeFirst ();
                        continue;
                    }
                    state.expectedIndex = current + 1;
                    beginWait (state, Wait.MOVE);
                    markDispatched (action);
                    cursor.selectNext ();
                }
                else
                {
                    if (!cursor.canSelectPrevious ())
                    {
                        state.actions.removeFirst ();
                        continue;
                    }
                    state.expectedIndex = current - 1;
                    beginWait (state, Wait.MOVE);
                    markDispatched (action);
                    cursor.selectPrevious ();
                }

                schedule (surface, state, model);
                return;
            }

            beginWait (state, Wait.ROOT_SELECT);
            markDispatched (action);
            target.select ();
            schedule (surface, state, model);
            return;
        }

        state.running = false;
        state.wait = Wait.NONE;
    }


    private static void runReselect (final IModel model, final Action action)
    {
        if (action.onReselect == null)
            return;
        try
        {
            action.onReselect.run ();
        }
        catch (final RuntimeException ex)
        {
            model.getHost ().println ("PUSHdriver Device reselect callback failed: " + ex.getMessage ());
        }
    }


    private static void schedule (final PushControlSurface surface, final State state, final IModel model)
    {
        // Bitwig may publish the cursor change synchronously. Confirm it before
        // scheduling a timer so the common fast path pays no avoidable 12 ms.
        final Action action = state.actions.peekFirst ();
        final ICursorDevice cursor = model.getCursorDevice ();
        if (action != null && isComplete (state.wait, cursor, action, state))
        {
            final Wait completed = state.wait;
            if (completed != Wait.MOVE || cursor.getIndex () == action.index)
            {
                state.actions.removeFirst ();
                reportTiming (model, action, "complete");
            }
            state.running = false;
            state.wait = Wait.NONE;
            state.polls = 0;
            state.waitedMs = 0;
            state.waitStartedAtNanos = 0;
            state.lastPollDelayMs = 0;
            startNext (surface, state);
            return;
        }

        // Probe at sub-10 ms intervals first so ordinary Bitwig cursor callbacks are
        // observed promptly, then back off for genuinely slow host transitions.
        // This changes observation latency only: the serialized host operation,
        // cursor fingerprint checks and 2.92 s safety deadline remain intact.
        final long remainingMs = MAX_WAIT_MS - effectiveWaitedMs (state);
        if (remainingMs <= 0)
        {
            advance (surface, state);
            return;
        }
        state.lastPollDelayMs = Math.min (POLL_BACKOFF_MS[Math.min (state.polls, POLL_BACKOFF_MS.length - 1)], remainingMs);

        // Do not let a host-owned timer keep a disposed PushControlSurface alive.
        final WeakReference<PushControlSurface> surfaceReference = new WeakReference<> (surface);
        final long delayMs = state.lastPollDelayMs;
        model.getHost ().scheduleTask (() -> {
            final PushControlSurface currentSurface = surfaceReference.get ();
            if (currentSurface != null)
                advance (currentSurface, state);
            else
                synchronized (state)
                {
                    state.cancel ();
                }
        }, delayMs);
    }


    private static void beginWait (final State state, final Wait wait)
    {
        state.wait = wait;
        state.polls = 0;
        state.waitedMs = 0;
        state.waitStartedAtNanos = System.nanoTime ();
        state.lastPollDelayMs = 0;
        state.running = true;
    }


    private static long effectiveWaitedMs (final State state)
    {
        final long elapsedNanos = System.nanoTime () - state.waitStartedAtNanos;
        final long elapsedMs = Math.max (0, elapsedNanos / 1_000_000);
        return Math.max (state.waitedMs, elapsedMs);
    }


    private static void markDispatched (final Action action)
    {
        if (TIMING_ENABLED && action.dispatchedAtNanos == 0)
            action.dispatchedAtNanos = System.nanoTime ();
    }


    private static void reportTiming (final IModel model, final Action action, final String status)
    {
        if (!TIMING_ENABLED || action.dispatchedAtNanos == 0)
            return;

        final long completedAt = System.nanoTime ();
        final long queueUs = (action.dispatchedAtNanos - action.queuedAtNanos) / 1000;
        final long hostUs = (completedAt - action.dispatchedAtNanos) / 1000;
        final long totalUs = (completedAt - action.queuedAtNanos) / 1000;
        model.getHost ().println ("PUSHdriver_NAV_TIMING status=" + status + " type=" + action.type
            + " index=" + action.index + " queue_us=" + queueUs + " host_us=" + hostUs
            + " total_us=" + totalUs + " polls=" + action.timingPolls);
    }


    private static boolean isComplete (final Wait waiting, final ICursorDevice cursor, final Action action, final State state)
    {
        return switch (waiting)
        {
            case ENTER -> cursor.doesExist () && cursor.isNested () && cursor.getIndex () == state.expectedIndex
                && !state.before.equals (fingerprint (cursor));
            // API 17 has no parent reference/ID to compare after selectParent(); the fingerprint delta is the strongest available signal.
            case EXIT -> cursor.doesExist () && !state.before.equals (fingerprint (cursor));
            case MOVE -> cursor.getIndex () == state.expectedIndex;
            case ROOT_SELECT -> cursor.getIndex () == action.index;
            default -> false;
        };
    }


    private static void advance (final PushControlSurface surface, final State state)
    {
        synchronized (state)
        {
            if (!state.running)
                return;

            final IModel model = state.model.get ();
            if (model == null)
            {
                state.cancel ();
                return;
            }

            final ICursorDevice cursor = model.getCursorDevice ();
            final Action action = state.actions.peekFirst ();
            if (action == null)
            {
                state.cancel ();
                return;
            }

            state.waitedMs += state.lastPollDelayMs;
            state.polls++;
            if (TIMING_ENABLED)
                action.timingPolls++;

            final Wait waiting = state.wait;
            if (isComplete (waiting, cursor, action, state))
            {
                // Nested sibling selection can require several adjacent moves. Keep
                // the action only until its requested index is reached; do not run
                // the same-device reselect callback after a successful movement.
                if (waiting != Wait.MOVE || cursor.getIndex () == action.index)
                {
                    state.actions.removeFirst ();
                    reportTiming (model, action, "complete");
                }

                state.running = false;
                state.wait = Wait.NONE;
                state.polls = 0;
                state.waitedMs = 0;
                state.waitStartedAtNanos = 0;
                state.lastPollDelayMs = 0;
                startNext (surface, state);
                return;
            }

            if (effectiveWaitedMs (state) >= MAX_WAIT_MS)
            {
                state.cancel ();
                reportTiming (model, action, "timeout");
                model.getHost ().showNotification ("Push Device navigation timed out waiting for Bitwig's cursor; pending selections were cancelled");
                return;
            }

            schedule (surface, state, model);
        }
    }


    /**
     * Immutable cursor identity snapshot. Keep fields typed rather than joining
     * them into a delimiter-separated string: device IDs/names are host data and
     * may themselves contain delimiters, which could otherwise hide a real move.
     */
    record CursorFingerprint (String id, String name, int position, int index, boolean nested)
    {
    }


    static CursorFingerprint fingerprint (final ICursorDevice cursor)
    {
        return new CursorFingerprint (cursor.getID (), cursor.getName (), cursor.getPosition (), cursor.getIndex (), cursor.isNested ());
    }
}
