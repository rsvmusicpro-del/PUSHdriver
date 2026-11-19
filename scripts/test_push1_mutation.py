#!/usr/bin/env python3
"""Prove the empty-clip runtime regression detects an injected Java defect."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/java/de/mossgrabers/framework/view/sequencer/AbstractNoteSequencerView.java"
GUARD = (
    "        if (!clip.doesExist ())\n"
    "        {\n"
    "            gridPad.turnOff ();\n"
    "            return;\n"
    "        }\n"
    "        final int step = clip.getCurrentStep ();"
)
MUTANT = "        final int step = clip.getCurrentStep ();"
EXPECTED_FAILURE = "Classic Sequencer queried the playhead while the selected slot had no clip"


def run(command, *, capture=False):
    result = subprocess.run(command, cwd=ROOT, text=True, capture_output=capture)
    if result.returncode != 0 and not capture:
        raise subprocess.CalledProcessError(result.returncode, command)
    return result


def main() -> int:
    if not SOURCE.is_file():
        print(f"Assembled source is missing: {SOURCE}", file=sys.stderr)
        return 2
    original = SOURCE.read_text(encoding="utf-8")
    if original.count(GUARD) != 1:
        print("Expected exactly one shared empty-clip playhead guard to mutate.", file=sys.stderr)
        return 2

    try:
        SOURCE.write_text(original.replace(GUARD, MUTANT, 1), encoding="utf-8")
        run(["mvn", "-q", "-DskipTests", "package"])
        result = run([
            "python3", "scripts/test_push1_extension.py",
            "--fuzz-actions=64", "--fuzz-seeds=4", "--no-shrink-failures",
        ], capture=True)
        combined = result.stdout + "\n" + result.stderr
        if result.returncode == 0 or EXPECTED_FAILURE not in combined:
            print(combined, file=sys.stderr)
            print("Mutation survived: the empty-clip regression did not detect the injected playhead read.", file=sys.stderr)
            return 1
        print("Mutation killed: removing the production empty-clip guard triggers the expected runtime regression.")
        return 0
    finally:
        SOURCE.write_text(original, encoding="utf-8")
        run(["mvn", "-q", "-DskipTests", "package"])


if __name__ == "__main__":
    raise SystemExit(main())
