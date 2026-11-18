// Copyright (c) 2026 PUSHdriver contributors
package de.mossgrabers.controller.ableton.push.view;

import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.observer.IItemSelectionObserver;
import de.mossgrabers.framework.observer.INoteObserver;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;


/**
 * Shares the API-17 track-bank observers without retaining obsolete Poly views.
 *
 * Bitwig API 17 has no removeNoteObserver method. The bank therefore owns one
 * observer pair, while the registry and callbacks retain only weak references
 * to the bank and the current Push Poly view.
 */
public final class PushPolySequencerObservers
{
    private static final Map<ITrackBank, Observer> OBSERVERS = new WeakHashMap<> ();

    private PushPolySequencerObservers ()
    {
        // Utility class.
    }

    /**
     * Register or refresh the weak view target for a track bank.
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
            observer.setView (view);
        }
    }

    private static final class Observer implements IItemSelectionObserver, INoteObserver
    {
        private volatile WeakReference<PolySequencerView> viewReference = new WeakReference<> (null);

        private void setView (final PolySequencerView view)
        {
            this.viewReference = new WeakReference<> (view);
        }

        @Override
        public void call (final int index, final boolean isSelected)
        {
            final PolySequencerView view = this.viewReference.get ();
            if (view != null)
                view.onTrackSelectionChanged ();
        }

        @Override
        public void call (final int trackIndex, final int note, final int velocity)
        {
            final PolySequencerView view = this.viewReference.get ();
            if (view != null && view.isCurrentActiveView ())
                view.getKeyManager ().call (trackIndex, note, velocity);
        }
    }
}
