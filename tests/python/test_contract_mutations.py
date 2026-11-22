import importlib.util
from pathlib import Path
import unittest

SCRIPT = (Path(__file__).resolve().parents[1] / ".." / "scripts/check_source_ownership.py").resolve()
SPEC = importlib.util.spec_from_file_location("check_source_ownership_mutation", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ContractMutationTests(unittest.TestCase):
    def setUp(self):
        self.contract = [
            'this.addButton (ButtonID.PAGE_LEFT, "In" this.addButton (ButtonID.PAGE_RIGHT, "Out"',
            "Capability.HAS_SLOT_CHAINS cursorDevice.selectSlotChain (slotChains[0])",
            "cursorDevice.isNested () cursorDevice.selectParent ()",
            "PUSH_BUTTON_DEVICE_LEFT = 62; PUSH_BUTTON_DEVICE_RIGHT = 63;",
            "public static final int IN = 62; public static final int OUT = 63;",
            "In (CC 62) enters the first available slot chain; Out (CC 63) returns to its parent device",
            'requireAction ("device.selectSlotChain:Chain 1", "In (CC 62)"); requireAction ("cursorDevice.selectParent", "Out (CC 63)"); "cursorDevice.selectNext"; "cursorDevice.selectPrevious"; contentTypeIndex () == inOutContentBefore + 1 contentTypeIndex () == inOutContentBefore',
        ]

    def test_each_single_contract_mutation_is_detected(self):
        mutations = [
            (0, 'this.addButton (ButtonID.PAGE_LEFT, "Out"'),
            (1, "deviceParamsMode.selectNextItem ()"),
            (2, "deviceParamsMode.selectPreviousItem ()"),
            (3, "PUSH_BUTTON_DEVICE_LEFT = 63; PUSH_BUTTON_DEVICE_RIGHT = 62;"),
            (4, "public static final int IN = 63; public static final int OUT = 62;"),
            (5, "In (CC 62) selects next; Out (CC 63) selects previous"),
            (6, 'requireAction ("device.selectNextItem", "In (CC 62)")'),
        ]
        for index, mutant in mutations:
            with self.subTest(index=index):
                altered = list(self.contract)
                altered[index] = mutant
                self.assertTrue(MODULE.validate_in_out_contract(*altered))


if __name__ == "__main__":
    unittest.main()
