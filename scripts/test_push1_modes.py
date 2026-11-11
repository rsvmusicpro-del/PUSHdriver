#!/usr/bin/env python3
"""Structural regression checks for Push 1 note-repeat and sequencer safety."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/java/de/mossgrabers"


def read(relative: str) -> str:
    path = SOURCE / relative
    if not path.is_file():
        raise SystemExit(f"Required assembled source is missing: {path}")
    return path.read_text()


note_repeat = read("controller/ableton/push/mode/NoteRepeatMode.java")
required_repeat = (
    "private INoteRepeat       noteRepeat;",
    "private void refreshNoteRepeat ()",
    "this.refreshNoteRepeat ();",
    "if (this.noteRepeat == null)",
    '"No MIDI input"',
    'display.setCell (row, column, "");',
    "final int modeDenominator = Math.max (1, arpeggiatorModes.size () - 1);",
    "final int safeModeIndex = Math.max (0, Math.min (modeDenominator, modeIndex));",
)
for marker in required_repeat:
    if marker not in note_repeat:
        raise SystemExit(f"NoteRepeatMode safety regression: missing {marker!r}")
if 'display.setCell (column, row, "");' in note_repeat:
    raise SystemExit("NoteRepeatMode no-input LCD fallback swaps row and column indices")
if "modeIndex * upperBound / (arpeggiatorModes.size () - 1)" in note_repeat:
    raise SystemExit("NoteRepeatMode still contains unsafe one-element mode scaling")

sequencer = read("framework/view/sequencer/AbstractSequencerView.java")
if "stepInfo = clip.getStep (notePosition);" not in sequencer:
    raise SystemExit("Sequencer repeat operator does not refresh the step after creating it")
if "if (stepInfo == null)" not in sequencer:
    raise SystemExit("Sequencer repeat operator does not guard missing step state")

for relative in (
    "controller/ableton/push/view/SequencerView.java",
    "controller/ableton/push/view/PolySequencerView.java",
    "controller/ableton/push/view/Drum4View.java",
    "controller/ableton/push/view/Drum8View.java",
):
    source = read(relative)
    if "index < 0 || index >= 64" not in source:
        raise SystemExit(f"Push 1 long-press bounds regression in {relative}")

drum64 = read("framework/view/AbstractDrum64View.java")
for marker in (
    "index < 0 || index >= this.columns * this.rows",
    "pressedNote < this.pressedKeys.length",
    "note < this.pressedKeys.length",
):
    if marker not in drum64:
        raise SystemExit(f"Drum 64 bounds regression: missing {marker!r}")

colors = read("controller/ableton/push/controller/PushColorManager.java")
for marker in (
    "AbstractSequencerView.COLOR_CONTENT",
    "AbstractSequencerView.COLOR_STEP_HILITE_CONTENT",
    "AbstractSequencerView.COLOR_STEP_MUTED",
    "AbstractSequencerView.COLOR_STEP_SELECTED",
    "AbstractSequencerView.COLOR_ACTIVE_PAGE",
    "PUSH1_COLOR2_BLUE_HI",
    "PUSH1_COLOR2_GREEN_HI",
    "PUSH1_COLOR2_YELLOW_HI",
    "COLOR_STEP_HILITE_RECORD",
    "COLOR_PAD_SOLO",
    "PUSH1_COLOR2_RED_HI",
    "PUSH1_COLOR2_WHITE",
    "PUSH1_COLOR2_YELLOW_LO",
    "PUSH1_COLOR2_BLUE_LO",
):
    if marker not in colors:
        raise SystemExit(f"Push 1 native color mapping regression: missing {marker!r}")

for mapping in (
    "AbstractSequencerView.COLOR_STEP_HILITE_RECORD, isPush2 ? PUSH2_COLOR2_RED_HI : PUSH1_COLOR2_RED_HI",
    "AbstractSequencerView.COLOR_STEP_MUTED, isPush2 ? PUSH2_COLOR2_GREY_MD : PUSH1_COLOR2_YELLOW_LO",
    "AbstractSequencerView.COLOR_STEP_SELECTED, isPush2 ? PUSH2_COLOR2_YELLOW_HI : PUSH1_COLOR2_WHITE",
    "AbstractDrumView.COLOR_PAD_SOLO, isPush2 ? PUSH2_COLOR2_BLUE_LO : PUSH1_COLOR2_BLUE_LO",
):
    if mapping not in colors:
        raise SystemExit(f"Live 10 Push 1 LED mapping regression: missing {mapping!r}")
if "this.model.hasRecordingState () ? COLOR_STEP_HILITE_RECORD : COLOR_STEP_HILITE_CONTENT" not in sequencer:
    raise SystemExit("Sequencer playhead must be green in playback and red while recording")
drum = read("framework/view/sequencer/AbstractDrumView.java")
if "if (drumPad.isSolo ())" not in drum or "return AbstractDrumView.COLOR_PAD_SOLO;" not in drum:
    raise SystemExit("Drum solo pads must retain dedicated dark-blue feedback")
piano = read("framework/view/PianoViewHelper.java")
root_color = "else if (Scales.SCALE_COLOR_OCTAVE.equals (keyManager.getColor (n)))"
track_color = "AbstractView.replaceOctaveColorWithTrackColor (track, Scales.SCALE_COLOR_OCTAVE)"
if root_color not in piano or track_color not in piano:
    raise SystemExit("Piano root-note color regression: fundamentals must use the selected track color")
if piano.index(root_color) > piano.index("else if (row % 2 == 0)"):
    raise SystemExit("Piano root-note track color must take precedence over fixed white/black key colors")

touchstrip = read("controller/ableton/push/command/pitchbend/TouchstripCommand.java")
for marker in (
    "final boolean isReset = this.surface.isDeletePressed ();",
    "this.surface.setTriggerConsumed (ButtonID.DELETE);",
    "volumeParameter.resetValue ();",
    "parameter.resetValue ();",
    "defaultNoteInput == null ? null : defaultNoteInput.getNoteRepeat ();",
):
    if marker not in touchstrip:
        raise SystemExit(f"Touchstrip reset/input-safety regression: missing {marker!r}")

pitchbend_config = read("controller/ableton/push/command/continuous/ConfigurePitchbendCommand.java")
for marker in (
    "public void executeNormal (final ButtonEvent event)",
    "this.surface.setTriggerConsumed (ButtonID.DELETE);",
    "parameter.resetValue ();",
    "PushConfiguration.RIBBON_MODE_LAST_TOUCHED",
):
    if marker not in pitchbend_config:
        raise SystemExit(f"Touchstrip configuration reset regression: missing {marker!r}")

display = read("controller/ableton/push/controller/Push1Display.java")
if "Push1DisplayProtocol.encodeLine (row, StringUtils.pad (text, 68))" not in display:
    raise SystemExit("Push 1 LCD output must normalize every display row to exactly 68 characters")

configuration_mode = read("controller/ableton/push/mode/ConfigurationMode.java")
if 'StringUtils.pad ("Low threshold maycause stuck pads!", 68)' not in configuration_mode:
    raise SystemExit("Low pad-threshold warning must fill the exact 68-character Push 1 LCD row")
if 'display.setRow (3, " Warning:Low threshold maycause stuck pads ")' in configuration_mode:
    raise SystemExit("ConfigurationMode still sends a short LCD row that can throw at runtime")

print("Push 1 note-repeat, sequencer bounds, repeat-state and color regressions passed.")
