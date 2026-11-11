#!/usr/bin/env python3
"""Regression checks for Push 1 In/Out bindings and mode-aware behavior."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/java/de/mossgrabers"


def read(relative: str) -> str:
    path = SOURCE / relative
    if not path.is_file():
        raise SystemExit(f"Required assembled source is missing: {path}")
    return path.read_text()


setup = read("controller/ableton/push/PushControllerSetup.java")
surface = read("controller/ableton/push/controller/PushControlSurface.java")
left = read("controller/ableton/push/command/trigger/PageLeftCommand.java")
right = read("controller/ableton/push/command/trigger/PageRightCommand.java")
browser = read("controller/ableton/push/mode/device/DeviceBrowserMode.java")
device_params = read("controller/ableton/push/mode/device/DeviceParamsMode.java")

for marker in (
    'this.addButton (ButtonID.PAGE_LEFT, "Out", new PageLeftCommand',
    'this.addButton (ButtonID.PAGE_RIGHT, "In", new PageRightCommand',
    "PushControlSurface.PUSH_BUTTON_DEVICE_LEFT",
    "PushControlSurface.PUSH_BUTTON_DEVICE_RIGHT",
):
    if marker not in setup:
        raise SystemExit(f"Push 1 In/Out setup binding is missing: {marker}")

if setup.count("this.addButton (ButtonID.PAGE_LEFT,") != 1:
    raise SystemExit("Push 1 Out must have exactly one physical binding")
if setup.count("this.addButton (ButtonID.PAGE_RIGHT,") != 1:
    raise SystemExit("Push 1 In must have exactly one physical binding")

for marker in (
    "PUSH_BUTTON_DEVICE_LEFT       = 62",
    "PUSH_BUTTON_DEVICE_RIGHT      = 63",
):
    if marker not in surface:
        raise SystemExit(f"Push 1 physical CC mapping changed: {marker}")

for source, direction, item, page, seq in (
    (left, "false", "selectPreviousItem", "selectPreviousPage", "onLeft"),
    (right, "true", "selectNextItem", "selectNextPage", "onRight"),
):
    for marker in (
        "DeviceBrowserMode",
        f"navigateFilterColumn ({direction})",
        "Modes.isDeviceMode (modeManager.getActiveID ())",
        "modeManager.isActive (Modes.DEVICE_PARAMS)",
        f"deviceParamsMode.select{'Previous' if direction == 'false' else 'Next'}InOutItem ()",
        "modeManager.isActive (Modes.DEVICE_CHAINS)",
        f"activeMode.{item} ()",
        f"this.model.getCurrentTrackBank ().{page} ()",
        f"sequencerView.{seq} (event)",
    ):
        if marker not in source:
            raise SystemExit(f"In/Out mode behavior or preserved fallback is missing: {marker}")
    if source.index("DeviceBrowserMode") > source.index("Modes.isDeviceMode"):
        raise SystemExit("Browser navigation must be dispatched before generic device navigation")

for marker in (
    "private int findNavigableFilterColumn (final boolean forward)",
    "browser.getSelectedFilterColumn ().getPosition ()",
    "browser.getFilterColumn (position).doesExist ()",
    "return this.findNavigableFilterColumn (forward) >= 0;",
    "while (browser.getSelectedFilterColumn ().getPosition () != targetPosition)",
    "browser.selectPreviousFilterColumn ();",
    "browser.selectNextFilterColumn ();",
    "browser.getSelectedFilterColumn ().getIndex ()",
    "this.selectionMode = DeviceBrowserMode.SELECTION_OFF;",
):
    if marker not in browser:
        raise SystemExit(f"Browser focus synchronization regression: {marker}")

if "browserMode.canNavigateFilterColumn (false)" not in setup or "browserMode.canNavigateFilterColumn (true)" not in setup:
    raise SystemExit("In/Out LED states must reflect browser column availability")
if "deviceParamsMode.hasPreviousInOutItem ()" not in setup or "deviceParamsMode.hasNextInOutItem ()" not in setup:
    raise SystemExit("In/Out LED states must reflect device/parameter-page availability")
if setup.count("if (modeManager.isActive (Modes.DEVICE_CHAINS))") != 2:
    raise SystemExit("Rack-chain mode must not advertise inactive In/Out commands")

for marker in (
    "cursorDevice.getParameterPageBank ().selectPreviousPage ();",
    "cursorDevice.getParameterPageBank ().selectNextPage ();",
    "getParameterPageBank ().canScrollPageBackwards ()",
    "getParameterPageBank ().canScrollPageForwards ()",
):
    if marker not in device_params:
        raise SystemExit(f"Device In/Out must navigate parameter-page banks: {marker}")

# The standard arrows retain the original per-item navigation and LED checks.
for marker in (
    "super.selectPreviousItem ();",
    "super.selectNextItem ();",
    "super.hasPreviousItem ();",
    "super.hasNextItem ();",
):
    if marker not in device_params:
        raise SystemExit(f"In/Out implementation regressed standard arrow navigation: {marker}")


print("Push 1 In/Out bindings, device/browser behavior and fallback regression checks passed.")
