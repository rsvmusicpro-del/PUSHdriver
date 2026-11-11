#!/usr/bin/env python3
"""Fail the build if non-Push-1 controller implementations are packaged."""
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

    forbidden_names = ("Push2Display", "Push2Controller", "MaschineColorManager", "com/studiohartman/jamepad/")
    leftovers = [p for p in entries if any(name in p for name in forbidden_names)]
    if leftovers:
        raise SystemExit(f"Push 2/Maschine implementation packaged: {leftovers}")

    service_path = "META-INF/services/com.bitwig.extension.ExtensionDefinition"
    service = archive.read(service_path).decode("utf-8").strip()
    if service != "de.mossgrabers.bitwig.controller.ableton.push.Push1ControllerExtensionDefinition":
        raise SystemExit(f"Unexpected extension registrations: {service}")

print(f"Verified {jar.name}: {len(classes)} classes; only Push 1 Bitwig extension entry point.")
