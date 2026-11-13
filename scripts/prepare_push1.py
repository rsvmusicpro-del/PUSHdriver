#!/usr/bin/env python3
"""Assemble the DrivenByMoss 19.2.2 sources required by the Push 1 extension."""
from pathlib import Path
import shutil
import subprocess
import re

root = Path.cwd()
upstream = root / "upstream"
EXPECTED_UPSTREAM_COMMIT = "013620c1330cdcf896e96ef8fdc8a2123e4fce6a"
try:
    actual_upstream_commit = subprocess.run(
        ["git", "-C", str(upstream), "rev-parse", "HEAD"],
        check=True, capture_output=True, text=True,
    ).stdout.strip()
except (OSError, subprocess.CalledProcessError) as error:
    raise SystemExit(f"Cannot verify DrivenByMoss submodule revision: {error}") from error
if actual_upstream_commit != EXPECTED_UPSTREAM_COMMIT:
    raise SystemExit(
        f"Unexpected DrivenByMoss revision: {actual_upstream_commit}; "
        f"expected {EXPECTED_UPSTREAM_COMMIT}"
    )

# Rebuild generated inputs from a clean state so repeated local builds cannot
# retain Java sources, service registrations or Maven artifacts from old runs.
shutil.rmtree(root / "src/main/java/de/mossgrabers", ignore_errors=True)
shutil.rmtree(root / "src/main/resources/META-INF/services", ignore_errors=True)
shutil.rmtree(root / "src/assembly", ignore_errors=True)
shutil.rmtree(root / "src/main/resources/devices", ignore_errors=True)
shutil.rmtree(root / "src/main/resources/images", ignore_errors=True)
(root / "src/main/resources/DrivenByMoss-Manual.pdf").unlink(missing_ok=True)
shutil.rmtree(root / "maven-local-repository", ignore_errors=True)
java = root / "src/main/java/de/mossgrabers"
resources = root / "src/main/resources/META-INF/services"
java.mkdir(parents=True, exist_ok=True)
resources.mkdir(parents=True, exist_ok=True)

def copy_tree(source: str, destination: str) -> None:
    shutil.copytree(upstream / source, root / destination, dirs_exist_ok=True)

# Copy runtime data used by HostImpl and the controller UI. In particular,
# HostImpl.readDeviceFiles() wraps these streams without a null check, so omitting
# devices/*.txt makes extension construction fail before init() is reached.
for resource_dir in ("devices", "images"):
    copy_tree(f"src/main/resources/{resource_dir}", f"src/main/resources/{resource_dir}")
shutil.copy2(
    upstream / "src/main/resources/Documentation/DrivenByMoss-Manual.pdf",
    root / "src/main/resources/DrivenByMoss-Manual.pdf",
)

def replace_once(path: Path, old: str, new: str, label: str) -> None:
    content = path.read_text()
    # Skip only when the requested replacement is already present, without
    # confusing generic lines (for example, an existing null slot) elsewhere.
    if new and new in content and (old in new or old not in content):
        return
    if content.count(old) != 1:
        raise SystemExit(f"{label}: expected exactly one source match")
    path.write_text(content.replace(old, new, 1))

copy_tree("src/main/java/de/mossgrabers/framework", "src/main/java/de/mossgrabers/framework")
copy_tree("src/main/java/de/mossgrabers/bitwig/framework", "src/main/java/de/mossgrabers/bitwig/framework")
# Push 1 uses MIDI only; the Bitwig USB-HID adapter exists for other hardware.
shutil.rmtree(root / "src/main/java/de/mossgrabers/bitwig/framework/usb", ignore_errors=True)
copy_tree("src/main/java/de/mossgrabers/bitwig/controller/ableton/push",
          "src/main/java/de/mossgrabers/bitwig/controller/ableton/push")
copy_tree("src/main/java/de/mossgrabers/controller/ableton/push",
          "src/main/java/de/mossgrabers/controller/ableton/push")

# Apply the 19.2.2 Push 1 firmware inquiry parser and LCD row-width fixes.
device_inquiry = root / "src/main/java/de/mossgrabers/framework/daw/midi/DeviceInquiry.java"
replace_once(device_inquiry,
    "    public int [] getData ()\n"
    "    {\n"
    "        return this.data;\n"
    "    }",
    "    public int [] getData ()\n"
    "    {\n"
    "        return this.data.clone ();\n"
    "    }",
    "DeviceInquiry defensive response accessor")

# The parsed SysEx packet must not retain a caller-owned mutable array.
replace_once(device_inquiry,
    "        this.data = data;\n",
    "        this.data = data == null ? new int [0] : data.clone ();\n"
    "        if (data == null)\n"
    "        {\n"
    "            this.responseType = ResponseType.ERROR;\n"
    "            return;\n"
    "        }\n",
    "DeviceInquiry null-safe defensive copy")

# Reject malformed MIDI SysEx data bytes before parsing device identity fields.
replace_once(device_inquiry,
    "        if (data[0] != 0xF0 || data[1] != 0x7E || data[3] != 0x06 || data[4] != 0x02 || data[data.length - 1] != 0xF7)\n"
    "            this.responseType = ResponseType.ERROR;\n",
    "        if (data[0] != 0xF0 || data[1] != 0x7E || data[3] != 0x06 || data[4] != 0x02 || data[data.length - 1] != 0xF7)\n"
    "            this.responseType = ResponseType.ERROR;\n"
    "        if (this.responseType != ResponseType.ERROR)\n"
    "        {\n"
    "            for (int i = 1; i < this.data.length - 1; i++)\n"
    "            {\n"
    "                if (this.data[i] < 0 || this.data[i] > 0x7F)\n"
    "                {\n"
    "                    this.responseType = ResponseType.ERROR;\n"
    "                    break;\n"
    "                }\n"
    "            }\n"
    "        }\n"
    "        // MIDI manufacturer IDs are one byte unless the first byte is zero,\n"
    "        // in which case two additional manufacturer bytes follow. Exact\n"
    "        // standard packet lengths must agree with that encoding.\n"
    "        if (this.responseType == ResponseType.SHORT && this.data[OFFSET_MANUFACTURER_ID] == 0\n"
    "            || this.responseType == ResponseType.LONG && this.data[OFFSET_MANUFACTURER_ID] != 0)\n"
    "            this.responseType = ResponseType.ERROR;\n",
    "DeviceInquiry seven-bit SysEx validation")


# Manufacturer-ID width is determined by its leading byte, not by the
# overall length of a non-standard extended identity reply.
replace_once(device_inquiry,
    "        // Old 1 byte ID\n"
    "        if (this.responseType == ResponseType.SHORT)\n"
    "        {\n"
    "            return new int []\n"
    "            {\n"
    "                this.data[OFFSET_MANUFACTURER_ID]\n"
    "            };\n"
    "        }\n\n"
    "        // Newer 3-byte ID if first byte is 0\n"
    "        return new int []\n"
    "        {\n"
    "            this.data[OFFSET_MANUFACTURER_ID],\n"
    "            this.data[OFFSET_MANUFACTURER_ID + 1],\n"
    "            this.data[OFFSET_MANUFACTURER_ID + 2]\n"
    "        };",
    "        // MIDI reserves a leading zero for the three-byte manufacturer form.\n"
    "        if (this.data[OFFSET_MANUFACTURER_ID] != 0)\n"
    "            return new int [] { this.data[OFFSET_MANUFACTURER_ID] };\n"
    "        return new int []\n"
    "        {\n"
    "            this.data[OFFSET_MANUFACTURER_ID],\n"
    "            this.data[OFFSET_MANUFACTURER_ID + 1],\n"
    "            this.data[OFFSET_MANUFACTURER_ID + 2]\n"
    "        };",
    "DeviceInquiry manufacturer ID width")
replace_once(device_inquiry,
    "        return this.responseType == ResponseType.SHORT ? OFFSET_CONTENT_SHORT : OFFSET_CONTENT_LONG;",
    "        return this.data[OFFSET_MANUFACTURER_ID] == 0 ? OFFSET_CONTENT_LONG : OFFSET_CONTENT_SHORT;",
    "DeviceInquiry extended identity content offset")

replace_once(device_inquiry,
    "        return INQUIRY;\n",
    "        return INQUIRY.clone ();\n",
    "DeviceInquiry query defensive copy")

push_surface = root / "src/main/java/de/mossgrabers/controller/ableton/push/controller/PushControlSurface.java"
replace_once(push_surface,
    "        else\n"
    "        {\n"
    "            final int [] data = deviceInquiry.getData ();\n"
    "            if (data.length != 35)\n"
    "                return;\n\n"
    "            this.majorVersion = data[10] + data[9] * 10;\n"
    "            this.minorVersion = data[12] + data[11] * 10;\n"
    "            this.buildNumber = 0;\n"
    "            this.serialNumber = 0;\n"
    "            this.boardRevision = 0;\n"
    "        }",
    "        else\n"
    "        {\n"
    "            final Push1FirmwareVersion firmwareVersion = Push1FirmwareVersion.parse (deviceInquiry);\n"
    "            if (firmwareVersion == null)\n"
    "                return;\n\n"
    "            this.majorVersion = firmwareVersion.major ();\n"
    "            this.minorVersion = firmwareVersion.minor ();\n"
    "            this.buildNumber = 0;\n"
    "            this.serialNumber = 0;\n"
    "            this.boardRevision = 0;\n"
    "        }",
    "Push 1 firmware inquiry parser")

configuration_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/ConfigurationMode.java"
replace_once(configuration_mode,
    "import de.mossgrabers.framework.daw.data.IItem;\n",
    "import de.mossgrabers.framework.daw.data.IItem;\n"
    "import de.mossgrabers.framework.utils.StringUtils;\n",
    "ConfigurationMode StringUtils import")
replace_once(configuration_mode,
    '            display.setRow (3, " Warning:Low threshold maycause stuck pads ");',
    '            display.setRow (3, StringUtils.pad ("Low threshold maycause stuck pads!", 68));',
    "ConfigurationMode fixed-width LCD warning")


# Match Ableton Live 10 Push 1 sequencer feedback without changing note/clip engines:
# playhead is green in playback and red while recording; selected steps are white;
# muted steps are pale yellow; soloed drum pads are dark blue.
sequencer_colors = root / "src/main/java/de/mossgrabers/framework/view/sequencer/AbstractSequencerView.java"
source = sequencer_colors.read_text()
replace_once(sequencer_colors,
    '    public static final String    COLOR_STEP_HILITE_CONTENT    = "COLOR_STEP_HILITE_CONTENT";\n',
    '    public static final String    COLOR_STEP_HILITE_CONTENT    = "COLOR_STEP_HILITE_CONTENT";\n'
    '    /** The color for the playhead while recording. */\n'
    '    public static final String    COLOR_STEP_HILITE_RECORD     = "COLOR_STEP_HILITE_RECORD";\n',
    "Sequencer recording playhead color")
source = sequencer_colors.read_text()
highlight = "                if (highlight)\n                    return COLOR_STEP_HILITE_CONTENT;"
if source.count(highlight) != 2:
    raise SystemExit("AbstractSequencerView: expected two content playhead branches")
sequencer_colors.write_text(source.replace(highlight,
    "                if (highlight)\n"
    "                    return this.model.hasRecordingState () ? COLOR_STEP_HILITE_RECORD : COLOR_STEP_HILITE_CONTENT;", 2))

drum_view = root / "src/main/java/de/mossgrabers/framework/view/sequencer/AbstractDrumView.java"
replace_once(drum_view,
    '    public static final String COLOR_PAD_SELECTED    = "COLOR_PAD_SELECTED";\n',
    '    public static final String COLOR_PAD_SELECTED    = "COLOR_PAD_SELECTED";\n'
    '    /** The color ID for a soloed drum pad. */\n'
    '    public static final String COLOR_PAD_SOLO        = "COLOR_PAD_SOLO";\n',
    "Drum solo pad color")
replace_once(drum_view,
    "        if (drumPad.isMute () || drumPadBank.hasSoloedPads () && !drumPad.isSolo ())\n"
    "            return AbstractDrumView.COLOR_PAD_MUTED;\n"
    "        return this.getPadContentColor (drumPad);",
    "        if (drumPad.isMute () || drumPadBank.hasSoloedPads () && !drumPad.isSolo ())\n"
    "            return AbstractDrumView.COLOR_PAD_MUTED;\n"
    "        if (drumPad.isSolo ())\n"
    "            return AbstractDrumView.COLOR_PAD_SOLO;\n"
    "        return this.getPadContentColor (drumPad);",
    "Drum solo state has dedicated Push 1 feedback")

push_colors = root / "src/main/java/de/mossgrabers/controller/ableton/push/controller/PushColorManager.java"
source = push_colors.read_text()
replace_once(push_colors,
    "        this.registerColorIndex (AbstractSequencerView.COLOR_STEP_HILITE_CONTENT, isPush2 ? PUSH2_COLOR2_GREEN_HI : PUSH1_COLOR2_GREEN_HI);\n",
    "        this.registerColorIndex (AbstractSequencerView.COLOR_STEP_HILITE_CONTENT, isPush2 ? PUSH2_COLOR2_GREEN_HI : PUSH1_COLOR2_GREEN_HI);\n"
    "        this.registerColorIndex (AbstractSequencerView.COLOR_STEP_HILITE_RECORD, isPush2 ? PUSH2_COLOR2_RED_HI : PUSH1_COLOR2_RED_HI);\n",
    "Push 1 recording playhead LED mapping")
for old, new, label in (
    ("this.registerColorIndex (AbstractSequencerView.COLOR_STEP_MUTED, isPush2 ? PUSH2_COLOR2_GREY_MD : PUSH1_COLOR2_GREY_MD);",
     "this.registerColorIndex (AbstractSequencerView.COLOR_STEP_MUTED, isPush2 ? PUSH2_COLOR2_GREY_MD : PUSH1_COLOR2_YELLOW_LO);", "Push 1 muted step color"),
    ("this.registerColorIndex (AbstractSequencerView.COLOR_STEP_MUTED_CONT, isPush2 ? PUSH2_COLOR2_GREY_LO : PUSH1_COLOR2_GREY_LO);",
     "this.registerColorIndex (AbstractSequencerView.COLOR_STEP_MUTED_CONT, isPush2 ? PUSH2_COLOR2_GREY_LO : PUSH1_COLOR2_YELLOW_LO);", "Push 1 muted continuation color"),
    ("this.registerColorIndex (AbstractSequencerView.COLOR_STEP_SELECTED, isPush2 ? PUSH2_COLOR2_YELLOW_HI : PUSH1_COLOR2_YELLOW_HI);",
     "this.registerColorIndex (AbstractSequencerView.COLOR_STEP_SELECTED, isPush2 ? PUSH2_COLOR2_YELLOW_HI : PUSH1_COLOR2_WHITE);", "Push 1 selected step color"),
    ("this.registerColorIndex (AbstractDrumView.COLOR_PAD_SELECTED, isPush2 ? PUSH2_COLOR2_BLUE_HI : PUSH1_COLOR2_BLUE_HI);",
     "this.registerColorIndex (AbstractDrumView.COLOR_PAD_SELECTED, isPush2 ? PUSH2_COLOR2_BLUE_HI : PUSH1_COLOR2_BLUE_HI);\n        this.registerColorIndex (AbstractDrumView.COLOR_PAD_SOLO, isPush2 ? PUSH2_COLOR2_BLUE_LO : PUSH1_COLOR2_BLUE_LO);", "Push 1 solo pad color"),
):
    replace_once(push_colors, old + "\n", new + "\n", label)


# Root notes in the piano layout must inherit the selected track color on every row.
piano_helper = root / "src/main/java/de/mossgrabers/framework/view/PianoViewHelper.java"
replace_once(piano_helper,
    "                final int color;\n"
    "                if (row % 2 == 0)",
    "                final int color;\n"
    "                if (keyManager.isKeyPressed (n))\n"
    "                    color = playKeyColor;\n"
    "                else if (Scales.SCALE_COLOR_OCTAVE.equals (keyManager.getColor (n)))\n"
    "                    color = colorManager.getColorIndex (AbstractView.replaceOctaveColorWithTrackColor (track, Scales.SCALE_COLOR_OCTAVE));\n"
    "                else if (row % 2 == 0)",
    "PianoViewHelper root notes follow selected track color")

# Establish one explicit physical-to-LCD row contract for Push menus.
# The physical top selector row is ROW1 (CC 20-27); the lower selector row is
# ROW2 (CC 102-109). Several upstream menu modes implement onFirstRow as the
# lower LCD option row and onSecondRow as the upper LCD option row. Keep that
# convention local to those modes, and route LED queries through the same map.
base_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/BaseMode.java"
replace_once(base_mode,
    "import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;\n",
    "import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;\n"
    "import de.mossgrabers.framework.controller.ButtonID;\n",
    "BaseMode selector row mapping import")
replace_once(base_mode,
    "    /** {@inheritDoc} */\n"
    "    @Override\n"
    "    public void onButton (final int row, final int index, final ButtonEvent event)\n"
    "    {\n"
    "        if (row == 0)\n"
    "            this.onFirstRow (index, event);\n"
    "        else\n"
    "            this.onSecondRow (index, event);\n"
    "    }\n",
    "    /**\n"
    "     * Whether this mode's first-row callback represents the lower LCD option row.\n"
    "     * The physical-to-menu mapping is shared by button dispatch and LED feedback.\n"
    "     *\n"
    "     * @return True if this mode's first and second menu rows are reversed\n"
    "     */\n"
    "    protected boolean isMenuRowDispatchInverted ()\n"
    "    {\n"
    "        return false;\n"
    "    }\n\n"
    "    /** {@inheritDoc} */\n"
    "    @Override\n"
    "    public void onButton (final int row, final int index, final ButtonEvent event)\n"
    "    {\n"
    "        final int logicalRow = this.isMenuRowDispatchInverted () ? 1 - row : row;\n"
    "        if (logicalRow == 0)\n"
    "            this.onFirstRow (index, event);\n"
    "        else\n"
    "            this.onSecondRow (index, event);\n"
    "    }\n\n"
    "    /** Map LED row queries through the same physical-to-menu row mapping. */\n"
    "    @Override\n"
    "    protected int isButtonRow (final int row, final ButtonID buttonID)\n"
    "    {\n"
    "        final int logicalRow = this.isMenuRowDispatchInverted () ? 1 - row : row;\n"
    "        return super.isButtonRow (logicalRow, buttonID);\n"
    "    }\n",
    "BaseMode unified LCD/button/LED row routing")

def enable_menu_row_inversion(mode_name, relative_path):
    mode_path = root / "src/main/java/de/mossgrabers/controller/ableton/push" / relative_path
    content = mode_path.read_text()
    if "isMenuRowDispatchInverted" in content:
        if "return true;" in content:
            return
        raise SystemExit(mode_name + ": conflicting selector-row policy already exists")
    pattern = re.compile(
        r"(public\s+(?:abstract\s+)?class\s+" + re.escape(mode_name) + r"\b[^{]*\{\s*\n)"
    )
    matches = list(pattern.finditer(content))
    if len(matches) != 1:
        raise SystemExit(mode_name + ": expected exactly one class declaration")
    policy = (
        "    @Override\n"
        "    protected boolean isMenuRowDispatchInverted ()\n"
        "    {\n"
        "        return true;\n"
        "    }\n\n"
    )
    match = matches[0]
    mode_path.write_text(content[:match.end()] + policy + content[match.end():])

# These modes display their onFirstRow choices in the lower LCD row and their
# onSecondRow choices in the upper LCD row. The shared policy keeps physical
# buttons and LEDs aligned without per-mode onButton workarounds.
for mode_name, relative_path in (
    ("FixedMode", "mode/FixedMode.java"),
    ("NoteMode", "mode/NoteMode.java"),
    ("NoteViewSelectMode", "mode/NoteViewSelectMode.java"),
    ("SessionViewSelectMode", "mode/SessionViewSelectMode.java"),
    ("ScalesMode", "mode/ScalesMode.java"),
    ("ScaleLayoutMode", "mode/ScaleLayoutMode.java"),
    ("GrooveMode", "mode/GrooveMode.java"),
    ("QuantizeMode", "mode/QuantizeMode.java"),
    ("RibbonMode", "mode/RibbonMode.java"),
    ("AutomationSelectionMode", "mode/AutomationSelectionMode.java"),
    ("MetronomeMode", "mode/MetronomeMode.java"),
    ("MarkerMode", "mode/MarkerMode.java"),
    ("FrameMode", "mode/FrameMode.java"),
    ("TrackDetailsMode", "mode/track/TrackDetailsMode.java"),
    ("AddTrackMode", "mode/track/AddTrackMode.java"),
    ("MasterMode", "mode/track/MasterMode.java"),
    ("ClipMode", "mode/track/ClipMode.java"),
    ("DeviceLayerDetailsMode", "mode/device/DeviceLayerDetailsMode.java"),
    ("DeviceChainsMode", "mode/device/DeviceChainsMode.java"),
    ("DeviceLayerMode", "mode/device/DeviceLayerMode.java"),
    ("DeviceParamsMode", "mode/device/DeviceParamsMode.java"),
    ("UserMode", "mode/device/UserMode.java"),
):
    enable_menu_row_inversion(mode_name, relative_path)

# DeviceChainsMode renders chain names in the lower LCD row. Its LED feedback
# must query that same lower physical row after the shared row policy is applied.
device_chains = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceChainsMode.java"
replace_once(device_chains,
    "        final int index = this.isButtonRow (0, buttonID);\n"
    "        if (index >= 0)\n"
    "            return super.getButtonColor (buttonID);\n\n"
    "        final int existsColor = this.isPush2 ? PushColorManager.PUSH2_COLOR_YELLOW_LO : PushColorManager.PUSH1_COLOR_YELLOW_LO;\n"
    "        final int offColor = this.isPush2 ? PushColorManager.PUSH2_COLOR_BLACK : PushColorManager.PUSH1_COLOR_BLACK;\n"
    "        final String [] slotChains = this.model.getCursorDevice ().getSlotChains ();\n"
    "        return index < slotChains.length ? existsColor : offColor;",
    "        final int index = this.isButtonRow (0, buttonID);\n"
    "        if (index >= 0)\n"
    "        {\n"
    "            final int existsColor = this.isPush2 ? PushColorManager.PUSH2_COLOR_YELLOW_LO : PushColorManager.PUSH1_COLOR_YELLOW_LO;\n"
    "            final int offColor = this.isPush2 ? PushColorManager.PUSH2_COLOR_BLACK : PushColorManager.PUSH1_COLOR_BLACK;\n"
    "            final String [] slotChains = this.model.getCursorDevice ().getSlotChains ();\n"
    "            return index < slotChains.length ? existsColor : offColor;\n"
    "        }\n"
    "        return super.getButtonColor (buttonID);",
    "DeviceChainsMode lower-row LED feedback")

# Note Repeat mixes top-row Period/Length/Groove with bottom-row toggles.
# Route each action by its actual LCD position rather than swapping the whole mode.
note_repeat = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/NoteRepeatMode.java"
replace_once(note_repeat,
    "    /** {@inheritDoc} */\n"
    "    @Override\n"
    "    public void onFirstRow (final int index, final ButtonEvent event)",
    "    /** Route each Note Repeat action to the LCD row where its label is shown. */\n"
    "    @Override\n"
    "    public void onButton (final int row, final int index, final ButtonEvent event)\n"
    "    {\n"
    "        if (row == 0)\n"
    "        {\n"
    "            if (index <= 3)\n"
    "                this.onFirstRow (index, event);\n"
    "            else if (index == 7)\n"
    "                this.onSecondRow (index, event);\n"
    "        }\n"
    "        else if (row == 1 && index >= 4)\n"
    "            this.onFirstRow (index, event);\n"
    "    }\n\n"
    "    /** {@inheritDoc} */\n"
    "    @Override\n"
    "    public void onFirstRow (final int index, final ButtonEvent event)",
    "NoteRepeat per-option physical row routing")

def replace_section(path, start_marker, end_marker, replacement, label):
    content = path.read_text()
    start_index = content.find(start_marker)
    end_index = content.find(end_marker, start_index + len(start_marker))
    if start_index < 0 or end_index < 0 or content.find(start_marker, start_index + 1) >= 0:
        raise SystemExit(label + ": expected one bounded source section")
    path.write_text(content[:start_index] + replacement + content[end_index:])

note_repeat_color = """    /** {@inheritDoc} */
    @Override
    public int getButtonColor (final ButtonID buttonID)
    {
        this.refreshNoteRepeat ();
        if (this.noteRepeat == null)
            return super.getButtonColor (buttonID);

        final ColorManager colorManager = this.model.getColorManager ();
        final int offColor = colorManager.getColorIndex (AbstractFeatureGroup.BUTTON_COLOR_OFF);
        final int onColor = colorManager.getColorIndex (AbstractFeatureGroup.BUTTON_COLOR_ON);
        final int hiColor = colorManager.getColorIndex (AbstractMode.BUTTON_COLOR_HI);

        int index = this.isButtonRow (0, buttonID);
        if (index >= 0)
        {
            // Top row: Period, Length and Groove enablement.
            if (index == 7)
                return this.model.getGroove ().getParameter (GrooveParameterID.ENABLED).getValue () > 0 ? hiColor : onColor;
            if (index >= 4)
                return offColor;
            if (index <= 1)
                return onColor;
            return this.host.supports (Capability.NOTE_REPEAT_LENGTH) ? onColor : offColor;
        }

        index = this.isButtonRow (1, buttonID);
        if (index >= 0)
        {
            // Bottom row: Latch, Pressure, Sync and Shuffle.
            switch (index)
            {
                case 4:
                    if (this.host.supports (Capability.NOTE_REPEAT_LATCH))
                        return this.noteRepeat.isLatchActive () ? hiColor : onColor;
                    return offColor;
                case 5:
                    if (this.host.supports (Capability.NOTE_REPEAT_USE_PRESSURE_TO_VELOCITY))
                        return this.noteRepeat.usePressure () ? hiColor : onColor;
                    return offColor;
                case 6:
                    if (this.host.supports (Capability.NOTE_REPEAT_IS_FREE_RUNNING))
                        return !this.noteRepeat.isFreeRunning () ? hiColor : onColor;
                    return offColor;
                case 7:
                    if (this.host.supports (Capability.NOTE_REPEAT_SWING))
                        return this.noteRepeat.isShuffle () ? hiColor : onColor;
                    return offColor;
                default:
                    return offColor;
            }
        }

        return super.getButtonColor (buttonID);
    }


"""
replace_section(
    note_repeat,
    "    /** {@inheritDoc} */\n    @Override\n    public int getButtonColor (final ButtonID buttonID)\n",
    "    /** {@inheritDoc} */\n    @Override\n    public void updateDisplay1 (final ITextDisplay display)\n",
    note_repeat_color,
    "NoteRepeat LCD-aligned LED feedback"
)

# Apply the small, reviewed Push 1 protocol override maintained by this project.
shutil.copytree(root / "overrides/java", root / "src/main/java", dirs_exist_ok=True)
# DeviceParamsMode is replaced by a project override above; reapply its row policy after that copy.
enable_menu_row_inversion("DeviceParamsMode", "mode/device/DeviceParamsMode.java")

push_display = root / "src/main/java/de/mossgrabers/controller/ableton/push/controller/Push1Display.java"
replace_once(push_display,
    "    private static final String [] SYSEX_MESSAGE =\n"
    "    {\n"
    "        \"F0 47 7F 15 18 00 45 00 \",\n"
    "        \"F0 47 7F 15 19 00 45 00 \",\n"
    "        \"F0 47 7F 15 1A 00 45 00 \",\n"
    "        \"F0 47 7F 15 1B 00 45 00 \"\n"
    "    };\n",
    "", "Push1Display protocol header ownership")
replace_once(push_display,
    "        this.output.sendSysex (Push1Display.SYSEX_MESSAGE[row] + StringUtils.asciiToHex (text) + \"F7\");",
    "        this.output.sendSysex (Push1DisplayProtocol.encodeLine (row, Push1DisplayText.fit (text, 68)));",
    "Push1Display SysEx protocol delegation")
replace_once(push_display,
    "    private final int              maxParameterValue;",
    "    private final int              maxParameterValue;\n"
    "    private boolean                compactNotification;",
    "Push1Display compact notification state")
replace_once(push_display,
    "    public void writeLine (final int row, final String text)\n"
    "    {\n"
    "        this.output.sendSysex (Push1DisplayProtocol.encodeLine (row, Push1DisplayText.fit (text, 68)));\n"
    "    }",
    "    public void writeLine (final int row, final String text)\n"
    "    {\n"
    "        this.output.sendSysex (Push1DisplayProtocol.encodeLine (row, Push1DisplayText.fit (text, 68)));\n"
    "    }\n\n"
    "    /** {@inheritDoc} */\n"
    "    @Override\n"
    "    protected void notifyOnDisplay (final String message)\n"
    "    {\n"
    "        this.compactNotification = message.codePointCount (0, message.length ()) <= 17;\n"
    "        super.notifyOnDisplay (message);\n"
    "    }\n\n"
    "    /** {@inheritDoc} */\n"
    "    @Override\n"
    "    public void flush ()\n"
    "    {\n"
    "        if (this.compactNotification && this.isNotificationActive ())\n"
    "        {\n"
    "            final String trimmed = this.notificationMessage.trim ();\n"
    "            final String row0 = this.currentMessage[0] == null ? this.emptyLine : this.currentMessage[0];\n"
    "            final String row1 = this.currentMessage[1] == null ? this.emptyLine : this.currentMessage[1];\n"
    "            this.updateLine (0, Push1DisplayText.overlayRightBlock (row0, trimmed));\n"
    "            this.updateLine (1, Push1DisplayText.overlayRightBlock (row1, \"\"));\n"
    "            return;\n"
    "        }\n"
    "        super.flush ();\n"
    "    }\n\n"
    "    /** {@inheritDoc} */\n"
    "    @Override\n"
    "    public void forceFlush ()\n"
    "    {\n"
    "        this.compactNotification = false;\n"
    "        super.forceFlush ();\n"
    "    }",
    "Push1Display compact notification overlay")

# Clamp parameter values before formatting the eight LCD bar cells. The
# upstream overlay calculation indexes one cell beyond the array when both
# the base and modulated values reach their maximum.
replace_once(push_display,
    "    public static String formatValue (final int value, final int maxParam)\n"
    "    {\n"
    "        final StringBuilder n = new StringBuilder ();\n"
    "        Arrays.asList (fillFields (value, maxParam)).forEach (n::append);\n"
    "        return n.toString ();\n"
    "    }",
    "    public static String formatValue (final int value, final int maxParam)\n"
    "    {\n"
    "        return Push1DisplayValueFormatter.formatValue (value, maxParam, BARS_NON, BARS_ONE, BARS_TWO);\n"
    "    }",
    "Push 1 bounded value bar formatting")
replace_once(push_display,
    "    public static String formatValue (final int modulated, final int value, final int maxParam)\n"
    "    {\n"
    "        final String [] fields = fillFields (value, maxParam);\n\n"
    "        final int noOfBarsModulated = (int) Math.round (16.0 * modulated / maxParam);\n"
    "        final int pos = noOfBarsModulated / 2;\n"
    "        if (noOfBarsModulated % 2 == 0)\n"
    "        {\n"
    "            if (pos > 0)\n"
    "            {\n"
    "                if (fields[pos - 1].charAt (0) == BARS_NON.charAt (0))\n"
    "                    fields[pos - 1] = BARS_ONE_L;\n"
    "                else if (fields[pos].charAt (0) == BARS_ONE.charAt (0))\n"
    "                    fields[pos - 1] = BARS_TWO;\n"
    "            }\n"
    "        }\n"
    "        else\n"
    "        {\n"
    "            if (fields[pos].charAt (0) == BARS_NON.charAt (0))\n"
    "                fields[pos] = BARS_ONE;\n"
    "            else\n"
    "                fields[pos] = BARS_TWO;\n"
    "        }\n\n"
    "        final StringBuilder n = new StringBuilder ();\n"
    "        Arrays.asList (fields).forEach (n::append);\n"
    "        return n.toString ();\n"
    "    }",
    "    public static String formatValue (final int modulated, final int value, final int maxParam)\n"
    "    {\n"
    "        return Push1DisplayValueFormatter.formatModulatedValue (modulated, value, maxParam, BARS_NON, BARS_ONE, BARS_TWO, BARS_ONE_L);\n"
    "    }",
    "Push 1 bounded modulated value bar formatting")
replace_once(push_display,
    "    private static String [] fillFields (final int value, final int maxParam)\n"
    "    {\n"
    "        final String [] fields = new String [8];\n"
    "        Arrays.fill (fields, Push1Display.BARS_NON);\n\n"
    "        final int noOfBars = (int) Math.round (16.0 * value / maxParam);\n"
    "        int count;\n"
    "        for (count = 0; count < noOfBars / 2; count++)\n"
    "            fields[count] = Push1Display.BARS_TWO;\n"
    "        if (noOfBars % 2 == 1)\n"
    "            fields[count] = Push1Display.BARS_ONE;\n\n"
    "        return fields;\n"
    "    }\n\n\n",
    "",
    "Remove unbounded Push 1 bar helper")

replace_once(push_display,
    "import java.util.Arrays;\n",
    "",
    "Remove obsolete Push1Display Arrays import")

# Make the physical four-module LCD geometry a single production contract.
replace_once(push_display,
    "        super (host, output, 4 /* No of rows */, 8 /* No of cells */, 68 /* No of characters */);",
    "        super (host, output, Push1DisplayLayout.ROWS, Push1DisplayLayout.CELLS_PER_ROW, Push1DisplayLayout.COLUMNS);",
    "Push 1 physical LCD geometry")
replace_once(push_display,
    "        this.cells[row * this.noOfCells + column] = column % 2 == 0 ? \"         \" : \"        \";",
    "        this.cells[Push1DisplayLayout.cellIndex (row, column)] = \" \".repeat (Push1DisplayLayout.cellWidth (column));",
    "Push 1 alternating LCD cell widths")
replace_once(push_display,
    "        final String t = text.replace (Push1Display.BARS_NON, \"-\").replace (Push1Display.BARS_ONE, \"|\").replace (Push1Display.BARS_TWO, \"|\").replace (Push1Display.BARS_ONE_L, \"|\").replace (Push1Display.SELECT_ARROW, \">\").replace (Push1Display.RIGHT_ARROW, \">\");\n"
    "        final StringBuilder sb = new StringBuilder (t.length ());\n"
    "        int beginIndex = 0;\n"
    "        int endIndex = 17;\n"
    "        for (int i = 0; i < 4; i++)\n"
    "        {\n"
    "            sb.append (t.substring (beginIndex, endIndex));\n"
    "            beginIndex += 17;\n"
    "            endIndex += 17;\n"
    "            if (i != 3)\n"
    "                sb.append (' ');\n"
    "        }",
    "        final String t = text.replace (Push1Display.BARS_NON, \"-\").replace (Push1Display.BARS_ONE, \"|\").replace (Push1Display.BARS_TWO, \"|\").replace (Push1Display.BARS_ONE_L, \"|\").replace (Push1Display.SELECT_ARROW, \">\").replace (Push1Display.RIGHT_ARROW, \">\");\n"
    "        return Push1DisplayLayout.separateModulesForSimulator (t);",
    "Push 1 simulator display module segmentation")
replace_once(push_display,
    "        return sb.toString ();\n"
    "    }\n\n\n"
    "    /** {@inheritDoc} */\n"
    "    @Override\n"
    "    public void writeLine",
    "    }\n\n\n"
    "    /** {@inheritDoc} */\n"
    "    @Override\n"
    "    public void writeLine",
    "Push 1 simulator module renderer return")
replace_once(push_display,
    "        this.output.sendSysex (Push1DisplayProtocol.encodeLine (row, Push1DisplayText.fit (text, 68)));",
    "        this.output.sendSysex (Push1DisplayProtocol.encodeLine (row, Push1DisplayLayout.fitRow (text)));",
    "Push 1 LCD row geometry")

# Keep all logical cells within their physical 8/9-character LCD boundaries.
replace_once(push_display,
    "        final int cell = 2 * block;\n"
    "        if (value.length () > 9)\n"
    "        {\n"
    "            this.cells[row * 8 + cell] = value.substring (0, 9);\n"
    "            this.cells[row * 8 + cell + 1] = StringUtils.pad (value.substring (9), 8, ' ');\n"
    "        }\n"
    "        else\n"
    "        {\n"
    "            this.cells[row * 8 + cell] = StringUtils.pad (value, 9, ' ');\n"
    "            this.clearCell (row, cell + 1);\n"
    "        }",
    "        final int cell = 2 * block;\n"
    "        final int firstCell = Push1DisplayLayout.firstCellForBlock (block);\n"
    "        final int index = Push1DisplayLayout.cellIndex (row, firstCell);\n"
    "        final String fitted = Push1DisplayLayout.fitBlock (value);\n"
    "        this.cells[index] = fitted.substring (0, Push1DisplayLayout.cellWidth (firstCell));\n"
    "        this.cells[index + 1] = fitted.substring (Push1DisplayLayout.cellWidth (firstCell));",
    "Push1Display dynamic 17-character block fitting")
replace_once(push_display,
    "        this.cells[row * 8 + cell] = StringUtils.pad (value, 8, ' ') + (cell % 2 == 0 ? \" \" : \"\");",
    "        this.cells[row * 8 + cell] = Push1DisplayText.fit (value, 8) + (cell % 2 == 0 ? \" \" : \"\");",
    "Push1Display dynamic cell fitting")
replace_once(push_display,
    "        this.cells[row * 8 + cell] = Push1DisplayText.fit (value, 8) + (cell % 2 == 0 ? \" \" : \"\");",
    "        this.cells[Push1DisplayLayout.cellIndex (row, cell)] = Push1DisplayLayout.fitCell (value, cell);",
    "Push 1 geometry-aware logical cell fitting")

# Keep Push 1 pan bars bounded at both ends of the eight-cell LCD field.
replace_once(push_display,
    "    private static String formatPan (final int pan, final int maxParam)\n"
    "    {\n"
    "        final int middle = maxParam / 2;\n"
    "        if (pan == middle)\n"
    "            return Push1Display.NON_4 + Push1Display.NON_4;\n"
    "        final boolean isLeft = pan < middle;\n"
    "        final int pos = isLeft ? middle - pan : pan - middle;\n"
    "        final int noOfBars = 16 * pos / maxParam;\n"
    "        StringBuilder n = new StringBuilder ();\n"
    "        for (int i = 0; i < noOfBars / 2; i++)\n"
    "            n.append (BARS_TWO);\n"
    "        if (pan >= maxParam - 1)\n"
    "            n.append (BARS_TWO);\n"
    "        else if (noOfBars % 2 == 1)\n"
    "            n.append (isLeft ? Push1Display.BARS_ONE_L : Push1Display.BARS_ONE);\n"
    "        n = new StringBuilder (Push1Display.NON_4).append (StringUtils.pad (n.toString (), 4, Push1Display.BARS_NON.charAt (0)));\n"
    "        return isLeft ? n.reverse ().toString () : n.toString ();\n"
    "    }",
    "    private static String formatPan (final int pan, final int maxParam)\n"
    "    {\n"
    "        return Push1DisplayValueFormatter.formatPan (pan, maxParam, BARS_NON, BARS_ONE, BARS_TWO, BARS_ONE_L);\n"
    "    }",
    "Push 1 bounded pan bar formatting")

# Port the API-17-compatible DrivenByMoss 19.2.2 ribbon improvements:
# Delete+touchstrip resets the active parameter, including the last-touched target.
# DrivenByMoss 19.2.2 already implements Delete+touchstrip reset.

touchstrip = root / "src/main/java/de/mossgrabers/controller/ableton/push/command/pitchbend/TouchstripCommand.java"
replace_once(touchstrip,
    "import de.mossgrabers.framework.command.core.AbstractPitchbendCommand;\n",
    "import de.mossgrabers.framework.command.core.AbstractPitchbendCommand;\n"
    "import de.mossgrabers.framework.controller.ButtonID;\n",
    "TouchstripCommand Delete binding")
replace_once(touchstrip,
    "import de.mossgrabers.framework.daw.midi.INoteRepeat;\n",
    "import de.mossgrabers.framework.daw.midi.INoteInput;\n"
    "import de.mossgrabers.framework.daw.midi.INoteRepeat;\n",
    "TouchstripCommand MIDI input guard")
replace_once(touchstrip,
    "            final INoteRepeat noteRepeat = this.surface.getMidiInput ().getDefaultNoteInput ().getNoteRepeat ();\n"
    "            if (ribbonNoteRepeat == PushConfiguration.NOTE_REPEAT_PERIOD)\n"
    "                noteRepeat.setPeriod (value);\n"
    "            else\n"
    "                noteRepeat.setNoteLength (value);\n"
    "            return;",
    "            final INoteInput defaultNoteInput = this.surface.getMidiInput ().getDefaultNoteInput ();\n"
    "            final INoteRepeat noteRepeat = defaultNoteInput == null ? null : defaultNoteInput.getNoteRepeat ();\n"
    "            if (noteRepeat == null)\n"
    "                return;\n"
    "            if (ribbonNoteRepeat == PushConfiguration.NOTE_REPEAT_PERIOD)\n"
    "                noteRepeat.setPeriod (value);\n"
    "            else\n"
    "                noteRepeat.setNoteLength (value);\n"
    "            return;",
    "TouchstripCommand null-safe note-repeat input")
replace_once(touchstrip,
    "    protected void handleRibbonMode (final int data1, final int data2, final PushConfiguration config)\n"
    "    {\n"
    "        switch (config.getRibbonMode ())",
    "    protected void handleRibbonMode (final int data1, final int data2, final PushConfiguration config)\n"
    "    {\n"
    "        final boolean isReset = this.surface.isDeletePressed ();\n"
    "        if (isReset)\n"
    "            this.surface.setTriggerConsumed (ButtonID.DELETE);\n\n"
    "        switch (config.getRibbonMode ())",
    "TouchstripCommand reset modifier")
replace_once(touchstrip,
    "            case PushConfiguration.RIBBON_MODE_FADER:\n"
    "                this.model.getCursorTrack ().setVolume (this.model.getValueChanger ().toDAWValue (data2));\n"
    "                return;",
    "            case PushConfiguration.RIBBON_MODE_FADER:\n"
    "                final IParameter volumeParameter = this.model.getCursorTrack ().getVolumeParameter ();\n"
    "                if (isReset)\n"
    "                    volumeParameter.resetValue ();\n"
    "                else\n"
    "                    volumeParameter.setValue (this.model.getValueChanger ().toDAWValue (data2));\n"
    "                return;",
    "TouchstripCommand fader reset")
# Upstream 19.2.2 already handles Delete reset for last-touched parameters.
replace_once(touchstrip,
    "            final INoteRepeat noteRepeat = this.surface.getMidiInput ().getDefaultNoteInput ().getNoteRepeat ();\n"
    "            final double value = ribbonNoteRepeat == PushConfiguration.NOTE_REPEAT_PERIOD ? noteRepeat.getPeriod () : noteRepeat.getNoteLength ();",
    "            final INoteInput defaultNoteInput = this.surface.getMidiInput ().getDefaultNoteInput ();\n"
    "            final INoteRepeat noteRepeat = defaultNoteInput == null ? null : defaultNoteInput.getNoteRepeat ();\n"
    "            if (noteRepeat == null)\n"
    "            {\n"
    "                this.surface.setRibbonValue (0);\n"
    "                return;\n"
    "            }\n"
    "            final double value = ribbonNoteRepeat == PushConfiguration.NOTE_REPEAT_PERIOD ? noteRepeat.getPeriod () : noteRepeat.getNoteLength ();",
    "TouchstripCommand null-safe note-repeat feedback")

# Bitwig may expose the default note input before its optional Note Repeat
# service exists. Do not let configuration observer initialization dereference
# that absent service.
note_repeat_setup = root / "src/main/java/de/mossgrabers/framework/controller/AbstractControllerSetup.java"
replace_once(note_repeat_setup,
    "        final INoteRepeat noteRepeat = defaultNoteInput.getNoteRepeat ();\n"
    "        conf.addSettingObserver (AbstractConfiguration.NOTEREPEAT_ACTIVE, () -> noteRepeat.setActive (conf.isNoteRepeatActive ()));",
    "        final INoteRepeat noteRepeat = defaultNoteInput.getNoteRepeat ();\n"
    "        if (noteRepeat == null)\n"
    "            return;\n"
    "        conf.addSettingObserver (AbstractConfiguration.NOTEREPEAT_ACTIVE, () -> noteRepeat.setActive (conf.isNoteRepeatActive ()));",
    "Controller setup optional Note Repeat service guard")

# Harden Push 1 note-repeat against MIDI-input initialization and capability gaps.
note_repeat = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/NoteRepeatMode.java"
replace_once(note_repeat,
    "    private final INoteRepeat noteRepeat;\n",
    "    private INoteRepeat       noteRepeat;\n",
    "NoteRepeatMode mutable input binding")
replace_once(note_repeat,
    "        final INoteInput defaultNoteInput = surface.getMidiInput ().getDefaultNoteInput ();\n"
    "        this.noteRepeat = defaultNoteInput == null ? null : defaultNoteInput.getNoteRepeat ();",
    "        this.refreshNoteRepeat ();",
    "NoteRepeatMode lazy input binding")
replace_once(note_repeat,
    "    /** {@inheritDoc} */\n    @Override\n    public void onActivate ()",
    "    /** Refresh the active MIDI note-repeat service; the default input may be initialized later. */\n"
    "    private void refreshNoteRepeat ()\n"
    "    {\n"
    "        final INoteInput defaultNoteInput = this.surface.getMidiInput ().getDefaultNoteInput ();\n"
    "        this.noteRepeat = defaultNoteInput == null ? null : defaultNoteInput.getNoteRepeat ();\n"
    "    }\n\n\n"
    "    /** {@inheritDoc} */\n    @Override\n    public void onActivate ()",
    "NoteRepeatMode input refresh helper")
replace_once(note_repeat,
    "        super.onActivate ();\n\n        this.model.getGroove ().enableObservers (true);",
    "        super.onActivate ();\n\n        this.refreshNoteRepeat ();\n        this.model.getGroove ().enableObservers (true);",
    "NoteRepeatMode activation refresh")
replace_once(note_repeat,
    "        this.setTouchedKnob (index, isTouched);\n\n        if (isTouched && this.surface.isDeletePressed ())",
    "        this.setTouchedKnob (index, isTouched);\n"
    "        this.refreshNoteRepeat ();\n\n"
    "        if (isTouched && this.surface.isDeletePressed ())",
    "NoteRepeatMode knob-touch service refresh")
replace_once(note_repeat,
    "                    if (this.host.supports (Capability.NOTE_REPEAT_MODE))\n"
    "                        this.noteRepeat.setMode (ArpeggiatorMode.UP);",
    "                    if (this.noteRepeat != null && this.host.supports (Capability.NOTE_REPEAT_MODE))\n"
    "                        this.noteRepeat.setMode (ArpeggiatorMode.UP);",
    "NoteRepeatMode mode reset requires active input")
replace_once(note_repeat,
    "                    if (this.host.supports (Capability.NOTE_REPEAT_OCTAVES))\n"
    "                        this.noteRepeat.setOctaves (1);",
    "                    if (this.noteRepeat != null && this.host.supports (Capability.NOTE_REPEAT_OCTAVES))\n"
    "                        this.noteRepeat.setOctaves (1);",
    "NoteRepeatMode octave reset requires active input")
replace_once(note_repeat,
    "    public void onFirstRow (final int index, final ButtonEvent event)\n    {\n"
    "        if (event != ButtonEvent.UP || this.noteRepeat == null)",
    "    public void onFirstRow (final int index, final ButtonEvent event)\n    {\n"
    "        this.refreshNoteRepeat ();\n"
    "        if (event != ButtonEvent.UP)",
    "NoteRepeatMode first-row service refresh")
replace_once(note_repeat,
    "    public void onSecondRow (final int index, final ButtonEvent event)\n    {\n"
    "        if (event != ButtonEvent.UP || this.noteRepeat == null)",
    "    public void onSecondRow (final int index, final ButtonEvent event)\n    {\n"
    "        if (event != ButtonEvent.UP)",
    "NoteRepeatMode Groove control independent of MIDI input")
replace_once(note_repeat,
    "    public void updateDisplay1 (final ITextDisplay display)\n    {\n",
    "    public void updateDisplay1 (final ITextDisplay display)\n    {\n"
    "        this.refreshNoteRepeat ();\n",
    "NoteRepeatMode LCD configuration without MIDI input")
replace_once(note_repeat,
    "    public void updateDisplay2 (final IGraphicDisplay display)\n    {\n"
    "        if (this.noteRepeat == null)",
    "    public void updateDisplay2 (final IGraphicDisplay display)\n    {\n"
    "        this.refreshNoteRepeat ();\n"
    "        if (this.noteRepeat == null)",
    "NoteRepeatMode graphic-display input refresh")
replace_once(note_repeat,
    "        return this.noteRepeat == null ? -1 : Resolution.getMatch (this.noteRepeat.getPeriod ());",
    "        return Resolution.getMatch (this.noteRepeat == null ? this.surface.getConfiguration ().getNoteRepeatPeriod ().getValue () : this.noteRepeat.getPeriod ());",
    "NoteRepeatMode period display uses stored configuration without MIDI input")
replace_once(note_repeat,
    "        return this.noteRepeat == null ? -1 : Resolution.getMatch (this.noteRepeat.getNoteLength ());",
    "        return Resolution.getMatch (this.noteRepeat == null ? this.surface.getConfiguration ().getNoteRepeatLength ().getValue () : this.noteRepeat.getNoteLength ());",
    "NoteRepeatMode length display uses stored configuration without MIDI input")

mode_value = "final int value = modeIndex * upperBound / (arpeggiatorModes.size () - 1);"
mode_value_replacement = (
    "final int modeDenominator = Math.max (1, arpeggiatorModes.size () - 1);\n"
    "            final int safeModeIndex = Math.max (0, Math.min (modeDenominator, modeIndex));\n"
    "            final int value = safeModeIndex * upperBound / modeDenominator;"
)
mode_source = note_repeat.read_text()
if mode_source.count(mode_value) != 2:
    raise SystemExit("NoteRepeatMode: expected two mode display scaling expressions")
note_repeat.write_text(mode_source.replace(mode_value, mode_value_replacement))

# Backport API-17-compatible Push 1 workflow improvements from DrivenByMoss 24.1:
# the track bank's volume parameters are exposed in Session/Marker modes,
# and the two unused Master-mode second-row buttons load/save the project.
session_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/SessionMode.java"
replace_once(session_mode,
    "import de.mossgrabers.controller.ableton.push.mode.track.AbstractTrackMode;\n",
    "import de.mossgrabers.controller.ableton.push.mode.track.AbstractTrackMode;\n"
    "import de.mossgrabers.framework.parameterprovider.track.VolumeParameterProvider;\n",
    "SessionMode track-volume parameter provider")
replace_once(session_mode,
    "        this.rowDisplayMode = this.isPush2 ? RowDisplayMode.ALL : RowDisplayMode.UPPER;",
    "        this.rowDisplayMode = this.isPush2 ? RowDisplayMode.ALL : RowDisplayMode.UPPER;\n"
    "        this.setParameterProvider (new VolumeParameterProvider (model));",
    "SessionMode track-volume encoder binding")

marker_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/MarkerMode.java"
replace_once(marker_mode,
    "import de.mossgrabers.framework.featuregroup.AbstractMode;\n",
    "import de.mossgrabers.framework.featuregroup.AbstractMode;\n"
    "import de.mossgrabers.framework.parameterprovider.track.VolumeParameterProvider;\n",
    "MarkerMode track-volume parameter provider")
replace_once(marker_mode,
    "        super (\"Marker\", surface, model, model.getMarkerBank ());",
    "        super (\"Marker\", surface, model, model.getMarkerBank ());\n"
    "        this.setParameterProvider (new VolumeParameterProvider (model));",
    "MarkerMode track-volume encoder binding")

master_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/track/MasterMode.java"
replace_once(master_mode,
    "        display.setCell (0, 6, \"Project:\");",
    "        display.setCell (0, 6, \"Load\").setCell (0, 7, \"Save\").setCell (1, 6, \"Project:\");",
    "Push 1 Master mode Load/Save labels")
replace_once(master_mode,
    "import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;\n",
    "import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;\n"
    "import de.mossgrabers.controller.ableton.push.controller.Push1Display;\n",
    "Push 1 Master mode display formatter import")
replace_once(master_mode,
    "        final boolean canEditCueVolume = this.model.getHost ().supports (Capability.CUE_VOLUME);",
    "        final boolean canEditCueVolume = this.model.getHost ().supports (Capability.CUE_VOLUME);\n"
    "        final int upperBound = this.model.getValueChanger ().getUpperBound ();",
    "Push 1 Master mode value display range")
replace_once(master_mode,
    "        display.setCell (2, 0, this.surface.getConfiguration ().isEnableVUMeters () ? master.getVu () : master.getVolume (), Format.FORMAT_VALUE);",
    "        display.setCell (2, 0, this.surface.getConfiguration ().isEnableVUMeters ()\n"
    "            ? Push1Display.formatValue (master.getVolume (), master.getVu (), upperBound)\n"
    "            : Push1Display.formatValue (master.getVolume (), upperBound));",
    "Push 1 Master mode volume/VU overlay")

replace_once(master_mode,
    "        display.setBlock (1, 2, \"Audio Engine\").setBlock (1, 3, this.model.getProject ().getName ());",
    "        display.setBlock (1, 2, \"Audio Engine\").setBlock (2, 3, this.model.getProject ().getName ());",
    "Push 1 Master mode project name placement")
replace_once(master_mode,
    "        display.addOptionElement (\"Project:\", \"\", false, this.model.getProject ().getName (), \"Previous\", false, false);\n"
    "        display.addOptionElement (\"\", \"\", false, \"\", \"Next\", false, false);",
    "        display.addOptionElement (\"Project:\", \"Load\", false, this.model.getProject ().getName (), \"Previous\", false, true);\n"
    "        display.addOptionElement (\"\", \"Save\", false, \"\", \"Next\", false, true);",
    "Push 1 Master mode graphical Load/Save labels")
replace_once(master_mode,
    "            if (this.isPush2 || index > 0)\n"
    "                return off;\n\n"
    "            final boolean muteState = this.surface.getConfiguration ().isMuteState ();\n"
    "            final IMasterTrack master = this.model.getMasterTrack ();\n"
    "            if (muteState)\n"
    "                return master.isMute () ? off : PushColorManager.PUSH1_COLOR2_YELLOW_HI;\n"
    "            return master.isSolo () ? PushColorManager.PUSH1_COLOR2_BLUE_HI : PushColorManager.PUSH1_COLOR2_GREY_LO;",
    "            if (this.isPush2)\n"
    "                return off;\n"
    "            switch (index)\n"
    "            {\n"
    "                case 0:\n"
    "                    final boolean muteState = this.surface.getConfiguration ().isMuteState ();\n"
    "                    final IMasterTrack master = this.model.getMasterTrack ();\n"
    "                    if (muteState)\n"
    "                        return master.isMute () ? off : PushColorManager.PUSH1_COLOR2_YELLOW_HI;\n"
    "                    return master.isSolo () ? PushColorManager.PUSH1_COLOR2_BLUE_HI : PushColorManager.PUSH1_COLOR2_GREY_LO;\n"
    "                case 6:\n"
    "                    return PushColorManager.PUSH1_COLOR_GREEN_HI;\n"
    "                case 7:\n"
    "                    return PushColorManager.PUSH1_COLOR_GREEN_LO;\n"
    "                default:\n"
    "                    return off;\n"
    "            }",
    "Push 1 Master mode Load/Save LED feedback")
replace_once(master_mode,
    "        if (event != ButtonEvent.DOWN || this.isPush2 || index > 0)\n"
    "            return;\n\n"
    "        final IMasterTrack master = this.model.getMasterTrack ();\n"
    "        if (this.surface.getConfiguration ().isMuteState ())\n"
    "            master.toggleMute ();\n"
    "        else\n"
    "            master.toggleSolo ();",
    "        if (event != ButtonEvent.DOWN || this.isPush2)\n"
    "            return;\n\n"
    "        switch (index)\n"
    "        {\n"
    "            case 0:\n"
    "                final IMasterTrack master = this.model.getMasterTrack ();\n"
    "                if (this.surface.getConfiguration ().isMuteState ())\n"
    "                    master.toggleMute ();\n"
    "                else\n"
    "                    master.toggleSolo ();\n"
    "                break;\n"
    "            case 6:\n"
    "                this.model.getProject ().load ();\n"
    "                break;\n"
    "            case 7:\n"
    "                this.model.getProject ().save ();\n"
    "                break;\n"
    "            default:\n"
    "                // Not used\n"
    "                break;\n"
    "        }",
    "Push 1 Master mode project Load/Save actions")


# Note Repeat period/length controls are stored in PushConfiguration and
# remain usable without a live MIDI note-input service.
replace_once(note_repeat,
    "final int sel = Resolution.change (Resolution.getMatch (this.noteRepeat.getPeriod ()), index == 1);",
    "final int sel = Resolution.change (Resolution.getMatch (configuration.getNoteRepeatPeriod ().getValue ()), index == 1);",
    "NoteRepeatMode period selection independent of MIDI input")
replace_once(note_repeat,
    "final int sel2 = Resolution.change (Resolution.getMatch (this.noteRepeat.getNoteLength ()), index == 3);",
    "final int sel2 = Resolution.change (Resolution.getMatch (configuration.getNoteRepeatLength ().getValue ()), index == 3);",
    "NoteRepeatMode length selection independent of MIDI input")
replace_once(note_repeat,
    "                if (this.host.supports (Capability.NOTE_REPEAT_LATCH))\n"
    "                    this.noteRepeat.toggleLatchActive ();",
    "                if (this.noteRepeat != null && this.host.supports (Capability.NOTE_REPEAT_LATCH))\n"
    "                    this.noteRepeat.toggleLatchActive ();",
    "NoteRepeatMode latch toggle requires active input")
replace_once(note_repeat,
    "                if (this.host.supports (Capability.NOTE_REPEAT_USE_PRESSURE_TO_VELOCITY))\n"
    "                    this.noteRepeat.toggleUsePressure ();",
    "                if (this.noteRepeat != null && this.host.supports (Capability.NOTE_REPEAT_USE_PRESSURE_TO_VELOCITY))\n"
    "                    this.noteRepeat.toggleUsePressure ();",
    "NoteRepeatMode pressure toggle requires active input")
replace_once(note_repeat,
    "                if (this.host.supports (Capability.NOTE_REPEAT_IS_FREE_RUNNING))\n"
    "                    this.noteRepeat.toggleIsFreeRunning ();",
    "                if (this.noteRepeat != null && this.host.supports (Capability.NOTE_REPEAT_IS_FREE_RUNNING))\n"
    "                    this.noteRepeat.toggleIsFreeRunning ();",
    "NoteRepeatMode sync toggle requires active input")
replace_once(note_repeat,
    "                if (this.host.supports (Capability.NOTE_REPEAT_SWING))\n"
    "                    this.noteRepeat.toggleShuffle ();",
    "                if (this.noteRepeat != null && this.host.supports (Capability.NOTE_REPEAT_SWING))\n"
    "                    this.noteRepeat.toggleShuffle ();",
    "NoteRepeatMode repeat shuffle toggle requires active input")
replace_once(note_repeat,
    "        if (this.host.supports (Capability.NOTE_REPEAT_LATCH))\n"
    "            display.setCell (3, 4, \" Latch\");\n\n"
    "        final int upperBound = this.model.getValueChanger ().getUpperBound ();\n"
    "        if (this.host.supports (Capability.NOTE_REPEAT_MODE))",
    "        if (this.host.supports (Capability.NOTE_REPEAT_LATCH))\n"
    "            display.setCell (3, 4, \" Latch\");\n\n"
    "        final int upperBound = this.model.getValueChanger ().getUpperBound ();\n"
    "        if (this.host.supports (Capability.NOTE_REPEAT_MODE) && this.noteRepeat != null)",
    "NoteRepeatMode mode display requires active input")
replace_once(note_repeat,
    "        if (this.host.supports (Capability.NOTE_REPEAT_OCTAVES))\n"
    "        {\n"
    "            final String bottomMenu = this.host.supports (Capability.NOTE_REPEAT_IS_FREE_RUNNING) ? \"  Sync\" : \"\";",
    "        if (this.noteRepeat == null)\n"
    "            display.setCell (0, 5, \"No MIDI\");\n\n"
    "        if (this.host.supports (Capability.NOTE_REPEAT_OCTAVES) && this.noteRepeat != null)\n"
    "        {\n"
    "            final String bottomMenu = this.host.supports (Capability.NOTE_REPEAT_IS_FREE_RUNNING) ? \"  Sync\" : \"\";",
    "NoteRepeatMode octave display requires active input")
replace_once(note_repeat,
    "                case 4:\n"
    "                    if (this.host.supports (Capability.NOTE_REPEAT_LATCH))\n"
    "                        return this.noteRepeat.isLatchActive () ? hiColor : onColor;",
    "                case 4:\n"
    "                    if (this.noteRepeat != null && this.host.supports (Capability.NOTE_REPEAT_LATCH))\n"
    "                        return this.noteRepeat.isLatchActive () ? hiColor : onColor;",
    "NoteRepeatMode latch LED requires active input")
replace_once(note_repeat,
    "                case 5:\n"
    "                    if (this.host.supports (Capability.NOTE_REPEAT_USE_PRESSURE_TO_VELOCITY))\n"
    "                        return this.noteRepeat.usePressure () ? hiColor : onColor;",
    "                case 5:\n"
    "                    if (this.noteRepeat != null && this.host.supports (Capability.NOTE_REPEAT_USE_PRESSURE_TO_VELOCITY))\n"
    "                        return this.noteRepeat.usePressure () ? hiColor : onColor;",
    "NoteRepeatMode pressure LED requires active input")
replace_once(note_repeat,
    "                case 6:\n"
    "                    if (this.host.supports (Capability.NOTE_REPEAT_IS_FREE_RUNNING))\n"
    "                        return !this.noteRepeat.isFreeRunning () ? hiColor : onColor;",
    "                case 6:\n"
    "                    if (this.noteRepeat != null && this.host.supports (Capability.NOTE_REPEAT_IS_FREE_RUNNING))\n"
    "                        return !this.noteRepeat.isFreeRunning () ? hiColor : onColor;",
    "NoteRepeatMode sync LED requires active input")
replace_once(note_repeat,
    "                case 7:\n"
    "                    if (this.host.supports (Capability.NOTE_REPEAT_SWING))\n"
    "                        return this.noteRepeat.isShuffle () ? hiColor : onColor;",
    "                case 7:\n"
    "                    if (this.noteRepeat != null && this.host.supports (Capability.NOTE_REPEAT_SWING))\n"
    "                        return this.noteRepeat.isShuffle () ? hiColor : onColor;",
    "NoteRepeatMode repeat shuffle LED requires active input")

# Keep sequencer editing within the physical 8x8 MIDI pad matrix.
sequencer = root / "src/main/java/de/mossgrabers/framework/view/sequencer/AbstractSequencerView.java"
replace_once(sequencer,
    "        final IStepInfo stepInfo = clip.getStep (notePosition);\n"
    "        if (stepInfo.getState () == StepState.OFF)\n"
    "            clip.toggleStep (notePosition, velocity);",
    "        IStepInfo stepInfo = clip.getStep (notePosition);\n"
    "        if (stepInfo == null)\n"
    "            return;\n"
    "        if (stepInfo.getState () == StepState.OFF)\n"
    "        {\n"
    "            clip.toggleStep (notePosition, velocity);\n"
    "            stepInfo = clip.getStep (notePosition);\n"
    "            if (stepInfo == null)\n"
    "                return;\n"
    "        }",
    "Sequencer repeat operator refreshes step state after note creation")

for relative in (
    "src/main/java/de/mossgrabers/controller/ableton/push/view/SequencerView.java",
    "src/main/java/de/mossgrabers/controller/ableton/push/view/PolySequencerView.java",
):
    source_file = root / relative
    replace_once(source_file,
        "        final int index = note - 36;\n"
        "        this.surface.getButton (ButtonID.get (ButtonID.PAD1, index)).setConsumed ();",
        "        final int index = note - 36;\n"
        "        if (index < 0 || index >= 64)\n"
        "            return;\n"
        "        this.surface.getButton (ButtonID.get (ButtonID.PAD1, index)).setConsumed ();",
        relative + " long-press pad bounds")

for relative in (
    "src/main/java/de/mossgrabers/controller/ableton/push/view/Drum4View.java",
    "src/main/java/de/mossgrabers/controller/ableton/push/view/Drum8View.java",
):
    source_file = root / relative
    replace_once(source_file,
        "        final int index = note - DRUM_START_KEY;\n"
        "        this.surface.getButton (ButtonID.get (ButtonID.PAD1, index)).setConsumed ();",
        "        final int index = note - DRUM_START_KEY;\n"
        "        if (index < 0 || index >= 64)\n"
        "            return;\n"
        "        this.surface.getButton (ButtonID.get (ButtonID.PAD1, index)).setConsumed ();",
        relative + " long-press pad bounds")

# The standard Loop Selector view also receives MIDI long-press callbacks;
# validate the physical 8x8 pad index before looking up a hardware button.
drum_view = root / "src/main/java/de/mossgrabers/controller/ableton/push/view/DrumView.java"
replace_once(drum_view,
    "        final int index = note - DRUM_START_KEY;\n"
    "        this.surface.getButton (ButtonID.get (ButtonID.PAD1, index)).setConsumed ();",
    "        final int index = note - DRUM_START_KEY;\n"
    "        if (index < 0 || index >= 64)\n"
    "            return;\n"
    "        this.surface.getButton (ButtonID.get (ButtonID.PAD1, index)).setConsumed ();",
    "DrumView long-press 8x8 pad bounds")

drum64 = root / "src/main/java/de/mossgrabers/framework/view/AbstractDrum64View.java"
replace_once(drum64,
    "        final int index = note - 36;\n"
    "        final int x = index % this.columns;",
    "        final int index = note - 36;\n"
    "        if (index < 0 || index >= this.columns * this.rows)\n"
    "            return;\n"
    "        final int x = index % this.columns;",
    "Drum64View incoming grid-note bounds")
replace_once(drum64,
    "        this.pressedKeys[this.offsetY + this.selectedPad] = velocity;",
    "        final int pressedNote = this.offsetY + this.selectedPad;\n"
    "        if (pressedNote >= 0 && pressedNote < this.pressedKeys.length)\n"
    "            this.pressedKeys[pressedNote] = velocity;",
    "Drum64View pressed-note array bounds")
replace_once(drum64,
    "        if (this.pressedKeys[this.offsetY + index] > 0)\n"
    "            return isRecording ? AbstractDrumView.COLOR_PAD_RECORD : AbstractDrumView.COLOR_PAD_PLAY;",
    "        final int note = this.offsetY + index;\n"
    "        if (note >= 0 && note < this.pressedKeys.length && this.pressedKeys[note] > 0)\n"
    "            return isRecording ? AbstractDrumView.COLOR_PAD_RECORD : AbstractDrumView.COLOR_PAD_PLAY;",
    "Drum64View LED lookup bounds")

# Remove the Push Raindrops mode and its framework-only implementation completely.
for obsolete in (
    root / "src/main/java/de/mossgrabers/controller/ableton/push/view/RaindropsView.java",
    root / "src/main/java/de/mossgrabers/framework/view/sequencer/AbstractRaindropsView.java",
):
    if not obsolete.is_file():
        raise SystemExit(f"Expected Raindrops source not found: {obsolete}")
    obsolete.unlink()


setup = root / "src/main/java/de/mossgrabers/controller/ableton/push/PushControllerSetup.java"
replace_once(setup,
    "import de.mossgrabers.controller.ableton.push.view.RaindropsView;\n", "",
    "PushControllerSetup Raindrops import")
replace_once(setup,
    "        viewManager.register (Views.RAINDROPS, new RaindropsView (surface, this.model));\n", "",
    "PushControllerSetup Raindrops registration")

configuration = root / "src/main/java/de/mossgrabers/controller/ableton/push/PushConfiguration.java"
replace_once(configuration, "        Views.RAINDROPS,\n", "",
    "PushConfiguration Raindrops preference")

note_view_select = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/NoteViewSelectMode.java"
replace_once(note_view_select, "        Views.RAINDROPS,\n", "        null,\n",
    "NoteViewSelectMode Raindrops slot")

# Arrange every supported game view in two balanced, contiguous left-aligned rows.
# Keep Clip Length and Program Change in their original lower-row positions (7, 8).
# The remaining cells are deliberately reserved for future Note Mode entries.
replace_once(note_view_select,
    "        Views.PLAY,\n"
    "        Views.CHORDS,\n"
    "        Views.PIANO,\n"
    "        Views.DRUM64,\n"
    "        null,\n"
    "        null,\n"
    "        Views.CLIP_LENGTH,\n"
    "        Views.PRG_CHANGE\n",
    "        Views.PLAY,\n"
    "        Views.DRUM,\n"
    "        Views.DRUM64,\n"
    "        Views.DRUM4,\n"
    "        Views.DRUM8,\n"
    "        null,\n"
    "        Views.CLIP_LENGTH,\n"
    "        Views.PRG_CHANGE\n",
    "NoteViewSelectMode bottom game-view row")
replace_once(note_view_select,
    "        Views.SEQUENCER,\n"
    "        Views.POLY_SEQUENCER,\n"
    "        null,\n"
    "        Views.DRUM,\n"
    "        Views.DRUM4,\n"
    "        Views.DRUM8,\n"
    "        null,\n"
    "        null\n",
    "        Views.SEQUENCER,\n"
    "        Views.POLY_SEQUENCER,\n"
    "        Views.PIANO,\n"
    "        Views.CHORDS,\n"
    "        null,\n"
    "        null,\n"
    "        null,\n"
    "        null\n",
    "NoteViewSelectMode top game-view row")

layout = root / "src/main/java/de/mossgrabers/controller/ableton/push/command/trigger/LayoutCommand.java"
replace_once(layout,
    "        else if (viewManager.isActive (Views.SEQUENCER))\n"
    "            this.activatePreferredView (Views.RAINDROPS);\n"
    "        else if (viewManager.isActive (Views.RAINDROPS))\n"
    "            this.activatePreferredView (Views.DRUM);",
    "        else if (viewManager.isActive (Views.SEQUENCER))\n"
    "            this.activatePreferredView (Views.DRUM);",
    "LayoutCommand Raindrops cycle")
# Keep the upstream SelectPlayViewCommand unchanged: Note remains the native
# view-selection / session-restore control and is not repurposed for Drum64.

views = root / "src/main/java/de/mossgrabers/framework/view/Views.java"
replace_once(views,
    "    /** View for raindrops sequencer. */\n    RAINDROPS,\n",
    "    /** Reserved to preserve the ordinal values of the remaining view IDs. */\n    UNUSED_VIEW_SLOT,\n",
    "Views enum compatibility slot")
for old, label in (
    ("    /** The name of the raindrops view. */\n    public static final String              NAME_RAINDROPS      = \"Raindrop\";\n", "Views Raindrops name"),
    ("        NOTE_VIEWS.add (RAINDROPS);\n", "Views note-view registration"),
    ("        VIEW_NAMES.put (NAME_RAINDROPS, RAINDROPS);\n", "Views name registration"),
    ("        SEQUENCER_VIEWS.add (RAINDROPS);\n", "Views sequencer registration"),
):
    replace_once(views, old, "", label)


# Register and compile only the Push 1 extension entry point and text display.
(root / "src/main/java/de/mossgrabers/bitwig/controller/ableton/push/Push2ControllerExtensionDefinition.java").unlink(missing_ok=True)
(root / "src/main/java/de/mossgrabers/controller/ableton/push/controller/Push2Display.java").unlink(missing_ok=True)
(root / "src/main/java/de/mossgrabers/controller/ableton/push/controller/PushUsbDisplay.java").unlink(missing_ok=True)

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
for obsolete in ("private final boolean isPush2;", "* @param isPush2 True if Push 2"):
    if setup_source.count(obsolete) != 1:
        raise SystemExit(f"PushControllerSetup: expected one obsolete declaration: {obsolete}")
    setup_source = setup_source.replace(obsolete, "", 1)
old_signature = "public PushControllerSetup (final IHost host, final ISetupFactory factory, final ISettingsUI globalSettings, final ISettingsUI documentSettings, final boolean isPush2)"
new_signature = "public PushControllerSetup (final IHost host, final ISetupFactory factory, final ISettingsUI globalSettings, final ISettingsUI documentSettings)"
if setup_source.count(old_signature) != 1:
    raise SystemExit("PushControllerSetup: expected dual-generation constructor was not found")
setup_source = setup_source.replace(old_signature, new_signature, 1)
if setup_source.count("this.isPush2 = isPush2;") != 1:
    raise SystemExit("PushControllerSetup: expected Push 2 field assignment was not found")
setup_source = setup_source.replace("this.isPush2 = isPush2;", "", 1)
setup_source = setup_source.replace("new PushColorManager (isPush2)", "new PushColorManager (false)", 1)
setup_source = setup_source.replace("new PushConfiguration (host, this.valueChanger, factory.getArpeggiatorModes (), isPush2)", "new PushConfiguration (host, this.valueChanger, factory.getArpeggiatorModes (), false)", 1)
old_config_check = "this.configuration.isPush2 ()"
if setup_source.count(old_config_check) != 1:
    raise SystemExit("PushControllerSetup: expected configuration Push 2 check was not found")
setup_source = setup_source.replace(old_config_check, "false", 1)
setup_source = setup_source.replace("this.isPush2", "false")
setup_source = setup_source.replace("isPush2", "false")
setup_source = setup_source.replace("Push 1 and Push 2 controllers", "Push 1 controller")
setup_source = setup_source.replace("Redraw the Push 2 display.", "Redraw the Push 1 display.")
setup_source = setup_source.replace("Push 2", "Push 1")
if "isPush2" in setup_source or "Push2Display" in setup_source or "Push 2" in setup_source:
    raise SystemExit("PushControllerSetup still contains Push 2 runtime references")
setup.write_text(setup_source)

# Map the physical Push 1 Page Left/Right CC controls to their Ableton In/Out
# roles in Device and Browser modes. Preserve their established Session and
# sequencer page behavior in all other contexts.
replace_once(setup,
    'this.addButton (ButtonID.PAGE_LEFT, "Page Left"',
    'this.addButton (ButtonID.PAGE_LEFT, "Out"',
    "Push 1 Out button label")
replace_once(setup,
    'this.addButton (ButtonID.PAGE_RIGHT, "Page Right"',
    'this.addButton (ButtonID.PAGE_RIGHT, "In"',
    "Push 1 In button label")
replace_once(setup,
    """PushControlSurface.PUSH_BUTTON_DEVICE_LEFT, () -> {

            if (viewManager.isActive (Views.SESSION))""",
    """PushControlSurface.PUSH_BUTTON_DEVICE_LEFT, () -> {

            final IMode activeMode = modeManager.getActive ();
            if (activeMode instanceof final DeviceBrowserMode browserMode)
                return browserMode.canNavigateFilterColumn (false);
            if (modeManager.isActive (Modes.DEVICE_PARAMS) && activeMode instanceof final DeviceParamsMode deviceParamsMode)
                return deviceParamsMode.hasPreviousInOutItem ();
            if (modeManager.isActive (Modes.DEVICE_CHAINS))
                return false;
            if (Modes.isDeviceMode (modeManager.getActiveID ()) && activeMode != null)
                return activeMode.hasPreviousItem ();
            if (viewManager.isActive (Views.SESSION))""",
    "Push 1 Out button LED state")
replace_once(setup,
    """PushControlSurface.PUSH_BUTTON_DEVICE_RIGHT, () -> {

            if (viewManager.isActive (Views.SESSION))""",
    """PushControlSurface.PUSH_BUTTON_DEVICE_RIGHT, () -> {

            final IMode activeMode = modeManager.getActive ();
            if (activeMode instanceof final DeviceBrowserMode browserMode)
                return browserMode.canNavigateFilterColumn (true);
            if (modeManager.isActive (Modes.DEVICE_PARAMS) && activeMode instanceof final DeviceParamsMode deviceParamsMode)
                return deviceParamsMode.hasNextInOutItem ();
            if (modeManager.isActive (Modes.DEVICE_CHAINS))
                return false;
            if (Modes.isDeviceMode (modeManager.getActiveID ()) && activeMode != null)
                return activeMode.hasNextItem ();
            if (viewManager.isActive (Views.SESSION))""",
    "Push 1 In button LED state")

# Keep browser filter focus, LCD marker and button availability synchronized.
browser_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceBrowserMode.java"
replace_once(browser_mode,
    """    public void resetFilterColumn ()
    {
        this.filterColumn = -1;
    }
""",
    """    public void resetFilterColumn ()
    {
        this.filterColumn = -1;
    }


    /**
     * Find the next existing browser filter column in the requested direction.
     * Bitwig exposes a fixed column bank; individual columns may not exist for
     * the current browser content type, so numeric bounds alone are insufficient.
     *
     * @param forward True for In (right), false for Out (left)
     * @return The position of the next existing column, or -1 if none is available
     */
    private int findNavigableFilterColumn (final boolean forward)
    {
        final IBrowser browser = this.model.getBrowser ();
        final int count = browser.getFilterColumnCount ();
        final int selectedPosition = browser.getSelectedFilterColumn ().getPosition ();
        final int step = forward ? 1 : -1;

        for (int position = selectedPosition + step; position >= 0 && position < count; position += step)
        {
            if (browser.getFilterColumn (position).doesExist ())
                return position;
        }
        return -1;
    }


    /**
     * Check if In/Out can move to another existing browser filter column.
     *
     * @param forward True for In (right), false for Out (left)
     * @return True if another existing column is available
     */
    public boolean canNavigateFilterColumn (final boolean forward)
    {
        return this.findNavigableFilterColumn (forward) >= 0;
    }


    /**
     * Move the browser focus to the next existing filter column.
     *
     * @param forward True for In (right), false for Out (left)
     */
    public void navigateFilterColumn (final boolean forward)
    {
        final IBrowser browser = this.model.getBrowser ();
        final int targetPosition = this.findNavigableFilterColumn (forward);
        if (targetPosition < 0)
            return;

        int selectedPosition = browser.getSelectedFilterColumn ().getPosition ();
        for (int attempts = 0; selectedPosition != targetPosition && attempts < browser.getFilterColumnCount (); attempts++)
        {
            final int previousPosition = selectedPosition;
            if (forward)
                browser.selectNextFilterColumn ();
            else
                browser.selectPreviousFilterColumn ();

            selectedPosition = browser.getSelectedFilterColumn ().getPosition ();
            // Do not trap Bitwig's controller thread if the host does not
            // publish the selected-column change synchronously.
            if (selectedPosition == previousPosition)
                return;
        }
        if (selectedPosition != targetPosition)
            return;

        this.filterColumn = browser.getSelectedFilterColumn ().getIndex ();
        this.selectionMode = DeviceBrowserMode.SELECTION_OFF;
    }
""",
    "Push 1 In/Out browser filter-column navigation")

# Keep ordinary horizontal-arrow item navigation unchanged. In/Out has a
# dedicated path so its parameter-page behavior cannot leak into PushCursorCommand.
device_params_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceParamsMode.java"
replace_once(device_params_mode,
    """    public boolean hasNextItem ()
    {
        if (this.showDevices)
            return this.model.getCursorDevice ().getDeviceBank ().canScrollForwards ();
        return super.hasNextItem ();
    }
""",
    """    public boolean hasNextItem ()
    {
        return this.showDevices
            ? this.model.getCursorDevice ().getDeviceBank ().canScrollForwards ()
            : super.hasNextItem ();
    }


    /**
     * Navigate one step using the Push 1 In/Out controls.
     * Device selection and parameter-page selection are intentionally separate
     * from the standard arrow-button selectPreviousItem/selectNextItem paths.
     */
    public void selectPreviousInOutItem ()
    {
        final ICursorDevice cursorDevice = this.model.getCursorDevice ();
        if (this.showDevices)
            cursorDevice.getDeviceBank ().selectPreviousItem ();
        else
            cursorDevice.getParameterPageBank ().selectPreviousPage ();
    }


    /** {@inheritDoc} */
    public void selectNextInOutItem ()
    {
        final ICursorDevice cursorDevice = this.model.getCursorDevice ();
        if (this.showDevices)
            cursorDevice.getDeviceBank ().selectNextItem ();
        else
            cursorDevice.getParameterPageBank ().selectNextPage ();
    }


    /** Check whether Out has a previous device or parameter-page bank. */
    public boolean hasPreviousInOutItem ()
    {
        if (this.showDevices)
            return this.model.getCursorDevice ().getDeviceBank ().canScrollBackwards ();
        return this.model.getCursorDevice ().getParameterPageBank ().canScrollPageBackwards ();
    }


    /** Check whether In has a next device or parameter-page bank. */
    public boolean hasNextInOutItem ()
    {
        if (this.showDevices)
            return this.model.getCursorDevice ().getDeviceBank ().canScrollForwards ();
        return this.model.getCursorDevice ().getParameterPageBank ().canScrollPageForwards ();
    }
""",
    "Push 1 In/Out-only device and parameter-page navigation")

# Keep the physical Push 1 New button on its native dedicated binding, while
# stopping the cursor track's current clip before preparing its next note clip.
replace_once(setup,
    "import de.mossgrabers.framework.command.trigger.clip.NewCommand;\n",
    "import de.mossgrabers.controller.ableton.push.command.trigger.PushNewCommand;\n",
    "Push 1 New command import")
replace_once(setup,
    "new NewCommand<> (this.model, surface)",
    "new PushNewCommand (this.model, surface)",
    "Push 1 New command binding")

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
