#!/usr/bin/env python3
"""Fail the build if the package contains anything outside the Push 1 scope."""
from pathlib import Path
from zipfile import ZipFile

jar = Path("target/PUSHdriver-19.0.0.jar")
if not jar.is_file() or jar.stat().st_size == 0:
    raise SystemExit("PUSHdriver JAR is missing or empty")

with ZipFile(jar) as archive:
    entries = archive.namelist()
    classes = [p for p in entries if p.endswith(".class")]
    bitwig_classes = [p for p in classes if p.startswith("de/mossgrabers/bitwig/controller/")]
    expected = ["de/mossgrabers/bitwig/controller/ableton/push/Push1ControllerExtensionDefinition.class"]
    if bitwig_classes != expected:
        raise SystemExit(f"Unexpected Bitwig controller entry points: {bitwig_classes}")

    forbidden_prefixes = (
        "de/mossgrabers/controller/ni/",
        "de/mossgrabers/controller/akai/",
        "de/mossgrabers/controller/novation/",
        "de/mossgrabers/controller/faderfox/",
        "de/mossgrabers/controller/livid/",
        "de/mossgrabers/controller/nativeinstruments/",
    )
    foreign = [p for p in classes if p.startswith(forbidden_prefixes)]
    if foreign:
        raise SystemExit(f"Foreign controller implementation packaged: {foreign}")

    forbidden_names = (
        "Push2Display", "Push2Controller", "MaschineColorManager",
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
    setup_class = archive.read("de/mossgrabers/controller/ableton/push/PushControllerSetup.class")
    if b"isPush2" in setup_class or b"Push2Display" in setup_class:
        raise SystemExit("PushControllerSetup still contains Push 2 runtime branches")

    service_path = "META-INF/services/com.bitwig.extension.ExtensionDefinition"
    service = archive.read(service_path).decode("utf-8").strip()
    if service != "de.mossgrabers.bitwig.controller.ableton.push.Push1ControllerExtensionDefinition":
        raise SystemExit(f"Unexpected extension registrations: {service}")

print(f"Verified {jar.name}: {len(classes)} classes; Push 1 only; Raindrops fully absent.")
