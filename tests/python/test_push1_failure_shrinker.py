import importlib.util
from pathlib import Path
import unittest

SCRIPT = Path(__file__).resolve().parents[2] / "scripts/push1_failure_shrinker.py"
SPEC = importlib.util.spec_from_file_location("push1_failure_shrinker", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class FailureShrinkerTests(unittest.TestCase):
    def test_finds_first_failing_prefix(self):
        calls = []

        def reproduces(action_count):
            calls.append(action_count)
            return action_count >= 37

        self.assertEqual(MODULE.minimal_failing_prefix(256, reproduces), 37)
        self.assertTrue(all(1 <= count <= 256 for count in calls))
        self.assertLessEqual(len(calls), 10)

    def test_returns_none_when_full_scenario_passes(self):
        self.assertIsNone(MODULE.minimal_failing_prefix(128, lambda _: False))

    def test_one_action_failure_is_supported(self):
        self.assertEqual(MODULE.minimal_failing_prefix(1, lambda _: True), 1)

    def test_rejects_empty_search_range(self):
        with self.assertRaises(ValueError):
            MODULE.minimal_failing_prefix(0, lambda _: True)


if __name__ == "__main__":
    unittest.main()
