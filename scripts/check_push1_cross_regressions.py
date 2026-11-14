#!/usr/bin/env python3
"""Detect cross-row Push 1 LED command collisions in every overridden mode."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
COLOR_MANAGER = (
    ROOT / "src/main/java/de/mossgrabers/controller/ableton/push/controller/PushColorManager.java"
)
OVERRIDES = ROOT / "overrides/java"

CONSTANT_RE = re.compile(
    r"public\s+static\s+final\s+int\s+([A-Z0-9_]+)\s*=\s*(\d+)\s*;"
)
METHOD_RE = re.compile(r"\bgetButtonColor\s*\([^)]*\)\s*\{")
ROW_ONE_RE = re.compile(r"\bisButtonRow\s*\(\s*0\s*,\s*buttonID\s*\)")
COLOR2_RE = re.compile(r"PushColorManager\.(PUSH1_COLOR2_[A-Z0-9_]+)")


def braced_block(source: str, opening: int) -> str:
    """Return a Java brace block, accounting for nested blocks."""
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    raise ValueError("Unbalanced Java braces while scanning LED method")


def constants_by_name(source: str) -> dict[str, int]:
    return {name: int(value) for name, value in CONSTANT_RE.findall(source)}


def scan_mode(path: Path, blink_ids: dict[int, list[str]], constants: dict[str, int]) -> tuple[int, list[str]]:
    source = path.read_text(encoding="utf-8")
    findings: list[str] = []
    checked = 0
    for method in METHOD_RE.finditer(source):
        body = braced_block(source, method.end() - 1)
        for row in ROW_ONE_RE.finditer(body):
            block_open = body.find("{", row.end())
            if block_open < 0:
                continue
            row_block = braced_block(body, block_open)
            checked += 1
            for name in sorted(set(COLOR2_RE.findall(row_block))):
                value = constants.get(name)
                if value is None:
                    findings.append(f"{path.relative_to(ROOT)}: unknown LED constant {name}")
                    continue
                collisions = blink_ids.get(value, [])
                if collisions:
                    findings.append(
                        f"{path.relative_to(ROOT)}: first-row LED uses {name}={value}, "
                        f"which aliases first-row blink command(s): {', '.join(collisions)}"
                    )
    return checked, findings


def main() -> int:
    if not COLOR_MANAGER.is_file():
        raise SystemExit(
            "PushColorManager.java is missing; run scripts/prepare_push1.py before this check."
        )
    if not OVERRIDES.is_dir():
        raise SystemExit(f"Override source directory is missing: {OVERRIDES}")

    constants = constants_by_name(COLOR_MANAGER.read_text(encoding="utf-8"))
    blink_ids: dict[int, list[str]] = {}
    for name, value in constants.items():
        if name.startswith("PUSH1_COLOR_") and name.endswith(("_SBLINK", "_FBLINK")):
            blink_ids.setdefault(value, []).append(name)
    if not blink_ids:
        raise SystemExit("Could not identify Push 1 first-row blink command constants.")

    files = sorted(OVERRIDES.rglob("*.java"))
    checked = 0
    findings: list[str] = []
    for path in files:
        count, errors = scan_mode(path, blink_ids, constants)
        checked += count
        findings.extend(errors)

    if findings:
        print("Push 1 cross-row LED regression(s) detected:", file=sys.stderr)
        for finding in findings:
            print(f" - {finding}", file=sys.stderr)
        return 1

    print(
        f"Push 1 cross-regression guard passed: {checked} first-row LED branch(es) "
        f"checked across {len(files)} override Java file(s); no blink-command aliases found."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
