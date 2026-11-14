#!/usr/bin/env python3
"""Reject Push 1 RGB/blink palette identifiers on the static CC button row."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEVICE = ROOT / "overrides/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceParamsMode.java"
CHAINS = ROOT / "overrides/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceChainsMode.java"
BROWSER = ROOT / "overrides/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceBrowserMode.java"


def section(source: str, start: str, end: str, label: str) -> str:
    begin = source.find(start)
    if begin < 0:
        raise SystemExit(f"{label}: static-row color section start not found")
    finish = source.find(end, begin)
    if finish < 0:
        raise SystemExit(f"{label}: static-row color section end not found")
    return source[begin:finish]


def reject_rgb(source: str, start: str, end: str, label: str) -> None:
    block = section(source, start, end, label)
    forbidden = [token for token in ("PUSH1_COLOR2_", "BUTTON_COLOR2_ON", "BUTTON_COLOR2_OFF") if token in block]
    if forbidden:
        raise SystemExit(f"{label}: RGB/blink palette identifier on static CC row: {forbidden}")


device = DEVICE.read_text(encoding="utf-8")
chains = CHAINS.read_text(encoding="utf-8")
browser = BROWSER.read_text(encoding="utf-8")

reject_rgb(device, "// ROW2 / CC102-109: static CC palette only",
           "return super.getButtonColor (buttonID);", "DeviceParamsMode")
reject_rgb(chains, "final int index = this.isButtonRow (1, buttonID);",
           "return super.getButtonColor (buttonID);", "DeviceChainsMode")
reject_rgb(browser, "index = this.isButtonRow (1, buttonID);",
           "return super.getButtonColor (buttonID);", "DeviceBrowserMode")
browser_ids = section(browser, "// ROW2 uses the static CC palette",
                      "return AbstractFeatureGroup.BUTTON_COLOR_OFF;",
                      "DeviceBrowserMode color identifiers")
if "BUTTON_COLOR2_ON" in browser_ids or "BUTTON_COLOR2_OFF" in browser_ids:
    raise SystemExit("DeviceBrowserMode: static ROW2 exposes a color-2/blink identifier")

print("Push 1 LED safety passed: static CC row uses no RGB/blink color identifiers in Device, Chains or Browser.")
