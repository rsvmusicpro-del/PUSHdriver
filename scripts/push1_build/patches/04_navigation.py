# Internal source-assembly patch batch. Executed in order by prepare_push1.py.
sequencer = root / "src/main/java/de/mossgrabers/framework/view/sequencer/AbstractSequencerView.java"
setup = root / "src/main/java/de/mossgrabers/controller/ableton/push/PushControllerSetup.java"

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
            if (modeManager.isActive (Modes.DEVICE_PARAMS))
            {
                final var cursorDevice = this.model.getCursorDevice ();
                if (!cursorDevice.doesExist () || !this.model.getHost ().supports (Capability.HAS_SLOT_CHAINS))
                    return false;
                final String [] chains = cursorDevice.getSlotChains ();
                return chains != null && chains.length > 0;
            }
            if (Modes.isDeviceMode (modeManager.getActiveID ()) && activeMode != null)
                return activeMode.hasNextItemPage ();
            if (viewManager.isActive (Views.SESSION))""",
    "Push 1 In button LED state")
replace_once(setup,
    """PushControlSurface.PUSH_BUTTON_DEVICE_RIGHT, () -> {

            if (viewManager.isActive (Views.SESSION))""",
    """PushControlSurface.PUSH_BUTTON_DEVICE_RIGHT, () -> {

            final IMode activeMode = modeManager.getActive ();
            if (modeManager.isActive (Modes.BROWSER) && activeMode != null)
                return activeMode.hasPreviousItemPage ();
            if (modeManager.isActive (Modes.DEVICE_CHAINS))
                return true;
            if (modeManager.isActive (Modes.DEVICE_PARAMS))
            {
                final var cursorDevice = this.model.getCursorDevice ();
                // OUT is actionable at root too: native moveUp reveals devices or returns to Track.
                return cursorDevice.doesExist ();
            }
            if (Modes.isDeviceMode (modeManager.getActiveID ()) && activeMode != null)
                return activeMode.hasPreviousItemPage ();
            if (viewManager.isActive (Views.SESSION))""",
    "Push 1 Out button LED state")

# DeviceBrowserMode remains the pinned upstream state machine. Only the
# defensive Bitwig empty-bank guards below are applied; no project-owned
# filter-column navigation state or methods are injected into Browser mode.
browser_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceBrowserMode.java"

# Bitwig's Browser banks can temporarily expose fewer than sixteen items while
# filters/results refresh. The upstream Ableton renderer indexes a fixed 4x4
# page, so bound every access against the current API bank before reading it.
# Browser selection is already protected by the physical encoder-touch gate and
# by the single-touched-encoder rule above. Do not apply BaseMode's coarse
# every-eighth-detent limiter here: it drops seven deliberate navigation steps.
replace_once(browser_mode,
    """    public void onKnobValue (final int index, final int value)
    {
        if (!this.isKnobTouched (index))
            return;

        if (this.increaseKnobMovement ())
            this.changeValue (index, value);
    }""",
    """    public void onKnobValue (final int index, final int value)
    {
        if (!this.isKnobTouched (index))
            return;

        this.changeValue (index, value);
    }""",
    "Push 1 Browser preserves every touched encoder navigation detent")

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

# Keep DeviceParamsMode upstream-owned. Select the exact nested ROW1 sibling
# directly through its existing DeviceBank item; avoid repeated relative steps.
device_params_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceParamsMode.java"

# Defer only the dependent ROW1 gesture through the existing command owner.
# Keep the pinned DeviceParamsMode public/protected method inventory unchanged.
replace_once(device_params_mode,
    """        if (event == ButtonEvent.UP)
        {
            final ICursorDevice cd = this.model.getCursorDevice ();
            if (!cd.doesExist ())
                return;
""",
    """        if (event == ButtonEvent.UP)
        {
            if (de.mossgrabers.controller.ableton.push.command.trigger.PageLeftCommand.deferRow1Selection (
                    this, this.model, this.surface, index))
                return;
            final ICursorDevice cd = this.model.getCursorDevice ();
            if (!cd.doesExist ())
                return;
""",
    "Defer only ROW1 gestures dependent on an unpublished chain cursor")

replace_once(device_params_mode,
    """    protected void moveUp ()
    {
        final ModeManager modeManager = this.surface.getModeManager ();
""",
    """    protected void moveUp ()
    {
        final ModeManager modeManager = this.surface.getModeManager ();
        final DeviceParamsMode pendingMode = (DeviceParamsMode) modeManager.get (Modes.DEVICE_PARAMS);
        if (de.mossgrabers.controller.ableton.push.command.trigger.PageLeftCommand.deferOutUntilChainCursor (
                pendingMode, this.model, this.surface))
            return;
""",
    "Defer native ROW2-UP until the chain cursor is published")

# Preserve the native hierarchy decisions, but finish ROW2-UP on cursor
# publication rather than imposing DrivenByMoss' fixed 300 ms sleep. The
# command owner observes the cursor name and keeps an 8 ms bounded fallback
# for hosts that do not notify observers reliably.
replace_once(device_params_mode,
    """            cd.selectParent ();
            this.model.getHost ().scheduleTask ( () -> {
                if (cd.hasLayers ())
                    modeManager.setActive (this.surface.getConfiguration ().getCurrentLayerMixMode ());
                else
                    modeManager.setActive (Modes.DEVICE_PARAMS);
                deviceParamsMode.setShowDevices (false);
                cd.selectChannel ();
            }, 300);""",
    """            de.mossgrabers.controller.ableton.push.command.trigger.PageLeftCommand.beginParentReturn (
                deviceParamsMode, this.model, this.surface);
            cd.selectParent ();""",
    "Replace fixed 300 ms parent return with cursor-publication completion")

replace_once(device_params_mode,
    """            if (cd.getIndex () != index)
            {
                cd.getDeviceBank ().getItem (index).select ();
                return;
            }
""",
    """            if (cd.getIndex () != index)
            {
                // A ROW1 position is selectable only when the corresponding
                // sibling exists. Never clamp an unavailable button to another device.
                final var targetDevice = cd.getDeviceBank ().getItem (index);
                if (targetDevice == null || !targetDevice.doesExist ())
                    return;

                // DeviceBank exposes the exact sibling, including nested chain
                // children. Select it directly instead of issuing up to seven
                // relative cursor commands.
                targetDevice.select ();
                return;
            }
""",
    "Push 1 immediate nested Device ROW1 cursor selection")

# The dedicated OUT button also needs the native layer-mode exit. Push 1's
# ROW2 buttons are mute/solo actions in layer mode, so expose the existing
# moveUp path without synthesizing a ROW1 long-press event.
device_layer_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceLayerMode.java"
replace_once(device_layer_mode,
    "    protected void moveUp ()",
    "    public void moveUp ()",
    "Expose native layer exit to dedicated OUT")

# Selecting a chain returns to Device mode with the device selector visible,
# even if the Banks action had previously switched ROW1 to parameter pages.
device_chains_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceChainsMode.java"

# Bitwig may transiently return a null slot-chain array while the cursor
# device is changing. Guard selection, LED feedback and both LCD renderers.
replace_once(device_chains_mode,
    "            final String [] slotChains = cd.getSlotChains ();\n            if (index >= slotChains.length)",
    "            final String [] slotChains = cd.getSlotChains ();\n            if (slotChains == null || index >= slotChains.length)",
    "Device Chains null slot-bank selection guard")
replace_once(device_chains_mode,
    "        return index < slotChains.length ? existsColor : offColor;",
    "        return slotChains != null && index < slotChains.length ? existsColor : offColor;",
    "Device Chains null slot-bank LED guard")
replace_once(device_chains_mode,
    """        final String [] slotChains = cd.getSlotChains ();
        for (int i = 0; i < 8; i++)
        {
            final String bottomMenu = i < slotChains.length ? slotChains[i] : "";
            display.setCell""",
    """        final String [] slotChains = cd.getSlotChains ();
        for (int i = 0; i < 8; i++)
        {
            final String bottomMenu = slotChains != null && i < slotChains.length ? slotChains[i] : "";
            display.setCell""",
    "Device Chains null slot-bank text LCD guard")
replace_once(device_chains_mode,
    """        final String [] slotChains = cd.getSlotChains ();
        for (int i = 0; i < 8; i++)
        {
            final boolean isTopMenuOn = this.getTopMenuEnablement (cd, hasPinning, i);
            final String bottomMenu = i < slotChains.length ? slotChains[i] : "";""",
    """        final String [] slotChains = cd.getSlotChains ();
        for (int i = 0; i < 8; i++)
        {
            final boolean isTopMenuOn = this.getTopMenuEnablement (cd, hasPinning, i);
            final String bottomMenu = slotChains != null && i < slotChains.length ? slotChains[i] : "";""",
    "Device Chains null slot-bank graphic LCD guard")
replace_once(device_chains_mode,
    "            final boolean isBottomMenuOn = i < slotChains.length;",
    "            final boolean isBottomMenuOn = slotChains != null && i < slotChains.length;",
    "Device Chains null slot-bank graphic LED guard")
replace_once(device_chains_mode,
    """            cd.selectSlotChain (slotChains[index]);
            this.surface.getModeManager ().setActive (Modes.DEVICE_PARAMS);
            return;
""",
    """            final DeviceParamsMode deviceParamsMode = (DeviceParamsMode) this.surface.getModeManager ().get (Modes.DEVICE_PARAMS);
            deviceParamsMode.setShowDevices (true);
            if (de.mossgrabers.controller.ableton.push.command.trigger.PageLeftCommand.deferChainSelectionUntilParentReturn (
                    deviceParamsMode, this.model, this.surface, slotChains[index]))
            {
                this.surface.getModeManager ().setActive (Modes.DEVICE_PARAMS);
                return;
            }
            de.mossgrabers.controller.ableton.push.command.trigger.PageLeftCommand.prepareSlotChainSelection (
                deviceParamsMode, this.model, this.surface, slotChains[index]);
            // Match DrivenByMoss: issue the chain selection and mode change
            // immediately unless a parent return is already in flight.
            cd.selectSlotChain (slotChains[index]);
            this.surface.getModeManager ().setActive (Modes.DEVICE_PARAMS);
            return;
""",
    "Direct Device Chains entry without deferred navigation")

# No general navigation queue or input interception is injected. Only
# model-scoped ROW1, IN/Chains selection and OUT/ROW2-UP hierarchy intents
# wait for the specific cursor publication they depend on.

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




# Device Layer entry is another asynchronous cursor transition. Capture the
# parent before layer.enter() so an immediate OUT/ROW2-UP waits for publication.
device_layer_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceLayerMode.java"
replace_once(device_layer_mode,
    "            layer.enter ();",
    """            final DeviceParamsMode deviceParamsMode = (DeviceParamsMode) this.surface.getModeManager ().get (Modes.DEVICE_PARAMS);
            de.mossgrabers.controller.ableton.push.command.trigger.PageLeftCommand.prepareLayerEntry (
                deviceParamsMode, this.model, this.surface);
            layer.enter ();""",
    "Track asynchronous Device Layer entry")
