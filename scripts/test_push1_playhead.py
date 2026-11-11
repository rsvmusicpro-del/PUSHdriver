#!/usr/bin/env python3
"""Regression checks for Push 1 sequencer auto-follow and empty-clip safety."""
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


def method_body(source: str, signature: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise SystemExit(f"Required method is missing: {signature}")
    opening = source.find("{", start + len(signature))
    closing = block_end(source, opening)
    return source[opening + 1:closing]


if not SOURCE.is_file():
    raise SystemExit(f"Required assembled source is missing: {SOURCE}")

source = SOURCE.read_text()
draw_grid = method_body(source, "public void drawGrid ()")

for marker in (
    "this.followPlayhead && this.isActive () && this.model.getTransport ().isPlaying ()",
    "clip.doesExist ()",
    "clip.getCurrentStep ()",
    "this.numDisplayCols",
    "clip.getEditPage ()",
    "clip.scrollToPage (playheadPage)",
    "super.drawGrid ();",
):
    if marker not in draw_grid:
        raise SystemExit(f"Push 1 live sequencer follow regression: missing {marker!r}")

guard_start = draw_grid.find("if (clip.doesExist ())")
if guard_start < 0:
    raise SystemExit("Empty-clip existence guard is missing")
guard_open = draw_grid.find("{", guard_start)
guard_close = block_end(draw_grid, guard_open)
guard = draw_grid[guard_open + 1:guard_close]
for operation in (
    "clip.getCurrentStep ()",
    "this.numDisplayCols",
    "clip.getEditPage ()",
    "clip.scrollToPage (playheadPage)",
):
    if operation not in guard:
        raise SystemExit(
            f"Playhead operation must remain inside the real-clip guard: {operation}"
        )

if draw_grid.find("super.drawGrid ();") < guard_close:
    raise SystemExit("Shared grid rendering must remain outside the playhead guard")

page_handler = method_body(source, "protected void handleLoopArea (final int pad, final int velocity)")
for marker in (
    "final int pressedPage = this.loopPadPressed;",
    "super.handleLoopArea (pad, velocity);",
    "if (velocity > 0 || pressedPage < 0)",
    "if (pad != pressedPage)",
    "this.followPlayhead = true;",
    "clip.doesExist ()",
    "clip.getCurrentStep ()",
    "this.numDisplayCols",
    "currentStep / this.numDisplayCols == pad",
):
    if marker not in page_handler:
        raise SystemExit(f"Push 1 page-lock regression: missing {marker!r}")

if page_handler.index("super.handleLoopArea (pad, velocity);") > page_handler.index("if (velocity > 0 || pressedPage < 0)"):
    raise SystemExit("Page gestures must be processed by the shared loop handler first")
if "currentStep / this.numDisplayCols == pad" not in page_handler:
    raise SystemExit("Page lock must use the visible sequencer width, not the clip length")
if "currentStep / stepsPerPage == pad" in page_handler:
    raise SystemExit("Page lock must not divide by the total clip step count")
if page_handler.index("clip.doesExist ()") > page_handler.index("clip.getCurrentStep ()"):
    raise SystemExit("Page-lock logic must check clip existence before reading playhead state")

for signature in (
    "public void onLeft (final ButtonEvent event)",
    "public void onRight (final ButtonEvent event)",
):
    body = method_body(source, signature)
    if "this.followPlayhead = false;" not in body or "super." not in body:
        raise SystemExit(f"Manual page navigation must pause auto-follow: {signature}")

# The step/page animation remains delegated to the common DrivenByMoss renderer.
if "this.getStepColor (stepInfo, x == hiStep" not in source and "super.drawGrid ();" not in draw_grid:
    raise SystemExit("Shared moving step-head renderer is missing")

print("Push 1 sequencer auto-follow, manual page lock, and empty-clip guard checks passed.")
