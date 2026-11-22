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

    def test_device_chains_mode_patch_is_reviewed(self):
        self.assertIn(
            "de/mossgrabers/controller/ableton/push/mode/device/DeviceChainsMode.java",
            MODULE.PATCH_TARGET_ALLOWLIST,
        )

    def test_add_track_mode_patch_is_reviewed(self):
        self.assertIn(
            "de/mossgrabers/controller/ableton/push/mode/track/AddTrackMode.java",
            MODULE.PATCH_TARGET_ALLOWLIST,
        )

    def test_empty_note_sequencer_patch_is_reviewed(self):
        self.assertIn(
            "de/mossgrabers/framework/view/sequencer/AbstractNoteSequencerView.java",
            MODULE.PATCH_TARGET_ALLOWLIST,
        )

    def test_push1_display_name_fitting_targets_are_reviewed(self):
        expected = {
            "de/mossgrabers/controller/ableton/push/mode/NoteMode.java",
            "de/mossgrabers/controller/ableton/push/mode/track/AbstractTrackMode.java",
            "de/mossgrabers/framework/view/ColorView.java",
        }
        self.assertTrue(expected.issubset(MODULE.PATCH_TARGET_ALLOWLIST))

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
            "Capability.HAS_SLOT_CHAINS cursorDevice.selectSlotChain (slotChains[0])",
            "deviceParamsMode.onSecondRow (7, ButtonEvent.DOWN) Modes.DEVICE_CHAINS",
            "PUSH_BUTTON_DEVICE_LEFT = 62; PUSH_BUTTON_DEVICE_RIGHT = 63;",
            "public static final int IN = 62; public static final int OUT = 63;",
            "In (CC 62) enters the first available slot chain; Out (CC 63) returns to its parent device",
            'requireAction ("device.selectSlotChain:Chain 1", "In (CC 62)"); requireAction ("cursorDevice.selectParent", "Out (CC 63)"); "cursorDevice.selectNext"; "cursorDevice.selectPrevious"; contentTypeIndex () == inOutContentBefore + 1 contentTypeIndex () == inOutContentBefore',
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



    def test_navigation_uses_direct_cursor_calls_without_a_queue(self):
        root = Path(__file__).resolve().parents[2]
        helper = root / "overrides/java/de/mossgrabers/controller/ableton/push/command/trigger/PushDeviceNavigation.java"
        self.assertFalse(helper.exists())
        patch = (root / "scripts/push1_build/patches/04_navigation.py").read_text(encoding="utf-8")
        self.assertNotIn("PushDeviceNavigation", patch)
        self.assertIn("targetDevice.select ();", patch)
        self.assertNotIn("cd.selectNext ();", patch)
        self.assertNotIn("cd.selectPrevious ();", patch)

        command = (root / "overrides/java/de/mossgrabers/controller/ableton/push/command/trigger/PageLeftCommand.java").read_text(encoding="utf-8")
        self.assertIn("addNameObserver", command)
        self.assertIn("Map<IModel, PendingRow1Selection>", command)
        self.assertIn("OBSERVED_MODELS", command)
        self.assertIn("WeakReference<IModel>", command)
        self.assertIn("WeakReference<> (mode)", command)
        self.assertIn("WeakReference<> (surface)", command)
        self.assertIn("completePendingIfReady", command)
        self.assertIn("CHAIN_CURSOR_POLL_MS = 8", command)
        self.assertIn("CHAIN_CURSOR_MAX_POLLS = 75", command)
        self.assertIn("beginParentReturn", command)
        self.assertIn("PendingParentReturn", command)
        self.assertIn("completeParentReturnIfReady", command)
        self.assertIn("scheduleParentReturnProbe", command)
        self.assertIn("returnedToRoot", command)
        self.assertIn("cursorFingerprint", command)
        self.assertIn("parentFingerprint", command)
        self.assertIn("deferInUntilParentReturn", command)
        self.assertIn("selectFirstAvailableChain", command)
        self.assertIn("childFingerprint", command)

        page_right = (root / "overrides/java/de/mossgrabers/controller/ableton/push/command/trigger/PageRightCommand.java").read_text(encoding="utf-8")
        self.assertIn("DeviceLayerMode", page_right)
        self.assertIn("layerMode.moveUp ()", page_right)
        self.assertIn("device_layer_mode = root", patch)
        self.assertIn('"    public void moveUp ()"', patch)

        self.assertIn("PageLeftCommand.beginParentReturn", patch)
        parent_return_patch = patch.split('"Replace fixed 300 ms parent return', 1)[1]
        self.assertNotIn("}, 300);", parent_return_patch)
        self.assertIn("cd.selectParent ();", patch)

    def test_all_build_and_validation_scripts_compile(self):
        root = Path(__file__).resolve().parents[2]
        for script in sorted((root / "scripts").rglob("*.py")):
            with self.subTest(script=script.relative_to(root)):
                source = script.read_text(encoding="utf-8")
                compile(source, str(script), "exec")


if __name__ == "__main__":
    unittest.main()
