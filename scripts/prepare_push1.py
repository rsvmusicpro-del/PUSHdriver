#!/usr/bin/env python3
"""Assemble the DrivenByMoss 19.0.0 sources required by the Push 1 extension."""
from pathlib import Path
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
# Push 1 uses MIDI only; the Bitwig USB-HID adapter exists for other hardware.
shutil.rmtree(root / "src/main/java/de/mossgrabers/bitwig/framework/usb", ignore_errors=True)
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
setup_source = setup.read_text()
setup_source = setup_source.replace("    private final boolean isPush2;\\n", "", 1)
setup_source = setup_source.replace("     * @param isPush2 True if Push 2\\n", "", 1)
old_signature = "public PushControllerSetup (final IHost host, final ISetupFactory factory, final ISettingsUI globalSettings, final ISettingsUI documentSettings, final boolean isPush2)"
new_signature = "public PushControllerSetup (final IHost host, final ISetupFactory factory, final ISettingsUI globalSettings, final ISettingsUI documentSettings)"
if setup_source.count(old_signature) != 1:
    raise SystemExit("PushControllerSetup: expected dual-generation constructor was not found")
setup_source = setup_source.replace(old_signature, new_signature, 1)
setup_source = setup_source.replace("        this.isPush2 = isPush2;\\n", "", 1)
setup_source = setup_source.replace("new PushColorManager (isPush2)", "new PushColorManager (false)", 1)
setup_source = setup_source.replace("new PushConfiguration (host, this.valueChanger, factory.getArpeggiatorModes (), isPush2)", "new PushConfiguration (host, this.valueChanger, factory.getArpeggiatorModes (), false)", 1)
setup_source = setup_source.replace("this.isPush2", "false")
setup_source = setup_source.replace("isPush2", "false")
setup_source = setup_source.replace("Push 1 and Push 2 controllers", "Push 1 controller")
setup_source = setup_source.replace("Redraw the Push 2 display.", "Redraw the Push 1 display.")
setup_source = setup_source.replace("Push 2", "Push 1")
if "isPush2" in setup_source or "Push2Display" in setup_source or "Push 2" in setup_source:
    raise SystemExit("PushControllerSetup still contains Push 2 runtime references")
setup.write_text(setup_source)

# Replace the dual-generation USB/MIDI discovery definition with the Push 1 definition only.
definition = root / "src/main/java/de/mossgrabers/controller/ableton/push/PushControllerDefinition.java"
definition.write_text("""// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push;

import de.mossgrabers.framework.controller.DefaultControllerDefinition;
import de.mossgrabers.framework.usb.UsbMatcher;
import de.mossgrabers.framework.utils.OperatingSystem;
import de.mossgrabers.framework.utils.Pair;

import java.util.List;
import java.util.UUID;


/** Push 1 MIDI discovery definition. */
public class PushControllerDefinition extends DefaultControllerDefinition
{
    private static final UUID EXTENSION_ID_MK_I = UUID.fromString ("DBED9610-C474-11E6-9598-0800200C9A66");

    public PushControllerDefinition ()
    {
        super (EXTENSION_ID_MK_I, "Push 1", "Ableton", 1, 1);
    }

    @Override
    public List<Pair<String [], String []>> getMidiDiscoveryPairs (final OperatingSystem os)
    {
        final List<Pair<String [], String []>> midiDiscoveryPairs = super.getMidiDiscoveryPairs (os);
        switch (os)
        {
            case WINDOWS:
                midiDiscoveryPairs.addAll (this.createWindowsDeviceDiscoveryPairs ("MIDIIN2 (%sAbleton Push)", "MIDIOUT2 (%sAbleton Push)"));
                break;

            case LINUX, MAC:
                midiDiscoveryPairs.add (this.addDeviceDiscoveryPair ("Ableton Push User Port"));
                midiDiscoveryPairs.addAll (this.createLinuxDeviceDiscoveryPairs ("Push", "Push", 1));
                break;

            default:
                break;
        }
        return midiDiscoveryPairs;
    }

    @Override
    public UsbMatcher claimUSBDevice ()
    {
        return null;
    }
}
""")

push1_definition = root / "src/main/java/de/mossgrabers/bitwig/controller/ableton/push/Push1ControllerExtensionDefinition.java"
source = push1_definition.read_text()
if source.count("new PushControllerDefinition (false)") != 1:
    raise SystemExit("Push1ControllerExtensionDefinition: expected Push 1 definition constructor was not found")
source = source.replace("new PushControllerDefinition (false)", "new PushControllerDefinition ()", 1)
old_setup = "new PushControllerSetup (new HostImpl (host), new BitwigSetupFactory (host), new SettingsUIImpl (host, host.getPreferences ()), new SettingsUIImpl (host, host.getDocumentState ()), false)"
new_setup = "new PushControllerSetup (new HostImpl (host), new BitwigSetupFactory (host), new SettingsUIImpl (host, host.getPreferences ()), new SettingsUIImpl (host, host.getDocumentState ()))"
if source.count(old_setup) != 1:
    raise SystemExit("Push1ControllerExtensionDefinition: expected PushControllerSetup constructor was not found")
push1_definition.write_text(source.replace(old_setup, new_setup, 1))


# HostImpl still implements the shared IHost contract. Keep its USB methods but disable
# USB-HID access: Push 1 is MIDI-only and this removes unrelated HID controller libraries.
host = root / "src/main/java/de/mossgrabers/bitwig/framework/daw/HostImpl.java"
source = host.read_text()
for unwanted in (
    "import de.mossgrabers.bitwig.framework.usb.UsbDeviceImpl;\n",
    "import com.bitwig.extension.controller.api.HardwareDevice;\n",
    "import com.bitwig.extension.controller.api.UsbDevice;\n",
):
    if source.count(unwanted) != 1:
        raise SystemExit(f"HostImpl: expected one import to remove: {unwanted.strip()}")
    source = source.replace(unwanted, "", 1)
field = "    private final List<IUsbDevice> usbDevices = new ArrayList<> ();\n"
if source.count(field) != 1:
    raise SystemExit("HostImpl: USB device tracking field was not found")
source = source.replace(field, "", 1)
old_get = """    public IUsbDevice getUsbDevice (final int index) throws UsbException
    {
        try
        {
            final HardwareDevice hardwareDevice = this.host.hardwareDevice (index);
            final UsbDeviceImpl usbDevice = new UsbDeviceImpl (this, (UsbDevice) hardwareDevice);
            this.usbDevices.add (usbDevice);
            return usbDevice;
        }
        catch (final RuntimeException ex)
        {
            throw new UsbException ("Could not lookup or open the device.", ex);
        }
    }"""
new_get = """    public IUsbDevice getUsbDevice (final int index) throws UsbException
    {
        throw new UsbException ("PUSHdriver supports the MIDI-only Ableton Push 1; USB-HID access is disabled.");
    }"""
if source.count(old_get) != 1:
    raise SystemExit("HostImpl: expected USB device implementation was not found")
source = source.replace(old_get, new_get, 1)
old_release = """    public void releaseUsbDevices ()
    {
        for (final IUsbDevice usbDevice: this.usbDevices)
            usbDevice.release ();
        this.usbDevices.clear ();
    }"""
new_release = """    public void releaseUsbDevices ()
    {
        // Push 1 has no USB-HID display or control endpoint.
    }"""
if source.count(old_release) != 1:
    raise SystemExit("HostImpl: expected USB release implementation was not found")
source = source.replace(old_release, new_release, 1)
host.write_text(source)

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
marker = "<!-- Gamepad support. -->"
if text.count(marker) != 1:
    raise SystemExit("Expected exactly one Gamepad support section in upstream POM")
start = text.index(marker)
dependency_start = text.index("<dependency>", start)
dependency_end = text.index("</dependency>", dependency_start) + len("</dependency>")
dependency = text[dependency_start:dependency_end]
if "<artifactId>jamepad</artifactId>" not in dependency:
    raise SystemExit("Gamepad support section does not contain the expected Jamepad dependency")
text = text[:start] + text[dependency_end:]
# Remove libraries used only by non-Push-1 drivers or the USB-HID adapter.
for group, artifact in (
    ("net.java.dev.jna", "jna"),
    ("net.java.dev.jna", "jna-platform"),
    ("purejavahidapi", "purejavahidapi"),
    ("com.fasterxml.jackson.core", "jackson-databind"),
):
    needle = f"<groupId>{group}</groupId>"
    position = text.find(needle)
    if position < 0:
        raise SystemExit(f"Missing expected dependency group: {group}")
    start = text.rfind("<dependency>", 0, position)
    end = text.find("</dependency>", position)
    if start < 0 or end < 0 or f"<artifactId>{artifact}</artifactId>" not in text[start:end]:
        raise SystemExit(f"Could not isolate dependency {group}:{artifact}")
    text = text[:start] + text[end + len("</dependency>"):]
pom.write_text(text)
