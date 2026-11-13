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

    layout = root / "overrides/java/de/mossgrabers/controller/ableton/push/controller/Push1DisplayLayout.java"
    virtual_device = root / "tests/java/VirtualPush1Device.java"
    layout_test = root / "tests/java/VirtualPush1DisplayLayoutTest.java"
    subprocess.run(
        ["javac", "-cp", output, "-d", output, str(layout), str(virtual_device), str(layout_test)],
        cwd=root, check=True,
    )
    subprocess.run(
        ["java", "-cp", output, "VirtualPush1DisplayLayoutTest"],
        cwd=root, check=True,
    )

assembled_display = root / "src/main/java/de/mossgrabers/controller/ableton/push/controller/Push1Display.java"
display_source = assembled_display.read_text()
if "Push1DisplayLayout.fitBlock (value)" not in display_source or "Push1DisplayLayout.fitCell (value, cell)" not in display_source:
    raise SystemExit("Push 1 LCD cells are not routed through the bounded text-fitting engine")
if "Push1DisplayLayout.fitRow (text)" not in display_source:
    raise SystemExit("Push 1 LCD rows must be bounded to 68 cells before SysEx encoding")
if "Push1DisplayLayout.ROWS" not in display_source or "Push1DisplayLayout.COLUMNS" not in display_source:
    raise SystemExit("Push1Display must use the shared physical four-module LCD geometry")
if "Push1DisplayLayout.firstCellForBlock" not in display_source or "Push1DisplayLayout.fitCell" not in display_source:
    raise SystemExit("Push1Display logical blocks/cells are not routed through the shared geometry")
if "Push1DisplayLayout.MODULE_COUNT" not in display_source or "Push1DisplayLayout.MODULE_COLUMNS" not in display_source:
    raise SystemExit("Push1Display simulator view is not segmented into four 17-character modules")
if "Push1DisplayValueFormatter.formatValue" not in display_source or "Push1DisplayValueFormatter.formatModulatedValue" not in display_source:
    raise SystemExit("Push 1 LCD value bars are not routed through the bounded value formatter")
if "Push1DisplayValueFormatter.formatPan" not in display_source:
    raise SystemExit("Push 1 LCD pan bars are not routed through the bounded pan formatter")
if "Push1DisplayText.overlayRightBlock" not in display_source or "message.codePointCount" not in display_source or "compactNotification" not in display_source:
    raise SystemExit("Short Push 1 notifications must use the bounded right-side LCD overlay")

# AbstractTextDisplay.setRow requires a complete 68-character row. Scan every
# literal row assignment in the assembled Push controller package so a future
# short warning or status string cannot crash the four-line LCD flush.
import re

push_sources = root / "src/main/java/de/mossgrabers/controller/ableton/push"
row_pattern = re.compile(r'\.setRow\s*\(\s*\d+\s*,\s*"([^"\\]*)"')
for source_path in sorted(push_sources.rglob("*.java")):
    source_text = source_path.read_text()
    for match in row_pattern.finditer(source_text):
        row_text = match.group(1)
        if len(row_text) != 68:
            line_number = source_text.count("\n", 0, match.start()) + 1
            raise SystemExit(
                f"Push 1 LCD setRow must be exactly 68 characters: "
                f"{source_path}:{line_number} has {len(row_text)}"
            )
