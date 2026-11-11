#!/usr/bin/env python3
"""Behavior-boundary regression check for the Push 1 empty-clip playhead guard."""
from pathlib import Path

SOURCE = Path(
    "src/main/java/de/mossgrabers/controller/ableton/push/view/SequencerView.java"
)


def block_end(source: str, opening: int) -> int:
    if opening < 0 or source[opening] != "{":
        raise SystemExit("Expected Java block opening brace")
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return index
    raise SystemExit("Unbalanced Java block")


if not SOURCE.is_file():
    raise SystemExit(f"Required assembled source is missing: {SOURCE}")

source = SOURCE.read_text()
signature = "public void drawGrid ()"
method_start = source.find(signature)
if method_start < 0:
    raise SystemExit("Push 1 drawGrid override is missing")
method_open = source.find("{", method_start + len(signature))
method_close = block_end(source, method_open)
method = source[method_open + 1:method_close]

guard_start = method.find("if (clip.doesExist ())")
if guard_start < 0:
    raise SystemExit("Empty-clip existence guard is missing")
guard_open = method.find("{", guard_start)
guard_close = block_end(method, guard_open)
guard = method[guard_open + 1:guard_close]

for operation in (
    "clip.getNumSteps ()",
    "clip.getCurrentStep ()",
    "clip.getEditPage ()",
    "clip.scrollToPage (playheadPage)",
):
    if operation not in guard:
        raise SystemExit(
            f"Playhead operation must remain inside the real-clip guard: {operation}"
        )

super_draw = method.find("super.drawGrid ();")
if super_draw < 0:
    raise SystemExit("Shared sequencer renderer call is missing")
if super_draw < guard_close:
    raise SystemExit("Shared grid rendering must remain outside the playhead guard")

print("Push 1 empty-clip playhead guard regression check passed.")
