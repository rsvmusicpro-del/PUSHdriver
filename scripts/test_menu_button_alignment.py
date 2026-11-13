#!/usr/bin/env python3
"""Regression guard for Push 1 LCD-to-selector row alignment."""
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
note = (base / "NoteMode.java").read_text(encoding="utf-8")
assert note.count("this.isButtonRow (1, buttonID)") >= 1, "NoteMode upper LCD options must use the upper physical selector row"
assert note.count("this.isButtonRow (0, buttonID)") >= 1, "NoteMode lower LCD page options must use the lower physical selector row"
print("Push 1 Fixed Length / Note Mode selector alignment: PASS")
