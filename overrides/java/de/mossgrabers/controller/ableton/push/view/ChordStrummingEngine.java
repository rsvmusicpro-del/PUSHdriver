package de.mossgrabers.controller.ableton.push.view;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Push Chords-only onset scheduler. Note data and held duration are never altered. */
final class ChordStrummingEngine
{
    static final int MIN_GAP_MS = 20;
    static final int MAX_SPAN_MS = 3000;

    @FunctionalInterface interface Scheduler { void schedule (Runnable task, long delayMs); }
    @FunctionalInterface interface MidiSender { void send (int status, int note, int value); }

    static final class Plan
    {
        final int [] notes;
        final int [] offsets;
        Plan (final int [] notes, final int [] offsets) { this.notes = notes; this.offsets = offsets; }
    }

    private static final class Gesture
    {
        final int [] notes, offsets, state;
        final int velocity, status;
        final MidiSender sender;
        final long generation;
        int remaining;
        Gesture (final Plan plan, final int velocity, final int status, final MidiSender sender, final long generation)
        {
            this.notes = plan.notes; this.offsets = plan.offsets; this.state = new int [notes.length];
            this.velocity = velocity; this.status = status; this.sender = sender;
            this.generation = generation; this.remaining = notes.length;
        }
    }

    private final Random random;
    private final Map<Integer, ArrayDeque<Gesture>> byPad = new HashMap<> ();
    private final Set<Gesture> pending = new HashSet<> ();
    private long generation;

    ChordStrummingEngine () { this (new Random ()); }
    ChordStrummingEngine (final Random random) { this.random = random; }

    /** First note is immediate; remaining notes are shuffled across a random 20–3000 ms span. */
    static Plan createPlan (final int [] chord, final Random random)
    {
        if (chord == null || chord.length == 0) return new Plan (new int [0], new int [0]);
        final int [] notes = chord.clone ();
        for (int i = notes.length - 1; i > 0; i--)
        {
            final int j = random.nextInt (i + 1), value = notes[i];
            notes[i] = notes[j]; notes[j] = value;
        }
        final int [] offsets = new int [notes.length];
        if (notes.length == 1) return new Plan (notes, offsets);
        final int minimum = MIN_GAP_MS * (notes.length - 1);
        final int extraTotal = random.nextInt (MAX_SPAN_MS - minimum + 1);
        final double [] weights = new double [notes.length - 1];
        double sum = 0;
        for (int i = 0; i < weights.length; i++) { weights[i] = 0.25 + random.nextDouble (); sum += weights[i]; }
        int allocated = 0;
        for (int i = 0; i < weights.length; i++)
        {
            final int extra = i == weights.length - 1 ? extraTotal - allocated : (int) Math.floor (extraTotal * weights[i] / sum);
            allocated += extra;
            offsets[i + 1] = offsets[i] + MIN_GAP_MS + extra;
        }
        return new Plan (notes, offsets);
    }

    void noteOn (final int pad, final int [] chord, final int velocity, final int status,
        final Scheduler scheduler, final MidiSender sender)
    {
        if (velocity <= 0 || chord == null || chord.length == 0) return;
        final Gesture g = new Gesture (createPlan (chord, random), velocity, status, sender, generation);
        byPad.computeIfAbsent (Integer.valueOf (pad), ignored -> new ArrayDeque<> ()).addLast (g);
        pending.add (g);
        for (int i = 0; i < g.notes.length; i++)
        {
            final int index = i;
            final Runnable start = () -> {
                if (g.generation != generation || g.state[index] != 0) return;
                g.state[index] = 1;
                g.sender.send (g.status, g.notes[index], g.velocity);
            };
            if (g.offsets[i] == 0) start.run (); else scheduler.schedule (start, g.offsets[i]);
        }
    }

    void noteOff (final int pad, final Scheduler scheduler)
    {
        final ArrayDeque<Gesture> queue = byPad.get (Integer.valueOf (pad));
        if (queue == null || queue.isEmpty ()) return;
        final Gesture g = queue.removeLast();
        if (queue.isEmpty ()) byPad.remove (Integer.valueOf (pad));
        for (int i = 0; i < g.notes.length; i++)
        {
            final int index = i;
            scheduler.schedule (() -> {
                if (g.generation != generation) return;
                // A very short tap still gets a correctly paired note-on/off.
                if (g.state[index] == 0)
                {
                    g.state[index] = 1;
                    g.sender.send (g.status, g.notes[index], g.velocity);
                }
                if (g.state[index] == 1)
                {
                    g.state[index] = 2;
                    g.sender.send (g.status, g.notes[index], 0);
                }
                if (--g.remaining == 0) pending.remove (g);
            }, g.offsets[i]);
        }
    }

    /** Invalidate pending onsets and stop already sounding chord notes on view exit. */
    void deactivate ()
    {
        generation++;
        for (final Gesture g: pending)
            for (int i = 0; i < g.notes.length; i++)
                if (g.state[i] == 1)
                {
                    g.state[i] = 2;
                    g.sender.send (g.status, g.notes[i], 0);
                }
        pending.clear();
        byPad.clear();
    }

    static int [] sorted (final int [] source) { final int [] copy = source.clone (); Arrays.sort (copy); return copy; }
}
