#!/usr/bin/env python3
"""Structural regression checks for Push 1 note-repeat and sequencer safety."""
from pathlib import Path
import re

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


# The pinned PushConfiguration already defaults the second row to Mute.
# Verify the actual state default instead of adding a redundant startup write.
configuration = read("controller/ableton/push/PushConfiguration.java")
if not re.search(r"private\s+TrackState\s+trackState\s*=\s*TrackState\.MUTE\s*;", configuration):
    raise SystemExit("Push 1 second-row state must default to Mute")


note_repeat = read("controller/ableton/push/mode/NoteRepeatMode.java")
required_repeat = (
    "private INoteRepeat       noteRepeat;",
    "private void refreshNoteRepeat ()",
    "this.refreshNoteRepeat ();",
    "if (this.noteRepeat == null)",
    'display.setCell (0, 5, "No MIDI");',
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

# A global marker is insufficient: each callback must refresh the late-bound
# MIDI service and guard only the operations that actually depend on it.
for signature in (
    "public void onFirstRow (final int index, final ButtonEvent event)",
    "public void updateDisplay1 (final ITextDisplay display)",
    "public int getButtonColor (final ButtonID buttonID)",
):
    body = method_body(note_repeat, signature)
    if "this.refreshNoteRepeat ();" not in body:
        raise SystemExit(f"NoteRepeatMode callback does not refresh its late-bound input: {signature}")

graphic_display = method_body(note_repeat, "public void updateDisplay2 (final IGraphicDisplay display)")
if "this.refreshNoteRepeat ();" not in graphic_display or "if (this.noteRepeat == null)" not in graphic_display:
    raise SystemExit("NoteRepeatMode graphic display must safely handle a missing MIDI input")

first_row = method_body(note_repeat, "public void onFirstRow (final int index, final ButtonEvent event)")
if "if (event != ButtonEvent.UP)" not in first_row or "event != ButtonEvent.UP || this.noteRepeat == null" in first_row:
    raise SystemExit("Note Repeat period/length buttons must remain active without MIDI input")
for marker in (
    "configuration.getNoteRepeatPeriod ().getValue ()",
    "configuration.getNoteRepeatLength ().getValue ()",
    "this.noteRepeat != null && this.host.supports (Capability.NOTE_REPEAT_LATCH)",
    "this.noteRepeat != null && this.host.supports (Capability.NOTE_REPEAT_USE_PRESSURE_TO_VELOCITY)",
    "this.noteRepeat != null && this.host.supports (Capability.NOTE_REPEAT_IS_FREE_RUNNING)",
    "this.noteRepeat != null && this.host.supports (Capability.NOTE_REPEAT_SWING)",
):
    if marker not in first_row:
        raise SystemExit(f"NoteRepeatMode first-row action regression: missing {marker!r}")

display1 = method_body(note_repeat, "public void updateDisplay1 (final ITextDisplay display)")
if "display.setCell (0, 5, \"No MIDI\");" not in display1 or "GrooveParameterID.SHUFFLE_AMOUNT" not in display1:
    raise SystemExit("NoteRepeatMode LCD must retain the Groove display when MIDI input is absent")

button_colors = method_body(note_repeat, "public int getButtonColor (final ButtonID buttonID)")
for marker in (
    "case 0, 1:",
    "case 2:",
    "case 3:",
    "this.noteRepeat != null && this.host.supports (Capability.NOTE_REPEAT_LATCH)",
    "this.noteRepeat != null && this.host.supports (Capability.NOTE_REPEAT_USE_PRESSURE_TO_VELOCITY)",
    "this.noteRepeat != null && this.host.supports (Capability.NOTE_REPEAT_IS_FREE_RUNNING)",
    "this.noteRepeat != null && this.host.supports (Capability.NOTE_REPEAT_SWING)",
):
    if marker not in button_colors:
        raise SystemExit(f"NoteRepeatMode LED state regression: missing {marker!r}")

# Knob 5/6 reset the optional MIDI note-repeat service, but knob 7 resets the
# independent Groove Shuffle parameter and must still work when no input exists.
knob_touch = method_body(note_repeat, "public void onKnobTouch (final int index, final boolean isTouched)")
if "this.refreshNoteRepeat ();" not in knob_touch or "if (this.noteRepeat == null)" in knob_touch:
    raise SystemExit("NoteRepeatMode knob touch must refresh input without globally blocking Groove reset")
for marker in (
    "this.noteRepeat != null && this.host.supports (Capability.NOTE_REPEAT_MODE)",
    "this.noteRepeat != null && this.host.supports (Capability.NOTE_REPEAT_OCTAVES)",
    "this.model.getGroove ().getParameter (GrooveParameterID.SHUFFLE_AMOUNT).resetValue ();",
):
    if marker not in knob_touch:
        raise SystemExit(f"NoteRepeatMode Delete+knob action regression: missing {marker!r}")

second_row = method_body(note_repeat, "public void onSecondRow (final int index, final ButtonEvent event)")
if "this.noteRepeat" in second_row or "this.refreshNoteRepeat" in second_row:
    raise SystemExit("NoteRepeatMode Groove enable button must not depend on a MIDI note-repeat service")
if "GrooveParameterID.ENABLED" not in second_row:
    raise SystemExit("NoteRepeatMode Groove enable button is missing")


# Knob 0-7 edit persistent configuration and Groove state; they do not
# dereference the optional MIDI note-repeat service and must remain usable
# when Bitwig has no default note input.
for signature, marker in (
    ("private int getSelectedPeriodIndex ()", "getNoteRepeatPeriod ().getValue ()"),
    ("private int getSelectedNoteLengthIndex ()", "getNoteRepeatLength ().getValue ()"),
):
    if marker not in method_body(note_repeat, signature):
        raise SystemExit(f"NoteRepeatMode display selection must use stored config without MIDI input: {signature}")


knob_value = method_body(note_repeat, "public void onKnobValue (final int index, final int value)")
if "this.refreshNoteRepeat ();" in knob_value or "this.noteRepeat == null" in knob_value:
    raise SystemExit("NoteRepeatMode configuration knobs must not be disabled by a missing MIDI input")
for marker in (
    "configuration.setNoteRepeatPeriod",
    "configuration.setNoteRepeatLength",
    "configuration.setPrevNextNoteRepeatMode",
    "configuration.setNoteRepeatOctave",
):
    if marker not in knob_value:
        raise SystemExit(f"NoteRepeatMode configuration knob regression: missing {marker!r}")


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

# The Note button is intentionally left to DrivenByMoss' native
# SelectPlayViewCommand. Drum/Drum64 must not be switched by Note, which
# already owns view-selection and session-restore behavior.
note_command = read("controller/ableton/push/command/trigger/SelectPlayViewCommand.java")
execute_note = method_body(note_command, "public void execute (final ButtonEvent event, final int velocity)")
if "this.activatePreferredView (Views.DRUM64);" in execute_note or "this.activatePreferredView (Views.DRUM);" in execute_note:
    raise SystemExit("Note command must not be repurposed to switch Drum / Drum64 layouts")
if "modeManager.setTemporary (Modes.VIEW_SELECT);" not in execute_note or "modeManager.restore ();" not in execute_note:
    raise SystemExit("Native Note view-selection / restore behavior must remain available")
if "private boolean noteLongPressed;" in note_command or "this.shiftedOnPress" in execute_note:
    raise SystemExit("Custom Note press-state interception must not be injected")

if "if (event != ButtonEvent.DOWN)" not in execute_note:
    raise SystemExit("Native Note command must react only to its original DOWN event")
setup = read("controller/ableton/push/PushControllerSetup.java")
note_bindings = [line for line in setup.splitlines() if "addButton (ButtonID.NOTE" in line]
if len(note_bindings) != 1 or "new SelectPlayViewCommand" not in note_bindings[0]:
    raise SystemExit("Push 1 Note must retain exactly one native SelectPlayViewCommand binding")

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
if "Push1DisplayProtocol.encodeLine (row, Push1DisplayLayout.fitRow (text))" not in display:
    raise SystemExit("Push 1 LCD output must normalize every display row to exactly 68 characters")

configuration_mode = read("controller/ableton/push/mode/ConfigurationMode.java")
if 'StringUtils.pad ("Low threshold maycause stuck pads!", 68)' not in configuration_mode:
    raise SystemExit("Low pad-threshold warning must fill the exact 68-character Push 1 LCD row")
if 'display.setRow (3, " Warning:Low threshold maycause stuck pads ")' in configuration_mode:
    raise SystemExit("ConfigurationMode still sends a short LCD row that can throw at runtime")

# Third-party plug-ins may publish remote-control parameter names after Device mode
# activation. Those name updates must refresh the hardware encoder bindings without
# polling or relying on an unrelated matrix-pad press.
device_params = read("controller/ableton/push/mode/device/DeviceParamsMode.java")
for marker in (
    "final IParameterBank parameterBank = this.model.getCursorDevice ().getParameterBank ();",
    "parameterBank.getItem (i).addNameObserver (name -> this.parametersAdjusted ());",
):
    if marker not in device_params:
        raise SystemExit(f"Third-party device encoder binding refresh regression: missing {marker!r}")

# API-17-compatible 24.1 backports: Session and Marker encoders control
# the current track bank's volume, while Master mode exposes project I/O.
for relative in (
    "controller/ableton/push/mode/SessionMode.java",
    "controller/ableton/push/mode/MarkerMode.java",
):
    source = read(relative)
    if "this.setParameterProvider (new VolumeParameterProvider (model));" not in source:
        raise SystemExit(f"Track-volume encoder provider is missing from {relative}")

master_mode = read("controller/ableton/push/mode/track/MasterMode.java")
master_display = method_body(master_mode, "public void updateDisplay1 (final ITextDisplay display)")
for marker in (
    'display.setCell (0, 6, "Load").setCell (0, 7, "Save")',
    'setCell (1, 6, "Project:")',
    'setBlock (2, 3, this.model.getProject ().getName ())',
):
    if marker not in master_display:
        raise SystemExit(f"Master mode project I/O display regression: missing {marker!r}")
if "Push1Display.formatValue (master.getVolume (), master.getVu (), upperBound)" not in master_display:
    raise SystemExit("Master mode must overlay the VU peak on the master volume bar")
master_second_row = method_body(master_mode, "public void onSecondRow (final int index, final ButtonEvent event)")
for marker in (
    "case 6:",
    "this.model.getProject ().load ();",
    "case 7:",
    "this.model.getProject ().save ();",
):
    if marker not in master_second_row:
        raise SystemExit(f"Master mode project I/O action regression: missing {marker!r}")
master_leds = method_body(master_mode, "public int getButtonColor (final ButtonID buttonID)")
for marker in (
    "PushColorManager.PUSH1_COLOR_GREEN_HI",
    "PushColorManager.PUSH1_COLOR_GREEN_LO",
):
    if marker not in master_leds:
        raise SystemExit(f"Master mode project I/O LED feedback regression: missing {marker!r}")

# Preserve the complete Push 1 feature surface: the source assembler must
# register every supported mode and view, while excluding Push 2/unused views.
setup = read("controller/ableton/push/PushControllerSetup.java")
expected_modes = (
    "TRACK", "TRACK_DETAILS", "VOLUME", "PAN", "CROSSFADER", "MASTER", "MASTER_TEMP",
    "DEVICE_PARAMS", "DEVICE_CHAINS", "DEVICE_LAYER", "DEVICE_LAYER_VOLUME",
    "DEVICE_LAYER_PAN", "DEVICE_LAYER_DETAILS", "BROWSER", "CLIP", "NOTE", "FRAME",
    "GROOVE", "REC_ARM", "ACCENT", "SCALES", "SCALE_LAYOUT", "FIXED", "RIBBON",
    "VIEW_SELECT", "AUTOMATION", "TRANSPORT", "MARKERS", "USER", "SETUP", "INFO",
    "CONFIGURATION", "SESSION", "SESSION_VIEW_SELECT", "REPEAT_NOTE", "ADD_TRACK",
)
for mode in expected_modes:
    if f"modeManager.register (Modes.{mode}," not in setup:
        raise SystemExit(f"Push 1 feature inventory lost mode registration: {mode}")
for marker in (
    "Modes.get (Modes.SEND1, i)",
    "Modes.get (Modes.DEVICE_LAYER_SEND1, i)",
):
    if marker not in setup:
        raise SystemExit(f"Push 1 feature inventory lost send-bank registration: {marker}")

expected_views = (
    "PLAY", "CHORDS", "PIANO", "PRG_CHANGE", "CLIP_LENGTH", "COLOR", "SESSION",
    "SEQUENCER", "POLY_SEQUENCER", "DRUM", "DRUM4", "DRUM8", "SCENE_PLAY", "DRUM64",
)
for view in expected_views:
    if f"viewManager.register (Views.{view}," not in setup:
        raise SystemExit(f"Push 1 feature inventory lost view registration: {view}")
if "Views.RAINDROPS" in setup or "new RaindropsView" in setup:
    raise SystemExit("Push 1 package must not register the unused Raindrops view")

for button in (
    "PLAY", "RECORD", "NEW", "FIXED_LENGTH", "DUPLICATE", "QUANTIZE", "DELETE",
    "DOUBLE", "UNDO", "AUTOMATION", "TRACK", "DEVICE", "BROWSE", "CLIP", "SHIFT",
    "SELECT", "TAP_TEMPO", "METRONOME", "MASTERTRACK", "PAGE_LEFT", "PAGE_RIGHT", "MUTE", "SOLO",
    "SCALES", "ACCENT", "ADD_TRACK", "NOTE", "ARROW_UP", "ARROW_DOWN", "ARROW_LEFT",
    "ARROW_RIGHT", "OCTAVE_UP", "OCTAVE_DOWN", "VOLUME", "PAN_SEND", "SETUP",
    "SESSION", "REPEAT", "STOP_CLIP", "FOOTSWITCH2",
):
    if f"ButtonID.{button}" not in setup:
        raise SystemExit(f"Push 1 feature inventory lost hardware control mapping: {button}")
if "PUSH_KNOB1 + i" not in setup or "PUSH_KNOB9" not in setup or "PUSH_SMALL_KNOB1" not in setup:
    raise SystemExit("Push 1 feature inventory lost its encoder/transport knob bindings")
if "registerAftertouchCommand" not in setup or "TOUCHSTRIP" not in setup:
    raise SystemExit("Push 1 feature inventory lost aftertouch or touchstrip bindings")

print("Push 1 note-repeat, sequencer bounds, repeat-state and color regressions passed.")
