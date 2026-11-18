from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
POLY_VIEW = ROOT / "overrides/java/de/mossgrabers/controller/ableton/push/view/PolySequencerView.java"
CLASSIC_VIEW = ROOT / "overrides/java/de/mossgrabers/controller/ableton/push/view/SequencerView.java"
POLY_PATCHES = ROOT / "scripts/push1_build/patches/01_framework.py"
POLY_OBSERVERS = ROOT / "overrides/java/de/mossgrabers/controller/ableton/push/view/PushPolySequencerObservers.java"


class SequencerIsolationContractTests(unittest.TestCase):
    def test_classic_and_poly_keep_distinct_upstream_bases(self):
        classic = CLASSIC_VIEW.read_text(encoding="utf-8")
        poly = POLY_VIEW.read_text(encoding="utf-8")
        self.assertIn("extends AbstractNoteSequencerView", classic)
        self.assertIn("extends AbstractPolySequencerView", poly)

    def test_poly_uses_drivenbymoss_note_and_step_event_handlers(self):
        source = POLY_VIEW.read_text(encoding="utf-8")
        self.assertNotIn("void handleNoteArea", source)
        self.assertNotIn("void handleSequencerArea", source)
        self.assertIn("onGridNoteLongPress", source)
        self.assertIn("handleSequencerAreaButtonCombinations", source)

    def test_fundamental_track_color_precedes_upstream_key_feedback(self):
        source = POLY_VIEW.read_text(encoding="utf-8")
        fundamental = source.index("this.engine.isScaleFundamental (mappedNote, this.scales.getScaleOffset ())")
        upstream_fallback = source.index("return super.getGridColor")
        self.assertLess(fundamental, upstream_fallback)
        self.assertIn("this.getPadColor (note, this.useDawColors ? track : null)", source)

    def test_poly_observers_are_shared_and_do_not_retain_view_instances(self):
        source = POLY_OBSERVERS.read_text(encoding="utf-8")
        self.assertIn("WeakHashMap<ITrackBank, Observer>", source)
        self.assertIn("WeakReference<PolySequencerView>", source)
        self.assertIn("view.isCurrentActiveView ()", source)
        self.assertIn("view.getKeyManager ().call (trackIndex, note, velocity)", source)
        self.assertIn("view.onTrackSelectionChanged ()", source)
        assembly = POLY_PATCHES.read_text(encoding="utf-8")
        self.assertIn("Replace per-view observers with weak shared routing", assembly)
        self.assertNotIn("tb.addNoteObserver ((trackIndex, note, velocity)", assembly)

    def test_view_engines_follow_view_manager_lifecycle(self):
        for path in (CLASSIC_VIEW, POLY_VIEW):
            source = path.read_text(encoding="utf-8")
            self.assertIn("public void onActivate ()", source)
            self.assertIn("this.engine.activate ()", source)
            self.assertIn("public void onDeactivate ()", source)
            self.assertIn("this.engine.deactivate ()", source)
            self.assertNotIn("synchronizeEngineLifecycle", source)
        poly = POLY_VIEW.read_text(encoding="utf-8")
        self.assertIn("this.noteMemory.clear ()", poly)
        self.assertIn("this.copyStep = -1", poly)
        self.assertIn("this.keyManager.clearPressedKeys ()", poly)

    def test_each_sequencer_has_a_distinct_feature_engine(self):
        classic = CLASSIC_VIEW.read_text(encoding="utf-8")
        poly = POLY_VIEW.read_text(encoding="utf-8")
        self.assertIn("ClassicSequencerEngine engine", classic)
        self.assertIn("PolySequencerEngine engine", poly)
        self.assertIn("this.engine.onManualPageNavigation ()", classic)
        self.assertIn("this.engine.isScaleFundamental", poly)

    def test_engine_lifecycle_invalidates_stale_work(self):
        root = ROOT / "overrides/java/de/mossgrabers/controller/ableton/push/engine/sequencer"
        lifecycle = (root / "AbstractSequencerEngine.java").read_text(encoding="utf-8")
        contract = (root / "SequencerEngine.java").read_text(encoding="utf-8")
        self.assertIn("Math.incrementExact", lifecycle)
        self.assertIn("token.generation () == this.generation", lifecycle)
        self.assertIn("this.onDeactivated ();", lifecycle)
        self.assertIn("record ActivationToken", contract)

    def test_poly_engine_bounds_checks_hardware_and_coordinates(self):
        root = ROOT / "overrides/java/de/mossgrabers/controller/ableton/push/engine/sequencer"
        engine = (root / "PolySequencerEngine.java").read_text(encoding="utf-8")
        self.assertIn("Push1PadGrid.indexForNote (note) >= 0", engine)
        self.assertIn("Push1PadGrid.isValidIndex (index)", engine)
        self.assertIn("x >= columns", engine)
        self.assertIn("y >= rows", engine)


if __name__ == "__main__":
    unittest.main()
