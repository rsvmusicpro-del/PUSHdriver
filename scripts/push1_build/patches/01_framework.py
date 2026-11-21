# Internal source-assembly patch batch. Executed in order by prepare_push1.py.
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
# playhead is green in playback and red while recording; selected steps use the
# brightest available amber-orange Push 1 LED (#FF7F00); muted steps are pale yellow;
# soloed drum pads are dark blue.
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

# The Poly Sequencer must preserve keyboard feedback but avoid querying an
# absent clip's playhead/steps. Empty slots are not equivalent to empty notes.
replace_once(poly_sequencer,
    "        final INoteClip clip = this.getClip ();\n"
    "        final boolean isActive = this.isActive ();\n"
    "        final int step = clip.getCurrentStep ();",
    "        final INoteClip clip = this.getClip ();\n"
    "        final boolean isActive = this.isActive ();\n"
    "        final boolean hasClip = clip.doesExist ();\n"
    "        final int step = hasClip ? clip.getCurrentStep () : -1;",
    "Poly sequencer checks clip existence before reading the playhead")
replace_once(poly_sequencer,
    "        final int hiStep = this.isInXRange (step) ? step % this.sequencerSteps : -1;",
    "        final int hiStep = hasClip && this.isInXRange (step) ? step % this.sequencerSteps : -1;",
    "Poly sequencer skips clip-range queries when the selected slot has no clip")
replace_once(poly_sequencer,
    "            final IStepInfo stepInfo = this.getStep (clip, col);\n"
    "            final boolean hilite = col == hiStep;",
    "            final IStepInfo stepInfo = hasClip ? this.getStep (clip, col) : null;\n"
    "            final boolean hilite = col == hiStep;",
    "Poly sequencer skips note reads when the selected slot has no clip")
replace_once(poly_sequencer,
    "            padGrid.lightEx (x, y, isActive ? this.getStepColor (stepInfo, hilite, col, editNotes) : AbstractSequencerView.COLOR_NO_CONTENT);",
    "            padGrid.lightEx (x, y, hasClip && isActive ? this.getStepColor (stepInfo, hilite, col, editNotes) : AbstractSequencerView.COLOR_NO_CONTENT);",
    "Poly sequencer renders empty clip steps as no content")
replace_once(poly_sequencer,
    "        final INoteClip clip = this.getClip ();\n"
    "        final int step = this.numColumns * (this.numRows - 1 - y) + x;",
    "        final INoteClip clip = this.getClip ();\n"
    "        if (!clip.doesExist ())\n"
    "            return;\n"
    "        final int step = this.numColumns * (this.numRows - 1 - y) + x;",
    "Poly sequencer ignores step edits when the selected slot has no clip")

# Bound per-step note aggregation by the highest occupied pitch. The API-17
# adapter can report empty steps without retrieving 128 individual step infos.
replace_java_method(poly_sequencer,
    "    protected IStepInfo getStep (final INoteClip clip, final int col)",
    """    protected IStepInfo getStep (final INoteClip clip, final int col)
    {
        final DefaultStepInfo result = new DefaultStepInfo ();
        boolean isMuted = true;
        final int channel = this.configuration.getMidiEditChannel ();
        final int highestRow = clip.getHighestRow (channel, col);
        if (highestRow < 0)
        {
            result.setMuted (true);
            return result;
        }

        final NotePosition notePosition = new NotePosition (channel, col, 0);
        for (int row = 0; row <= highestRow; row++)
        {
            notePosition.setNote (row);
            final IStepInfo stepInfo = clip.getStep (notePosition);
            final StepState state = stepInfo.getState ();
            if (state == StepState.START)
                result.setState (StepState.START);
            else if (state == StepState.CONTINUE && result.getState () != StepState.START)
                result.setState (StepState.CONTINUE);

            if ((state == StepState.START || state == StepState.CONTINUE) && !stepInfo.isMuted ())
                isMuted = false;
        }
        result.setMuted (isMuted);
        return result;
    }""",
    "Bound Poly step aggregation by the highest occupied note")

# API 17 note observers cannot be unregistered. Remove the base class's
# per-view callbacks and use a weakly routed observer pair from the Push view
# so inactive views are neither retained nor fed note events.
replace_once(poly_sequencer,
    "import de.mossgrabers.framework.daw.data.bank.ITrackBank;\n",
    "",
    "Remove the upstream per-view track-bank import")
replace_once(poly_sequencer,
    "        final ITrackBank tb = model.getTrackBank ();\n"
    "        tb.addSelectionObserver ( (index, isSelected) -> this.keyManager.clearPressedKeys ());\n"
    "        tb.addNoteObserver (this.keyManager::call);",
    "        // PushPolySequencerObservers installs one weakly routed observer pair per track bank.",
    "Replace per-view observers with weak shared routing")

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
     "this.registerColorIndex (AbstractSequencerView.COLOR_STEP_SELECTED, isPush2 ? PUSH2_COLOR2_YELLOW_HI : 96);", "Push 1 selected step amber color"),
    ("this.registerColorIndex (AbstractDrumView.COLOR_PAD_SELECTED, isPush2 ? PUSH2_COLOR2_BLUE_HI : PUSH1_COLOR2_BLUE_HI);",
     "this.registerColorIndex (AbstractDrumView.COLOR_PAD_SELECTED, isPush2 ? PUSH2_COLOR2_BLUE_HI : 3);\n        this.registerColorIndex (AbstractDrumView.COLOR_PAD_SOLO, isPush2 ? PUSH2_COLOR2_BLUE_LO : PUSH1_COLOR2_BLUE_LO);", "Push 1 solo pad color"),
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



# Universal Push 1 button edge guard. State-based rather than time-based:
# duplicate DOWN/UP edges are removed, while complete rapid taps are preserved.
button_guard = root / "src/main/java/de/mossgrabers/framework/controller/hardware/AbstractHwButton.java"
replace_once(button_guard,
    "    private ButtonEvent                    state;\n",
    "    private ButtonEvent                    state;\n"
    "    /** Generation of the current physical press; invalidates stale long-press callbacks. */\n"
    "    private long                          pressGeneration = 0;\n",
    "Universal button press generation")
replace_java_method(button_guard, "public void clearState ()", """    public void clearState ()
    {
        // A queued long-press callback from the previous press must never affect
        // a later press after the state has been cleared or the surface rebound.
        this.pressGeneration++;
        this.state = null;
    }""", "Invalidate pending long-press callbacks when clearing button state")
replace_java_method(button_guard, "protected void handleButtonPressed (final double value)", """    protected void handleButtonPressed (final double value)
    {
        if (value == 0 || this.isPressed ())
            return;

        final long generation = ++this.pressGeneration;
        this.state = ButtonEvent.DOWN;
        this.isConsumed = false;

        this.host.scheduleTask ( () -> this.checkButtonState (generation), this.optimizer.getTimeout ());
        this.pressedVelocity = (int) (value * 127.0);
        if (this.command != null)
            this.command.execute (ButtonEvent.DOWN, this.pressedVelocity);

        this.downEventHandlers.forEach (handler -> handler.handle (ButtonEvent.DOWN));
    }""", "Deduplicate repeated button DOWN edges")
replace_java_method(button_guard, "protected void handleButtonRelease ()", """    protected void handleButtonRelease ()
    {
        // Only a matching release closes an active press. Repeated or orphaned
        // release edges are harmless and cannot execute a command twice.
        if (!this.isBound () || !this.isPressed ())
            return;

        this.pressGeneration++;
        this.state = ButtonEvent.UP;
        if (this.command != null && !this.isConsumed)
            this.command.execute (ButtonEvent.UP, 0);

        this.upEventHandlers.forEach (handler -> handler.handle (ButtonEvent.UP));
    }""", "Deduplicate repeated and orphaned button UP edges")
replace_java_method(button_guard, "private void checkButtonState ()", """    private void checkButtonState (final long generation)
    {
        // A callback belongs only to the exact press that scheduled it. This
        // also makes duplicate scheduler delivery idempotent.
        if (generation != this.pressGeneration || this.state != ButtonEvent.DOWN)
            return;

        this.state = ButtonEvent.LONG;
        if (this.command != null)
            this.command.execute (ButtonEvent.LONG, this.pressedVelocity);
    }""", "Guard long-press callbacks against stale or duplicate delivery")
