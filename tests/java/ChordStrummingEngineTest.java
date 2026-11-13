package de.mossgrabers.controller.ableton.push.view;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Regression tests for note order, timing bounds, held duration and cleanup. */
public final class ChordStrummingEngineTest
{
    private record Task (long due, long order, Runnable run) { }
    private record Event (long time, int status, int note, int value) { }
    private static final class Clock implements ChordStrummingEngine.Scheduler
    {
        long now, order;
        final List<Task> tasks = new ArrayList<> ();
        public void schedule (final Runnable run, final long delay) { tasks.add (new Task (now + delay, order++, run)); }
        void advance (final long target)
        {
            while (true)
            {
                final Task t = tasks.stream().filter(x -> x.due() <= target)
                    .min(Comparator.comparingLong(Task::due).thenComparingLong(Task::order)).orElse(null);
                if (t == null) break;
                tasks.remove(t); now = t.due(); t.run().run();
            }
            now = target;
        }
    }
    private ChordStrummingEngineTest () { }
    public static void main (final String [] args)
    {
        planBoundsAndPermutation();
        heldDuration();
        earlyRelease();
        deactivation();
    }
    private static void planBoundsAndPermutation ()
    {
        final int [] chord = { 48, 55, 60, 64, 67 };
        final ChordStrummingEngine.Plan p = ChordStrummingEngine.createPlan(chord, new Random(17));
        require(java.util.Arrays.equals(ChordStrummingEngine.sorted(chord), ChordStrummingEngine.sorted(p.notes)), "Pitch set changed");
        require(p.offsets[0] == 0 && p.offsets.length == chord.length, "First note must be immediate");
        for (int i=1;i<p.offsets.length;i++) require(p.offsets[i]-p.offsets[i-1] >= 20, "Gap below 20 ms");
        require(p.offsets[p.offsets.length-1] <= 3000, "Strum exceeded 3 seconds");
        require(!java.util.Arrays.equals(chord,p.notes), "Seed did not produce shuffled order");
    }
    private static void heldDuration ()
    {
        final Clock c=new Clock(); final List<Event> e=new ArrayList<>();
        final ChordStrummingEngine g=new ChordStrummingEngine(new Random(17));
        g.noteOn(36,new int[]{48,55,60,64,67},93,0x90,c,(s,n,v)->e.add(new Event(c.now,s,n,v)));
        c.advance(275); g.noteOff(36,c); c.advance(4000); paired(e,new int[]{48,55,60,64,67},93,275);
    }
    private static void earlyRelease ()
    {
        final Clock c=new Clock(); final List<Event> e=new ArrayList<>();
        final ChordStrummingEngine g=new ChordStrummingEngine(new Random(17));
        g.noteOn(36,new int[]{48,55,60,64,67},101,0x92,c,(s,n,v)->e.add(new Event(c.now,s,n,v)));
        c.advance(13); g.noteOff(36,c); c.advance(4000); paired(e,new int[]{48,55,60,64,67},101,13);
    }
    private static void deactivation ()
    {
        final Clock c=new Clock(); final List<Event> e=new ArrayList<>();
        final ChordStrummingEngine g=new ChordStrummingEngine(new Random(17));
        g.noteOn(36,new int[]{48,55,60,64,67},88,0x91,c,(s,n,v)->e.add(new Event(c.now,s,n,v)));
        c.advance(100); g.deactivate(); c.advance(4000);
        require(e.stream().filter(x->x.value()==88).count()==e.stream().filter(x->x.value()==0).count(),"Deactivation left hanging notes");
    }
    private static void paired (final List<Event> e, final int [] chord, final int velocity, final long held)
    {
        require(e.size()==chord.length*2,"Expected one on/off pair per chord note: "+e);
        for(int n:chord)
        {
            final Event on=e.stream().filter(x->x.note()==n&&x.value()==velocity).findFirst().orElseThrow();
            final Event off=e.stream().filter(x->x.note()==n&&x.value()==0).findFirst().orElseThrow();
            require(off.time()-on.time()==held,"Pad-held duration changed for "+n);
            require(on.status()==off.status(),"MIDI status/channel changed");
        }
    }
    private static void require (final boolean ok, final String message) { if(!ok) throw new AssertionError(message); }
}
