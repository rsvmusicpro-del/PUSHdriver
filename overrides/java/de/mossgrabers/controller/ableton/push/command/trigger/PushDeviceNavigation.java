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

/** Serializes Push 1 hierarchy commands against Bitwig's asynchronous cursor updates. */
public final class PushDeviceNavigation
{
    private static final long MAX_WAIT_MS=2920;
    private static final String TIMING_SETTING=System.getenv("PUSHDRIVER_NAV_TIMING");
    private static final boolean TIMING_ENABLED="1".equals(TIMING_SETTING)||Boolean.parseBoolean(TIMING_SETTING);
    private static final int MAX_ACTIONS=32;
    private static final long [] POLL_BACKOFF_MS={12,16,24,36,73};
    private static final Map<PushControlSurface,State> STATES=new WeakHashMap<> ();
    private enum Type { ENTER, EXIT, SELECT }
    private enum Wait { NONE, ENTER, EXIT, MOVE, ROOT_SELECT }
    private static final class Action
    {
        final Type type; final String chain; int index; Runnable onReselect;
        final long queuedAtNanos=TIMING_ENABLED?System.nanoTime():0; long dispatchedAtNanos; int timingPolls;
        Action(final Type type,final String chain,final int index){this(type,chain,index,null);}
        Action(final Type type,final String chain,final int index,final Runnable onReselect){this.type=type;this.chain=chain;this.index=index;this.onReselect=onReselect;}
    }
    private static final class State
    {
        final WeakReference<IModel> model; final ArrayDeque<Action> actions=new ArrayDeque<> ();
        Wait wait=Wait.NONE; String before; int previousIndex,expectedIndex,polls; long waitedMs,lastPollDelayMs; boolean running;
        State(final IModel model){this.model=new WeakReference<> (model);}
    }
    private PushDeviceNavigation(){}
    public static void enterFirstSlotChain(final PushControlSurface surface,final IModel model)
    {enqueue(surface,model,new Action(Type.ENTER,null,-1));}
    public static void selectSlotChain(final PushControlSurface surface,final IModel model,final String chain)
    {if(chain!=null&&!chain.isBlank())enqueue(surface,model,new Action(Type.ENTER,chain,-1));}
    public static void exitParent(final PushControlSurface surface,final IModel model)
    {enqueue(surface,model,new Action(Type.EXIT,null,-1));}
    /** Queue ROW1 while a hierarchy transition or nested cursor move is outstanding. */
    public static boolean selectRow(final PushControlSurface surface,final IModel model,final int index)
    {return selectRow(surface,model,index,null);}
    /** Queue ROW1 and preserve upstream's same-device reselect gesture if the cursor reaches that target first. */
    public static boolean selectRow(final PushControlSurface surface,final IModel model,final int index,final Runnable onReselect)
    {
        final ICursorDevice cursor=model.getCursorDevice();final State state=stateFor(surface,model);
        synchronized(state)
        {
            if(!state.running&&state.actions.isEmpty()&&(!cursor.isNested()||cursor.getIndex()==index))return false;
            final boolean accepted=enqueueLocked(state,new Action(Type.SELECT,null,index,onReselect));startNext(surface,state);return accepted;
        }
    }
    public static boolean isEntering(final PushControlSurface surface)
    {
        final State state=stateFor(surface);if(state==null)return false;
        synchronized(state){if(state.wait==Wait.ENTER)return true;for(final Action a:state.actions)if(a.type==Type.ENTER)return true;return false;}
    }
    public static boolean isBusy(final PushControlSurface surface)
    {
        final State state=stateFor(surface);if(state==null)return false;
        synchronized(state){return state.running||!state.actions.isEmpty();}
    }
    private static void enqueue(final PushControlSurface surface,final IModel model,final Action action)
    {
        final State state=stateFor(surface,model);synchronized(state){enqueueLocked(state,action);startNext(surface,state);}
    }
    private static boolean enqueueLocked(final State state,final Action action)
    {
        final Action last=state.actions.peekLast();
        if(action.type==Type.SELECT&&last!=null&&last.type==Type.SELECT
            &&(!state.running||last!=state.actions.peekFirst()))
        {last.index=action.index;last.onReselect=action.onReselect;return true;}
        if(state.actions.size()>=MAX_ACTIONS)
        {
            // A Bitwig operation already sent to the host cannot be cancelled. Keep
            // the in-flight head and its serialized queue; reject only the newest intent.
            final IModel model=state.model.get();if(model!=null)model.getHost().showNotification("Push Device navigation queue full: newest command ignored");
            return false;
        }
        state.actions.addLast(action);
        return true;
    }
    private static State stateFor(final PushControlSurface surface,final IModel model)
    {
        synchronized(STATES){State s=STATES.get(surface);if(s==null||s.model.get()!=model){s=new State(model);STATES.put(surface,s);}return s;}
    }
    private static State stateFor(final PushControlSurface surface){synchronized(STATES){return STATES.get(surface);}}
    private static void startNext(final PushControlSurface surface,final State state)
    {
        if(state.running)return;
        final IModel model=state.model.get();if(model==null){state.actions.clear();return;}
        final ICursorDevice cursor=model.getCursorDevice();
        while(!state.actions.isEmpty())
        {
            final Action action=state.actions.peekFirst();
            if(!cursor.doesExist()){state.actions.clear();return;}
            if(action.type==Type.ENTER)
            {
                if(!model.getHost().supports(Capability.HAS_SLOT_CHAINS)){state.actions.removeFirst();continue;}
                final String[] chains=cursor.getSlotChains();String selected=action.chain;
                if(selected==null)selected=chains==null||chains.length==0?null:chains[0];
                boolean found=false;if(selected!=null&&chains!=null)for(final String chain:chains)if(selected.equals(chain)){found=true;break;}
                if(!found){state.actions.removeFirst();continue;}
                state.before=fingerprint(cursor);state.expectedIndex=0;state.wait=Wait.ENTER;state.polls=0;state.waitedMs=0;state.running=true;
                markDispatched(action);cursor.selectSlotChain(selected);schedule(surface,state,model);return;
            }
            if(action.type==Type.EXIT)
            {
                if(!cursor.isNested()){state.actions.removeFirst();continue;}
                state.before=fingerprint(cursor);state.wait=Wait.EXIT;state.polls=0;state.waitedMs=0;state.running=true;
                markDispatched(action);cursor.selectParent();schedule(surface,state,model);return;
            }
            final IDevice target=cursor.getDeviceBank().getItem(action.index);
            if(target==null||!target.doesExist()){state.actions.removeFirst();continue;}
            final int current=cursor.getIndex();
            if(current==action.index){state.actions.removeFirst();if(action.onReselect!=null)action.onReselect.run();continue;}
            if(cursor.isNested())
            {
                if(action.index>current)
                {if(!cursor.canSelectNext()){state.actions.removeFirst();continue;}state.previousIndex=current;state.expectedIndex=current+1;state.wait=Wait.MOVE;state.polls=0;state.waitedMs=0;state.running=true;markDispatched(action);cursor.selectNext();}
                else
                {if(!cursor.canSelectPrevious()){state.actions.removeFirst();continue;}state.previousIndex=current;state.expectedIndex=current-1;state.wait=Wait.MOVE;state.polls=0;state.waitedMs=0;state.running=true;markDispatched(action);cursor.selectPrevious();}
                schedule(surface,state,model);return;
            }
            state.wait=Wait.ROOT_SELECT;state.polls=0;state.waitedMs=0;state.running=true;markDispatched(action);target.select();schedule(surface,state,model);return;
        }
        state.running=false;state.wait=Wait.NONE;
    }
    private static void schedule(final PushControlSurface surface,final State state,final IModel model)
    {
        // Bitwig may publish the cursor change synchronously. Confirm it before
        // scheduling a timer so the common fast path pays no avoidable 12 ms.
        final Action action=state.actions.peekFirst();
        final ICursorDevice cursor=model.getCursorDevice();
        if(action!=null&&isComplete(state.wait,cursor,action,state))
        {
            final Wait completed=state.wait;
            if(completed!=Wait.MOVE||cursor.getIndex()==action.index)
            {
                state.actions.removeFirst();
                reportTiming(model,action,"complete");
            }
            state.running=false;state.wait=Wait.NONE;state.polls=0;state.waitedMs=0;
            startNext(surface,state);
            return;
        }
        // Probe aggressively just after a command, then back off for genuinely
        // slow host transitions. The total confirmation timeout remains 2.92 s.
        state.lastPollDelayMs=POLL_BACKOFF_MS[Math.min(state.polls,POLL_BACKOFF_MS.length-1)];
        model.getHost().scheduleTask(()->advance(surface,state),state.lastPollDelayMs);
    }
    private static void markDispatched(final Action action)
    {
        if(TIMING_ENABLED&&action.dispatchedAtNanos==0)
            action.dispatchedAtNanos=System.nanoTime();
    }
    private static void reportTiming(final IModel model,final Action action,final String status)
    {
        if(!TIMING_ENABLED||action.dispatchedAtNanos==0)
            return;
        final long completedAt=System.nanoTime();
        final long queueUs=(action.dispatchedAtNanos-action.queuedAtNanos)/1000;
        final long hostUs=(completedAt-action.dispatchedAtNanos)/1000;
        final long totalUs=(completedAt-action.queuedAtNanos)/1000;
        model.getHost().println("PUSHdriver_NAV_TIMING status="+status+" type="+action.type
            +" index="+action.index+" queue_us="+queueUs+" host_us="+hostUs
            +" total_us="+totalUs+" polls="+action.timingPolls);
    }
    private static boolean isComplete(final Wait waiting,final ICursorDevice cursor,
        final Action action,final State state)
    {
        return switch(waiting)
        {
            case ENTER -> cursor.doesExist()&&cursor.isNested()&&cursor.getIndex()==state.expectedIndex&&!state.before.equals(fingerprint(cursor));
            // API 17 has no parent reference/ID to compare after selectParent(); the fingerprint delta is the strongest available signal.
            case EXIT -> cursor.doesExist()&&!state.before.equals(fingerprint(cursor));
            case MOVE -> cursor.getIndex()==state.expectedIndex;
            case ROOT_SELECT -> cursor.getIndex()==action.index;
            default -> false;
        };
    }
    private static void advance(final PushControlSurface surface,final State state)
    {
        synchronized(state)
        {
            if(!state.running)return;
            final IModel model=state.model.get();if(model==null){state.actions.clear();state.running=false;state.wait=Wait.NONE;return;}
            final ICursorDevice cursor=model.getCursorDevice();final Action action=state.actions.peekFirst();
            if(action==null){state.running=false;state.wait=Wait.NONE;return;}
            state.waitedMs+=state.lastPollDelayMs;
            state.polls++;
            if(TIMING_ENABLED)action.timingPolls++;
            final Wait waiting=state.wait;
            if(isComplete(waiting,cursor,action,state))
            {
                // Nested sibling selection can require several adjacent moves. Keep
                // the action only until its requested index is reached; do not run
                // the same-device reselect callback after a successful movement.
                if(waiting!=Wait.MOVE||cursor.getIndex()==action.index)
                {
                    state.actions.removeFirst();
                    reportTiming(model,action,"complete");
                }
                state.running=false;state.wait=Wait.NONE;state.polls=0;state.waitedMs=0;startNext(surface,state);return;
            }
            if(state.waitedMs>=MAX_WAIT_MS)
            {
                state.actions.clear();state.running=false;state.wait=Wait.NONE;
                reportTiming(model,action,"timeout");
                model.getHost().showNotification("Push Device navigation timed out waiting for Bitwig's cursor; pending selections were cancelled");return;
            }
            schedule(surface,state,model);
        }
    }
    private static String fingerprint(final ICursorDevice cursor)
    {return cursor.getID()+"|"+cursor.getName()+"|"+cursor.getPosition()+"|"+cursor.getIndex()+"|"+cursor.isNested();}
}
