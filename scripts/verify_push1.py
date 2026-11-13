#!/usr/bin/env python3
"""Fail the build if the package contains anything outside the Push 1 scope."""
from pathlib import Path
import os
import re
import subprocess
from zipfile import ZipFile

jar = Path("target/PUSHdriver-19.2.2.jar")
if not jar.is_file() or jar.stat().st_size == 0:
    raise SystemExit("PUSHdriver JAR is missing or empty")

pom = Path("pom.xml").read_text()
if not re.search(r"<artifactId>extension-api</artifactId>\s*<version>17</version>", pom):
    raise SystemExit("Build is not pinned to Bitwig Extension API 17")

with ZipFile(jar) as archive:
    entries = archive.namelist()
    if len(entries) != len(set(entries)):
        raise SystemExit("The extension archive contains duplicate ZIP entries")
    classes = [p for p in entries if p.endswith(".class")]
    too_new = [p for p in classes if int.from_bytes(archive.read(p)[6:8], "big") > 61]
    if too_new:
        raise SystemExit(f"Classes require a Java version newer than Java 17: {too_new}")
    bitwig_classes = sorted(p for p in classes if p.startswith("de/mossgrabers/bitwig/controller/"))
    expected = ["de/mossgrabers/bitwig/controller/ableton/push/Push1ControllerExtensionDefinition.class"]
    if bitwig_classes != expected:
        raise SystemExit(f"Unexpected Bitwig controller entry points: {bitwig_classes}")

    # Every Java source assembled by prepare_push1.py must produce a class in the
    # extension. This prevents silent source omissions across shared framework,
    # Bitwig adapters, Push views, modes, commands and controller implementations.
    source_root = Path("src/main/java")
    missing_sources = []
    for source in sorted(source_root.rglob("*.java")):
        relative = source.relative_to(source_root).with_suffix(".class").as_posix()
        if relative not in entries:
            missing_sources.append((source.as_posix(), relative))
    if missing_sources:
        raise SystemExit(f"Java sources missing from the compiled extension: {missing_sources}")

    required_push1_classes = (
        "de/mossgrabers/controller/ableton/push/controller/Push1Display.class",
        "de/mossgrabers/controller/ableton/push/controller/Push1DisplayLayout.class",
        "de/mossgrabers/controller/ableton/push/controller/Push1DisplayGlyphs.class",
        "de/mossgrabers/controller/ableton/push/controller/PushColorManager.class",
        "de/mossgrabers/controller/ableton/push/controller/PushControlSurface.class",
        "de/mossgrabers/controller/ableton/push/PushControllerSetup.class",
        "de/mossgrabers/controller/ableton/push/PushConfiguration.class",
        "de/mossgrabers/controller/ableton/push/view/SessionView.class",
        "de/mossgrabers/framework/view/sequencer/ClipLengthView.class",
        "de/mossgrabers/controller/ableton/push/view/PlayView.class",
        "de/mossgrabers/controller/ableton/push/view/PianoView.class",
        "de/mossgrabers/controller/ableton/push/view/ChordsView.class",
        "de/mossgrabers/controller/ableton/push/view/Drum4View.class",
        "de/mossgrabers/controller/ableton/push/view/Drum8View.class",
        "de/mossgrabers/controller/ableton/push/view/Drum64View.class",
        "de/mossgrabers/controller/ableton/push/view/SequencerView.class",
        "de/mossgrabers/controller/ableton/push/view/PolySequencerView.class",
        "de/mossgrabers/controller/ableton/push/mode/device/DeviceBrowserMode.class",
        "de/mossgrabers/controller/ableton/push/mode/device/DeviceParamsMode.class",
        "de/mossgrabers/controller/ableton/push/mode/ScalesMode.class",
        "de/mossgrabers/controller/ableton/push/mode/NoteRepeatMode.class",
    )
    note_repeat_source = Path("src/main/java/de/mossgrabers/controller/ableton/push/mode/NoteRepeatMode.java").read_text()
    button_color_start = note_repeat_source.index("public int getButtonColor")
    button_color_end = note_repeat_source.index("public void updateDisplay1", button_color_start)
    button_color_source = note_repeat_source[button_color_start:button_color_end]
    if "this.refreshNoteRepeat ();" not in button_color_source or "if (this.noteRepeat == null)" not in button_color_source:
        raise SystemExit("NoteRepeat LED rendering is not safe when the MIDI input is unavailable")

    missing = [path for path in required_push1_classes if path not in entries]
    if missing:
        raise SystemExit(f"Previously supported Push 1 functionality is missing: {missing}")

    forbidden_prefixes = (
        "de/mossgrabers/controller/ni/",
        "de/mossgrabers/controller/akai/",
        "de/mossgrabers/controller/novation/",
        "de/mossgrabers/controller/faderfox/",
        "de/mossgrabers/controller/livid/",
        "de/mossgrabers/controller/nativeinstruments/",
        "com/bitwig/extension/",
    )
    foreign = [p for p in classes if p.startswith(forbidden_prefixes)]
    if foreign:
        raise SystemExit(f"Foreign controller implementation packaged: {foreign}")

    forbidden_names = (
        "Push2Display", "PushUsbDisplay", "Push2Controller", "MaschineColorManager",
        "RaindropsView", "AbstractRaindropsView", "RAINDROPS",
        "NAME_RAINDROPS", "com/studiohartman/jamepad/",
        "purejavahidapi/", "com/fasterxml/jackson/",
        "de/mossgrabers/bitwig/framework/usb/",
    )
    leftovers = [p for p in entries if any(name in p for name in forbidden_names)]
    if leftovers:
        raise SystemExit(f"Forbidden controller code or Raindrops dependency packaged: {leftovers}")

    # Scan class constant pools as well as entry names: no dormant Raindrops mode may remain.
    raindrops_refs = []
    for path in classes:
        data = archive.read(path)
        if any(token in data for token in (b"Raindrops", b"Raindrop", b"RAINDROPS", b"NAME_RAINDROPS")):
            raindrops_refs.append(path)
    if raindrops_refs:
        raise SystemExit(f"Raindrops symbols remain in compiled classes: {raindrops_refs}")

    definition_class = archive.read("de/mossgrabers/controller/ableton/push/PushControllerDefinition.class")
    if b"Push 2" in definition_class or b"Ableton Push 2" in definition_class or b"2982" in definition_class:
        raise SystemExit("PushControllerDefinition still contains Push 2 discovery metadata")
    definition_literals = archive.read("de/mossgrabers/controller/ableton/push/PushControllerDefinition.class")
    for expected in (b"DB830BE9-77B3-46F5-8F97-744901A94B56", b"Ableton Push User Port", b"MIDIIN2 (%sAbleton Push)"):
        if expected not in definition_literals:
            raise SystemExit(f"Push 1 MIDI discovery metadata is missing: {expected!r}")

    display_class = archive.read("de/mossgrabers/controller/ableton/push/controller/Push1Display.class")
    if b"Push1DisplayProtocol" not in display_class:
        raise SystemExit("Push1Display is not delegated to the dedicated protocol engine")
    protocol_class = archive.read("de/mossgrabers/controller/ableton/push/controller/Push1DisplayProtocol.class")
    for header in (
        b"F0 47 7F 15 18 00 45 00",
        b"F0 47 7F 15 19 00 45 00",
        b"F0 47 7F 15 1A 00 45 00",
        b"F0 47 7F 15 1B 00 45 00",
    ):
        if header not in protocol_class:
            raise SystemExit(f"Push 1 LCD SysEx row header is missing from protocol engine: {header!r}")

    setup_class = archive.read("de/mossgrabers/controller/ableton/push/PushControllerSetup.class")
    if b"isPush2" in setup_class or b"Push2Display" in setup_class:
        raise SystemExit("PushControllerSetup still contains Push 2 runtime branches")

    service_path = "META-INF/services/com.bitwig.extension.ExtensionDefinition"
    service = archive.read(service_path).decode("utf-8").strip()
    if service != "de.mossgrabers.bitwig.controller.ableton.push.Push1ControllerExtensionDefinition":
        raise SystemExit(f"Unexpected extension registrations: {service}")

    # Verify every copied runtime resource is present byte-for-byte. This covers
    # device metadata, all SVG UI assets, and the extension help PDF.
    resource_root = Path("src/main/resources")
    missing_resources = []
    changed_resources = []
    for resource in sorted(path for path in resource_root.rglob("*") if path.is_file()):
        relative = resource.relative_to(resource_root).as_posix()
        if relative not in entries:
            missing_resources.append(relative)
        elif archive.read(relative) != resource.read_bytes():
            changed_resources.append(relative)
    if missing_resources or changed_resources:
        raise SystemExit(
            f"Runtime resource packaging mismatch; missing={missing_resources}, "
            f"changed={changed_resources}"
        )

    manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8", errors="replace")
    if "Implementation-Version: 19.2.2" not in manifest:
        raise SystemExit("The extension manifest does not identify the pinned 19.2.2 runtime")

# Resolve every class-level dependency against the JDK, the Bitwig API and the
# shaded extension itself. A successful compile alone cannot detect missing
# runtime classes hidden behind reflective or rarely used controller paths.
api_candidates = (
    Path.home() / ".m2/repository/com/bitwig/extension-api/17/extension-api-17.jar",
    Path("maven-local-repository/com/bitwig/extension-api/17/extension-api-17.jar"),
)
api = next((candidate for candidate in api_candidates if candidate.is_file()), None)
if api is None:
    raise SystemExit("Bitwig Extension API 17 JAR is unavailable for dependency analysis")
dependency_check = subprocess.run(
    ["jdeps", "--multi-release", "17", "--missing-deps", "--class-path", str(api), str(jar)],
    capture_output=True, text=True,
)
if dependency_check.returncode != 0:
    raise SystemExit(f"jdeps failed: {dependency_check.stderr}")
if dependency_check.stdout.strip():
    raise SystemExit("Unresolved runtime class dependencies detected:\n" + dependency_check.stdout)

print(f"Verified {jar.name}: {len(classes)} classes; Push 1 only; resources complete; no unresolved class dependencies.")
