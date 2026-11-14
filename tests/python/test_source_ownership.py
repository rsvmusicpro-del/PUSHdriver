import importlib.util
from pathlib import Path
import tempfile
import unittest
SCRIPT=Path(__file__).resolve().parents[2]/"scripts/check_source_ownership.py"
SPEC=importlib.util.spec_from_file_location("check_source_ownership",SCRIPT)
MODULE=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
class SourceOwnershipTests(unittest.TestCase):
 def test_unreviewed_override_is_rejected(self):
  self.assertTrue(any("Unreviewed project override" in e for e in MODULE.validate_override_inventory({"de/example/NewMode.java"})))
 def test_mode_override_is_rejected(self):
  p="de/example/mode/device/DeviceParamsMode.java"
  self.assertTrue(any("Project-owned mode implementation" in e for e in MODULE.validate_override_inventory(MODULE.OVERRIDE_ALLOWLIST|{p})))
 def test_device_browser_override_is_rejected(self):
  p="de/example/DeviceBrowserMode.java"
  self.assertTrue(any("Project-owned Device/Browser" in e for e in MODULE.validate_override_inventory(MODULE.OVERRIDE_ALLOWLIST|{p})))
 def test_duplicate_fqcn_is_rejected(self):
  with tempfile.TemporaryDirectory() as d:
   a=Path(d)/"A.java"; b=Path(d)/"B.java"
   a.write_text("package example;\\npublic class Collision {}\\n",encoding="utf-8")
   b.write_text("package example;\\npublic class Collision {}\\n",encoding="utf-8")
   errors=MODULE.validate_unique_types([a,b])
  self.assertTrue(any("Duplicate Java type example.Collision" in e for e in errors))
 def test_new_patch_target_is_detected(self):
  s='device_mode = root / "src/main/java/example/mode/DeviceMode.java"\\nreplace_once(device_mode, "a", "b", "test")\\n'
  self.assertEqual(MODULE.patch_targets(s),{"example/mode/DeviceMode.java"})
if __name__=="__main__": unittest.main()
