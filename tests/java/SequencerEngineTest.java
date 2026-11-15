import de.mossgrabers.controller.ableton.push.engine.sequencer.ClassicSequencerEngine;
import de.mossgrabers.controller.ableton.push.engine.sequencer.PolySequencerEngine;
import de.mossgrabers.controller.ableton.push.engine.sequencer.SequencerEngine;
import de.mossgrabers.controller.ableton.push.engine.sequencer.SequencerEngine.ActivationToken;

/** Deterministic tests for isolated Push 1 sequencer engine state. */
public final class SequencerEngineTest
{
    private SequencerEngineTest () { }

    public static void main (final String [] args)
    {
        testLifecycleTokens ();
        testSharedLifecycleSynchronization ();
        testClassicPageFollowPolicy ();
        testPolyGridAndScaleRules ();
        System.out.println ("SequencerEngineTest: all checks passed.");
    }

    private static void testLifecycleTokens ()
    {
        final ClassicSequencerEngine engine = new ClassicSequencerEngine ();
        final ActivationToken first = engine.activate ();
        require (engine.isActive (), "engine activates");
        require (engine.accepts (first), "fresh activation token accepted");
        require (engine.activate ().equals (first), "activation is idempotent");
        engine.deactivate ();
        require (!engine.isActive (), "engine deactivates");
        require (!engine.accepts (first), "deactivation invalidates token");
        final ActivationToken second = engine.activate ();
        require (second.generation () > first.generation (), "reactivation advances generation");
        require (!engine.accepts (first), "old token stays stale after reactivation");
        require (engine.accepts (second), "new token accepted");
    }

    private static void testSharedLifecycleSynchronization ()
    {
        final ClassicSequencerEngine engine = new ClassicSequencerEngine ();
        SequencerEngine.synchronize (engine, true);
        final ActivationToken active = engine.activate ();
        require (engine.isActive (), "shared lifecycle activates engine");
        SequencerEngine.synchronize (engine, false);
        require (!engine.isActive (), "shared lifecycle deactivates engine");
        require (!engine.accepts (active), "shared lifecycle invalidates prior activation");
    }

    private static void testClassicPageFollowPolicy ()
    {
        final ClassicSequencerEngine engine = new ClassicSequencerEngine ();
        engine.activate ();
        require (engine.shouldFollowPlayhead (), "follow enabled by default");
        engine.onManualPageNavigation ();
        require (!engine.shouldFollowPlayhead (), "manual navigation locks page");
        engine.onPageGesture (2, 2, 0, true, 8, 16);
        require (engine.shouldFollowPlayhead (), "playing page restores follow");
        engine.onPageGesture (2, 1, 0, true, 8, 16);
        require (engine.shouldFollowPlayhead (), "two-pad loop gesture preserves follow");
        engine.onPageGesture (1, 1, 0, true, 8, 16);
        require (!engine.shouldFollowPlayhead (), "non-playing page locks follow");
        engine.onPageGesture (1, 1, 0, false, 8, 16);
        require (!engine.shouldFollowPlayhead (), "missing clip cannot enable follow");
        engine.onPageGesture (1, 1, 127, true, 8, 16);
        require (!engine.shouldFollowPlayhead (), "pad-down does not change follow");
        require (ClassicSequencerEngine.pageForStep (17, 8) == 2, "global step maps to page");
        require (ClassicSequencerEngine.pageForStep (-1, 8) == -1, "negative step rejected");
        require (ClassicSequencerEngine.pageForStep (1, 0) == -1, "zero page width rejected");
    }

    private static void testPolyGridAndScaleRules ()
    {
        final PolySequencerEngine engine = new PolySequencerEngine ();
        require (engine.isHardwarePadNote (36), "first Push grid note accepted");
        require (engine.isHardwarePadNote (99), "last Push grid note accepted");
        require (!engine.isHardwarePadNote (35), "note below Push grid rejected");
        require (!engine.isHardwarePadNote (100), "note above Push grid rejected");
        require (engine.isHardwarePadIndex (0), "first pad index accepted");
        require (engine.isHardwarePadIndex (63), "last pad index accepted");
        require (!engine.isHardwarePadIndex (64), "out-of-range pad rejected");
        require (engine.stepForPad (0, 0, 8, 4) == 24, "top-left maps to final matrix row");
        require (engine.stepForPad (7, 3, 8, 4) == 7, "bottom-right maps to step seven");
        require (engine.stepForPad (8, 0, 8, 4) == -1, "invalid column rejected");
        require (engine.stepForPad (0, 4, 8, 4) == -1, "invalid row rejected");
        require (engine.isScaleFundamental (62, 2), "scale fundamental recognized");
        require (!engine.isScaleFundamental (63, 2), "non-fundamental pitch rejected");
        require (!engine.isScaleFundamental (128, 2), "invalid MIDI note rejected");
        require (!engine.isScaleFundamental (62, 12), "invalid scale offset rejected");
    }

    private static void require (final boolean condition, final String message)
    {
        if (!condition)
            throw new AssertionError (message);
    }
}
