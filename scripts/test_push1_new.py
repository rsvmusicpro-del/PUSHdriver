#!/usr/bin/env python3
"""Regression checks for the Push 1 New button and its Bitwig clip workflow."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SETUP = ROOT / "src/main/java/de/mossgrabers/controller/ableton/push/PushControllerSetup.java"
COMMAND = ROOT / "src/main/java/de/mossgrabers/controller/ableton/push/command/trigger/PushNewCommand.java"
UPSTREAM_COMMAND = ROOT / "src/main/java/de/mossgrabers/framework/command/trigger/clip/NewCommand.java"


def method_body(source: str, signature: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise SystemExit(f"Missing required method: {signature}")
    opening = source.find("{", start + len(signature))
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    raise SystemExit(f"Unbalanced method body: {signature}")


for path in (SETUP, COMMAND, UPSTREAM_COMMAND):
    if not path.is_file():
        raise SystemExit(f"Required New workflow source is missing: {path}")

setup = SETUP.read_text()
command = COMMAND.read_text()
upstream = UPSTREAM_COMMAND.read_text()

# New owns one dedicated physical button binding; it must not alias Record,
# Duplicate, Play, or any other Push control.
new_binding = (
    'this.addButton (ButtonID.NEW, "New", new PushNewCommand (this.model, surface), '
    'PushControlSurface.PUSH_BUTTON_NEW);'
)
if setup.count(new_binding) != 1 or setup.count("this.addButton (ButtonID.NEW") != 1:
    raise SystemExit("Push 1 New must have exactly one dedicated PushNewCommand binding")
if setup.count("PushControlSurface.PUSH_BUTTON_NEW") != 1:
    raise SystemExit("Push 1 New hardware MIDI identifier is duplicated or missing")
for button, command_name in (
    ("RECORD", "RecordCommand"),
    ("DUPLICATE", "DuplicateCommand"),
    ("PLAY", "PlayCommand"),
):
    if setup.count(f"this.addButton (ButtonID.{button},") != 1:
        raise SystemExit(f"Existing {button} button binding was changed or duplicated")
    if command_name not in setup:
        raise SystemExit(f"Existing {button} command is no longer registered")

if "extends NewCommand<PushControlSurface, PushConfiguration>" not in command:
    raise SystemExit("PushNewCommand must preserve the upstream NewCommand contract")
if "public void executeNormal" in command or "public void executeShifted" in command:
    raise SystemExit("PushNewCommand must preserve upstream normal/Shift event semantics")

normal = method_body(upstream, "public void executeNormal (final ButtonEvent event)")
shifted = method_body(upstream, "public void executeShifted (final ButtonEvent event)")
if "this.handleExecute (true);" not in normal or "this.handleExecute (false);" not in shifted:
    raise SystemExit("Normal New and Shift+New overdub behavior must remain unchanged")

execute = method_body(command, "public void handleExecute (final boolean enableOverdub)")
slot_lookup = execute.find("slotBank.getEmptySlot (slotIndex)")
empty_guard = execute.find("if (slot.isEmpty ())")
selected_playing = execute.find("selectedSlot.isPresent () && selectedSlot.get ().isPlaying ()")
stop_guard = execute.find("if (selectedClipIsPlaying)")
stop = execute.find("cursorTrack.stop ();")
create = execute.find("this.model.createNoteClip (cursorTrack, slot.get (), this.getClipLength (), enableOverdub);")
if min(slot_lookup, selected_playing, empty_guard, stop_guard, stop, create) < 0 or not selected_playing < slot_lookup < empty_guard < stop_guard < stop < create:
    raise SystemExit("New must verify a free slot and a playing selected clip before stopping playback")
if execute.count("cursorTrack.stop ();") != 1 or execute.count("createNoteClip") != 1:
    raise SystemExit("New must stop only a selected playing Launcher clip and create exactly one replacement clip")
if "super.handleExecute (enableOverdub);" not in execute:
    raise SystemExit("Missing-track and no-free-slot notifications must remain delegated upstream")

print("Push 1 New button routing and clip workflow regression checks passed.")
