#!/usr/bin/env python3
"""Assemble the DrivenByMoss 19.0.0 sources required by the Push 1 extension."""
from pathlib import Path
import re
import shutil

root = Path.cwd()
upstream = root / "upstream"
java = root / "src/main/java/de/mossgrabers"
resources = root / "src/main/resources/META-INF/services"
java.mkdir(parents=True, exist_ok=True)
resources.mkdir(parents=True, exist_ok=True)

def copy_tree(source: str, destination: str) -> None:
    shutil.copytree(upstream / source, root / destination, dirs_exist_ok=True)

copy_tree("src/main/java/de/mossgrabers/framework", "src/main/java/de/mossgrabers/framework")
copy_tree("src/main/java/de/mossgrabers/bitwig/framework", "src/main/java/de/mossgrabers/bitwig/framework")
copy_tree("src/main/java/de/mossgrabers/bitwig/controller/ableton/push",
          "src/main/java/de/mossgrabers/bitwig/controller/ableton/push")
copy_tree("src/main/java/de/mossgrabers/controller/ableton/push",
          "src/main/java/de/mossgrabers/controller/ableton/push")

# Register and compile only the Push 1 extension entry point and text display.
(root / "src/main/java/de/mossgrabers/bitwig/controller/ableton/push/Push2ControllerExtensionDefinition.java").unlink(missing_ok=True)
(root / "src/main/java/de/mossgrabers/controller/ableton/push/controller/Push2Display.java").unlink(missing_ok=True)

# Upstream AbstractDrumView had an accidental compile-time dependency on Maschine.
# Resolve its two velocity-pad colors via the active controller's color manager.
drum_view = root / "src/main/java/de/mossgrabers/framework/view/sequencer/AbstractDrumView.java"
source = drum_view.read_text()
maschine_import = "import de.mossgrabers.controller.ni.maschine.core.MaschineColorManager;\n"
if source.count(maschine_import) != 1:
    raise SystemExit("AbstractDrumView: unexpected MaschineColorManager import state")
source = source.replace(maschine_import, "", 1)
for old, new in (
    ("MaschineColorManager.COLOR_GREEN", "this.colorManager.getColorIndex (DAWColor.getColorID (DAWColor.DAW_COLOR_GREEN.getColor ()))"),
    ("MaschineColorManager.COLOR_LIME_LO", "this.colorManager.getColorIndex (DAWColor.getColorID (DAWColor.DAW_COLOR_MOSS_GREEN.getColor ()))"),
):
    if source.count(old) != 1:
        raise SystemExit(f"AbstractDrumView: expected one {old}")
    source = source.replace(old, new, 1)
drum_view.write_text(source)

# Remove the Push 2 display branch entirely; Push 1 always uses its 2xLCD display.
setup = root / "src/main/java/de/mossgrabers/controller/ableton/push/PushControllerSetup.java"
source = setup.read_text()
push2_import = "import de.mossgrabers.controller.ableton.push.controller.Push2Display;\n"
if source.count(push2_import) != 1:
    raise SystemExit("PushControllerSetup: expected Push2Display import was not found")
source = source.replace(push2_import, "", 1)
old_display = (
    "        if (this.isPush2)\n"
    "            surface.addGraphicsDisplay (new Push2Display (this.host, this.valueChanger.getUpperBound (), this.configuration));\n"
    "        else\n"
    "            surface.addTextDisplay (new Push1Display (this.host, this.valueChanger.getUpperBound (), output, this.configuration));"
)
new_display = "        surface.addTextDisplay (new Push1Display (this.host, this.valueChanger.getUpperBound (), output, this.configuration));"
if source.count(old_display) != 1:
    raise SystemExit("PushControllerSetup: expected Push 1/Push 2 display branch was not found")
setup.write_text(source.replace(old_display, new_display, 1))

(resources / "com.bitwig.extension.ExtensionDefinition").write_text(
    "de.mossgrabers.bitwig.controller.ableton.push.Push1ControllerExtensionDefinition\n"
)

(root / "src/assembly").mkdir(parents=True, exist_ok=True)
shutil.copy2(upstream / "src/assembly/dep.xml", root / "src/assembly/dep.xml")
shutil.copytree(upstream / "maven-local-repository", root / "maven-local-repository", dirs_exist_ok=True)
shutil.copy2(upstream / "pom.xml", root / "pom.xml")
pom = root / "pom.xml"
text = pom.read_text()
text = text.replace("<artifactId>DrivenByMoss</artifactId>", "<artifactId>PUSHdriver</artifactId>", 1)
text = text.replace("<name>DrivenByMoss</name>", "<name>PUSHdriver</name>", 1)
text, removed = re.subn(
    r"\\s*<!-- Gamepad support\\. -->\\s*<dependency>.*?<artifactId>jamepad</artifactId>.*?</dependency>",
    "",
    text,
    count=1,
    flags=re.DOTALL,
)
if removed != 1:
    raise SystemExit("Expected exactly one Jamepad dependency in upstream POM")
pom.write_text(text)
