// Copyright (c) 2026 PUSHdriver contributors
package de.mossgrabers.controller.ableton.push.engine.sequencer;

/** Lifecycle contract for an isolated controller feature engine. */
public interface SequencerEngine
{
    /** Immutable capability bound to one activation of an engine. */
    record ActivationToken (long generation)
    {
        public ActivationToken
        {
            if (generation < 0)
                throw new IllegalArgumentException ("Generation must not be negative");
        }
    }

    /** Keep an engine lifecycle aligned with its owning view without duplicating adapter logic. */
    static void synchronize (final SequencerEngine engine, final boolean active)
    {
        if (active)
            engine.activate ();
        else
            engine.deactivate ();
    }

    ActivationToken activate ();
    void deactivate ();
    boolean isActive ();
    long generation ();
    boolean accepts (ActivationToken token);
}
