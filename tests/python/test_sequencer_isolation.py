from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
POLY_VIEW = ROOT / "overrides/java/de/mossgrabers/controller/ableton/push/view/PolySequencerView.java"
CLASSIC_VIEW = ROOT / "overrides/java/de/mossgrabers/controller/ableton/push/view/SequencerView.java"
ASSEMBLY = ROOT / "scripts/prepare_push1.py"


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

    def test_poly_note_observer_routes_only_to_active_view(self):
        source = ASSEMBLY.read_text(encoding="utf-8")
        self.assertIn("tb.addNoteObserver ((trackIndex, note, velocity) ->", source)
        self.assertIn("this.surface.getViewManager ().getActive () == this", source)
        self.assertIn("this.keyManager.call (trackIndex, note, velocity)", source)

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
        self.assertIn("record ActivationToken", contract)

    def test_poly_engine_bounds_checks_hardware_and_coordinates(self):
        root = ROOT / "overrides/java/de/mossgrabers/controller/ableton/push/engine/sequencer"
        engine = (root / "PolySequencerEngine.java").read_text(encoding="utf-8")
        self.assertIn("index < PAD_COUNT", engine)
        self.assertIn("x >= columns", engine)
        self.assertIn("y >= rows", engine)


if __name__ == "__main__":
    unittest.main()
