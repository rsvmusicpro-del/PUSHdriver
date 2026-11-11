#!/usr/bin/env python3
"""Compile and run the isolated Push 1 LCD protocol regression tests."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
protocol = root / "overrides/java/de/mossgrabers/controller/ableton/push/controller/Push1DisplayProtocol.java"
test = root / "tests/java/Push1DisplayProtocolTest.java"

with tempfile.TemporaryDirectory(prefix="pushdriver-display-test-") as output:
    subprocess.run(
        ["javac", "-d", output, str(protocol), str(test)],
        cwd=root, check=True,
    )
    subprocess.run(
        ["java", "-cp", output, "Push1DisplayProtocolTest"],
        cwd=root, check=True,
    )
