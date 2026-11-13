#!/usr/bin/env python3
"""Regression guards for Push 1 selector rows and the two-row game-view menu."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
base = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode"
for name in ("FixedMode", "NoteMode"):
    source = (base / (name + ".java")).read_text(encoding="utf-8")
    assert "super.onButton (1 - row, index, event);" in source, (
        name + " must route each physical selector row to its matching LCD row"
    )
if "this.isButtonRow (1, buttonID)" not in (base / "FixedMode.java").read_text(encoding="utf-8"):
    raise SystemExit("FixedMode selected-length LED must follow the lower LCD option row")
note_mode = (base / "NoteMode.java").read_text(encoding="utf-8")
assert note_mode.count("this.isButtonRow (1, buttonID)") >= 1, "NoteMode upper LCD options must use the upper physical selector row"
assert note_mode.count("this.isButtonRow (0, buttonID)") >= 1, "NoteMode lower LCD page options must use the lower physical selector row"
assert '"Recurr. Pattern"' in note_mode and "case 7:" in note_mode, (
    "Note Mode page placement must remain unchanged until its dedicated redesign"
)

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
assert "activateView (VIEWS[index])" in source and "activateView (VIEWS_TOP[index])" in source, (
    "Physical row selection must use the same arrays as the displayed menu"
)
assert source.count("VIEWS[index]") >= 2 and source.count("VIEWS_TOP[index]") >= 2, (
    "LCD and LED feedback must remain driven by the corresponding selector arrays"
)

print("Push 1 selector alignment and balanced two-row game-view layout: PASS")
