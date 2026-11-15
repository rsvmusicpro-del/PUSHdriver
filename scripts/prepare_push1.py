#!/usr/bin/env python3
"""Assemble the DrivenByMoss 19.2.2 sources required by the Push 1 extension."""
from pathlib import Path
import shutil
import subprocess

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


def replace_java_method(path: Path, signature: str, replacement: str, label: str) -> None:
    source = path.read_text()
    start = source.find(signature)
    if start < 0:
        raise SystemExit(f"{label}: method signature not found")
    brace = source.find("{", start)
    if brace < 0:
        raise SystemExit(f"{label}: method body not found")
    depth = 0
    end = None
    for position in range(brace, len(source)):
        if source[position] == "{":
            depth += 1
        elif source[position] == "}":
            depth -= 1
            if depth == 0:
                end = position + 1
                break
    if end is None:
        raise SystemExit(f"{label}: unclosed method body")
    path.write_text(source[:start] + replacement + source[end:])

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

# API banks may return null for an empty layer slot. DeviceLayerMode already
# checks existence before selecting a first-row item, and must do the same
# before applying mute/solo to a second-row selector.
device_layer_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceLayerMode.java"
replace_once(device_layer_mode,
    "            final ILayer layer = this.bank.getItem (offset + index);\n"
    "            if (this.configuration.isMuteState ())\n",
    "            final ILayer layer = this.bank.getItem (offset + index);\n"
    "            if (layer == null || !layer.doesExist ())\n"
    "                return;\n"
    "            if (this.configuration.isMuteState ())\n",
    "DeviceLayerMode empty-slot mute/solo guard")
replace_once(device_layer_mode,
    "            final ILayer layer = this.bank.getItem (offset + index);\n"
    "            if (!layer.doesExist ())\n",
    "            final ILayer layer = this.bank.getItem (offset + index);\n"
    "            if (layer == null || !layer.doesExist ())\n",
    "DeviceLayerMode empty-slot first-row guard")
replace_once(device_layer_mode,
    "                    final ILayer layer = this.bank.getItem (offset + i);\n"
    "                    final ISendBank sendBank = layer.getSendBank ();\n",
    "                    final ILayer layer = this.bank.getItem (offset + i);\n"
    "                    if (layer == null || !layer.doesExist ())\n"
    "                        continue;\n"
    "                    final ISendBank sendBank = layer.getSendBank ();\n",
    "DeviceLayerMode empty-slot send-page guard")

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



# Selected notes must remain visible when the playhead crosses them. The same
# rule applies to sustained notes: every continuation pad inherits its start's
# edit highlight until the note ends.
source = sequencer_colors.read_text()
old = """                if (highlight)
                    return this.model.hasRecordingState () ? COLOR_STEP_HILITE_RECORD : COLOR_STEP_HILITE_CONTENT;
                if (isEdit (channel, step, note, editNotes))
                    return COLOR_STEP_SELECTED;"""
new = """                if (isEditOrContinuation (channel, step, note, editNotes))
                    return COLOR_STEP_SELECTED;
                if (highlight)
                    return this.model.hasRecordingState () ? COLOR_STEP_HILITE_RECORD : COLOR_STEP_HILITE_CONTENT;"""
if source.count(old) != 2:
    raise SystemExit("AbstractSequencerView: expected two selected-note color branches")
sequencer_colors.write_text(source.replace(old, new))

poly_sequencer = root / "src/main/java/de/mossgrabers/framework/view/sequencer/AbstractPolySequencerView.java"

# The upstream Poly view is a persistent model observer. API 17 exposes no
# removeNoteObserver method, so route note feedback only to the currently
# selected view. This keeps the inactive Poly engine's KeyManager untouched
# when the user switches to the classic sequencer or another view.
replace_once(poly_sequencer,
    "        tb.addNoteObserver (this.keyManager::call);",
    "        tb.addNoteObserver ((trackIndex, note, velocity) -> {\n"
    "            if (this.surface.getViewManager ().getActive () == this)\n"
    "                this.keyManager.call (trackIndex, note, velocity);\n"
    "        });",
    "Poly sequencer active-view note observer routing")

source = poly_sequencer.read_text()
old = """                if (hilite)
                    return COLOR_STEP_HILITE_CONTENT;
                if (isChordEdit (channel, step, editNotes))
                    return COLOR_STEP_SELECTED;"""
new = """                if (isChordEdit (channel, step, editNotes))
                    return COLOR_STEP_SELECTED;
                if (hilite)
                    return COLOR_STEP_HILITE_CONTENT;"""
if source.count(old) != 2:
    raise SystemExit("AbstractPolySequencerView: expected two selected-step color branches")
poly_sequencer.write_text(source.replace(old, new))

replace_once(poly_sequencer,
    """    protected static boolean isChordEdit (final int channel, final int step, final List<NotePosition> editNotes)
    {
        for (final NotePosition editNote: editNotes)
        {
            if (editNote.getChannel () == channel && editNote.getStep () == step)
                return true;
        }
        return false;
    }""",
    """    protected boolean isChordEdit (final int channel, final int step, final List<NotePosition> editNotes)
    {
        final INoteClip clip = this.getClip ();
        for (final NotePosition editNote: editNotes)
        {
            if (editNote.getChannel () != channel || editNote.getStep () > step)
                continue;

            final NotePosition position = new NotePosition (channel, step, editNote.getNote ());
            for (int sourceStep = step; sourceStep >= editNote.getStep (); sourceStep--)
            {
                position.setStep (sourceStep);
                final StepState state = clip.getStep (position).getState ();
                if (state == StepState.START)
                {
                    if (sourceStep == editNote.getStep ())
                        return true;
                    break;
                }
                if (state == StepState.OFF)
                    break;
            }
        }
        return false;
    }""",
    "Poly sequencer edit highlight spans note continuations")

replace_once(sequencer_colors,
    """    protected static boolean isEdit (final int channel, final int step, final int note, final List<NotePosition> editNotes)
    {
        for (final NotePosition editNote: editNotes)
        {
            if (editNote.getChannel () == channel && editNote.getStep () == step && editNote.getNote () == note)
                return true;
        }
        return false;
    }""",
    """    protected static boolean isEdit (final int channel, final int step, final int note, final List<NotePosition> editNotes)
    {
        for (final NotePosition editNote: editNotes)
        {
            if (editNote.getChannel () == channel && editNote.getStep () == step && editNote.getNote () == note)
                return true;
        }
        return false;
    }


    /**
     * Test if a displayed continuation belongs to a note selected for editing.
     *
     * @param channel The MIDI channel
     * @param step The displayed step
     * @param note The MIDI note
     * @param editNotes The currently edited notes
     * @return True if the step belongs to an edited note
     */
    protected boolean isEditOrContinuation (final int channel, final int step, final int note, final List<NotePosition> editNotes)
    {
        if (isEdit (channel, step, note, editNotes))
            return true;

        final INoteClip clip = this.getClip ();
        final NotePosition position = new NotePosition (channel, step, note);
        for (final NotePosition editNote: editNotes)
        {
            if (editNote.getChannel () != channel || editNote.getNote () != note || editNote.getStep () >= step)
                continue;

            for (int sourceStep = step; sourceStep >= editNote.getStep (); sourceStep--)
            {
                position.setStep (sourceStep);
                final StepState state = clip.getStep (position).getState ();
                if (state == StepState.START)
                {
                    if (sourceStep == editNote.getStep ())
                        return true;
                    break;
                }
                if (state == StepState.OFF)
                    break;
            }
        }
        return false;
    }""",
    "Classic sequencer edit highlight spans note continuations")

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


# Add Track keeps its existing LCD layout, but the physical button rows follow
# the corresponding screen halves: ROW1 selects the upper Instrument choices;
# ROW2 selects the lower Audio/Effect choices. Patch the upstream mode in place
# during deterministic assembly; do not introduce a parallel mode override.
add_track_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/track/AddTrackMode.java"
replace_java_method(add_track_mode, "public void onFirstRow", '''    public void onFirstRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.UP)
            return;

        String channelName = null;
        final List<IDeviceMetadata> devices = new ArrayList<> ();
        if (index > 0)
        {
            final PushConfiguration conf = this.surface.getConfiguration ();
            final Optional<IDeviceMetadata> instrumentFavorite = conf.getInstrumentFavorite (index - 1);
            if (instrumentFavorite.isPresent ())
            {
                final IDeviceMetadata deviceMetadata = instrumentFavorite.get ();
                devices.add (deviceMetadata);
                channelName = deviceMetadata.name ();
            }
        }

        this.model.getTrackBank ().addChannel (ChannelType.INSTRUMENT, channelName, devices);
        this.surface.getModeManager ().restore ();
    }''', "AddTrack ROW1 Instrument selection")
replace_java_method(add_track_mode, "public void onSecondRow", '''    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.UP)
            return;

        final PushConfiguration conf = this.surface.getConfiguration ();
        final List<IDeviceMetadata> devices = new ArrayList<> ();
        final ChannelType channelType;
        String channelName = null;

        if (index < 4)
        {
            channelType = ChannelType.AUDIO;
            if (index > 0)
            {
                final Optional<IDeviceMetadata> audioFavorite = conf.getAudioFavorite (index - 1);
                if (audioFavorite.isPresent ())
                {
                    final IDeviceMetadata deviceMetadata = audioFavorite.get ();
                    devices.add (deviceMetadata);
                    channelName = deviceMetadata.name ();
                }
            }
        }
        else
        {
            channelType = ChannelType.EFFECT;
            if (index > 4)
            {
                final Optional<IDeviceMetadata> effectFavorite = conf.getEffectFavorite (index - 5);
                if (effectFavorite.isPresent ())
                {
                    final IDeviceMetadata deviceMetadata = effectFavorite.get ();
                    devices.add (deviceMetadata);
                    channelName = deviceMetadata.name ();
                }
            }
        }

        this.model.getTrackBank ().addChannel (channelType, channelName, devices);
        this.surface.getModeManager ().restore ();
    }''', "AddTrack ROW2 Audio/Effect selection")
replace_java_method(add_track_mode, "public int getButtonColor", '''    public int getButtonColor (final ButtonID buttonID)
    {
        int index = this.isButtonRow (0, buttonID);
        if (index >= 0)
            return this.isPush2 ? (index == 0 ? PushColorManager.PUSH2_COLOR2_YELLOW_HI : PushColorManager.PUSH2_COLOR2_YELLOW_LO) : PushColorManager.PUSH1_COLOR_YELLOW_MD;

        index = this.isButtonRow (1, buttonID);
        if (index >= 0)
        {
            if (index == 0)
                return this.isPush2 ? PushColorManager.PUSH2_COLOR2_GREEN_HI : PushColorManager.PUSH1_COLOR2_GREEN_HI;
            if (index < 4)
                return this.isPush2 ? PushColorManager.PUSH2_COLOR2_GREEN_LO : PushColorManager.PUSH1_COLOR2_GREEN_LO;
            if (index == 4)
                return this.isPush2 ? PushColorManager.PUSH2_COLOR2_AMBER_HI : PushColorManager.PUSH1_COLOR2_AMBER_HI;
            return this.isPush2 ? PushColorManager.PUSH2_COLOR2_AMBER_LO : PushColorManager.PUSH1_COLOR2_AMBER_LO;
        }

        return this.colorManager.getColorIndex (AbstractFeatureGroup.BUTTON_COLOR_OFF);
    }''', "AddTrack LED row mapping")

# Apply the small, reviewed Push 1 protocol override maintained by this project.
shutil.copytree(root / "overrides/java", root / "src/main/java", dirs_exist_ok=True)

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
    "    public int getButtonColor (final ButtonID buttonID)\n"
    "    {\n"
    "        int index = this.isButtonRow (0, buttonID);\n"
    "        if (index >= 0)\n"
    "        {\n"
    "            final ColorManager colorManager = this.model.getColorManager ();",
    "    public int getButtonColor (final ButtonID buttonID)\n"
    "    {\n"
    "        int index = this.isButtonRow (0, buttonID);\n"
    "        if (index >= 0)\n"
    "        {\n"
    "            this.refreshNoteRepeat ();\n"
    "            if (this.noteRepeat == null)\n"
    "                return super.getButtonColor (buttonID);\n\n"
    "            final ColorManager colorManager = this.model.getColorManager ();",
    "NoteRepeatMode per-action LED input guards")

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
    source = source_file.read_text ()
    # Project-owned sequencer engines validate their own Push pad indices.
    # Keep this assembly patch idempotent when an override already owns that guard.
    if ("Push1PadGrid.indexForNote (note)" in source
            or "this.engine.isHardwarePadIndex (index)" in source
            or "if (index < 0 || index >= 64)" in source):
        continue
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

# Keep the upstream Raindrops view registered and available to the Push 1 Note menu.
setup = root / "src/main/java/de/mossgrabers/controller/ableton/push/PushControllerSetup.java"

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
    'this.addButton (ButtonID.PAGE_LEFT, "In"',
    "Push 1 In button label")
replace_once(setup,
    'this.addButton (ButtonID.PAGE_RIGHT, "Page Right"',
    'this.addButton (ButtonID.PAGE_RIGHT, "Out"',
    "Push 1 Out button label")
replace_once(setup,
    """PushControlSurface.PUSH_BUTTON_DEVICE_LEFT, () -> {

            if (viewManager.isActive (Views.SESSION))""",
    """PushControlSurface.PUSH_BUTTON_DEVICE_LEFT, () -> {

            final IMode activeMode = modeManager.getActive ();
            if (modeManager.isActive (Modes.BROWSER) && activeMode != null)
                return activeMode.hasNextItemPage ();
            if (modeManager.isActive (Modes.DEVICE_CHAINS))
                return false;
            if (modeManager.isActive (Modes.DEVICE_PARAMS) && activeMode instanceof final DeviceParamsMode deviceParamsMode)
                return deviceParamsMode.isShowDevices () ? deviceParamsMode.hasNextItem () : deviceParamsMode.hasNextItemPage ();
            if (Modes.isDeviceMode (modeManager.getActiveID ()) && activeMode != null)
                return activeMode.hasNextItemPage ();
            if (viewManager.isActive (Views.SESSION))""",
    "Push 1 Out button LED state")
replace_once(setup,
    """PushControlSurface.PUSH_BUTTON_DEVICE_RIGHT, () -> {

            if (viewManager.isActive (Views.SESSION))""",
    """PushControlSurface.PUSH_BUTTON_DEVICE_RIGHT, () -> {

            final IMode activeMode = modeManager.getActive ();
            if (modeManager.isActive (Modes.BROWSER) && activeMode != null)
                return activeMode.hasPreviousItemPage ();
            if (modeManager.isActive (Modes.DEVICE_CHAINS))
                return false;
            if (modeManager.isActive (Modes.DEVICE_PARAMS) && activeMode instanceof final DeviceParamsMode deviceParamsMode)
                return deviceParamsMode.isShowDevices () ? deviceParamsMode.hasPreviousItem () : deviceParamsMode.hasPreviousItemPage ();
            if (Modes.isDeviceMode (modeManager.getActiveID ()) && activeMode != null)
                return activeMode.hasPreviousItemPage ();
            if (viewManager.isActive (Views.SESSION))""",
    "Push 1 In button LED state")

# DeviceBrowserMode remains the pinned upstream state machine. Only the
# defensive Bitwig empty-bank guards below are applied; no project-owned
# filter-column navigation state or methods are injected into Browser mode.
browser_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceBrowserMode.java"

# Bitwig's Browser banks can temporarily expose fewer than sixteen items while
# filters/results refresh. The upstream Ableton renderer indexes a fixed 4x4
# page, so bound every access against the current API bank before reading it.
replace_once(browser_mode,
    """                final IBrowserColumnItem [] items = browser.getFilterColumn (this.filterColumn).getItems ();
                for (int i = 0; i < 16; i++)
                {
                    String text = (items[i].isSelected () ? Push1Display.SELECT_ARROW : " ") + StringUtils.shortenAndFixASCII (items[i].getName (), 18) + "                ";
                    if (!items[i].getName ().isEmpty ())
                    {
                        final String hitStr = "(" + items[i].getHitCount () + ")";
                        text = text.substring (0, 17 - hitStr.length ()) + hitStr;
                    }
                    display.setBlock (i % 4, i / 4, text);
                }
""",
    """                final IBrowserColumn selectedColumn = browser.getFilterColumn (this.filterColumn);
                final IBrowserColumnItem [] items = selectedColumn == null ? null : selectedColumn.getItems ();
                for (int i = 0; i < 16; i++)
                {
                    if (items == null || i >= items.length || items[i] == null || !items[i].doesExist ())
                    {
                        display.setBlock (i % 4, i / 4, "");
                        continue;
                    }
                    final String itemName = items[i].getName ();
                    String text = (items[i].isSelected () ? Push1Display.SELECT_ARROW : " ") + StringUtils.shortenAndFixASCII (itemName, 18) + "                ";
                    if (itemName != null && !itemName.isEmpty ())
                    {
                        final String hitStr = "(" + items[i].getHitCount () + ")";
                        text = text.substring (0, 17 - hitStr.length ()) + hitStr;
                    }
                    display.setBlock (i % 4, i / 4, text);
                }
""",
    "Push 1 Browser text filter item bounds")
replace_once(browser_mode,
    """                final IBrowserColumnItem [] item = browser.getFilterColumn (this.filterColumn).getItems ();
                for (int i = 0; i < 8; i++)
                {
                    final String [] items = new String [6];
                    final boolean [] selected = new boolean [6];
                    for (int itemIndex = 0; itemIndex < 6; itemIndex++)
                    {
                        final int pos = i * 6 + itemIndex;
                        final String hitText = " (" + item[pos].getHitCount () + ")";
                        String text = item[pos].getName (12 - hitText.length ());
                        if (!text.isEmpty ())
                            text = text + hitText;
                        items[itemIndex] = text;
                        selected[itemIndex] = item[pos].isSelected ();
                    }
                    display.addListElement (items, selected);
                }
""",
    """                final IBrowserColumn selectedColumn = browser.getFilterColumn (this.filterColumn);
                final IBrowserColumnItem [] item = selectedColumn == null ? null : selectedColumn.getItems ();
                for (int i = 0; i < 8; i++)
                {
                    final String [] items = new String [6];
                    final boolean [] selected = new boolean [6];
                    for (int itemIndex = 0; itemIndex < 6; itemIndex++)
                    {
                        final int pos = i * 6 + itemIndex;
                        if (item == null || pos >= item.length || item[pos] == null || !item[pos].doesExist ())
                        {
                            items[itemIndex] = "";
                            selected[itemIndex] = false;
                            continue;
                        }
                        final String hitText = " (" + item[pos].getHitCount () + ")";
                        String text = item[pos].getName (12 - hitText.length ());
                        if (!text.isEmpty ())
                            text = text + hitText;
                        items[itemIndex] = text;
                        selected[itemIndex] = item[pos].isSelected ();
                    }
                    display.addListElement (items, selected);
                }
""",
    "Push 1 Browser graphic filter item bounds")

replace_once(browser_mode,
    """if (!results[0].doesExist ())
                {
                    display.clear ().setBlock (1, 1, "       No results").setBlock (1, 2, "available...").allDone ();
                    return;
                }""",
    """if (results == null || results.length == 0 || results[0] == null || !results[0].doesExist ())
                {
                    display.clear ().setBlock (1, 1, "       No results").setBlock (1, 2, "available...").allDone ();
                    return;
                }""",
    "Push 1 Browser text empty result guard")
replace_once(browser_mode,
    """if (!results[0].doesExist ())
                {
                    for (int i = 0; i < 8; i++)
                        display.addOptionElement (i == 3 ? "No results available..." : "", "", false, "", "", false, false);
                    return;
                }""",
    """if (results == null || results.length == 0 || results[0] == null || !results[0].doesExist ())
                {
                    for (int i = 0; i < 8; i++)
                        display.addOptionElement (i == 3 ? "No results available..." : "", "", false, "", "", false, false);
                    return;
                }""",
    "Push 1 Browser graphic empty result guard")
replace_once(browser_mode,
    "if (i < results.length)\n                        display.setBlock",
    "if (i < results.length && results[i] != null && results[i].doesExist ())\n                        display.setBlock",
    "Push 1 Browser text result item guard")
replace_once(browser_mode,
    "items[item] = pos < results.length ? results[pos].getName (14) : \"\";\n                        selected[item] = pos < results.length && results[pos].isSelected ();",
    "items[item] = pos < results.length && results[pos] != null && results[pos].doesExist () ? results[pos].getName (14) : \"\";\n                        selected[item] = pos < results.length && results[pos] != null && results[pos].doesExist () && results[pos].isSelected ();",
    "Push 1 Browser graphic result item guard")

# DeviceParamsMode remains byte-for-byte on the pinned upstream implementation.
# The Push 1 In/Out command adapter calls its existing item/page methods.
# Never inject a second DeviceParamsMode navigation implementation.

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



# Apply a Push 1 RGB palette to the Device action row (ROW2 / CC102-109).
# Keep the upstream Device mode as the sole implementation; this is a narrow
# source adaptation performed reproducibly during assembly.
device_params_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceParamsMode.java"
replace_once(device_params_mode,
    """            final int white = this.isPush2 ? PushColorManager.PUSH2_COLOR2_WHITE : PushColorManager.PUSH1_COLOR2_WHITE;
            if (!cd.doesExist ())
                return index == 7 ? white : super.getButtonColor (buttonID);

            final int green = this.isPush2 ? PushColorManager.PUSH2_COLOR2_GREEN : PushColorManager.PUSH1_COLOR2_GREEN;
            final int grey = this.isPush2 ? PushColorManager.PUSH2_COLOR2_GREY_LO : PushColorManager.PUSH1_COLOR2_GREY_LO;
            final int orange = this.isPush2 ? PushColorManager.PUSH2_COLOR2_ORANGE : PushColorManager.PUSH1_COLOR2_ORANGE;
            final int off = this.isPush2 ? PushColorManager.PUSH2_COLOR_BLACK : PushColorManager.PUSH1_COLOR_BLACK;
            final int turquoise = this.isPush2 ? PushColorManager.PUSH2_COLOR2_TURQUOISE_HI : PushColorManager.PUSH1_COLOR2_TURQUOISE_HI;

            switch (index)
            {
                case 0:
                    return cd.isEnabled () ? green : grey;
                case 1:
                    return cd.isParameterPageSectionVisible () ? orange : white;
                case 2:
                    return cd.isExpanded () ? orange : white;
                case 3:
                    return this.surface.getModeManager ().isActive (Modes.DEVICE_CHAINS) ? orange : white;
                case 4:
                    return this.showDevices ? white : orange;
                case 5:
                    if (!this.model.getHost ().supports (Capability.HAS_PINNING))
                        return off;
                    return cd.isPinned () ? turquoise : grey;
                case 6:
                    return cd.isWindowOpen () ? turquoise : grey;
                default:
                case 7:
                    return white;
            }""",
    """            final int off = PushColorManager.PUSH1_COLOR2_BLACK;
            final int white = PushColorManager.PUSH1_COLOR2_WHITE;
            if (!cd.doesExist ())
                return index == 7 ? white : off;

            final int greenOn = PushColorManager.PUSH1_COLOR2_GREEN_HI;
            final int greenOff = PushColorManager.PUSH1_COLOR2_GREEN_LO;
            final int blueOn = PushColorManager.PUSH1_COLOR2_BLUE_HI;
            final int blueOff = PushColorManager.PUSH1_COLOR2_BLUE_LO;
            final int magentaOn = PushColorManager.PUSH1_COLOR2_MAGENTA_HI;
            final int magentaOff = PushColorManager.PUSH1_COLOR2_MAGENTA_LO;
            final int orangeOn = PushColorManager.PUSH1_COLOR2_AMBER_HI;
            final int orangeOff = PushColorManager.PUSH1_COLOR2_AMBER_LO;
            final int yellowOn = PushColorManager.PUSH1_COLOR2_YELLOW_HI;
            final int yellowOff = PushColorManager.PUSH1_COLOR2_YELLOW_LO;
            final int redOn = PushColorManager.PUSH1_COLOR2_RED_HI;
            final int redOff = PushColorManager.PUSH1_COLOR2_RED_LO;
            final int violetOn = PushColorManager.PUSH1_COLOR2_ORCHID_HI;
            final int violetOff = PushColorManager.PUSH1_COLOR2_ORCHID_LO;

            switch (index)
            {
                case 0:
                    return cd.isEnabled () ? greenOn : greenOff;
                case 1:
                    return cd.isParameterPageSectionVisible () ? blueOn : blueOff;
                case 2:
                    return cd.isExpanded () ? magentaOn : magentaOff;
                case 3:
                    return this.surface.getModeManager ().isActive (Modes.DEVICE_CHAINS) ? orangeOn : orangeOff;
                case 4:
                    return this.showDevices ? yellowOff : yellowOn;
                case 5:
                    if (!this.model.getHost ().supports (Capability.HAS_PINNING))
                        return off;
                    return cd.isPinned () ? redOn : redOff;
                case 6:
                    return cd.isWindowOpen () ? violetOn : violetOff;
                default:
                case 7:
                    return white;
            }""",
    "Push 1 Device ROW2 semantic RGB palette")

# Align the Push 1 physical button rows with the corresponding LCD halves.
# Row 1 owns the upper display half; Row 2 owns the lower display half.
# These adaptations are deliberately local to the affected menus.
def replace_menu_fragment(path: str, old: str, new: str, label: str) -> None:
    target = root / path
    source = target.read_text()
    if new in source and old not in source:
        return
    if source.count(old) != 1:
        raise SystemExit(f"{label}: expected exactly one source block")
    target.write_text(source.replace(old, new, 1))


note_view = "src/main/java/de/mossgrabers/controller/ableton/push/mode/NoteViewSelectMode.java"
replace_menu_fragment(note_view,
    """    private static final Views [] VIEWS_TOP =
    {
        Views.SEQUENCER,
        Views.POLY_SEQUENCER,
        Views.RAINDROPS,
        Views.DRUM,
        Views.DRUM4,
        Views.DRUM8,
        null,
        null
    };""",
    """    private static final Views [] VIEWS_TOP =
    {
        Views.SEQUENCER,
        Views.POLY_SEQUENCER,
        Views.DRUM,
        Views.DRUM4,
        Views.DRUM8,
        null,
        null,
        Views.RAINDROPS
    };""",
    "NoteViewSelect upper-row layout")
replace_menu_fragment(note_view,
    """    public void onFirstRow (final int index, final ButtonEvent event)
    {
        if (event == ButtonEvent.UP)
            this.activateView (VIEWS[index]);
    }


    /** {@inheritDoc} */
    @Override
    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (event == ButtonEvent.UP)
            this.activateView (VIEWS_TOP[index]);
    }""",
    """    public void onFirstRow (final int index, final ButtonEvent event)
    {
        if (event == ButtonEvent.UP)
            this.activateView (VIEWS_TOP[index]);
    }


    /** {@inheritDoc} */
    @Override
    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (event == ButtonEvent.UP)
            this.activateView (VIEWS[index]);
    }""",
    "NoteViewSelect row actions")
replace_menu_fragment(note_view,
    """        int index = this.isButtonRow (0, buttonID);
        if (index >= 0)
        {
            final ViewManager viewManager = this.surface.getViewManager ();
            if (VIEWS[index] == null)
                return AbstractFeatureGroup.BUTTON_COLOR_OFF;
            return viewManager.isActive (VIEWS[index]) ? AbstractMode.BUTTON_COLOR_HI : AbstractFeatureGroup.BUTTON_COLOR_ON;
        }

        index = this.isButtonRow (1, buttonID);
        if (index >= 0)
        {
            final ViewManager viewManager = this.surface.getViewManager ();
            if (VIEWS_TOP[index] == null)
                return AbstractFeatureGroup.BUTTON_COLOR_OFF;
            return viewManager.isActive (VIEWS_TOP[index]) ? AbstractMode.BUTTON_COLOR_HI : AbstractFeatureGroup.BUTTON_COLOR_ON;
        }""",
    """        int index = this.isButtonRow (0, buttonID);
        if (index >= 0)
        {
            final ViewManager viewManager = this.surface.getViewManager ();
            if (VIEWS_TOP[index] == null)
                return AbstractFeatureGroup.BUTTON_COLOR_OFF;
            return viewManager.isActive (VIEWS_TOP[index]) ? de.mossgrabers.controller.ableton.push.controller.PushColorManager.PUSH_YELLOW_LO : de.mossgrabers.controller.ableton.push.controller.PushColorManager.PUSH_GREEN_HI;
        }

        index = this.isButtonRow (1, buttonID);
        if (index >= 0)
        {
            final ViewManager viewManager = this.surface.getViewManager ();
            if (VIEWS[index] == null)
                return AbstractFeatureGroup.BUTTON_COLOR_OFF;
            return viewManager.isActive (VIEWS[index]) ? de.mossgrabers.controller.ableton.push.controller.PushColorManager.PUSH_YELLOW_LO : de.mossgrabers.controller.ableton.push.controller.PushColorManager.PUSH_GREEN_HI;
        }""",
    "NoteViewSelect row LEDs")


session_view = "src/main/java/de/mossgrabers/controller/ableton/push/mode/SessionViewSelectMode.java"
replace_menu_fragment(session_view,
    """            case 6:
                this.surface.getModeManager ().setActive (Modes.MARKERS);
                break;

            case 7:
                this.surface.getModeManager ().restore ();
                configuration.toggleScenesClipMode ();
                break;

""",
    "",
    "SessionView move lower actions to Row 2")
replace_menu_fragment(session_view,
    """    /** {@inheritDoc} */
    @Override
    public void updateDisplay1 (final ITextDisplay display)""",
    """    /** {@inheritDoc} */
    @Override
    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.UP)
            return;
        if (index == 6)
            this.surface.getModeManager ().setActive (Modes.MARKERS);
        else if (index == 7)
        {
            this.surface.getModeManager ().restore ();
            this.surface.getConfiguration ().toggleScenesClipMode ();
        }
    }


    /** {@inheritDoc} */
    @Override
    public void updateDisplay1 (final ITextDisplay display)""",
    "SessionView Row 2 actions")
replace_menu_fragment(session_view,
    "display.setCell (3, i, (this.isSelected (viewManager, i) ? Push1Display.SELECT_ARROW : \"\") + VIEW_NAMES[i]);",
    "display.setCell (0, i, (this.isSelected (viewManager, i) ? Push1Display.SELECT_ARROW : \"\") + VIEW_NAMES[i]);",
    "SessionView upper LCD row")
replace_menu_fragment(session_view,
    """display.addOptionElement ("", "", false, i == 0 ? "Session view" : "", VIEW_NAMES[i], isMenuBottomSelected, false);""",
    """display.addOptionElement (i == 0 ? "Session view" : "", VIEW_NAMES[i], isMenuBottomSelected, "", "", false, false);""",
    "SessionView upper graphic row")
replace_menu_fragment(session_view,
    """    public String getButtonColorID (final ButtonID buttonID)
    {
        final int index = this.isButtonRow (0, buttonID);
        if (index >= 0)
        {
            if (index < VIEWS.length)
            {
                if (VIEWS[index] == null)
                    return AbstractFeatureGroup.BUTTON_COLOR_OFF;
                final ViewManager viewManager = this.surface.getViewManager ();
                return this.isSelected (viewManager, index) ? AbstractMode.BUTTON_COLOR_HI : AbstractFeatureGroup.BUTTON_COLOR_ON;
            }

            if (index == 6)
                return AbstractFeatureGroup.BUTTON_COLOR_ON;
            if (index == 7)
            {
                final boolean isOn = this.surface.getModeManager ().isActive (Modes.SESSION);
                return isOn ? AbstractMode.BUTTON_COLOR_HI : AbstractFeatureGroup.BUTTON_COLOR_ON;
            }
            return AbstractFeatureGroup.BUTTON_COLOR_OFF;
        }

        return AbstractFeatureGroup.BUTTON_COLOR_OFF;
    }""",
    """    public String getButtonColorID (final ButtonID buttonID)
    {
        int index = this.isButtonRow (0, buttonID);
        if (index >= 0 && index < VIEWS.length)
        {
            if (VIEWS[index] == null)
                return AbstractFeatureGroup.BUTTON_COLOR_OFF;
            final ViewManager viewManager = this.surface.getViewManager ();
            return this.isSelected (viewManager, index) ? AbstractMode.BUTTON_COLOR_HI : AbstractFeatureGroup.BUTTON_COLOR_ON;
        }

        index = this.isButtonRow (1, buttonID);
        if (index == 6)
            return AbstractFeatureGroup.BUTTON_COLOR_ON;
        if (index == 7)
        {
            final boolean isOn = this.surface.getModeManager ().isActive (Modes.SESSION);
            return isOn ? AbstractMode.BUTTON_COLOR_HI : AbstractFeatureGroup.BUTTON_COLOR_ON;
        }
        return AbstractFeatureGroup.BUTTON_COLOR_OFF;
    }""",
    "SessionView row LED ownership")


scales_mode = "src/main/java/de/mossgrabers/controller/ableton/push/mode/ScalesMode.java"
replace_menu_fragment(scales_mode,
    """    public void onFirstRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.UP)
            return;
        if (index == 0)
        {
            if (this.isPush2)
                this.scales.nextScale ();
            else
                this.scales.prevScale ();
        }
        else if (index > 0 && index < 7)
            this.scales.setScaleOffsetByIndex (index - 1);
        this.update ();
    }""",
    """    public void onFirstRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.UP)
            return;
        if (index == 0)
        {
            if (this.isPush2)
                this.scales.nextScale ();
            else
                this.scales.prevScale ();
        }
        else if (index > 0 && index < 7)
            this.scales.setScaleOffsetByIndex (index + 5);
        this.update ();
    }""",
    "Scales Row 1 upper offsets")
replace_menu_fragment(scales_mode,
    """    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.UP)
            return;
        if (index == 0)
        {
            if (this.isPush2)
                this.scales.prevScale ();
            else
                this.scales.nextScale ();
        }
        else if (index == 7)
            this.scales.toggleChromatic ();
        else
            this.scales.setScaleOffsetByIndex (index + 5);
        this.update ();
    }""",
    """    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.UP)
            return;
        if (index == 0)
        {
            if (this.isPush2)
                this.scales.prevScale ();
            else
                this.scales.nextScale ();
        }
        else if (index == 7)
            this.scales.toggleChromatic ();
        else
            this.scales.setScaleOffsetByIndex (index - 1);
        this.update ();
    }""",
    "Scales Row 2 lower offsets")
replace_menu_fragment(scales_mode,
    """            final int offset = this.scales.getScaleOffsetIndex ();
            return this.colorManager.getColorIndex (offset == index - 1 ? AbstractMode.BUTTON_COLOR_HI : AbstractFeatureGroup.BUTTON_COLOR_ON);
        }

        index = this.isButtonRow (1, buttonID);""",
    """            final int offset = this.scales.getScaleOffsetIndex ();
            return this.colorManager.getColorIndex (offset == index + 5 ? AbstractMode.BUTTON_COLOR_HI : AbstractFeatureGroup.BUTTON_COLOR_ON);
        }

        index = this.isButtonRow (1, buttonID);""",
    "Scales Row 1 LED state")
replace_menu_fragment(scales_mode,
    """            final int offset = this.scales.getScaleOffsetIndex ();
            return this.colorManager.getColorIndex (offset == index - 1 + 6 ? AbstractMode.BUTTON_COLOR2_HI : AbstractMode.BUTTON_COLOR2_ON);""",
    """            final int offset = this.scales.getScaleOffsetIndex ();
            return this.colorManager.getColorIndex (offset == index - 1 ? AbstractMode.BUTTON_COLOR2_HI : AbstractMode.BUTTON_COLOR2_ON);""",
    "Scales Row 2 LED state")


fixed_mode = "src/main/java/de/mossgrabers/controller/ableton/push/mode/FixedMode.java"
replace_menu_fragment(fixed_mode,
    """    public void onFirstRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.UP)
            return;
        this.surface.getConfiguration ().setNewClipLength (index);
        this.surface.getModeManager ().restore ();
    }


    /** {@inheritDoc} */
    @Override
    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.UP)
            return;
        this.newCommands[index].execute ();
        this.surface.getModeManager ().restore ();
    }""",
    """    public void onFirstRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.UP)
            return;
        this.newCommands[index].execute ();
        this.surface.getModeManager ().restore ();
    }


    /** {@inheritDoc} */
    @Override
    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.UP)
            return;
        this.surface.getConfiguration ().setNewClipLength (index);
        this.surface.getModeManager ().restore ();
    }""",
    "FixedMode row actions")
replace_menu_fragment(fixed_mode,
    """        final int index = this.isButtonRow (0, buttonID);
        if (index >= 0)
        {
            final Configuration configuration = this.surface.getConfiguration ();
            return this.colorManager.getColorIndex (configuration.getNewClipLength () == index ? AbstractMode.BUTTON_COLOR_HI : AbstractFeatureGroup.BUTTON_COLOR_ON);
        }""",
    """        final int index = this.isButtonRow (1, buttonID);
        if (index >= 0)
        {
            final Configuration configuration = this.surface.getConfiguration ();
            return this.colorManager.getColorIndex (configuration.getNewClipLength () == index ? AbstractMode.BUTTON_COLOR_HI : AbstractFeatureGroup.BUTTON_COLOR_ON);
        }""",
    "FixedMode lower-row length LED feedback")


quantize_mode = "src/main/java/de/mossgrabers/controller/ableton/push/mode/QuantizeMode.java"
replace_menu_fragment(quantize_mode,
    """    public void onFirstRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.UP)
            return;

        final ITrack cursorTrack = this.model.getCursorTrack ();
        if (!cursorTrack.doesExist ())
            return;

        switch (index)
        {
            case 0, 1, 2, 3, 4:
                cursorTrack.setRecordQuantizationGrid (RecordQuantization.values ()[index]);
                break;

            case 6:
                cursorTrack.toggleRecordQuantizationNoteLength ();
                break;

            default:
                // Not used
                break;
        }
    }


    /** {@inheritDoc} */
    @Override
    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.UP)
            return;

        if (index == 1)
            this.surface.getModeManager ().setTemporary (Modes.GROOVE);
    }""",
    """    public void onFirstRow (final int index, final ButtonEvent event)
    {
        if (event == ButtonEvent.UP && index == 1)
            this.surface.getModeManager ().setTemporary (Modes.GROOVE);
    }


    /** {@inheritDoc} */
    @Override
    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.UP)
            return;

        final ITrack cursorTrack = this.model.getCursorTrack ();
        if (!cursorTrack.doesExist ())
            return;

        switch (index)
        {
            case 0, 1, 2, 3, 4:
                cursorTrack.setRecordQuantizationGrid (RecordQuantization.values ()[index]);
                break;

            case 6:
                cursorTrack.toggleRecordQuantizationNoteLength ();
                break;

            default:
                // Not used
                break;
        }
    }""",
    "Quantize row actions")
replace_menu_fragment(quantize_mode,
    """        int index = this.isButtonRow (0, buttonID);
        if (index >= 0)
        {
            final RecordQuantization [] values = RecordQuantization.values ();
            final ITrack cursorTrack = this.model.getCursorTrack ();
            final RecordQuantization recQuant = cursorTrack.doesExist () ? cursorTrack.getRecordQuantizationGrid () : RecordQuantization.RES_OFF;
            if (index < values.length)
                return values[index] == recQuant ? AbstractMode.BUTTON_COLOR_HI : AbstractFeatureGroup.BUTTON_COLOR_ON;
            if (index == 6)
                return cursorTrack.isRecordQuantizationNoteLength () ? AbstractMode.BUTTON_COLOR_HI : AbstractFeatureGroup.BUTTON_COLOR_ON;
            return AbstractFeatureGroup.BUTTON_COLOR_OFF;
        }

        index = this.isButtonRow (1, buttonID);""",
    """        int index = this.isButtonRow (1, buttonID);
        if (index >= 0)
        {
            final RecordQuantization [] values = RecordQuantization.values ();
            final ITrack cursorTrack = this.model.getCursorTrack ();
            final RecordQuantization recQuant = cursorTrack.doesExist () ? cursorTrack.getRecordQuantizationGrid () : RecordQuantization.RES_OFF;
            if (index < values.length)
                return values[index] == recQuant ? AbstractMode.BUTTON_COLOR_HI : AbstractFeatureGroup.BUTTON_COLOR_ON;
            if (index == 6)
                return cursorTrack.isRecordQuantizationNoteLength () ? AbstractMode.BUTTON_COLOR_HI : AbstractFeatureGroup.BUTTON_COLOR_ON;
            return AbstractFeatureGroup.BUTTON_COLOR_OFF;
        }

        index = this.isButtonRow (0, buttonID);""",
    "Quantize row LED feedback")



# PUSHdriver custom palette: 64 static Push 1 pad colors, shared with Bitwig RGB.
# RGB values are read from the pinned upstream hardware palette so the DAW
# palette cannot drift away from the controller's actual LED colors.
import re

push_color_manager = root / "src/main/java/de/mossgrabers/controller/ableton/push/controller/PushColorManager.java"
push_color_source = push_color_manager.read_text()
palette_match = re.search(r"DEFAULT_PALETTE\s*=\s*\{([\s\S]*?)\n\s*\};", push_color_source)
if palette_match is None:
    raise SystemExit("Push 1 color palette: DEFAULT_PALETTE was not found")
push_rgb = re.findall(r"\{\s*0x([0-9A-Fa-f]+),\s*0x([0-9A-Fa-f]+),\s*0x([0-9A-Fa-f]+)\s*\}", palette_match.group(1))
if len(push_rgb) < 128:
    raise SystemExit(f"Push 1 color palette: expected at least 128 RGB entries, found {len(push_rgb)}")

# Keep exactly 64 menu entries, replacing dark/weak slots with vivid colors
# already present in the Push 1 hardware palette. No synthetic RGB is used.
push1_hardware_indices = [0,1,2,3,4,5,107,95,8,9,108,84,12,13,73,109,16,17,74,75,20,21,85,86,24,25,88,89,28,29,77,90,32,33,34,35,36,37,79,92,40,41,80,81,44,45,116,94,48,49,82,98,52,53,110,111,56,57,122,96,60,61,62,63]
if len(set(push1_hardware_indices)) != 64:
    raise SystemExit("Push 1 color palette: hardware indices must be unique")

push1_color_names = [
    ("COLOR_OFF", "Black"),
    ("DAW_COLOR_DARK_GRAY", "Charcoal"),
    ("DAW_COLOR_GRAY", "Gray"),
    ("DAW_COLOR_GRAY_HALF", "White"),
    ("DAW_COLOR_LIGHT_GRAY", "Coral"),
    ("DAW_COLOR_SILVER", "Red"),
    ("DAW_COLOR_DARK_BROWN", "Dark Red"),
    ("DAW_COLOR_BROWN", "Deep Red"),
    ("DAW_COLOR_DARK_BLUE", "Peach"),
    ("DAW_COLOR_PURPLE_BLUE", "Orange"),
    ("DAW_COLOR_PURPLE", "Brown"),
    ("DAW_COLOR_PINK", "Dark Brown"),
    ("DAW_COLOR_RED", "Lemon"),
    ("DAW_COLOR_ORANGE", "Yellow"),
    ("DAW_COLOR_LIGHT_ORANGE", "Olive"),
    ("DAW_COLOR_MOSS_GREEN", "Dark Olive"),
    ("DAW_COLOR_GREEN", "Chartreuse"),
    ("DAW_COLOR_COLD_GREEN", "Lime"),
    ("DAW_COLOR_BLUE", "Forest Green"),
    ("DAW_COLOR_LIGHT_PURPLE", "Deep Forest"),
    ("DAW_COLOR_LIGHT_PINK", "Bright Green"),
    ("DAW_COLOR_ROSE", "Green"),
    ("DAW_COLOR_REDDISH_BROWN", "Dark Green"),
    ("DAW_COLOR_LIGHT_BROWN", "Deep Green"),
    ("DAW_COLOR_LIGHT_GREEN", "Mint"),
    ("DAW_COLOR_BLUISH_GREEN", "Neon Green"),
    ("DAW_COLOR_GREEN_BLUE", "Deep Moss"),
    ("DAW_COLOR_LIGHT_BLUE", "Night Green"),
    ("PUSH1_COLOR_28_MINT_GREEN", "Mint Green"),
    ("PUSH1_COLOR_29_SPRING_GREEN", "Spring Green"),
    ("PUSH1_COLOR_30_DARK_TEAL", "Dark Teal"),
    ("PUSH1_COLOR_31_DEEP_TEAL", "Deep Teal"),
    ("PUSH1_COLOR_32_AQUA_MINT", "Aqua Mint"),
    ("PUSH1_COLOR_33_AQUA", "Aqua"),
    ("PUSH1_COLOR_34_DEEP_AQUA", "Deep Aqua"),
    ("PUSH1_COLOR_35_NIGHT_TEAL", "Night Teal"),
    ("PUSH1_COLOR_36_SKY_BLUE", "Sky Blue"),
    ("PUSH1_COLOR_37_AZURE", "Azure"),
    ("PUSH1_COLOR_38_DEEP_TEAL_BLUE", "Deep Teal Blue"),
    ("PUSH1_COLOR_39_MIDNIGHT_BLUE", "Midnight Blue"),
    ("PUSH1_COLOR_40_CORNFLOWER", "Cornflower"),
    ("PUSH1_COLOR_41_BLUE", "Blue"),
    ("PUSH1_COLOR_42_NAVY", "Navy"),
    ("PUSH1_COLOR_43_MIDNIGHT_NAVY", "Midnight Navy"),
    ("PUSH1_COLOR_44_PERIWINKLE", "Periwinkle"),
    ("PUSH1_COLOR_45_ROYAL_BLUE", "Royal Blue"),
    ("PUSH1_COLOR_46_DEEP_BLUE", "Deep Blue"),
    ("PUSH1_COLOR_47_DARK_NAVY", "Dark Navy"),
    ("PUSH1_COLOR_48_VIOLET", "Violet"),
    ("PUSH1_COLOR_49_PURPLE", "Purple"),
    ("PUSH1_COLOR_50_DEEP_PURPLE", "Deep Purple"),
    ("PUSH1_COLOR_51_DARK_PURPLE", "Dark Purple"),
    ("PUSH1_COLOR_52_MAGENTA", "Magenta"),
    ("PUSH1_COLOR_53_FUCHSIA", "Fuchsia"),
    ("PUSH1_COLOR_54_DEEP_MAGENTA", "Deep Magenta"),
    ("PUSH1_COLOR_55_DARK_MAGENTA", "Dark Magenta"),
    ("PUSH1_COLOR_56_HOT_PINK", "Hot Pink"),
    ("PUSH1_COLOR_57_PINK", "Pink"),
    ("PUSH1_COLOR_58_DEEP_PINK", "Deep Pink"),
    ("PUSH1_COLOR_59_WINE", "Wine"),
    ("PUSH1_COLOR_60_VERMILION", "Vermilion"),
    ("PUSH1_COLOR_61_BURNT_ORANGE", "Burnt Orange"),
    ("PUSH1_COLOR_62_OCHRE", "Ochre"),
    ("PUSH1_COLOR_63_OLIVE_GREEN", "Olive Green"),
]
for slot, display_name in {"6":"Coral Red","7":"Hot Pink","10":"Burnt Orange","11":"Vivid Orange","14":"Neon Yellow","15":"Electric Yellow","18":"Acid Green","19":"Neon Lime","22":"Bright Chartreuse","23":"Electric Lime","26":"Spring Green","27":"Vivid Lime","30":"Bright Mint","31":"Turquoise","38":"Electric Blue","39":"Cobalt","42":"Electric Indigo","43":"Ultramarine","46":"Bright Violet","47":"Magenta Violet","50":"Berry","51":"Neon Green","54":"Hot Magenta","55":"Lime Green","58":"Golden Green","59":"Electric Orange"}.items():
    constant, _ = push1_color_names[slot]
    push1_color_names[slot] = (constant, display_name)

daw_color = root / "src/main/java/de/mossgrabers/framework/daw/DAWColor.java"
daw_source = daw_color.read_text()
enum_start = daw_source.index("{", daw_source.index("public enum DAWColor")) + 1
enum_end = daw_source.index("\n\n\n    private static final List", enum_start)
enum_entries = []
for index, (constant, display_name) in enumerate(push1_color_names):
    hardware_index = push1_hardware_indices[index]
    red, green, blue = (int(component, 16) for component in push_rgb[hardware_index])
    suffix = ";" if index == len(push1_color_names) - 1 else ","
    enum_entries.append(
        f'    /** Push 1 palette color {index}: {display_name}. */\n'
        f'    {constant}("{display_name}", new ColorEx ({red / 255.0:.12g}, {green / 255.0:.12g}, {blue / 255.0:.12g})){suffix}'
    )
daw_source = daw_source[:enum_start] + "\n" + "\n".join(enum_entries) + daw_source[enum_end:]
daw_color.write_text(daw_source)

# The enum order is the physical Push 1 palette order: Bitwig RGB and pad LED
# index are therefore a one-to-one mapping for all 64 choices.
registration_start = push_color_source.index("        this.registerColorIndex (DAWColor.COLOR_OFF,")
registration_end_marker = "        this.registerColorIndex (DAWColor.DAW_COLOR_LIGHT_BLUE, isPush2 ? 96 : 41);"
registration_end = push_color_source.index(registration_end_marker, registration_start) + len(registration_end_marker)
registration = (
    "        final int [] push1PaletteIndices = new int [] { 0, 1, 2, 3, 4, 5, 107, 95, 8, 9, 108, 84, 12, 13, 73, 109, 16, 17, 74, 75, 20, 21, 85, 86, 24, 25, 88, 89, 28, 29, 77, 90, 32, 33, 34, 35, 36, 37, 79, 92, 40, 41, 80, 81, 44, 45, 116, 94, 48, 49, 82, 98, 52, 53, 110, 111, 56, 57, 122, 96, 60, 61, 62, 63 };\\n"
    "        for (int i = 0; i < DAWColor.values ().length; i++)\\n"
    "            this.registerColorIndex (DAWColor.values ()[i], push1PaletteIndices[i]);"
)
push_color_source = push_color_source[:registration_start] + registration + push_color_source[registration_end:]
push_color_manager.write_text(push_color_source)



# Defer all Push 1 label shortening until the physical cell/block width is known.
# Keep the upstream ASCII transliteration and selected-item markers unchanged.
track_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/track/AbstractTrackMode.java"
replace_once(track_mode,
    "            final String n = StringUtils.shortenAndFixASCII (trackName, isSel ? 7 : 8);\n"
    "            d.setCell (3, i, isSel ? Push1Display.SELECT_ARROW + n : n);",
    "            final String n = de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fitAscii ((isSel ? Push1Display.SELECT_ARROW : \"\") + trackName, 8);\n"
    "            d.setCell (3, i, n);",
    "Push 1 track names truncate after selection marker")

device_layer_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceLayerMode.java"
replace_once(device_layer_mode,
    "            final String sendName = StringUtils.optimizeName (layerBank.getEditSendName (i), 12);",
    "            final String sendName = de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fit (layerBank.getEditSendName (i), 12);",
    "Push 1 layer send names use display fitter")
replace_once(device_layer_mode,
    "            final String n = StringUtils.shortenAndFixASCII (layer.getName (), layer.isSelected () ? 7 : 8);\n"
    "            display.setCell (3, i, layer.isSelected () ? Push1Display.SELECT_ARROW + n : n);",
    "            final String n = de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fitAscii ((layer.isSelected () ? Push1Display.SELECT_ARROW : \"\") + layer.getName (), 8);\n"
    "            display.setCell (3, i, n);",
    "Push 1 layer names truncate after selection marker")

session_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/SessionMode.java"
replace_once(session_mode,
    "                final String n = StringUtils.shortenAndFixASCII (scene.getName (8), isSel ? 7 : 8);\n"
    "                display.setCell (row, col, isSel ? Push1Display.SELECT_ARROW + n : n);",
    "                final String n = de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fitAscii ((isSel ? Push1Display.SELECT_ARROW : \"\") + scene.getName (), 8);\n"
    "                display.setCell (row, col, n);",
    "Push 1 scene names truncate after selection marker")
replace_once(session_mode,
    "                String name = slot.getName (8);",
    "                String name = slot.getName ();",
    "Push 1 clip names retain full source text")
replace_once(session_mode,
    "                display.setCell (row, col, StringUtils.shortenAndFixASCII (name, 8));",
    "                display.setCell (row, col, de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fitAscii (name, 8));",
    "Push 1 clip names use display fitter")

marker_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/MarkerMode.java"
replace_once(marker_mode,
    "                display.setCell (3, i, StringUtils.shortenAndFixASCII (getMarkerName (marker), 8));",
    "                display.setCell (3, i, de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fitAscii (getMarkerName (marker), 8));",
    "Push 1 marker names use display fitter")
replace_once(marker_mode,
    "            final String menuBottomName = StringUtils.shortenAndFixASCII (getMarkerName (marker), 12);",
    "            final String menuBottomName = de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fitAscii (getMarkerName (marker), 12);",
    "Push 1 marker menu names use display fitter")

browser_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceBrowserMode.java"
replace_once(browser_mode,
    "                selectedResult = selectedResult == null || selectedResult.isBlank () ? \"None\" : StringUtils.shortenAndFixASCII (selectedResult, 18);",
    "                selectedResult = selectedResult == null || selectedResult.isBlank () ? \"None\" : de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fitAscii (selectedResult, 18);",
    "Push 1 browser selected result uses display fitter")
replace_once(browser_mode,
    "                    String name = column.isEmpty () ? \"\" : StringUtils.shortenAndFixASCII (column.get ().getName (), 8);\n"
    "                    if (i == this.filterColumn)\n"
    "                        name = Push1Display.SELECT_ARROW + name;\n"
    "                    display.setCell (0, i, name).setCell (1, i, getColumnName (column));",
    "                    String name = column.isEmpty () ? \"\" : column.get ().getName ();\n"
    "                    if (i == this.filterColumn)\n"
    "                        name = Push1Display.SELECT_ARROW + name;\n"
    "                    display.setCell (0, i, de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fitAscii (name, 8)).setCell (1, i, getColumnName (column));",
    "Push 1 browser filter names use display fitter")
replace_once(browser_mode,
    "                        display.setBlock (i % 4, i / 4, (results[i].isSelected () ? Push1Display.SELECT_ARROW : \" \") + StringUtils.shortenAndFixASCII (results[i].getName (), 16));",
    "                        display.setBlock (i % 4, i / 4, de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fitAscii ((results[i].isSelected () ? Push1Display.SELECT_ARROW : \" \") + results[i].getName (), 17));",
    "Push 1 browser preset names use complete block width")
replace_once(browser_mode,
    "                    final String itemName = items[i].getName ();\n"
    "                    String text = (items[i].isSelected () ? Push1Display.SELECT_ARROW : \" \") + StringUtils.shortenAndFixASCII (itemName, 18) + \"                \";\n"
    "                    if (itemName != null && !itemName.isEmpty ())\n"
    "                    {\n"
    "                        final String hitStr = \"(\" + items[i].getHitCount () + \")\";\n"
    "                        text = text.substring (0, 17 - hitStr.length ()) + hitStr;\n"
    "                    }\n"
    "                    display.setBlock (i % 4, i / 4, text);",
    "                    final String itemName = items[i].getName ();\n"
    "                    final String hitStr = itemName == null || itemName.isEmpty () ? \"\" : \"(\" + items[i].getHitCount () + \")\";\n"
    "                    final String text = (items[i].isSelected () ? Push1Display.SELECT_ARROW : \" \")\n"
    "                        + de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fitAscii (itemName, 16 - hitStr.length ()) + hitStr;\n"
    "                    display.setBlock (i % 4, i / 4, text);",
    "Push 1 browser filter names preserve hit counts after ellipsis fitting")

note_view_select = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/NoteViewSelectMode.java"
replace_once(note_view_select,
    "                    value = (viewManager.isActive (VIEWS[i]) ? Push1Display.SELECT_ARROW : \"\") + StringUtils.optimizeName (view.getName (), 8);",
    "                    value = de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fit ((viewManager.isActive (VIEWS[i]) ? Push1Display.SELECT_ARROW : \"\") + view.getName (), 8);",
    "Push 1 note view names use complete cell width")
replace_once(note_view_select,
    "                    value = (viewManager.isActive (VIEWS_TOP[i]) ? Push1Display.SELECT_ARROW : \"\") + StringUtils.optimizeName (view.getName (), 8);",
    "                    value = de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fit ((viewManager.isActive (VIEWS_TOP[i]) ? Push1Display.SELECT_ARROW : \"\") + view.getName (), 8);",
    "Push 1 top note view names use complete cell width")

note_repeat = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/NoteRepeatMode.java"
old_mode_name = "StringUtils.optimizeName (mode.getName (), 8)"
new_mode_name = "de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fit (mode.getName (), 8)"
source = note_repeat.read_text ()
if new_mode_name not in source or old_mode_name in source:
    if source.count (old_mode_name) != 2:
        raise SystemExit ("Push 1 arpeggiator mode names: expected exactly two early-truncation call sites")
    note_repeat.write_text (source.replace (old_mode_name, new_mode_name))

note_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/NoteMode.java"
replace_once(note_mode,
    "StringUtils.optimizeName (occurrence.getName (), 8)",
    "de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fit (occurrence.getName (), 8)",
    "Push 1 occurrence names use display fitter")
replace_once(note_mode,
    "StringUtils.optimizeName (occurrence.getName (), 9)",
    "de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fit (occurrence.getName (), 9)",
    "Push 1 occurrence menu names use display fitter")

# The layout helper owns all physical row widths; do not duplicate 68 here.
push_display = root / "src/main/java/de/mossgrabers/controller/ableton/push/controller/Push1Display.java"
replace_once(push_display,
    "Push1DisplayProtocol.encodeLine (row, Push1DisplayText.fit (text, 68))",
    "Push1DisplayProtocol.encodeLine (row, Push1DisplayLayout.fitRow (text))",
    "Push 1 row fitting uses shared geometry")
