#!/usr/bin/env python3
"""Verify Push 1 menu row actions match the upper/lower LCD halves."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODE = ROOT / "src/main/java/de/mossgrabers/controller/ableton/push/mode"


def read(name: str) -> str:
    path = MODE / name
    if not path.is_file():
        raise SystemExit(f"Missing assembled mode: {path}")
    return path.read_text()


def method(source: str, signature: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise AssertionError(f"Missing method: {signature}")
    brace = source.find("{", start)
    depth = 0
    for pos in range(brace, len(source)):
        if source[pos] == "{":
            depth += 1
        elif source[pos] == "}":
            depth -= 1
            if depth == 0:
                return source[start:pos + 1]
    raise AssertionError(f"Unclosed method: {signature}")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


note = read("NoteViewSelectMode.java")
require("this.activateView (VIEWS_TOP[index]);" in method(note, "public void onFirstRow"),
        "Note View Select Row 1 must select the upper display choices")
require("this.activateView (VIEWS[index]);" in method(note, "public void onSecondRow"),
        "Note View Select Row 2 must select the lower display choices")
note_colors = method(note, "public String getButtonColorID")
require("isButtonRow (0, buttonID)" in note_colors.split("index = this.isButtonRow (1", 1)[0]
        and "VIEWS_TOP[index]" in note_colors.split("index = this.isButtonRow (1", 1)[0],
        "Note View Select Row 1 LED feedback must describe upper choices")
require("VIEWS[index]" in note_colors.split("index = this.isButtonRow (1", 1)[1],
        "Note View Select Row 2 LED feedback must describe lower choices")

session = read("SessionViewSelectMode.java")
require("display.setCell (0, i" in method(session, "public void updateDisplay1"),
        "Session view choices must be drawn in the upper LCD row")
require("display.setCell (3, 6, \"Markers\")" in method(session, "public void updateDisplay1")
        and "display.setCell (3, 7" in method(session, "public void updateDisplay1"),
        "Markers and Clips must remain in the lower LCD row")
require("setActive (Modes.MARKERS)" in method(session, "public void onSecondRow")
        and "toggleScenesClipMode" in method(session, "public void onSecondRow"),
        "Session lower-half actions must be handled by Row 2")
require("isButtonRow (0, buttonID)" in method(session, "public String getButtonColorID")
        and "isButtonRow (1, buttonID)" in method(session, "public String getButtonColorID"),
        "Session LED feedback must be split across the two physical rows")

scales = read("ScalesMode.java")
require("setScaleOffsetByIndex (index + 5)" in method(scales, "public void onFirstRow"),
        "Scales Row 1 must select the upper base-note choices")
require("setScaleOffsetByIndex (index - 1)" in method(scales, "public void onSecondRow"),
        "Scales Row 2 must select the lower base-note choices")
require("offset == index + 5" in method(scales, "public int getButtonColor")
        and "offset == index - 1" in method(scales, "public int getButtonColor"),
        "Scales LED feedback must follow the relocated base-note choices")

fixed = read("FixedMode.java")
require("this.newCommands[index].execute ()" in method(fixed, "public void onFirstRow"),
        "Fixed Length Row 1 must execute the upper Create Clip choices")
require("setNewClipLength (index)" in method(fixed, "public void onSecondRow"),
        "Fixed Length Row 2 must select the lower New Clip Length choices")
require("isButtonRow (1, buttonID)" in method(fixed, "public int getButtonColor"),
        "Fixed Length selection LED must be on Row 2")

quantize = read("QuantizeMode.java")
require("setTemporary (Modes.GROOVE)" in method(quantize, "public void onFirstRow"),
        "Quantize Row 1 must open the upper Groove choice")
require("setRecordQuantizationGrid" in method(quantize, "public void onSecondRow")
        and "toggleRecordQuantizationNoteLength" in method(quantize, "public void onSecondRow"),
        "Quantize Row 2 must control the lower Record Quantize choices")
quantize_colors = method(quantize, "public String getButtonColorID")
require("isButtonRow (1, buttonID)" in quantize_colors.split("index = this.isButtonRow (0", 1)[0],
        "Quantize lower-choice LED feedback must be on Row 2")

print("PASS: Note View Select, Session View Select, Scales, Fixed Length and Quantize row/display contracts")
