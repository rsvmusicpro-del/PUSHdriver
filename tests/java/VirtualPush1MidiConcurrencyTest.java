import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Concurrent MIDI ingress stress. Bitwig controller callbacks are expected to
 * be delivered serially; the virtual bench preserves that event-loop contract
 * even when independent producers race to inject messages.
 */
public final class VirtualPush1MidiConcurrencyTest
{
    private static final int WORKERS = 8;
    private static final int EVENTS_PER_WORKER = 4096;

    private VirtualPush1MidiConcurrencyTest () { }

    public static void main (final String [] args) throws Exception
    {
        final VirtualPush1MidiBench bench = new VirtualPush1MidiBench ();
        final AtomicInteger callbacks = new AtomicInteger ();
        final AtomicInteger active = new AtomicInteger ();
        final AtomicInteger maxActive = new AtomicInteger ();
        final Set<Long> delivered = ConcurrentHashMap.newKeySet ();
        bench.input ().setMidiCallback ((status, data1, data2) -> {
            final int concurrent = active.incrementAndGet ();
            maxActive.accumulateAndGet (concurrent, Math::max);
            try
            {
                final long key = ((long) (status & 0x0F) << 14) | ((long) data2 << 7) | data1;
                if (!delivered.add (key))
                    throw new AssertionError ("Duplicate MIDI callback: " + key);
                callbacks.incrementAndGet ();
                // Widen the callback critical section so an accidental loss of
                // serialization is observable under the start barrier.
                LockSupport.parkNanos (50_000L);
            }
            finally
            {
                active.decrementAndGet ();
            }
        });

        final ExecutorService pool = Executors.newFixedThreadPool (WORKERS);
        final CountDownLatch ready = new CountDownLatch (WORKERS);
        final CountDownLatch start = new CountDownLatch (1);
        final List<Future<?>> tasks = new ArrayList<> ();
        try
        {
            for (int worker = 0; worker < WORKERS; worker++)
            {
                final int channel = worker;
                tasks.add (pool.submit (() -> {
                    ready.countDown ();
                    if (!start.await (10, TimeUnit.SECONDS))
                        throw new AssertionError ("Concurrent MIDI start barrier timed out");
                    for (int sequence = 0; sequence < EVENTS_PER_WORKER; sequence++)
                    {
                        final int data1 = sequence & 0x7F;
                        final int data2 = (sequence >>> 7) & 0x7F;
                        bench.inject (0xB0 | channel, data1, data2);
                    }
                    return null;
                }));
            }
            if (!ready.await (10, TimeUnit.SECONDS))
                throw new AssertionError ("Concurrent MIDI producers did not become ready");
            start.countDown ();
            for (final Future<?> task: tasks)
                task.get (30, TimeUnit.SECONDS);
        }
        finally
        {
            pool.shutdownNow ();
            pool.awaitTermination (5, TimeUnit.SECONDS);
        }

        final int expected = WORKERS * EVENTS_PER_WORKER;
        if (callbacks.get () != expected || delivered.size () != expected)
            throw new AssertionError ("MIDI callback loss: expected " + expected
                + ", callbacks=" + callbacks.get () + ", unique=" + delivered.size ());
        if (maxActive.get () != 1)
            throw new AssertionError ("MIDI callbacks overlapped across producer threads: max active="
                + maxActive.get ());
        System.out.println ("Concurrent MIDI ingress passed: " + expected
            + " unique messages from " + WORKERS + " producers; no loss, duplicates or callback overlap.");
    }
}
