#!/usr/bin/env python3
"""Run the complete Push 1 extension suite through the virtual hardware bench."""
from pathlib import Path
import os
import re
import subprocess
import sys
import tempfile

from push1_failure_shrinker import minimal_failing_prefix

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
    root / "tests/java/VirtualPush1MidiBench.java",
    root / "tests/java/VirtualPush1MidiConcurrencyTest.java",
    root / "tests/java/AbstractHwButtonSafetyTest.java",
    root / "tests/java/VirtualPush1Device.java",
    root / "tests/java/VirtualPush1DeviceTest.java",
    root / "tests/java/VirtualPush1DisplayLayoutTest.java",
    root / "tests/java/VirtualPush1ProtocolExhaustiveTest.java",
    root / "tests/java/VirtualPush1FirmwareVersionTest.java",
    root / "tests/java/VirtualPush1DisplayValueFormatterTest.java",
    root / "tests/java/VirtualPush1DisplayTextTest.java",
    root / "tests/java/VirtualPush1RuntimeTest.java",
    root / "tests/java/SequencerEngineTest.java",
    root / "overrides/java/de/mossgrabers/controller/ableton/push/engine/Push1PadGrid.java",
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
    command = ["java"]
    fuzz_seed = next(
        (argument.split("=", 1)[1] for argument in sys.argv[1:] if argument.startswith("--fuzz-seed=")),
        None,
    )
    if fuzz_seed is not None:
        command.append(f"-Dpush1.fuzz.seed={fuzz_seed}")
    fuzz_view = next(
        (argument.split("=", 1)[1] for argument in sys.argv[1:] if argument.startswith("--fuzz-view=")),
        None,
    )
    if fuzz_view is not None:
        command.append(f"-Dpush1.fuzz.view={fuzz_view}")
    for option, property_name in (("--fuzz-actions=", "push1.fuzz.actions"),
                                  ("--fuzz-seeds=", "push1.fuzz.seeds")):
        value = next((argument.split("=", 1)[1] for argument in sys.argv[1:]
                      if argument.startswith(option)), None)
        if value is not None:
            if not value.isdigit():
                raise SystemExit(f"{option[:-1]} must be a positive integer")
            command.append(f"-D{property_name}={value}")
    subprocess.run(["java", "-cp", runtime_classpath, "AbstractHwButtonSafetyTest"], cwd=root, check=True)
    subprocess.run(["java", "-cp", runtime_classpath, "VirtualPush1MidiConcurrencyTest"], cwd=root, check=True)
    subprocess.run(["java", "-cp", runtime_classpath, "SequencerEngineTest"], cwd=root, check=True)
    command.extend(["-cp", runtime_classpath, "VirtualPush1RuntimeTest"])
    if "--gui" in sys.argv:
        command.append("--gui")
    fuzz_actions = int(next(
        (argument.split("=", 1)[1] for argument in sys.argv[1:] if argument.startswith("--fuzz-actions=")),
        "256",
    ))
    completed = subprocess.run(command, cwd=root, capture_output=True, text=True)
    print(completed.stdout, end="")
    print(completed.stderr, end="", file=sys.stderr)
    if completed.returncode != 0:
        failure_text = completed.stdout + "\n" + completed.stderr
        failure_match = re.search(
            r"Generated Push 1 scenario failed: seed=(-?\d+), view=([A-Z0-9_]+)",
            failure_text,
        )
        shrink_disabled = "--no-shrink-failures" in sys.argv
        if failure_match is not None and not shrink_disabled and fuzz_view is None:
            seed, view = failure_match.group(1), failure_match.group(2)
            print(f"\nReducing generated failure: seed={seed}, view={view}, upper bound={fuzz_actions} actions.", flush=True)

            def reproduces(action_count: int) -> bool:
                replay = [
                    "java", f"-Dpush1.fuzz.seed={seed}", f"-Dpush1.fuzz.view={view}",
                    f"-Dpush1.fuzz.actions={action_count}", "-cp", runtime_classpath,
                    "VirtualPush1RuntimeTest",
                ]
                result = subprocess.run(replay, cwd=root, capture_output=True, text=True)
                combined = result.stdout + "\n" + result.stderr
                return result.returncode != 0 and re.search(
                    rf"Generated Push 1 scenario failed: seed={re.escape(seed)}, view={re.escape(view)}",
                    combined,
                ) is not None

            minimum = minimal_failing_prefix(fuzz_actions, reproduces)
            if minimum is not None:
                boundary_confirmed = reproduces(minimum) and (minimum == 1 or not reproduces(minimum - 1))
                if boundary_confirmed:
                    report = root / "target/push1-failures" / f"seed-{seed}-view-{view}.txt"
                    minimized = report.with_name(report.stem + "-prefix-minimized.txt")
                    original_lines = report.read_text(encoding="utf-8").splitlines() if report.is_file() else []
                    minimized.write_text(
                        "\n".join([
                            f"seed={seed}", f"view={view}", f"minimal_failing_prefix={minimum}",
                            "reduction=shortest reproducible action prefix; arbitrary action deletion is not claimed",
                            "trace:", *original_lines[4:],
                        ]) + "\n",
                        encoding="utf-8",
                    )
                    print(f"Shortest failing prefix confirmed: {minimum} actions. Report: {minimized}", flush=True)
                else:
                    print("Prefix reduction could not confirm a stable failing/pass boundary; original trace retained.", flush=True)
        raise SystemExit(completed.returncode)
