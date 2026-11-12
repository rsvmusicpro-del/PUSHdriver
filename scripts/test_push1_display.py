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
    text_test = root / "tests/java/Push1DisplayTextTest.java"
    display_text = root / "overrides/java/de/mossgrabers/controller/ableton/push/controller/Push1DisplayText.java"
    subprocess.run(
        ["javac", "-d", output, str(display_text), str(text_test)],
        cwd=root, check=True,
    )
    subprocess.run(
        ["java", "-cp", output, "Push1DisplayTextTest"],
        cwd=root, check=True,
    )

    value_formatter = root / "overrides/java/de/mossgrabers/controller/ableton/push/controller/Push1DisplayValueFormatter.java"
    value_test = root / "tests/java/Push1DisplayValueFormatterTest.java"
    subprocess.run(
        ["javac", "-d", output, str(value_formatter), str(value_test)],
        cwd=root, check=True,
    )
    subprocess.run(
        ["java", "-cp", output, "Push1DisplayValueFormatterTest"],
        cwd=root, check=True,
    )

assembled_display = root / "src/main/java/de/mossgrabers/controller/ableton/push/controller/Push1Display.java"
display_source = assembled_display.read_text()
if "Push1DisplayText.fitBlock (value)" not in display_source or "Push1DisplayText.fit (value, 8)" not in display_source:
    raise SystemExit("Push 1 LCD cells are not routed through the bounded text-fitting engine")
if "Push1DisplayValueFormatter.formatValue" not in display_source or "Push1DisplayValueFormatter.formatModulatedValue" not in display_source:
    raise SystemExit("Push 1 LCD value bars are not routed through the bounded value formatter")

# AbstractTextDisplay.setRow requires a complete 68-character row. Scan every
# literal row assignment in the assembled Push controller package so a future
# short warning or status string cannot crash the four-line LCD flush.
import re

push_sources = root / "src/main/java/de/mossgrabers/controller/ableton/push"
row_pattern = re.compile(r'\\.setRow\\s*\\(\\s*\\d+\\s*,\\s*"([^"\\\\]*)"')
for source_path in sorted(push_sources.rglob("*.java")):
    source_text = source_path.read_text()
    for match in row_pattern.finditer(source_text):
        row_text = match.group(1)
        if len(row_text) != 68:
            line_number = source_text.count("\\n", 0, match.start()) + 1
            raise SystemExit(
                f"Push 1 LCD setRow must be exactly 68 characters: "
                f"{source_path}:{line_number} has {len(row_text)}"
            )
