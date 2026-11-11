#!/usr/bin/env python3
"""Compile and run Push 1 firmware inquiry regression tests."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/java/de/mossgrabers/framework/daw/midi/DeviceInquiry.java"
HELPER = ROOT / "overrides/java/de/mossgrabers/controller/ableton/push/controller/Push1FirmwareVersion.java"
TEST = ROOT / "tests/java/Push1FirmwareVersionTest.java"

with tempfile.TemporaryDirectory(prefix="pushdriver-firmware-test-") as output:
    subprocess.run(
        ["javac", "-d", output, str(SOURCE), str(HELPER), str(TEST)],
        cwd=ROOT, check=True,
    )
    subprocess.run(
        ["java", "-cp", output, "Push1FirmwareVersionTest"],
        cwd=ROOT, check=True,
    )
