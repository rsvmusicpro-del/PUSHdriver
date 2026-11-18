# Internal source-assembly patch batch. Executed in order by prepare_push1.py.
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
    private static final UUID EXTENSION_ID_MK_I = UUID.fromString ("DB830BE9-77B3-46F5-8F97-744901A94B56");

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

            case MAC:
                midiDiscoveryPairs.add (this.addDeviceDiscoveryPair ("Ableton Push User Port"));
                break;

            case LINUX:
                // Linux Push 1 discovery uses the ALSA hardware-port variants.
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
shutil.copytree(upstream / "maven-local-repository", root / "maven-local-repository")
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



