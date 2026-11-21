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
expected_top = """    private static final Views [] VIEWS_TOP =
    {
        Views.SEQUENCER,
        Views.POLY_SEQUENCER,
        Views.DRUM,
        Views.DRUM4,
        Views.DRUM8,
        null,
        null,
        Views.RAINDROPS
    };"""
expected_bottom = """    private static final Views [] VIEWS     =
    {
        Views.PLAY,
        Views.CHORDS,
        Views.PIANO,
        Views.DRUM64,
        null,
        null,
        Views.CLIP_LENGTH,
        Views.PRG_CHANGE
    };"""
require(expected_top in note, "Note menu upper-row choices must match the requested eight-button layout")
require(expected_bottom in note, "Note menu lower row must preserve Play/Chords/Piano/Drum64 and fixed tools")
note_display = method(note, "public void updateDisplay1")
require("VIEWS_TOP[i]" in note_display and "display.setCell (0, i" in note_display,
        "Note menu upper LCD labels must follow the upper-row view mapping")
require("VIEWS[i]" in note_display and "display.setCell (3, i" in note_display,
        "Note menu lower LCD labels must preserve the lower-row mapping")
views_source = (ROOT / "src/main/java/de/mossgrabers/framework/view/Views.java").read_text()
setup_source = (ROOT / "src/main/java/de/mossgrabers/controller/ableton/push/PushControllerSetup.java").read_text()
require("RAINDROPS" in views_source and "VIEW_NAMES.put (NAME_RAINDROPS, RAINDROPS)" in views_source,
        "Raindrops must remain a registered framework view")
require("viewManager.register (Views.RAINDROPS, new RaindropsView (surface, this.model));" in setup_source,
        "Raindrops must remain registered in the Push controller")
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

require(note_colors.count("PushColorManager.PUSH_GREEN_HI") == 2
        and note_colors.count("PushColorManager.PUSH_YELLOW_LO") == 2,
        "Available Note View Select modes must be vivid green and the selected mode dim yellow")
require("AbstractMode.BUTTON_COLOR_HI" not in note_colors
        and "AbstractFeatureGroup.BUTTON_COLOR_ON" not in note_colors,
        "Note View Select must not fall back to the generic medium-yellow/low-green palette")

# Push 1 hardware geometry: ROW1 is the upper button bank nearest the LCD;
# ROW2 is the lower bank. Keep this distinct from assumptions based on Push 2.
require("new ButtonRowModeCommand<> (0, i" in setup_source
        and "new ButtonRowModeCommand<> (1, i" in setup_source,
        "Push 1 physical button banks must keep their row 0 / row 1 command IDs")
require("surface.getButton (ButtonID.ROW1_1).setBounds (32.57639592970192, 43.44862211964502"
        in setup_source
        and "surface.getButton (ButtonID.ROW2_1).setBounds (32.57639592970192, 51.6242466465187"
        in setup_source,
        "Push 1 upper/lower button geometry must match the expected physical row mapping")
require(note.count("this.activateView (VIEWS_TOP[index])") == 1
        and note.count("this.activateView (VIEWS[index])") == 1,
        "Each Note menu physical row must have exactly one matching selection handler")

# Device context must use the complete 68-column LCD line: one physical
# module for the label and the remaining three for the selected device name.
device_params = read("device/DeviceParamsMode.java")
device_display = method(device_params, "public void updateDisplay1")
require('display.setRow (2, Push1DisplayText.fitAscii ("DEVICE:" + " ".repeat (10) + cd.getName (), Push1DisplayLayout.COLUMNS));'
        in device_display,
        "Device context row must use all four 17-character LCD modules")

# All upstream mode writers pass through the physical Push 1 geometry.
display_path = ROOT / "src/main/java/de/mossgrabers/controller/ableton/push/controller/Push1Display.java"
if not display_path.is_file():
    raise SystemExit(f"Missing assembled Push 1 display: {display_path}")
push_display = display_path.read_text()
cell_writer = method(push_display, "public ITextDisplay setCell (final int row, final int cell, final String value)")
block_writer = method(push_display, "public ITextDisplay setBlock (final int row, final int block, final String value)")
require("Push1DisplayLayout.fitCell (value, cell)" in cell_writer,
        "every Push 1 cell write must be transliterated and bounded to its physical 9/8 width")
require("Push1DisplayLayout.fitBlock (value)" in block_writer
        and "Push1DisplayLayout.firstCellForBlock (block)" in block_writer
        and "Push1DisplayLayout.cellWidth (firstCell)" in block_writer
        and "fitted.substring" in block_writer,
        "every Push 1 module write must be bounded to exactly 17 characters and split at the physical 9/8 boundary")
require("Push1DisplayLayout.fitRow (text)" in method(push_display, "public void writeLine"),
        "every physical LCD row must use the shared 68-column fitter")
row_writer = method(push_display, "public ITextDisplay setRow (final int row, final String str)")
simulator_writer = method(push_display, "protected String convertCharacterset")
require("Push1DisplayLayout.fitRow (str)" in row_writer,
        "full-row writes must be normalized before AbstractTextDisplay stores them")
require("Push1DisplayLayout.fitRow (text)" in simulator_writer
        and 'replace (Character.toString ((char) 0x1C), "…")' in simulator_writer,
        "simulator rendering must use the same bounded row and one-cell ellipsis mapping")
require("overlayRightBlock" not in push_display and "compactNotification" not in push_display
        and "public void flush" not in push_display,
        "Push 1 notifications must not overlay or replace part of persistent display rows")
display_text_path = ROOT / "src/main/java/de/mossgrabers/controller/ableton/push/controller/Push1DisplayText.java"
require(display_text_path.is_file()
        and "overlayRightBlock" not in display_text_path.read_text(),
        "Push 1 text helpers must not expose the removed right-block overlay path")

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
require("setScaleOffsetByIndex (index - 1)" in method(scales, "public void onFirstRow"),
        "Scales Row 1 must select the upper base-note choices")
require("setScaleOffsetByIndex (index + 5)" in method(scales, "public void onSecondRow"),
        "Scales Row 2 must select the lower base-note choices")
require("offset == index - 1" in method(scales, "public int getButtonColor")
        and "offset == index + 5" in method(scales, "public int getButtonColor"),
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


# Add Track LCD halves and physical button rows must agree. The upstream display
# layout remains unchanged: Instrument is upper; Audio/Effect is lower.
add_track = read("track/AddTrackMode.java")
first_row = method(add_track, "public void onFirstRow")
second_row = method(add_track, "public void onSecondRow")
colors = method(add_track, "public int getButtonColor")
require("ChannelType.INSTRUMENT" in first_row and "getInstrumentFavorite" in first_row,
        "Add Track Row 1 must select the upper Instrument choices")
require("ChannelType.AUDIO" not in first_row and "ChannelType.EFFECT" not in first_row,
        "Add Track Row 1 must not select lower Audio/Effect choices")
require("ChannelType.AUDIO" in second_row and "ChannelType.EFFECT" in second_row
        and "getAudioFavorite" in second_row and "getEffectFavorite" in second_row,
        "Add Track Row 2 must select the lower Audio/Effect choices")
require("ChannelType.INSTRUMENT" not in second_row,
        "Add Track Row 2 must not select upper Instrument choices")
first_colors = colors.split("index = this.isButtonRow (1", 1)[0]
second_colors = colors.split("index = this.isButtonRow (1", 1)[1]
require("isButtonRow (0" in first_colors and "PUSH1_COLOR_YELLOW_MD" in first_colors
        and "PUSH1_COLOR2_" not in first_colors,
        "Add Track upper Instrument Row 1 LEDs must use the Push 1 static palette")
require("PUSH1_COLOR2_GREEN_HI" in second_colors
        and "PUSH1_COLOR2_GREEN_LO" in second_colors
        and "PUSH1_COLOR2_AMBER_HI" in second_colors
        and "PUSH1_COLOR2_AMBER_LO" in second_colors
        and "PUSH2_COLOR2_GREEN_HI" in second_colors
        and "PUSH2_COLOR2_GREEN_LO" in second_colors
        and "PUSH2_COLOR2_AMBER_HI" in second_colors
        and "PUSH2_COLOR2_AMBER_LO" in second_colors,
        "Add Track lower Audio/Effect Row 2 LEDs must use vivid green and orange palette levels on both controllers")

print("PASS: Add Track, Note View Select, Session View Select, Scales, Fixed Length and Quantize row/display contracts")
