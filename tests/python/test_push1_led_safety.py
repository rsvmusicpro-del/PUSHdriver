import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/check_push1_led_safety.py"
SPEC = importlib.util.spec_from_file_location("check_push1_led_safety", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class Push1LedSafetyTests(unittest.TestCase):
    def test_accepts_static_push1_palette(self):
        source = "return PushColorManager.PUSH1_COLOR_YELLOW_MD;"
        self.assertEqual(MODULE.forbidden_static_row_identifiers(source), ())

    def test_rejects_each_forbidden_rgb_or_blink_identifier(self):
        for identifier in MODULE.FORBIDDEN_STATIC_ROW_IDENTIFIERS:
            with self.subTest(identifier=identifier):
                source = f"return PushColorManager.{identifier}BLUE_HI;"
                self.assertEqual(
                    MODULE.forbidden_static_row_identifiers(source),
                    (identifier,),
                )

    def test_rejects_multiple_forbidden_identifiers(self):
        source = "BUTTON_COLOR2_ON + PUSH1_COLOR2_RED_HI + BUTTON_COLOR2_OFF"
        self.assertEqual(
            MODULE.forbidden_static_row_identifiers(source),
            MODULE.FORBIDDEN_STATIC_ROW_IDENTIFIERS,
        )


if __name__ == "__main__":
    unittest.main()
