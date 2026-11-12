#!/usr/bin/env python3
"""Scenario-oriented guards tying the virtual hardware profile to Push 1 runtime wiring.

These checks inspect the assembled production setup (not a second mock controller).
Behavioral navigation/New/sequencer cases remain in their dedicated regression tests.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/de/mossgrabers"


def read(path: str) -> str:
    target = JAVA / path
    if not target.is_file():
        raise SystemExit(f"Missing assembled production source: {target}")
    return target.read_text()


def require(source: str, *markers: str) -> None:
    for marker in markers:
        if marker not in source:
            raise SystemExit(f"Push 1 scenario wiring is missing: {marker}")


setup = read("controller/ableton/push/PushControllerSetup.java")
surface = read("controller/ableton/push/controller/PushControlSurface.java")
display = read("controller/ableton/push/controller/Push1Display.java")

# Scenario: the extension creates a real MIDI-only Push 1 endpoint and selects
# the four-line text display; no Push 2 HID/display path is required.
create_surface = setup[setup.index("protected void createSurface ()"):]
require(create_surface,
    'this.factory.createMidiAccess ()',
    'midiAccess.createOutput ()',
    'midiAccess.createInput ("Pads", "80????"',
    '"90????"',
    'new PushControlSurface (this.host, this.colorManager, this.configuration, output, input)',
    'new Push1Display (this.host, this.valueChanger.getUpperBound (), output, this.configuration)',
    'surface.getModeManager ().setDefaultID (Modes.TRACK);')

# Scenario: transitions initiated by the primary Push 1 mode/view buttons
# target the actual production commands and registered modes.
for marker in (
    'this.addButton (ButtonID.TRACK,',
    'new TrackCommand (this.model, surface)',
    'this.addButton (ButtonID.DEVICE, "Device", new DeviceCommand',
    'this.addButton (ButtonID.BROWSE, "Browse", new BrowserCommand',
    'this.addButton (ButtonID.CLIP, "Clip", new ClipCommand',
    'this.addButton (ButtonID.NOTE, "Note", new SelectPlayViewCommand',
    'this.addButton (ButtonID.SESSION, "Session", new SelectSessionViewCommand',
    'this.addButton (ButtonID.SHIFT, "Shift", new ShiftCommand',
    'this.addButton (ButtonID.VOLUME, "Volume", new VolumeCommand',
    'this.addButton (ButtonID.PAN_SEND, "Pan/Send", new PanSendCommand',
):
    require(setup, marker)

create_modes = setup[setup.index("protected void createModes ()"):setup.index("protected void createObservers ()")]
for marker in (
    "modeManager.register (Modes.TRACK,",
    "modeManager.register (Modes.DEVICE_PARAMS,",
    "modeManager.register (Modes.BROWSER,",
    "modeManager.register (Modes.CLIP,",
    "modeManager.register (Modes.NOTE,",
    "modeManager.register (Modes.SESSION,",
    "modeManager.register (Modes.REPEAT_NOTE,",
    "modeManager.register (Modes.CONFIGURATION,",
):
    require(create_modes, marker)

# Scenario: Shift is a concurrently-held physical button; eight main encoders
# and their independent touch notes, master and two small encoders are bound.
continuous = setup[setup.index("protected void registerContinuousCommands ()"):]
require(continuous,
    "for (int i = 0; i < 8; i++)",
    "this.addRelativeKnob (ContinuousID.get (ContinuousID.KNOB1, i)",
    "PushControlSurface.PUSH_KNOB1 + i",
    "knob.bindTouch (new KnobRowTouchModeCommand<>",
    "PushControlSurface.PUSH_KNOB1_TOUCH + i",
    "this.addRelativeKnob (ContinuousID.MASTER_KNOB",
    "PushControlSurface.PUSH_KNOB9",
    "PushControlSurface.PUSH_KNOB9_TOUCH",
    "PushControlSurface.PUSH_SMALL_KNOB1",
    "PushControlSurface.PUSH_SMALL_KNOB2",
    "PushControlSurface.PUSH_SMALL_KNOB1_TOUCH",
    "PushControlSurface.PUSH_SMALL_KNOB2_TOUCH")

# Scenario: the hardware emulator's IDs must remain aligned with the
# production PushControlSurface constants.
for marker in (
    "PUSH_BUTTON_SHIFT             = 49",
    "PUSH_BUTTON_LEFT              = 44",
    "PUSH_BUTTON_RIGHT             = 45",
    "PUSH_BUTTON_UP                = 46",
    "PUSH_BUTTON_DOWN              = 47",
    "PUSH_BUTTON_DEVICE_LEFT       = 62",
    "PUSH_BUTTON_DEVICE_RIGHT      = 63",
    "PUSH_KNOB1                    = 71",
    "PUSH_KNOB9                    = 79",
    "PUSH_SMALL_KNOB1              = 14",
    "PUSH_SMALL_KNOB2              = 15",
    "PUSH_RIBBON_TOUCH             = 12",
):
    require(surface, marker)

# Scenario: transport LEDs are derived from Bitwig's live playback/record
# state, not merely from the last button press.
require(setup,
    'this.addButton (ButtonID.PLAY, "Play", new PlayCommand<> (this.model, surface), PushControlSurface.PUSH_BUTTON_PLAY, t::isPlaying',
    "t.isLauncherOverdub ()",
    "t.isRecording ()")

# Scenario: output feedback from the extension reaches both LED MIDI and the
# real Push 1 LCD SysEx writer.
require(display, "sendSysex", "Push1DisplayProtocol.encodeLine")
require(surface, "new PadGridImpl (colorManager, output)")

# Keep the 8x8 pad note input and Push 1 display dimensions explicit.
require(setup, '"80????"', '"90????"')
require(display, "Push1DisplayProtocol.encodeLine")

print("Push 1 scenarios passed: mode wiring, Shift chord, encoders/touch, pads, "
      "transport-state LED bindings and LCD/LED MIDI feedback.")
