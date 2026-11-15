#!/usr/bin/env python3
"""Guard engine boundaries, lifecycle ownership and the single Push 1 pad map."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
OVERRIDES = ROOT / "overrides/java/de/mossgrabers/controller/ableton/push"
ENGINE_ROOT = OVERRIDES / "engine"
SEQUENCER_ROOT = ENGINE_ROOT / "sequencer"

FORBIDDEN_ENGINE_IMPORTS = (
    "de.mossgrabers.framework.",
    "de.mossgrabers.controller.ableton.push.controller.",
    "de.mossgrabers.controller.ableton.push.view.",
    "de.mossgrabers.controller.ableton.push.mode.",
    "javax.swing.",
    "java.awt.",
)
FORBIDDEN_ENGINE_APIS = (
    "getClip (", "getModel (", "getButton (", "getPadGrid (",
    "lightEx (", "sendMidi", "INoteClip", "IHwButton",
    "PushControlSurface", "ButtonID",
)


def main():
    errors = []
    grid = ENGINE_ROOT / "Push1PadGrid.java"
    base = SEQUENCER_ROOT / "AbstractSequencerEngine.java"
    classic = SEQUENCER_ROOT / "ClassicSequencerEngine.java"
    poly = SEQUENCER_ROOT / "PolySequencerEngine.java"
    interface = SEQUENCER_ROOT / "SequencerEngine.java"
    classic_view = OVERRIDES / "view/SequencerView.java"
    poly_view = OVERRIDES / "view/PolySequencerView.java"

    required = (grid, base, classic, poly, interface, classic_view, poly_view)
    for path in required:
        if not path.is_file():
            errors.append(f"Required architecture source is missing: {path.relative_to(ROOT)}")
    if errors:
        return report(errors)

    engine_files = (grid, base, classic, poly, interface)
    for path in engine_files:
        source = path.read_text(encoding="utf-8")
        for forbidden in FORBIDDEN_ENGINE_IMPORTS:
            if f"import {forbidden}" in source:
                errors.append(f"Engine imports framework/UI boundary {forbidden}: {path.relative_to(ROOT)}")
        for forbidden in FORBIDDEN_ENGINE_APIS:
            if forbidden in source:
                errors.append(f"Engine calls hardware/DAW API {forbidden.strip()}: {path.relative_to(ROOT)}")

    grid_source = grid.read_text(encoding="utf-8")
    for declaration in (
        r"FIRST_NOTE\s*=\s*36",
        r"PAD_COUNT\s*=\s*64",
        r"COLUMNS\s*=\s*8",
        r"ROWS\s*=\s*8",
    ):
        if not re.search(declaration, grid_source):
            errors.append(f"Push 1 grid contract missing declaration: {declaration}")

    for path in (classic_view, poly_view):
        source = path.read_text(encoding="utf-8")
        if "Push1PadGrid.indexForNote (note)" not in source:
            errors.append(f"View bypasses shared Push pad-note validation: {path.relative_to(ROOT)}")
        if re.search(r"\bnote\s*-\s*36\b", source):
            errors.append(f"View duplicates the Push pad MIDI offset: {path.relative_to(ROOT)}")

    lifecycle = base.read_text(encoding="utf-8")
    for contract in ("Math.incrementExact (this.generation)", "this.onDeactivated ();",
                     "token.generation () == this.generation"):
        if contract not in lifecycle:
            errors.append(f"Engine lifecycle contract missing: {contract}")

    if "this.followPlayhead = true;" not in classic.read_text(encoding="utf-8"):
        errors.append("Classic engine must reset transient page-follow state on deactivation.")

    return report(errors)


def report(errors):
    if errors:
        for error in errors:
            print("ENGINE ARCHITECTURE ERROR: " + error, file=sys.stderr)
        return 1
    print("Engine architecture passed: isolated policy, shared Push 1 grid, lifecycle invalidation and validated view boundaries.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
