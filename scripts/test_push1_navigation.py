#!/usr/bin/env python3
"""Regression checks for Push 1 directional-button routing and LED state."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COMMAND = ROOT / "src/main/java/de/mossgrabers/controller/ableton/push/command/trigger/PushCursorCommand.java"
SETUP = ROOT / "src/main/java/de/mossgrabers/controller/ableton/push/PushControllerSetup.java"


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


if not COMMAND.is_file() or not SETUP.is_file():
    raise SystemExit("Assembled Push 1 navigation sources are missing")

command = COMMAND.read_text()
setup = SETUP.read_text()

# The hardware arrow IDs must each have exactly one command binding.
for direction in ("UP", "DOWN", "LEFT", "RIGHT"):
    marker = f"this.addButton (ButtonID.ARROW_{direction},"
    if setup.count(marker) != 1:
        raise SystemExit(f"Expected exactly one physical ARROW_{direction} binding")

left = method_body(command, "protected void scrollLeft ()")
right = method_body(command, "protected void scrollRight ()")
for body, action, page_action in (
    (left, "selectPreviousItem ();", "selectPreviousItemPage ();"),
    (right, "selectNextItem ();", "selectNextItemPage ();"),
):
    if "trackMode && !shiftPressed || !trackMode && shiftPressed" not in body:
        raise SystemExit("Horizontal item navigation must use the same Track/Shift rule in every mode")
    if action not in body or page_action not in body:
        raise SystemExit("Horizontal navigation must preserve both item and page actions")

states = method_body(command, "protected void updateArrowStates ()")
if "shiftPressed ? mode.hasPreviousItemPage () : mode.hasPreviousItem ()" not in states:
    raise SystemExit("Left arrow LED state does not match Track-mode navigation granularity")
if "shiftPressed ? mode.hasNextItemPage () : mode.hasNextItem ()" not in states:
    raise SystemExit("Right arrow LED state does not match Track-mode navigation granularity")
if "shiftPressed ? mode.hasPreviousItem () : mode.hasPreviousItemPage ()" not in states:
    raise SystemExit("Left arrow LED state does not match non-Track navigation granularity")
if "shiftPressed ? mode.hasNextItem () : mode.hasNextItemPage ()" not in states:
    raise SystemExit("Right arrow LED state does not match non-Track navigation granularity")

normalized_states = " ".join(states.split())
if "final boolean pageNavigation = this.surface.isShiftPressed () || this.isScenePlay ();" not in normalized_states:
    raise SystemExit("Vertical arrow LED state does not account for Shift and Scene Play page navigation")
if "pageNavigation ? sceneBank.canScrollPageBackwards () : sceneBank.canScrollBackwards ()" not in normalized_states:
    raise SystemExit("Up arrow LED state does not match scene-bank navigation granularity")
if "pageNavigation ? sceneBank.canScrollPageForwards () : sceneBank.canScrollForwards ()" not in normalized_states:
    raise SystemExit("Down arrow LED state does not match scene-bank navigation granularity")

up = method_body(command, "protected void scrollUp ()")
down = method_body(command, "protected void scrollDown ()")
for body, single, page in (
    (up, "sceneBank.scrollBackwards ();", "sceneBank.selectPreviousPage ();"),
    (down, "sceneBank.scrollForwards ();", "sceneBank.selectNextPage ();"),
):
    if "this.surface.isShiftPressed () || this.isScenePlay ()" not in body or single not in body or page not in body:
        raise SystemExit("Vertical arrows must retain single-scene scrolling and Shift/Scene Play page navigation")

print("Push 1 directional navigation and arrow LED regression checks passed.")
