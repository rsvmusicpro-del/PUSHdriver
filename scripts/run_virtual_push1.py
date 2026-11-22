#!/usr/bin/env python3
"""Build and run the complete Ableton Push 1 virtual test bench."""
from pathlib import Path
import argparse
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def run(command: list[str]) -> None:
    print("\n$", " ".join(command), flush=True)
    subprocess.run(command, cwd=ROOT, check=True)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Assemble, build, verify and run the PUSHdriver virtual Push 1 bench."
    )
    parser.add_argument(
        "--gui",
        action="store_true",
        help="Open the Swing virtual Push 1 after the automated tests pass.",
    )
    parser.add_argument(
        "--lcd-rom",
        choices=("A00", "A02"),
        default="A00",
        help="HD44780U character ROM profile used by the simulator (default: A00).",
    )
    parser.add_argument(
        "--fuzz-actions",
        type=int,
        help="Number of generated actions per view/seed scenario (default: 256).",
    )
    parser.add_argument(
        "--fuzz-seeds",
        type=int,
        help="Number of rotating deterministic seeds (default: 16, plus regression corpus).",
    )
    parser.add_argument(
        "--fuzz-seed",
        help="Replay one generated interaction seed (decimal or 0x-prefixed hexadecimal).",
    )
    parser.add_argument(
        "--fuzz-view",
        help="Replay generated actions on one named Push view (for example SEQUENCER).",
    )
    parser.add_argument(
        "--no-shrink-failures",
        action="store_true",
        help="Disable automatic shortest-failing-prefix reduction after a generated failure.",
    )
    args = parser.parse_args()

    if shutil.which("java") is None:
        raise SystemExit("Java 17 is required. Install a JDK 17 and ensure java is on PATH.")
    if shutil.which("mvn") is None:
        raise SystemExit("Apache Maven is required. Install Maven and ensure mvn is on PATH.")

    java = subprocess.run(
        ["java", "-version"], cwd=ROOT, capture_output=True, text=True, check=True
    )
    java_version = java.stderr + java.stdout
    match = re.search(r'version "(\d+)', java_version)
    if match is None or int(match.group(1)) != 17:
        raise SystemExit(
            "This bench is validated with Java 17. Detected Java output:\n" + java_version
        )

    run([sys.executable, "-m", "unittest", "discover", "-s", "tests/python", "-v"])
    run([sys.executable, "scripts/check_push1_led_safety.py"])
    run([sys.executable, "scripts/prepare_push1.py"])
    run([sys.executable, "scripts/check_push1_color_palette.py"])
    run([sys.executable, "scripts/check_source_ownership.py"])
    run([sys.executable, "scripts/check_engine_architecture.py"])
    run([sys.executable, "scripts/check_push1_menu_row_contract.py"])
    run(["mvn", "--batch-mode", "--no-transfer-progress", "clean", "package"])
    run([sys.executable, "scripts/verify_push1.py"])
    test_command = [sys.executable, "scripts/test_push1_extension.py"]
    if args.gui:
        test_command.append("--gui")
    test_command.append(f"--lcd-rom={args.lcd_rom}")
    if args.fuzz_seed is not None:
        test_command.append(f"--fuzz-seed={args.fuzz_seed}")
    if args.fuzz_view is not None:
        test_command.append(f"--fuzz-view={args.fuzz_view}")
    if args.no_shrink_failures:
        test_command.append("--no-shrink-failures")
    if args.fuzz_actions is not None:
        test_command.append(f"--fuzz-actions={args.fuzz_actions}")
    if args.fuzz_seeds is not None:
        test_command.append(f"--fuzz-seeds={args.fuzz_seeds}")
    run(test_command)
    run([sys.executable, "scripts/test_push1_mutation.py"])

    print("\nAbleton Push 1 virtual test bench and mutation gate completed successfully.", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
