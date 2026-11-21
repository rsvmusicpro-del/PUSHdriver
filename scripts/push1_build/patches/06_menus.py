# Internal source-assembly patch batch. Executed in order by prepare_push1.py.
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
    source = target.read_text(encoding="utf-8")
    if new in source and old not in source:
        return
    if source.count(old) != 1:
        raise SystemExit(f"{label}: expected exactly one source block")
    target.write_text(source.replace(old, new, 1), encoding="utf-8")


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
            this.scales.setScaleOffsetByIndex (index - 1);
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
            this.scales.setScaleOffsetByIndex (index + 5);
        this.update ();
    }""",
    "Scales Row 2 lower offsets")
replace_menu_fragment(scales_mode,
    """            final int offset = this.scales.getScaleOffsetIndex ();
            return this.colorManager.getColorIndex (offset == index - 1 ? AbstractMode.BUTTON_COLOR_HI : AbstractFeatureGroup.BUTTON_COLOR_ON);
        }

        index = this.isButtonRow (1, buttonID);""",
    """            final int offset = this.scales.getScaleOffsetIndex ();
            return this.colorManager.getColorIndex (offset == index - 1 ? AbstractMode.BUTTON_COLOR_HI : AbstractFeatureGroup.BUTTON_COLOR_ON);
        }

        index = this.isButtonRow (1, buttonID);""",
    "Scales Row 1 LED state")
replace_menu_fragment(scales_mode,
    """            final int offset = this.scales.getScaleOffsetIndex ();
            return this.colorManager.getColorIndex (offset == index - 1 + 6 ? AbstractMode.BUTTON_COLOR2_HI : AbstractMode.BUTTON_COLOR2_ON);""",
    """            final int offset = this.scales.getScaleOffsetIndex ();
            return this.colorManager.getColorIndex (offset == index + 5 ? AbstractMode.BUTTON_COLOR2_HI : AbstractMode.BUTTON_COLOR2_ON);""",
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


# Give menu selection screens the same semantic LCD row compositor. The
# physical button rows, selected-state glyphs and per-mode choices remain
# mode-specific; only their common row geometry is centralized.
def replace_menu_display(path: str, replacement: str, label: str) -> None:
    replace_java_method(root / path, "public void updateDisplay1 (final ITextDisplay display)", replacement, label)


note_view = "src/main/java/de/mossgrabers/controller/ableton/push/mode/NoteViewSelectMode.java"
replace_menu_display(note_view, """public void updateDisplay1 (final ITextDisplay display)
    {
        final ViewManager viewManager = this.surface.getViewManager ();
        final String [] playbackViews = new String [8];
        final String [] sequenceViews = new String [8];

        for (int i = 0; i < VIEWS.length; i++)
        {
            if (VIEWS_TOP[i] != null)
            {
                final IView view = viewManager.get (VIEWS_TOP[i]);
                if (view != null)
                    playbackViews[i] = (viewManager.isActive (VIEWS_TOP[i]) ? Push1Display.SELECT_ARROW : "") + StringUtils.optimizeName (view.getName (), 8);
            }
            if (VIEWS[i] != null)
            {
                final IView view = viewManager.get (VIEWS[i]);
                if (view != null)
                    sequenceViews[i] = (viewManager.isActive (VIEWS[i]) ? Push1Display.SELECT_ARROW : "") + StringUtils.optimizeName (view.getName (), 8);
            }
        }

        display.setBlock (1, 0, "Sequence:");
        display.setBlock (2, 0, "Play:");
        display.setBlock (2, 3, "Tools:");
        display.setRow (0, de.mossgrabers.controller.ableton.push.controller.Push1DisplayComposition.encoderLabels (playbackViews));
        display.setRow (3, de.mossgrabers.controller.ableton.push.controller.Push1DisplayComposition.encoderLabels (sequenceViews));
    }""", "Note View Select semantic LCD rows")


session_view = "src/main/java/de/mossgrabers/controller/ableton/push/mode/SessionViewSelectMode.java"
replace_menu_display(session_view, """public void updateDisplay1 (final ITextDisplay display)
    {
        final ViewManager viewManager = this.surface.getViewManager ();
        final String [] views = new String [8];
        final String [] actions = new String [8];
        for (int i = 0; i < VIEWS.length; i++)
        {
            if (VIEWS[i] != null)
                views[i] = (this.isSelected (viewManager, i) ? Push1Display.SELECT_ARROW : "") + VIEW_NAMES[i];
        }

        final boolean isOn = this.surface.getModeManager ().isActive (Modes.SESSION);
        actions[6] = "Markers";
        actions[7] = (isOn ? Push1Display.SELECT_ARROW : "") + "Clips";

        display.setBlock (1, 0, "Session view:");
        display.setBlock (1, 3, "Session mode:");
        display.setRow (0, de.mossgrabers.controller.ableton.push.controller.Push1DisplayComposition.encoderLabels (views));
        display.setRow (3, de.mossgrabers.controller.ableton.push.controller.Push1DisplayComposition.actionHints (actions));
    }""", "Session View Select semantic LCD rows")


scales_mode = "src/main/java/de/mossgrabers/controller/ableton/push/mode/ScalesMode.java"
replace_menu_display(scales_mode, """public void updateDisplay1 (final ITextDisplay display)
    {
        final int selIndex = this.scales.getScale ().ordinal ();
        int pos = 0;
        for (final Pair<String, Boolean> p: Push1Display.createMenuList (4, Scale.getNames (), selIndex))
        {
            display.setBlock (pos, 0, (p.getValue ().booleanValue () ? Push1Display.SELECT_ARROW : " ") + p.getKey ());
            pos++;
        }

        display.setBlock (0, 3, this.scales.getRangeText ());

        final int offset = this.scales.getScaleOffsetIndex ();
        final String [] upperBases = new String [8];
        final String [] lowerBases = new String [8];
        for (int i = 0; i < 6; i++)
        {
            upperBases[i + 1] = "  " + (offset == i ? Push1Display.SELECT_ARROW : " ") + Scales.BASES.get (i);
            lowerBases[i + 1] = "  " + (offset == 6 + i ? Push1Display.SELECT_ARROW : " ") + Scales.BASES.get (6 + i);
        }
        lowerBases[7] = this.scales.isChromatic () ? "Chromatc" : "In Key";
        display.setRow (2, de.mossgrabers.controller.ableton.push.controller.Push1DisplayComposition.encoderLabels (upperBases));
        display.setRow (3, de.mossgrabers.controller.ableton.push.controller.Push1DisplayComposition.encoderLabels (lowerBases));
    }""", "Scales semantic base-note rows")


fixed_mode = "src/main/java/de/mossgrabers/controller/ableton/push/mode/FixedMode.java"
replace_menu_display(fixed_mode, """public void updateDisplay1 (final ITextDisplay display)
    {
        display.setBlock (1, 0, "Create Clip (leng").setBlock (1, 1, "th not stored):");
        display.setBlock (2, 0, "New Clip Length:");
        final int newClipLength = this.surface.getConfiguration ().getNewClipLength ();
        final String [] createClipActions = new String [8];
        final String [] clipLengths = new String [8];
        for (int i = 0; i < 8; i++)
        {
            final String value = AbstractConfiguration.getNewClipLengthValue (i);
            createClipActions[i] = value;
            clipLengths[i] = (newClipLength == i ? Push1Display.SELECT_ARROW : "") + value;
        }
        display.setRow (0, de.mossgrabers.controller.ableton.push.controller.Push1DisplayComposition.actionHints (createClipActions));
        display.setRow (3, de.mossgrabers.controller.ableton.push.controller.Push1DisplayComposition.encoderLabels (clipLengths));
    }""", "Fixed Length semantic LCD rows")


quantize_mode = "src/main/java/de/mossgrabers/controller/ableton/push/mode/QuantizeMode.java"
replace_menu_display(quantize_mode, """public void updateDisplay1 (final ITextDisplay display)
    {
        final String [] topActions = new String [8];
        final String [] recordQuantize = new String [8];
        topActions[0] = Push1Display.SELECT_ARROW + "Quantize";
        topActions[1] = "Groove";
        final ITrack cursorTrack = this.model.getCursorTrack ();
        final RecordQuantization recQuant = cursorTrack.doesExist () ? cursorTrack.getRecordQuantizationGrid () : RecordQuantization.RES_OFF;
        final RecordQuantization [] values = RecordQuantization.values ();
        display.setBlock (2, 0, "Record Quantize:");
        for (int i = 0; i < values.length; i++)
            recordQuantize[i] = (values[i] == recQuant ? Push1Display.SELECT_ARROW : "") + values[i].getName ();

        if (this.model.getHost ().supports (Capability.QUANTIZE_INPUT_NOTE_LENGTH))
        {
            display.setBlock (2, 2, "       Quant Note");
            display.setCell (2, 6, "Length:");
            recordQuantize[6] = cursorTrack.doesExist () && cursorTrack.isRecordQuantizationNoteLength () ? "On" : "Off";
        }

        final int quantizeAmount = this.surface.getConfiguration ().getQuantizeAmount ();
        topActions[7] = "Quant Amnt";
        display.setRow (0, de.mossgrabers.controller.ableton.push.controller.Push1DisplayComposition.actionHints (topActions));
        display.setCell (1, 7, quantizeAmount + "%");
        display.setCell (2, 7, quantizeAmount * 1023 / 100, Format.FORMAT_VALUE);
        display.setRow (3, de.mossgrabers.controller.ableton.push.controller.Push1DisplayComposition.encoderLabels (recordQuantize));
    }""", "Quantize semantic LCD rows")


note_repeat = "src/main/java/de/mossgrabers/controller/ableton/push/mode/NoteRepeatMode.java"
replace_menu_display(note_repeat, """public void updateDisplay1 (final ITextDisplay display)
    {
        final String [] labels = new String [8];
        final String [] actions = new String [8];
        labels[0] = "Period:";
        final int selPeriodIndex = this.getSelectedPeriodIndex ();
        int pos = 0;
        for (final Pair<String, Boolean> p: Push1Display.createMenuList (4, Resolution.getNames (), selPeriodIndex))
        {
            display.setCell (pos, 1, (p.getValue ().booleanValue () ? Push1Display.SELECT_ARROW : " ") + p.getKey ());
            pos++;
        }

        if (this.host.supports (Capability.NOTE_REPEAT_LENGTH))
        {
            labels[2] = "Length:";
            final int selLengthIndex = this.getSelectedNoteLengthIndex ();
            pos = 0;
            for (final Pair<String, Boolean> p: Push1Display.createMenuList (4, Resolution.getNames (), selLengthIndex))
            {
                display.setCell (pos, 3, (p.getValue ().booleanValue () ? Push1Display.SELECT_ARROW : " ") + p.getKey ());
                pos++;
            }
        }

        if (this.host.supports (Capability.NOTE_REPEAT_LATCH))
            actions[4] = " Latch";

        final int upperBound = this.model.getValueChanger ().getUpperBound ();
        if (this.host.supports (Capability.NOTE_REPEAT_MODE))
        {
            final String bottomMenu = this.host.supports (Capability.NOTE_REPEAT_USE_PRESSURE_TO_VELOCITY) ? "Use Pressure" : "";
            final ArpeggiatorMode mode = this.noteRepeat.getMode ();
            final Configuration configuration = this.surface.getConfiguration ();
            final List<ArpeggiatorMode> arpeggiatorModes = configuration.getArpeggiatorModes ();
            final int modeIndex = configuration.lookupArpeggiatorModeIndex (mode);
            final int value = modeIndex * upperBound / (arpeggiatorModes.size () - 1);
            labels[5] = "Mode";
            display.setCell (1, 5, StringUtils.optimizeName (mode.getName (), 8));
            display.setCell (2, 5, value, Format.FORMAT_VALUE);
            actions[5] = bottomMenu;
        }

        if (this.host.supports (Capability.NOTE_REPEAT_OCTAVES))
        {
            final String bottomMenu = this.host.supports (Capability.NOTE_REPEAT_IS_FREE_RUNNING) ? "  Sync" : "";
            final int octaves = this.noteRepeat.getOctaves ();
            final int value = octaves * upperBound / 8;
            labels[6] = "Octaves";
            display.setCell (1, 6, Integer.toString (octaves));
            display.setCell (2, 6, value, Format.FORMAT_VALUE);
            actions[6] = bottomMenu;
        }

        if (this.host.supports (Capability.NOTE_REPEAT_SWING))
        {
            final IParameter shuffleParam = this.model.getGroove ().getParameter (GrooveParameterID.SHUFFLE_AMOUNT);
            labels[7] = shuffleParam.getName (10);
            display.setCell (1, 7, shuffleParam.getDisplayedValue (8));
            display.setCell (2, 7, shuffleParam.getValue (), Format.FORMAT_VALUE);
            actions[7] = "Shuffle";
        }

        display.setRow (0, de.mossgrabers.controller.ableton.push.controller.Push1DisplayComposition.encoderLabels (labels));
        display.setRow (3, de.mossgrabers.controller.ableton.push.controller.Push1DisplayComposition.actionHints (actions));
    }""", "Note Repeat semantic LCD rows")


marker_mode = "src/main/java/de/mossgrabers/controller/ableton/push/mode/MarkerMode.java"
replace_menu_display(marker_mode, """public void updateDisplay1 (final ITextDisplay display)
    {
        final String [] actions = new String [8];
        final String [] markers = new String [8];
        display.setCell (2, 0, "Markers:");

        actions[0] = "";
        actions[5] = "Action:";
        actions[6] = (!this.actionModeLaunch ? Push1Display.SELECT_ARROW : "") + EDIT_MENU[6];
        actions[7] = (this.actionModeLaunch ? Push1Display.SELECT_ARROW : "") + EDIT_MENU[7];

        for (int i = 0; i < 8; i++)
        {
            final IMarker marker = this.bank.getItem (i);
            if (marker.doesExist ())
                markers[i] = StringUtils.shortenAndFixASCII (getMarkerName (marker), 8);
        }

        display.setRow (0, de.mossgrabers.controller.ableton.push.controller.Push1DisplayComposition.actionHints (actions));
        display.setBlock (0, 0, EDIT_MENU[0]);
        display.setRow (3, de.mossgrabers.controller.ableton.push.controller.Push1DisplayComposition.encoderLabels (markers));
    }""", "Marker semantic LCD rows")

# The device context row is a full-width status line, not a two-module label.
# Keep a short, stable label in the first 17-character module and let the
# selected device name use the remaining three modules (51 characters).
device_params_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceParamsMode.java"
replace_menu_fragment(device_params_mode,
    """import de.mossgrabers.controller.ableton.push.controller.Push1Display;
""",
    """import de.mossgrabers.controller.ableton.push.controller.Push1Display;
import de.mossgrabers.controller.ableton.push.controller.Push1DisplayComposition;
""",
    "Device context full-width LCD helpers")
replace_menu_fragment(device_params_mode,
    """        // Row 3
        display.setBlock (2, 0, "Selected Device:").setBlock (2, 1, cd.getName ());""",
    """        // Row 3: one stable context module, followed by three modules of detail.
        display.setRow (2, de.mossgrabers.controller.ableton.push.controller.Push1DisplayComposition.contextRow ("DEVICE:", cd.getName ()));""",
    "Device context row uses all four LCD modules")



