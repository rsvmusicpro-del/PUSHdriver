import de.mossgrabers.controller.ableton.push.engine.Push1PadGrid;
import de.mossgrabers.controller.ableton.push.engine.sequencer.ClassicSequencerEngine;
import de.mossgrabers.controller.ableton.push.engine.sequencer.PolySequencerEngine;
import de.mossgrabers.controller.ableton.push.engine.sequencer.SequencerEngine.ActivationToken;

/** Deterministic tests for isolated Push 1 sequencer engine state. */
public final class SequencerEngineTest
{
    private SequencerEngineTest () { }

    public static void main (final String [] args)
    {
        testLifecycleTokens ();
        testPush1PadGrid ();
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

    private static void testPush1PadGrid ()
    {
        require (Push1PadGrid.indexForNote (35) == -1, "note below Push grid rejected");
        require (Push1PadGrid.indexForNote (36) == 0, "MIDI note 36 maps to the bottom-left button index");
        require (Push1PadGrid.indexForNote (92) == 56, "MIDI note 92 maps to the top-left button index");
        require (Push1PadGrid.indexForNote (99) == 63, "MIDI note 99 maps to the top-right button index");
        require (Push1PadGrid.indexForNote (43) == 7, "MIDI note 43 maps to the bottom-right button index");
        require (Push1PadGrid.indexForNote (100) == -1, "note above Push grid rejected");
        require (Push1PadGrid.indexForNote (-1) == -1, "negative MIDI note rejected");
        require (Push1PadGrid.noteForIndex (0) == 36, "bottom-left button index maps back to MIDI note 36");
        require (Push1PadGrid.noteForIndex (56) == 92, "top-left button index maps back to MIDI note 92");
        require (Push1PadGrid.noteForIndex (-1) == -1, "negative pad index rejected");
        require (Push1PadGrid.noteForIndex (64) == -1, "pad index 64 rejected");
        require (Push1PadGrid.indexForCoordinate (0, 0) == 56, "top-left coordinate maps to top-left MIDI button index");
        require (Push1PadGrid.indexForCoordinate (7, 7) == 7, "bottom-right coordinate maps to bottom-right MIDI button index");
        require (Push1PadGrid.indexForCoordinate (0, 7) == 0, "bottom-left coordinate maps to first MIDI button index");
        require (Push1PadGrid.indexForCoordinate (7, 0) == 63, "top-right coordinate maps to last MIDI button index");
        require (Push1PadGrid.rowForIndex (0) == 7, "first MIDI button index is on the bottom row");
        require (Push1PadGrid.rowForIndex (56) == 0, "top-left MIDI button index is on the top row");
        require (Push1PadGrid.indexForCoordinate (8, 0) == -1, "out-of-range column rejected");
        require (Push1PadGrid.indexForCoordinate (0, 8) == -1, "out-of-range row rejected");
        for (int index = 0; index < Push1PadGrid.PAD_COUNT; index++)
        {
            require (Push1PadGrid.indexForNote (Push1PadGrid.noteForIndex (index)) == index,
                    "pad note/index round-trip " + index);
            require (Push1PadGrid.indexForCoordinate (Push1PadGrid.columnForIndex (index),
                    Push1PadGrid.rowForIndex (index)) == index, "pad coordinate round-trip " + index);
        }
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
        engine.deactivate ();
        engine.activate ();
        require (engine.shouldFollowPlayhead (), "deactivation resets feature-local page-follow state");
        engine.onPageGesture (1, 1, 127, true, 8, 16);
        require (engine.shouldFollowPlayhead (), "pad-down preserves reset follow state");
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
