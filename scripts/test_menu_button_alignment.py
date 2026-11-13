#!/usr/bin/env python3
"""Regression guards for Push 1 LCD rows, physical selectors and menu LEDs."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
base = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode"
base_mode = (base / "BaseMode.java").read_text(encoding="utf-8")

assert "protected boolean isMenuRowDispatchInverted ()" in base_mode
assert "this.isMenuRowDispatchInverted () ? 1 - row : row" in base_mode, (
    "Button dispatch must use the mode's explicit LCD row policy"
)
assert "super.isButtonRow (logicalRow, buttonID)" in base_mode, (
    "LED feedback must use exactly the same row mapping as button dispatch"
)

inverted_modes = (
    "FixedMode", "NoteMode", "NoteViewSelectMode", "SessionViewSelectMode",
    "ScalesMode", "ScaleLayoutMode", "GrooveMode", "QuantizeMode", "RibbonMode",
    "AutomationSelectionMode", "MetronomeMode", "MarkerMode", "FrameMode",
    "TrackDetailsMode", "AddTrackMode", "MasterMode", "ClipMode",
    "DeviceLayerDetailsMode", "DeviceChainsMode", "DeviceLayerMode",
    "DeviceParamsMode", "UserMode",
)
track_modes = {"TrackDetailsMode", "AddTrackMode", "MasterMode", "ClipMode"}
device_modes = {"DeviceLayerDetailsMode", "DeviceChainsMode", "DeviceLayerMode", "DeviceParamsMode", "UserMode"}
for name in inverted_modes:
    subdir = "track" if name in track_modes else "device" if name in device_modes else ""
    source = (base / subdir / (name + ".java")).read_text(encoding="utf-8")
    assert "protected boolean isMenuRowDispatchInverted ()" in source and "return true;" in source, (
        name + " must align its first/second row callbacks with the displayed LCD rows"
    )

for name in ("FixedMode", "NoteMode"):
    source = (base / (name + ".java")).read_text(encoding="utf-8")
    assert "public void onButton (" not in source, (
        name + " must use the shared row policy, not a local dispatch workaround"
    )

# Browser keeps its tested previous/next navigation pattern.
browser = (base / "device/DeviceBrowserMode.java").read_text(encoding="utf-8")
assert "isMenuRowDispatchInverted" not in browser

note_repeat = (base / "NoteRepeatMode.java").read_text(encoding="utf-8")
assert "if (row == 0)" in note_repeat and "if (index <= 3)" in note_repeat
assert "else if (row == 1 && index >= 4)" in note_repeat
assert "Bottom row: Latch, Pressure, Sync and Shuffle." in note_repeat
assert "Top row: Period, Length and Groove enablement." in note_repeat

device_chains = (base / "device/DeviceChainsMode.java").read_text(encoding="utf-8")
assert "return index < slotChains.length ? existsColor : offColor;" in device_chains
assert "return super.getButtonColor (buttonID);" in device_chains

selector = base / "NoteViewSelectMode.java"
source = selector.read_text(encoding="utf-8")

def row_entries(name):
    declaration = "private static final Views [] " + name
    start = source.index("{", source.index(declaration)) + 1
    end = source.index("};", start)
    return [line.strip().rstrip(",") for line in source[start:end].splitlines() if line.strip()]

assert row_entries("VIEWS") == [
    "Views.PLAY", "Views.DRUM", "Views.DRUM64", "Views.DRUM4",
    "Views.DRUM8", "null", "Views.CLIP_LENGTH", "Views.PRG_CHANGE",
], "Bottom row must have five game views, a reserved slot, then Clip Length and Program Change in their original positions"
assert row_entries("VIEWS_TOP") == [
    "Views.SEQUENCER", "Views.POLY_SEQUENCER", "Views.PIANO", "Views.CHORDS",
    "null", "null", "null", "null",
], "Top row must have four game views followed by four reserved Note Mode slots"
assert "activateView (VIEWS[index])" in source and "activateView (VIEWS_TOP[index])" in source
assert source.count("VIEWS[index]") >= 2 and source.count("VIEWS_TOP[index]") >= 2, (
    "LCD and LED feedback must remain driven by the corresponding selector arrays"
)

device = (root / "tests/java/VirtualPush1Device.java").read_text(encoding="utf-8")
assert "TOP_ROW_FIRST = 20" in device and "BOTTOM_ROW_FIRST = 102" in device, (
    "Virtual Push 1 top/bottom physical CC mapping must remain faithful to the Push 1 MIDI map"
)

print("Push 1 LCD/button/LED row alignment and balanced game-view menu: PASS")
