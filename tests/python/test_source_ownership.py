import importlib.util
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[2] / "scripts/check_source_ownership.py"
SPEC = importlib.util.spec_from_file_location("check_source_ownership", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class SourceOwnershipTests(unittest.TestCase):
    def test_unreviewed_override_is_rejected(self):
        errors = MODULE.validate_override_inventory({"de/example/NewMode.java"})
        self.assertTrue(any("Unreviewed project override" in e for e in errors))

    def test_mode_override_is_rejected(self):
        path = "de/example/mode/device/DeviceParamsMode.java"
        errors = MODULE.validate_override_inventory(MODULE.OVERRIDE_ALLOWLIST | {path})
        self.assertTrue(any("Project-owned mode implementation" in e for e in errors))

    def test_device_browser_override_is_rejected(self):
        path = "de/example/DeviceBrowserMode.java"
        errors = MODULE.validate_override_inventory(MODULE.OVERRIDE_ALLOWLIST | {path})
        self.assertTrue(any("Project-owned Device/Browser" in e for e in errors))

    def test_duplicate_fqcn_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            first = Path(directory) / "First.java"
            second = Path(directory) / "Second.java"
            first.write_text("package example;\npublic class Collision {}\n", encoding="utf-8")
            second.write_text("package example;\npublic class Collision {}\n", encoding="utf-8")
            errors = MODULE.validate_unique_types([first, second])
        self.assertTrue(any("Duplicate Java type example.Collision" in e for e in errors))

    def test_new_patch_target_is_detected(self):
        source = 'device_mode = root / "src/main/java/example/mode/DeviceMode.java"\nreplace_once(device_mode, "a", "b", "test")\n'
        self.assertEqual(MODULE.patch_targets(source), {"example/mode/DeviceMode.java"})

    def test_reassigned_patch_variable_tracks_both_targets(self):
        source = (
            'target = root / "src/main/java/example/First.java"\n'
            'replace_once(target, "a", "b", "first")\n'
            'target = root / "src/main/java/example/Second.java"\n'
            'replace_once(target, "c", "d", "second")\n'
        )
        self.assertEqual(
            MODULE.patch_targets(source),
            {"example/First.java", "example/Second.java"},
        )


if __name__ == "__main__":
    unittest.main()
