import importlib.util
import json
import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/push1_temporal_simulator.py"
SPEC = importlib.util.spec_from_file_location("push1_temporal_simulator", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class Push1TemporalSimulatorTests(unittest.TestCase):
    def setUp(self):
        self.profile = json.loads((ROOT / "tests/temporal/push1-theoretical-profile.json").read_text())

    def test_replay_is_deterministic_for_same_seed(self):
        first = MODULE.aggregate(self.profile, "chain-entry", 30, 1234)
        second = MODULE.aggregate(self.profile, "chain-entry", 30, 1234)
        self.assertEqual(first, second)

    def test_feedback_paths_are_reported_separately(self):
        result = MODULE.aggregate(self.profile, "chain-entry", 20, 77)
        self.assertGreater(result["metrics"]["led"]["count"], 0)
        self.assertGreater(result["metrics"]["lcd"]["count"], 0)
        self.assertNotEqual(result["metrics"]["led"]["p50_ms"], result["metrics"]["lcd"]["p50_ms"])

    def test_loss_is_counted_without_fabricating_completion(self):
        profile = json.loads(json.dumps(self.profile))
        profile["loss_probability"] = {"usb_input": 1.0}
        result = MODULE.aggregate(profile, "mode-burst", 2, 1)
        self.assertEqual(result["delivery"]["completed_commands"], 0)
        self.assertEqual(result["delivery"]["command_loss_count"], result["delivery"]["expected_commands"])
        self.assertEqual(result["delivery"]["losses_by_stage"]["usb_input"], result["delivery"]["expected_commands"])

    def test_nearest_rank_percentiles(self):
        self.assertEqual(MODULE.percentile([9, 1, 5], .50), 5)
        self.assertEqual(MODULE.percentile([9, 1, 5], .95), 9)
        self.assertIsNone(MODULE.percentile([], .99))

    def test_all_builtin_scenarios_execute(self):
        for scenario in MODULE.SCENARIOS:
            with self.subTest(scenario=scenario):
                result = MODULE.aggregate(self.profile, scenario, 2, 2026)
                self.assertEqual(result["scenario"], scenario)
                self.assertGreater(result["delivery"]["expected_commands"], 0)

    def test_short_repeated_and_long_holds_are_classified(self):
        result = MODULE.aggregate(self.profile, "button-repeat-hold", 3, 2026)
        self.assertEqual(result["button_holds"]["classes"]["short"], 12)
        self.assertEqual(result["button_holds"]["classes"]["long"], 3)
        self.assertEqual(result["button_holds"]["long_press_threshold_ms"], 300)

    def test_negative_delay_is_clamped_and_unknown_distribution_rejected(self):
        self.assertEqual(MODULE.sample_delay({"distribution":"normal","mean_ms":0,"stddev_ms":100}, __import__("random").Random(1)) >= 0, True)
        with self.assertRaises(ValueError):
            MODULE.sample_delay({"distribution":"unknown"}, __import__("random").Random(1))


if __name__ == "__main__":
    unittest.main()
