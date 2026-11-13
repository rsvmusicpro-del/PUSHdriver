#!/usr/bin/env python3
"""Run the complete Push 1 extension suite through the virtual hardware bench."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
extension = root / "target/PUSHdriver-19.2.2.jar"
api_candidates = (
    Path.home() / ".m2/repository/com/bitwig/extension-api/17/extension-api-17.jar",
    root / "maven-local-repository/com/bitwig/extension-api/17/extension-api-17.jar",
)
api = next((candidate for candidate in api_candidates if candidate.is_file()), None)
if not extension.is_file() or extension.stat().st_size == 0:
    raise SystemExit(f"Built extension is missing or empty: {extension}")
if api is None:
    raise SystemExit("Bitwig Extension API 17 JAR was not found in Maven's local repositories")

classpath = os.pathsep.join((str(extension), str(api)))

subprocess.run([sys.executable, str(root / "scripts/test_menu_button_alignment.py")], cwd=root, check=True)
test_sources = (
    root / "tests/java/VirtualPush1MidiBench.java",
    root / "tests/java/VirtualPush1Device.java",
    root / "tests/java/VirtualPush1DeviceTest.java",
    root / "tests/java/VirtualPush1DisplayLayoutTest.java",
    root / "tests/java/VirtualPush1ProtocolExhaustiveTest.java",
    root / "tests/java/VirtualPush1FirmwareVersionTest.java",
    root / "tests/java/VirtualPush1DisplayValueFormatterTest.java",
    root / "tests/java/VirtualPush1DisplayTextTest.java",
    root / "tests/java/VirtualPush1RuntimeTest.java",
    root / "tests/java/VirtualPush1Simulator.java",
    root / "overrides/java/de/mossgrabers/controller/ableton/push/controller/Push1DisplayProtocol.java",
    root / "overrides/java/de/mossgrabers/controller/ableton/push/controller/Push1DisplayLayout.java",
    root / "overrides/java/de/mossgrabers/controller/ableton/push/controller/Push1DisplayGlyphs.java",
)
with tempfile.TemporaryDirectory(prefix="pushdriver-virtual-bench-") as output:
    subprocess.run(
        ["javac", "-cp", classpath, "-d", output, *(str(source) for source in test_sources)],
        cwd=root, check=True,
    )
    runtime_classpath = os.pathsep.join((classpath, output))
    command = ["java", "-cp", runtime_classpath, "VirtualPush1RuntimeTest"]
    if "--gui" in sys.argv:
        command.append("--gui")
    subprocess.run(command, cwd=root, check=True)
