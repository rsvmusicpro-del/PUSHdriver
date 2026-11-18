# Internal source-assembly patch batch. Executed in order by prepare_push1.py.
note_repeat = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/NoteRepeatMode.java"

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

# Empty clip slots are valid API-17 states. Guard the shared note-sequencer
# input and renderer before asking the host for playhead or note data.
note_sequencer = root / "src/main/java/de/mossgrabers/framework/view/sequencer/AbstractNoteSequencerView.java"
replace_once(note_sequencer,
    "        final INoteClip clip = this.getClip ();\n"
    "        final int mappedY = this.keyManager.map (y);",
    "        final INoteClip clip = this.getClip ();\n"
    "        if (!clip.doesExist ())\n"
    "            return;\n"
    "        final int mappedY = this.keyManager.map (y);",
    "Note sequencer ignores pad edits when the selected slot has no clip")
replace_once(note_sequencer,
    "        final INoteClip clip = this.getClip ();\n"
    "        if (pad == this.loopPadPressed && pad != clip.getEditPage ())",
    "        final INoteClip clip = this.getClip ();\n"
    "        if (!clip.doesExist ())\n"
    "        {\n"
    "            this.loopPadPressed = -1;\n"
    "            return;\n"
    "        }\n"
    "        if (pad == this.loopPadPressed && pad != clip.getEditPage ())",
    "Note sequencer ignores loop gestures when the selected slot has no clip")
replace_once(note_sequencer,
    "        final INoteClip clip = this.getClip ();\n"
    "        final int step = clip.getCurrentStep ();",
    "        final INoteClip clip = this.getClip ();\n"
    "        if (!clip.doesExist ())\n"
    "        {\n"
    "            gridPad.turnOff ();\n"
    "            return;\n"
    "        }\n"
    "        final int step = clip.getCurrentStep ();",
    "Note sequencer renderer clears safely when the selected slot has no clip")

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
accent_palette = (
    "            // Evenly stepped Push 1 hardware colors from red through orange and yellow to green.\n"
    "            final int [] accentPalette = new int [] { 72, 120, 60, 84, 9, 96, 126, 124, 13, 74, 85, 98, 75, 17, 122, 87 };\n"
    "            int selectedVelocityPad = 15 - this.configuration.getFixedAccentValue () / 8;\n"
    "            final int selY = selectedVelocityPad / 4;\n"
    "            final int selX = selectedVelocityPad % 4;\n"
    "            selectedVelocityPad = selY * 4 + 3 - selX;\n"
    "            final IPadGrid padGrid = this.surface.getPadGrid ();"
)
draw_pages_start = source.index("protected void drawPages")
accent_start = source.index("        if (isAccentActive)", draw_pages_start)
accent_end = source.index("\n        final int step = clip.getCurrentStep ();", accent_start)
accent_source = source[accent_start:accent_end]
if accent_source.count("MaschineColorManager.COLOR_GREEN") != 1 or accent_source.count("MaschineColorManager.COLOR_LIME_LO") != 1:
    raise SystemExit("AbstractDrumView: unexpected Accent color expressions")
selected_velocity_logic = (
    "            int selectedVelocityPad = 15 - this.configuration.getFixedAccentValue () / 8;\n"
    "            final int selY = selectedVelocityPad / 4;\n"
    "            final int selX = selectedVelocityPad % 4;\n"
    "            selectedVelocityPad = selY * 4 + 3 - selX;\n\n"
)
if accent_source.count(selected_velocity_logic) != 1:
    raise SystemExit("AbstractDrumView: expected one selected-velocity highlight calculation")
accent_source = accent_source.replace(selected_velocity_logic, "", 1)
accent_source = accent_source.replace(
    "            final IPadGrid padGrid = this.surface.getPadGrid ();",
    accent_palette,
    1,
)
accent_source = accent_source.replace(
    "padGrid.lightEx (x, y, pad == selectedVelocityPad ? MaschineColorManager.COLOR_GREEN : MaschineColorManager.COLOR_LIME_LO);",
    "padGrid.lightEx (x, y, pad == selectedVelocityPad ? 3 : accentPalette[(3 - pad / 4) * 4 + pad % 4]);",
    1,
)
source = source[:accent_start] + accent_source + source[accent_end:]
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
replace_once(setup,
    "import de.mossgrabers.framework.daw.IHost;\n",
    "import de.mossgrabers.framework.daw.constants.Capability;\nimport de.mossgrabers.framework.daw.IHost;\n",
    "Push 1 Device hierarchy capability import")

