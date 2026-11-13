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

    run([sys.executable, "scripts/prepare_push1.py"])
    run(["mvn", "--batch-mode", "--no-transfer-progress", "clean", "package"])
    run([sys.executable, "scripts/verify_push1.py"])
    test_command = [sys.executable, "scripts/test_push1_extension.py"]
    if args.gui:
        test_command.append("--gui")
    run(test_command)

    print("\nAbleton Push 1 virtual test bench completed successfully.", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
