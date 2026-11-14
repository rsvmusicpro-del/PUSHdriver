#!/usr/bin/env python3
"""Validate the assembled 64-entry Push 1 / Bitwig color palette."""
from pathlib import Path
import re

ROOT = Path.cwd()
daw_path = ROOT / "src/main/java/de/mossgrabers/framework/daw/DAWColor.java"
push_path = ROOT / "src/main/java/de/mossgrabers/controller/ableton/push/controller/PushColorManager.java"
view_path = ROOT / "src/main/java/de/mossgrabers/framework/view/ColorView.java"

for path in (daw_path, push_path, view_path):
    if not path.is_file():
        raise SystemExit(f"Missing assembled source: {path}")

daw = daw_path.read_text()
push = push_path.read_text()
view = view_path.read_text()

entries = re.findall(
    r'^\s*([A-Z][A-Z0-9_]*)\("([^"]+)", new ColorEx \(([^)]*)\)\)([,;])$',
    daw,
    re.MULTILINE,
)
if len(entries) != 64:
    raise SystemExit(f"Expected exactly 64 DAW colors, found {len(entries)}")

ids = [entry[0] for entry in entries]
names = [entry[1] for entry in entries]
if len(set(ids)) != 64 or len(set(names)) != 64:
    raise SystemExit("Color IDs and display names must be unique")

rgb = []
for constant, name, components, suffix in entries:
    values = [float(part.strip()) for part in components.split(",")]
    if len(values) != 3 or any(value < 0.0 or value > 1.0 for value in values):
        raise SystemExit(f"Invalid RGB triplet for {constant}: {components}")
    rgb.append(tuple(round(value * 255) for value in values))
if len(set(rgb)) != 64:
    raise SystemExit(f"Expected 64 distinct RGB colors, found {len(set(rgb))}")

palette_match = re.search(r"DEFAULT_PALETTE\s*=\s*\{([\s\S]*?)\n\s*\};", push)
if palette_match is None:
    raise SystemExit("PushColorManager.DEFAULT_PALETTE not found")
hardware_rgb = [
    tuple(int(component, 16) for component in match)
    for match in re.findall(
        r"\{\s*0x([0-9A-Fa-f]+),\s*0x([0-9A-Fa-f]+),\s*0x([0-9A-Fa-f]+)\s*\}",
        palette_match.group(1),
    )
]
if len(hardware_rgb) < 64 or rgb != hardware_rgb[:64]:
    raise SystemExit("DAW RGB values do not match Push 1 hardware indices 0-63")

if "this.registerColorIndex (DAWColor.values ()[i], i);" not in push:
    raise SystemExit("DAWColor enum entries are not registered to matching Push palette indices")
if "final DAWColor [] dawColors = DAWColor.values ();" not in view:
    raise SystemExit("ColorView is not drawing the 64-entry DAWColor palette")
if "cursorTrack.setColor (entry);" not in view or "clip.setColor (entry);" not in view:
    raise SystemExit("Track/clip RGB assignment path is missing")
if "((ILayer) selectedItem.get ()).setColor (entry);" not in view:
    raise SystemExit("Layer RGB assignment path is missing")

print("PASS: 64 unique Bitwig RGB colors match Push 1 pad palette indices 0-63.")
print("PASS: all 64 choices are registered in order and track/clip/layer assignment remains active.")
