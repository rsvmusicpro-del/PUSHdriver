// Copyright (c) 2026 PUSHdriver contributors
package de.mossgrabers.controller.ableton.push.engine.sequencer;

/** Push 1 classic note-sequencer state engine. */
public final class ClassicSequencerEngine extends AbstractSequencerEngine
{
    private boolean followPlayhead = true;

    @Override
    protected void onDeactivated ()
    {
        this.followPlayhead = true;
    }

    public synchronized boolean shouldFollowPlayhead ()
    {
        return this.isActive () && this.followPlayhead;
    }

    public synchronized void onManualPageNavigation ()
    {
        if (this.isActive ())
            this.followPlayhead = false;
    }

    public synchronized void onPageGesture (final int pressedPage, final int releasedPage, final int velocity,
            final boolean clipExists, final int stepsPerPage, final int currentStep)
    {
        if (!this.isActive () || velocity > 0 || pressedPage < 0)
            return;
        if (releasedPage != pressedPage)
        {
            this.followPlayhead = true;
            return;
        }
        this.followPlayhead = clipExists && stepsPerPage > 0 && currentStep >= 0
                && pageForStep (currentStep, stepsPerPage) == releasedPage;
    }

    public static int pageForStep (final int currentStep, final int stepsPerPage)
    {
        return currentStep < 0 || stepsPerPage <= 0 ? -1 : currentStep / stepsPerPage;
    }
}
