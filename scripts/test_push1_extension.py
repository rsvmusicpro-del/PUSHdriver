#!/usr/bin/env python3
"""Load the packaged extension through Bitwig's Java Service Provider interface."""
from pathlib import Path
import os
import subprocess
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
test_sources = (
    root / "tests/java/PushdriverExtensionDiscoveryTest.java",
    root / "tests/java/VirtualPush1MidiBench.java",
    root / "tests/java/VirtualPush1Device.java",
    root / "tests/java/VirtualPush1DeviceTest.java",
    root / "overrides/java/de/mossgrabers/controller/ableton/push/controller/Push1DisplayProtocol.java",
)
with tempfile.TemporaryDirectory(prefix="pushdriver-extension-test-") as output:
    subprocess.run(
        ["javac", "-cp", classpath, "-d", output, *(str(source) for source in test_sources)],
        cwd=root, check=True,
    )
    runtime_classpath = os.pathsep.join((classpath, output))
    subprocess.run(
        ["java", "-cp", runtime_classpath, "PushdriverExtensionDiscoveryTest"],
        cwd=root, check=True,
    )
    subprocess.run(
        ["java", "-cp", runtime_classpath, "VirtualPush1MidiBench"],
        cwd=root, check=True,
    )
    subprocess.run(
        ["java", "-cp", runtime_classpath, "VirtualPush1DeviceTest"],
        cwd=root, check=True,
    )
