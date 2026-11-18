// Copyright (c) 2026 PUSHdriver contributors
package de.mossgrabers.controller.ableton.push.view;

import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.observer.IItemSelectionObserver;
import de.mossgrabers.framework.observer.INoteObserver;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * Shares API-17 track-bank observers without retaining obsolete Poly views.
 *
 * Bitwig API 17 has no removeNoteObserver method. The bank therefore owns one
 * observer pair, while the registry and callbacks retain only weak references
 * to every Push Poly view using that bank. This also supports multiple Push
 * surfaces sharing the same model without routing note feedback to only the
 * most recently constructed view.
 */
public final class PushPolySequencerObservers
{
    private static final WeakHashMap<ITrackBank, Observer> OBSERVERS = new WeakHashMap<> ();

    private PushPolySequencerObservers ()
    {
        // Utility class.
    }

    /**
     * Register or refresh a weak view target for a track bank.
     *
     * @param trackBank The model track bank
     * @param view The concrete Push Poly view
     */
    public static void register (final ITrackBank trackBank, final PolySequencerView view)
    {
        if (trackBank == null || view == null)
            return;

        synchronized (OBSERVERS)
        {
            Observer observer = OBSERVERS.get (trackBank);
            if (observer == null)
            {
                observer = new Observer ();
                trackBank.addSelectionObserver (observer);
                trackBank.addNoteObserver (observer);
                OBSERVERS.put (trackBank, observer);
            }
            observer.addView (view);
        }
    }

    private static final class Observer implements IItemSelectionObserver, INoteObserver
    {
        private final CopyOnWriteArrayList<WeakReference<PolySequencerView>> views = new CopyOnWriteArrayList<> ();

        private synchronized void addView (final PolySequencerView view)
        {
            for (final WeakReference<PolySequencerView> reference: this.views)
            {
                final PolySequencerView existing = reference.get ();
                if (existing == view)
                    return;
                if (existing == null)
                    this.views.remove (reference);
            }
            this.views.add (new WeakReference<> (view));
        }

        @Override
        public void call (final int index, final boolean isSelected)
        {
            for (final WeakReference<PolySequencerView> reference: this.views)
            {
                final PolySequencerView view = reference.get ();
                if (view == null)
                    this.views.remove (reference);
                else
                    view.onTrackSelectionChanged ();
            }
        }

        @Override
        public void call (final int trackIndex, final int note, final int velocity)
        {
            for (final WeakReference<PolySequencerView> reference: this.views)
            {
                final PolySequencerView view = reference.get ();
                if (view == null)
                    this.views.remove (reference);
                else if (view.isCurrentActiveView ())
                    view.getKeyManager ().call (trackIndex, note, velocity);
            }
        }
    }
}
