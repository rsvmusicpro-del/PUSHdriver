import importlib.util
from pathlib import Path
import unittest

SCRIPT = Path(__file__).resolve().parents[2] / "scripts/analyze_navigation_latency.py"
SPEC = importlib.util.spec_from_file_location("analyze_navigation_latency", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class NavigationLatencyTests(unittest.TestCase):
    def test_parses_completed_and_timeout_records(self):
        lines = [
            "PUSHdriver_NAV_TIMING status=complete type=ENTER index=-1 queue_us=10 host_us=20 total_us=30 polls=1",
            "PUSHdriver_NAV_TIMING status=timeout type=EXIT index=-1 queue_us=0 host_us=2920000 total_us=2920000 polls=40",
            "unrelated console output",
        ]
        records = MODULE.parse_lines(lines)
        self.assertEqual(len(records), 2)
        self.assertEqual(records[0]["total_us"], 30)
        self.assertEqual(records[1]["status"], "timeout")

    def test_percentiles_use_nearest_rank(self):
        self.assertEqual(MODULE.percentile([30, 10, 20], 50), 20)
        self.assertEqual(MODULE.percentile([30, 10, 20], 95), 30)
        self.assertEqual(MODULE.percentile([30, 10, 20], 99), 30)

    def test_summary_excludes_timeouts_from_latency_percentiles(self):
        records = MODULE.parse_lines([
            "PUSHdriver_NAV_TIMING status=complete type=ENTER index=-1 queue_us=10 host_us=20 total_us=30 polls=1",
            "PUSHdriver_NAV_TIMING status=complete type=EXIT index=-1 queue_us=20 host_us=40 total_us=60 polls=2",
            "PUSHdriver_NAV_TIMING status=timeout type=EXIT index=-1 queue_us=0 host_us=2920000 total_us=2920000 polls=40",
        ])
        summary = MODULE.summarize(records)
        self.assertEqual(summary["count"], 2)
        self.assertEqual(summary["timeouts"], 1)
        self.assertEqual(summary["total_us"]["p50"], 30)
        self.assertEqual(summary["total_us"]["p99"], 60)

    def test_empty_samples_have_no_summary(self):
        self.assertIsNone(MODULE.summarize([]))


if __name__ == "__main__":
    unittest.main()
