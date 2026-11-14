import argparse
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts"))
from promote_push1_regression import parse_seed, promote


class PromotePush1RegressionTest(unittest.TestCase):
    def test_accepts_decimal_and_hexadecimal_seeds(self):
        self.assertEqual(parse_seed("42"), 42)
        self.assertEqual(parse_seed("0x2a"), 42)

    def test_rejects_invalid_or_out_of_range_seeds(self):
        with self.assertRaises(argparse.ArgumentTypeError):
            parse_seed("not-a-seed")
        with self.assertRaises(argparse.ArgumentTypeError):
            parse_seed(str(2**63))

    def test_promotes_once_and_preserves_reason(self):
        with tempfile.TemporaryDirectory() as directory:
            corpus = Path(directory) / "fuzz-seeds.txt"
            corpus.write_text("# existing regression\n0x10\n", encoding="utf-8")
            self.assertFalse(promote(16, "duplicate", corpus))
            self.assertTrue(promote(17, "Device row regression", corpus))
            self.assertFalse(promote(17, "duplicate again", corpus))
            content = corpus.read_text(encoding="utf-8")
            self.assertIn("16", content)
            self.assertIn("# Device row regression\n17", content)
            self.assertEqual(content.count("\n17\n"), 1)


if __name__ == "__main__":
    unittest.main()
