// Copyright (c) 2026 PUSHdriver contributors
package de.mossgrabers.controller.ableton.push.engine.sequencer;

/** Thread-safe lifecycle foundation; generation changes invalidate deferred work. */
public abstract class AbstractSequencerEngine implements SequencerEngine
{
    private boolean active;
    private long generation;

    @Override
    public final synchronized ActivationToken activate ()
    {
        if (!this.active)
        {
            this.generation = Math.incrementExact (this.generation);
            this.active = true;
        }
        return new ActivationToken (this.generation);
    }

    @Override
    public final synchronized void deactivate ()
    {
        if (this.active)
        {
            this.active = false;
            this.generation = Math.incrementExact (this.generation);
        }
    }

    @Override
    public final synchronized boolean isActive () { return this.active; }

    @Override
    public final synchronized long generation () { return this.generation; }

    @Override
    public final synchronized boolean accepts (final ActivationToken token)
    {
        return token != null && this.active && token.generation () == this.generation;
    }
}
