# Internal source-assembly patch batch. Executed in order by prepare_push1.py.
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

