import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]

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

    def test_direct_source_writes_are_inventoried(self):
        source = (
            'definition = root / "src/main/java/example/Definition.java"\n'
            'definition.write_text("source")\n'
            'obsolete = root / "src/main/java/example/Obsolete.java").unlink()\n'
        )
        self.assertEqual(
            MODULE.patch_targets(source),
            {"example/Definition.java", "example/Obsolete.java"},
        )

    def test_sequencer_views_share_the_physical_pad_guard(self):
        classic = (ROOT / "overrides/java/de/mossgrabers/controller/ableton/push/view/SequencerView.java").read_text(encoding="utf-8")
        poly = (ROOT / "overrides/java/de/mossgrabers/controller/ableton/push/view/PolySequencerView.java").read_text(encoding="utf-8")
        self.assertIn("this.engine.isHardwarePadIndex (index)", classic)
        self.assertIn("this.engine.isHardwarePadIndex (index)", poly)

    def test_assembly_uses_shared_pad_bounds_for_drum_views(self):
        source = (ROOT / "scripts/prepare_push1.py").read_text(encoding="utf-8")
        self.assertGreaterEqual(source.count("Push1PadGrid.isValidIndex (index)"), 2)
        self.assertIn("Push1PadGrid.isValidIndex (index, this.columns * this.rows)", source)

    def test_project_device_navigation_helpers_are_forbidden(self):
        source = "browser_mode.navigateFilterColumn(false)"
        self.assertTrue(MODULE.validate_forbidden_navigation(source))

    def test_native_upstream_navigation_is_allowed(self):
        source = "activeMode.selectPreviousItemPage()"
        self.assertEqual(MODULE.validate_forbidden_navigation(source), [])


    def test_duplicate_settings_identifier_is_detected(self):
        source = 'settings.getEnumSetting("Mode", CATEGORY_RIBBON, options, "X")\nsettings.getRangeSetting("Mode", CATEGORY_RIBBON, 0, 10, 1, "", 2)\n'
        self.assertEqual(MODULE.duplicate_setting_ids(source), ["CATEGORY_RIBBON::Mode"])

    def test_duplicate_push_button_midi_id_is_detected(self):
        source = "static final int PUSH_BUTTON_A = 0x10;\nstatic final int PUSH_BUTTON_B = 16;\n"
        self.assertEqual(MODULE.duplicate_button_ids(source), ["PUSH_BUTTON_A, PUSH_BUTTON_B"])


    def test_new_device_mode_method_is_rejected(self):
        upstream = "public void selectPreviousItemPage() {}\n"
        project = upstream + "public void navigateFilterColumn(boolean forward) {}\n"
        self.assertTrue(MODULE.validate_mode_method_inventory(project, upstream, "DeviceBrowserMode.java"))

    def test_unchanged_device_mode_method_inventory_is_allowed(self):
        source = "public void selectPreviousItemPage() {}\nprivate boolean hasNextItemPage() { return false; }\n"
        self.assertEqual(MODULE.validate_mode_method_inventory(source, source, "DeviceParamsMode.java"), [])


    def test_in_out_direction_contract_accepts_physical_mapping(self):
        errors = MODULE.validate_in_out_contract(
            'this.addButton (ButtonID.PAGE_LEFT, "In" this.addButton (ButtonID.PAGE_RIGHT, "Out"',
            "deviceParamsMode.selectNextItem () deviceParamsMode.selectNextItemPage () activeMode.selectNextItemPage ()",
            "deviceParamsMode.selectPreviousItem () deviceParamsMode.selectPreviousItemPage () activeMode.selectPreviousItemPage ()",
            "PUSH_BUTTON_DEVICE_LEFT = 62; PUSH_BUTTON_DEVICE_RIGHT = 63;",
            "public static final int IN = 62; public static final int OUT = 63;",
            "In (CC 62) selects the next device; Out (CC 63) selects the previous device",
            'requireAction ("device.selectNextItem", "In (CC 62)"); requireAction ("device.selectPreviousItem", "Out (CC 63)"); parameterBank.selectNextPage parameterBank.selectPreviousPage contentTypeIndex () == inOutContentBefore + 1 contentTypeIndex () == inOutContentBefore',
        )
        self.assertEqual(errors, [])

    def test_in_out_direction_contract_rejects_swapped_labels(self):
        errors = MODULE.validate_in_out_contract(
            'this.addButton (ButtonID.PAGE_LEFT, "Out"',
            "deviceParamsMode.selectPreviousItem ()",
            "deviceParamsMode.selectNextItem ()",
            "PUSH_BUTTON_DEVICE_LEFT = 62; PUSH_BUTTON_DEVICE_RIGHT = 63;",
            "public static final int IN = 62; public static final int OUT = 63;",
            "In (CC 62) selects the next device; Out (CC 63) selects the previous device",
            "",
        )
        self.assertTrue(errors)



if __name__ == "__main__":
    unittest.main()
