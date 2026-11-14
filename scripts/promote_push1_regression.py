#!/usr/bin/env python3
"""Promote a reproducible generated Push 1 failure seed into the permanent corpus."""
from __future__ import annotations

import argparse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORPUS = ROOT / "tests/scenarios/fuzz-seeds.txt"
LONG_MIN = -(2**63)
LONG_MAX = 2**63 - 1


def parse_seed(value: str) -> int:
    try:
        seed = int(value, 0)
    except ValueError as error:
        raise argparse.ArgumentTypeError(
            "seed must be a decimal or 0x-prefixed integer"
        ) from error
    if not LONG_MIN <= seed <= LONG_MAX:
        raise argparse.ArgumentTypeError("seed must fit in a signed Java long")
    return seed


def promote(seed: int, reason: str, corpus: Path = CORPUS) -> bool:
    """Append one unique seed; return False when it is already present."""
    lines = corpus.read_text(encoding="utf-8").splitlines() if corpus.exists() else []
    for line in lines:
        token = line.split("#", 1)[0].strip()
        if token and int(token, 0) == seed:
            return False
    corpus.parent.mkdir(parents=True, exist_ok=True)
    if lines and lines[-1].strip():
        lines.append("")
    safe_reason = " ".join(reason.split()).replace("\n", " ")
    lines.extend([f"# {safe_reason}" if safe_reason else "# Promoted regression", str(seed)])
    corpus.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("seed", type=parse_seed, help="seed printed by a failing generated scenario")
    parser.add_argument("--reason", default="Generated Push 1 regression", help="short defect description")
    args = parser.parse_args()
    added = promote(args.seed, args.reason)
    if added:
        print(f"Promoted seed {args.seed} to {CORPUS.relative_to(ROOT)}; it now runs in every default bench execution.")
    else:
        print(f"Seed {args.seed} is already present in {CORPUS.relative_to(ROOT)}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
