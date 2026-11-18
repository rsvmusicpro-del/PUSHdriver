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
                return chains != null && chains.length > 0 && !PushDeviceNavigation.isBusy (surface);
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
                return false;
            if (modeManager.isActive (Modes.DEVICE_PARAMS))
            {
                final var cursorDevice = this.model.getCursorDevice ();
                return cursorDevice.doesExist () && (cursorDevice.isNested () || PushDeviceNavigation.isEntering (surface));
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

# Keep DeviceParamsMode upstream-owned. Route nested ROW1 through the
# callback-aware helper; root-device selection remains upstream-owned.
device_params_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceParamsMode.java"
replace_once(device_params_mode,
    """            if (cd.getIndex () != index)
            {
                cd.getDeviceBank ().getItem (index).select ();
                return;
            }
""",
    """            // The navigation queue can outlive this method while Bitwig updates
            // its cursor asynchronously. Keep the callback from retaining this
            // mode (and therefore the surface/model) through the queue's weak key.
            final java.lang.ref.WeakReference<DeviceParamsMode> modeReference =
                new java.lang.ref.WeakReference<> (this);
            if (de.mossgrabers.controller.ableton.push.command.trigger.PushDeviceNavigation.selectRow (this.surface, this.model, index, () -> {
                final DeviceParamsMode mode = modeReference.get ();
                if (mode == null || mode.surface.getModeManager ().getActive () != mode)
                    return;
                final var currentDevice = mode.model.getCursorDevice ();
                if (!currentDevice.doesExist ())
                    return;
                if (!currentDevice.hasLayers ())
                {
                    mode.setShowDevices (false);
                    return;
                }
                final Optional<?> layer = currentDevice.getLayerBank ().getSelectedItem ();
                if (layer.isEmpty ())
                    currentDevice.getLayerBank ().getItem (0).select ();
                mode.surface.getModeManager ().setActive (mode.surface.getConfiguration ().getCurrentLayerMixMode ());
            }))
                return;

            if (cd.getIndex () != index)
            {
                final var targetDevice = cd.getDeviceBank ().getItem (index);
                if (targetDevice == null || !targetDevice.doesExist ())
                    return;
                targetDevice.select ();
                return;
            }
""",
    "Push 1 nested Device ROW1 cursor selection")

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
    """            ((DeviceParamsMode) this.surface.getModeManager ().get (Modes.DEVICE_PARAMS)).setShowDevices (true);
            de.mossgrabers.controller.ableton.push.command.trigger.PushDeviceNavigation.selectSlotChain (this.surface, this.model, slotChains[index]);
            this.surface.getModeManager ().setActive (Modes.DEVICE_PARAMS);
            return;
""",
    "Restore Device selector after choosing a chain")

# Keep the physical Push 1 New button on its native dedicated binding, while
# stopping the cursor track's current clip before preparing its next note clip.
replace_once(setup,
    "import de.mossgrabers.framework.command.trigger.clip.NewCommand;\n",
    "import de.mossgrabers.controller.ableton.push.command.trigger.PushNewCommand;\nimport de.mossgrabers.controller.ableton.push.command.trigger.PushDeviceNavigation;\n",
    "Push 1 New command import")
replace_once(setup,
    "new NewCommand<> (this.model, surface)",
    "new PushNewCommand (this.model, surface)",
    "Push 1 New command binding")

