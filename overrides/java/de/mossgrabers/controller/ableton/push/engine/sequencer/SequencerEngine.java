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
    ActivationToken activate ();
    void deactivate ();
    boolean isActive ();
    long generation ();
    boolean accepts (ActivationToken token);
}
