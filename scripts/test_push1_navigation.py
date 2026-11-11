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

# Every physical arrow is routed exactly once.
for direction in ("UP", "DOWN", "LEFT", "RIGHT"):
    marker = f"this.addButton (ButtonID.ARROW_{direction},"
    if setup.count(marker) != 1:
        raise SystemExit(f"Expected exactly one physical ARROW_{direction} binding")

# Push 1 contract: no modifier = one item/scene; Shift = one eight-item page.
# This must not vary with Track, Device, Browser, Session or Scene Play mode.
left = method_body(command, "protected void scrollLeft ()")
right = method_body(command, "protected void scrollRight ()")
for body, single, page in (
    (left, "activeMode.selectPreviousItem ();", "activeMode.selectPreviousItemPage ();"),
    (right, "activeMode.selectNextItem ();", "activeMode.selectNextItemPage ();"),
):
    if "this.surface.isShiftPressed ()" not in body or single not in body or page not in body:
        raise SystemExit("Horizontal arrows must use one-item normally and one-page with Shift")
    if body.index("if (this.surface.isShiftPressed ())") > body.index(page):
        raise SystemExit("Shift must select page navigation before the single-item fallback")
    if "Modes.TRACK" in body:
        raise SystemExit("Horizontal Shift semantics must not invert outside Track mode")

states = method_body(command, "protected void updateArrowStates ()")
normalized_states = " ".join(states.split())
for marker in (
    "shiftPressed ? mode.hasPreviousItemPage () : mode.hasPreviousItem ()",
    "shiftPressed ? mode.hasNextItemPage () : mode.hasNextItem ()",
    "final boolean pageNavigation = this.surface.isShiftPressed ();",
    "pageNavigation ? sceneBank.canScrollPageBackwards () : sceneBank.canScrollBackwards ()",
    "pageNavigation ? sceneBank.canScrollPageForwards () : sceneBank.canScrollForwards ()",
):
    if marker not in normalized_states:
        raise SystemExit(f"Arrow LED state does not match Push navigation contract: {marker}")

up = method_body(command, "protected void scrollUp ()")
down = method_body(command, "protected void scrollDown ()")
for body, single, page in (
    (up, "sceneBank.scrollBackwards ();", "sceneBank.selectPreviousPage ();"),
    (down, "sceneBank.scrollForwards ();", "sceneBank.selectNextPage ();"),
):
    if "this.surface.isShiftPressed ()" not in body or single not in body or page not in body:
        raise SystemExit("Vertical arrows must move one scene normally and one page with Shift")
    if "this.isScenePlay ()" in body:
        raise SystemExit("Scene Play must not silently force page navigation without Shift")

if "this.isScenePlay ()" not in method_body(command, "protected ISceneBank getSceneBank ()"):
    raise SystemExit("Scene Play must retain its larger 64-scene navigation bank")
if "this.sceneBank64 = model.getSceneBank (64);" not in command:
    raise SystemExit("Scene Play's Shift page size must remain the intended 64-scene bank")

print("Push 1 directional navigation and arrow LED regression checks passed.")
