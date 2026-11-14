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
        fundamental = source.index("Math.floorMod (mappedNote, 12) == this.scales.getScaleOffset ()")
        upstream_fallback = source.index("return super.getGridColor")
        self.assertLess(fundamental, upstream_fallback)
        self.assertIn("this.getPadColor (note, this.useDawColors ? track : null)", source)

    def test_poly_note_observer_routes_only_to_active_view(self):
        source = ASSEMBLY.read_text(encoding="utf-8")
        self.assertIn("tb.addNoteObserver ((trackIndex, note, velocity) ->", source)
        self.assertIn("this.surface.getViewManager ().getActive () == this", source)
        self.assertIn("this.keyManager.call (trackIndex, note, velocity)", source)


if __name__ == "__main__":
    unittest.main()
