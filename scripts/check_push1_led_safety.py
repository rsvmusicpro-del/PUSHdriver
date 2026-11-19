#!/usr/bin/env python3
"""Verify that the pinned Ableton Push Device implementation keeps the Push 1 LED rows distinct."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEVICE_OVERRIDES = ROOT / "overrides/java/de/mossgrabers/controller/ableton/push/mode/device"
UPSTREAM_DEVICE = ROOT / "upstream/src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceParamsMode.java"


FORBIDDEN_STATIC_ROW_IDENTIFIERS = ("PUSH1_COLOR2_", "BUTTON_COLOR2_ON", "BUTTON_COLOR2_OFF")


def forbidden_static_row_identifiers(source: str) -> tuple[str, ...]:
    """Return every RGB/blink identifier that is invalid in a static Push 1 row."""
    return tuple(identifier for identifier in FORBIDDEN_STATIC_ROW_IDENTIFIERS if identifier in source)


def section(source: str, start: str, end: str, label: str) -> str:
    begin = source.find(start)
    if begin < 0:
        raise SystemExit(f"{label}: section start not found")
    finish = source.find(end, begin)
    if finish < 0:
        raise SystemExit(f"{label}: section end not found")
    return source[begin:finish]


# The production Device implementation must come from the pinned upstream
# Ableton Push controller. Project overrides here previously reversed ROW1
# and ROW2 and mixed RGB palette values with the Push 1 static CC palette.
for name in ("DeviceParamsMode.java", "DeviceChainsMode.java", "DeviceBrowserMode.java"):
    if (DEVICE_OVERRIDES / name).exists():
        raise SystemExit(f"Conflicting project Device/Browser override remains: {name}")

if not UPSTREAM_DEVICE.exists():
    raise SystemExit("Pinned upstream DeviceParamsMode.java is missing; initialize the upstream submodule")

source = UPSTREAM_DEVICE.read_text(encoding="utf-8")
first_row = section(
    source,
    "int index = this.isButtonRow (0, buttonID);",
    "index = this.isButtonRow (1, buttonID);",
    "Ableton Device first-row selector LED mapping",
)
violations = forbidden_static_row_identifiers(first_row)
if violations:
    raise SystemExit(f"Push 1 static ROW1 uses RGB/blink palette identifiers: {', '.join(violations)}")

# DeviceParamsMode remains upstream-owned. Its Push 1 RGB adaptation is
# deliberately applied by prepare_push1.py during assembly, so validate that
# reproducible patch recipe before assembly rather than expecting it in the
# pristine submodule source.
prepare_source = (ROOT / "scripts/push1_build/patches/06_menus.py").read_text(encoding="utf-8")
second_row = section(
    prepare_source,
    "final int off",
    "# Align the Push 1 physical button rows",
    "Push 1 Device second-row RGB patch recipe",
)
expected_colors = (
    "PUSH1_COLOR2_GREEN_HI", "PUSH1_COLOR2_GREEN_LO",
    "PUSH1_COLOR2_BLUE_HI", "PUSH1_COLOR2_BLUE_LO",
    "PUSH1_COLOR2_MAGENTA_HI", "PUSH1_COLOR2_MAGENTA_LO",
    "PUSH1_COLOR2_AMBER_HI", "PUSH1_COLOR2_AMBER_LO",
    "PUSH1_COLOR2_YELLOW_HI", "PUSH1_COLOR2_YELLOW_LO",
    "PUSH1_COLOR2_RED_HI", "PUSH1_COLOR2_RED_LO",
    "PUSH1_COLOR2_ORCHID_HI", "PUSH1_COLOR2_ORCHID_LO",
    "PUSH1_COLOR2_WHITE",
)
for color in expected_colors:
    if color not in second_row:
        raise SystemExit(f"Push 1 Device ROW2 is missing its assigned color: {color}")
for expected in (
    "cd.isEnabled () ? greenOn : greenOff",
    "cd.isParameterPageSectionVisible () ? blueOn : blueOff",
    "cd.isExpanded () ? magentaOn : magentaOff",
    "isActive (Modes.DEVICE_CHAINS) ? orangeOn : orangeOff",
    "this.showDevices ? yellowOff : yellowOn",
    "cd.isPinned () ? redOn : redOff",
    "cd.isWindowOpen () ? violetOn : violetOff",
    "return white;",
):
    if expected not in second_row:
        raise SystemExit(f"Push 1 Device ROW2 state mapping is incorrect: {expected}")

print("Push 1 Device LED safety passed: native Ableton row mapping is used and static ROW1 contains no RGB/blink identifiers.")
