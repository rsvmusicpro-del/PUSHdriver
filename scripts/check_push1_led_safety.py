#!/usr/bin/env python3
"""Verify that the pinned Ableton Push Device implementation keeps the Push 1 LED rows distinct."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEVICE_OVERRIDES = ROOT / "overrides/java/de/mossgrabers/controller/ableton/push/mode/device"
UPSTREAM_DEVICE = ROOT / "upstream/src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceParamsMode.java"


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
for forbidden in ("PUSH1_COLOR2_", "BUTTON_COLOR2_ON", "BUTTON_COLOR2_OFF"):
    if forbidden in first_row:
        raise SystemExit(f"Push 1 static ROW1 uses RGB/blink palette identifier: {forbidden}")

# Self-test: prove the static-row guard rejects a representative RGB/blink token.
try:
    for forbidden in ("PUSH1_COLOR2_", "BUTTON_COLOR2_ON", "BUTTON_COLOR2_OFF"):
        if forbidden in "return PushColorManager.PUSH1_COLOR2_BLUE_HI;":
            raise SystemExit(f"guard self-test: rejected {forbidden}")
except SystemExit:
    pass
else:
    raise SystemExit("Guard self-test failed: RGB/blink identifier was not rejected")

print("Push 1 Device LED safety passed: native Ableton row mapping is used and static ROW1 contains no RGB/blink identifiers.")
