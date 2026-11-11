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


def method_body(source: str, signature: str) -> str:
    """Return one Java method body using balanced braces."""
    start = source.find(signature)
    if start < 0:
        raise SystemExit(f"Required method is missing: {signature}")
    opening = source.find("{", start + len(signature))
    if opening < 0:
        raise SystemExit(f"Method has no body: {signature}")
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    raise SystemExit(f"Unbalanced method body: {signature}")


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

# A global marker is insufficient: every callback that can dereference the
# late-bound MIDI service must refresh it and handle the unavailable-input case.
for signature in (
    "public void onKnobValue (final int index, final int value)",
    "public void onKnobTouch (final int index, final boolean isTouched)",
    "public void onFirstRow (final int index, final ButtonEvent event)",
    "public void onSecondRow (final int index, final ButtonEvent event)",
    "public void updateDisplay1 (final ITextDisplay display)",
    "public void updateDisplay2 (final IGraphicDisplay display)",
    "public int getButtonColor (final ButtonID buttonID)",
):
    body = method_body(note_repeat, signature)
    if "this.refreshNoteRepeat ();" not in body or "this.noteRepeat == null" not in body:
        raise SystemExit(f"NoteRepeatMode callback lacks its own refreshed null guard: {signature}")


sequencer = read("framework/view/sequencer/AbstractSequencerView.java")
if "stepInfo = clip.getStep (notePosition);" not in sequencer:
    raise SystemExit("Sequencer repeat operator does not refresh the step after creating it")
if "if (stepInfo == null)" not in sequencer:
    raise SystemExit("Sequencer repeat operator does not guard missing step state")

# The Push 1 sequencer follows the playing page only while a real note
# clip exists, then uses the shared live current-step renderer.
push_sequencer = read("controller/ableton/push/view/SequencerView.java")
draw_grid = method_body(push_sequencer, "public void drawGrid ()")
for marker in (
    "this.model.getTransport ().isPlaying ()",
    "clip.doesExist ()",
    "clip.getCurrentStep ()",
    "this.numDisplayCols",
    "clip.getEditPage ()",
    "clip.scrollToPage (playheadPage)",
    "super.drawGrid ();",
):
    if marker not in draw_grid:
        raise SystemExit(f"Push 1 live sequencer follow regression: missing {marker!r}")
if draw_grid.index("clip.doesExist ()") > draw_grid.index("clip.getCurrentStep ()"):
    raise SystemExit("Push 1 playhead follow must check clip existence before reading its step state")
if "clip.getNumSteps ()" in draw_grid:
    raise SystemExit("Push 1 page tracking must not divide by total clip length")

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

# The Note button follows Ableton's Push 1 Drum Rack workflow: Note from a
# Drum Rack track enters Loop Selector; subsequent presses alternate Drum/Drum64.
note_command = read("controller/ableton/push/command/trigger/SelectPlayViewCommand.java")
execute_note = method_body(note_command, "public void execute (final ButtonEvent event, final int velocity)")
for marker in (
    "this.model.canSelectedTrackHoldNotes () && this.model.getDrumDevice ().hasDrumPads ()",
    "viewManager.setActive (Views.DRUM);",
    "if (viewManager.isActive (Views.DRUM))",
    "viewManager.setActive (Views.DRUM64);",
    "if (viewManager.isActive (Views.DRUM64))",
    "modeManager.restore ();",
):
    if marker not in execute_note:
        raise SystemExit(f"Push 1 Drum layout toggle regression: missing {marker!r}")
if execute_note.index("if (modeManager.isActive (Modes.VIEW_SELECT))") > execute_note.index("if (viewManager.isActive (Views.DRUM))"):
    raise SystemExit("The Note button must dismiss the temporary view selector before changing Drum layouts")
if execute_note.index("if (viewManager.isActive (Views.DRUM))") > execute_note.index("if (viewManager.isActive (Views.DRUM64))"):
    raise SystemExit("Drum must toggle to Drum64 before the reverse Drum64-to-Drum transition")

# Verify the complete Loop Selector behavior and its 16 fixed-velocity pads.
drum_engine = read("framework/view/sequencer/AbstractDrumView.java")
loop_handler = method_body(drum_engine, "protected void handleLoopArea (final int pad, final int velocity)")
for marker in (
    "this.configuration.isAccentActive ()",
    "this.configuration.setFixedAccentValue ((selPad + 1) * 8 - 1)",
    "clip.scrollToPage (pad)",
    "clip.setLoopStart (newStart)",
    "clip.setLoopLength ((end - start) * lengthOfOnePad)",
    "clip.setPlayRange (newStart, (double) end * lengthOfOnePad)",
):
    if marker not in loop_handler:
        raise SystemExit(f"Push 1 Loop Selector / velocity layout regression: missing {marker!r}")
pages = method_body(drum_engine, "protected void drawPages (final INoteClip clip, final boolean isActive)")
if "for (int pad = 0; pad < 16; pad++)" not in pages or "this.configuration.isAccentActive ()" not in pages:
    raise SystemExit("The 16-pad fixed-velocity selector is missing from the Drum Loop Selector")
velocity = method_body(drum_engine, "protected int getVelocity (final int index)")
if "this.configuration.getFixedAccentValue ()" not in velocity:
    raise SystemExit("Sequencer steps must use the selected fixed velocity when Accent is active")

# The standard Drum view long-presses must stay within the 64 physical pads.
drum_view = read("controller/ableton/push/view/DrumView.java")
drum_long_press = method_body(drum_view, "public void onGridNoteLongPress (final int note)")
if "index < 0 || index >= 64" not in drum_long_press:
    raise SystemExit("Drum Loop Selector long-press callback lacks 8x8 pad bounds")

# Drum64 remains a full-grid play view and retains its dedicated pad actions.
drum64_view = read("controller/ableton/push/view/Drum64View.java")
for marker in (
    "this.model.getDrumDevice (64)",
    "this.model.getBrowser ().replace",
    "this.model.getNoteClip (8, 128).clearRow",
    "drumPad.select ();",
    "scene.select ();",
    "scene.launch ();",
):
    if marker not in drum64_view:
        raise SystemExit(f"Drum64 interaction regression: missing {marker!r}")

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
for signature in (
    "public void onPitchbend (final int data1, final int data2)",
    "public void updateValue ()",
):
    body = method_body(touchstrip, signature)
    if "defaultNoteInput == null ? null : defaultNoteInput.getNoteRepeat ();" not in body:
        raise SystemExit(f"Touchstrip MIDI-input null guard is missing from {signature}")
    if "if (noteRepeat == null)" not in body:
        raise SystemExit(f"Touchstrip does not handle missing Note Repeat service in {signature}")

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
