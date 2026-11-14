// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.device;

import de.mossgrabers.controller.ableton.push.controller.Push1Display;
import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.BaseMode;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.display.ITextDisplay;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.DAWColor;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.constants.Capability;
import de.mossgrabers.framework.daw.data.ICursorDevice;
import de.mossgrabers.framework.daw.data.IDevice;
import de.mossgrabers.framework.daw.data.bank.IDeviceBank;
import de.mossgrabers.framework.daw.data.bank.IParameterBank;
import de.mossgrabers.framework.daw.data.bank.IParameterPageBank;
import de.mossgrabers.framework.featuregroup.ModeManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.parameterprovider.device.BankParameterProvider;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.utils.StringUtils;

import java.util.Optional;


/**
 * Mode for editing device remote control parameters.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class DeviceParamsMode extends BaseMode<IParameter>
{
    private static final String [] MENU     =
    {
        "On",
        "Parameters",
        "Expanded",
        "Chains",
        "Banks",
        "Pin Device",
        "Window",
        "Up"
    };

    public enum SelectorContext { DEVICES, PAGES, CHAINS }

    protected final String []      hostMenu = new String [MENU.length];
    protected boolean              showDevices;
    private SelectorContext        selectorContext = SelectorContext.DEVICES;
    private final IParameter []    touchedParameters = new IParameter [8];
    private long hierarchyTransitionGeneration;


    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     */
    public DeviceParamsMode (final PushControlSurface surface, final IModel model)
    {
        super ("Parameters", surface, model, model.getCursorDevice ().getParameterBank ());

        this.setParameterProvider (new BankParameterProvider (this.model.getCursorDevice ().getParameterBank ()));

        // Third-party plug-ins can publish their remote-control parameter names after
        // Device mode has already bound the hardware encoders. Rebind only when a
        // parameter's identity/name changes, rather than polling on every controller tick.
        final IParameterBank parameterBank = this.model.getCursorDevice ().getParameterBank ();
        for (int i = 0; i < parameterBank.getPageSize (); i++)
            parameterBank.getItem (i).addNameObserver (name -> this.parametersAdjusted ());

        this.setShowDevices (true);

        System.arraycopy (MENU, 0, this.hostMenu, 0, MENU.length);
        final IHost host = this.model.getHost ();
        if (!host.supports (Capability.HAS_PARAMETER_PAGE_SECTION))
            this.hostMenu[1] = "";
        if (!host.supports (Capability.HAS_PINNING))
            this.hostMenu[5] = "";
        if (!host.supports (Capability.HAS_SLOT_CHAINS))
            this.hostMenu[3] = "";
    }


    /**
     * Show devices or the parameter banks of the cursor device for selection.
     *
     * @param enable True to enable
     */
    public final void setShowDevices (final boolean enable)
    {
        this.showDevices = enable;
        this.selectorContext = enable ? SelectorContext.DEVICES : SelectorContext.PAGES;
    }


    /**
     * Returns true if devices are shown otherwise parameter banks.
     *
     * @return True if devices are shown otherwise parameter banks
     */
    public boolean isShowDevices ()
    {
        return this.selectorContext == SelectorContext.DEVICES;
    }


    protected DeviceParamsMode getPrimaryMode ()
    {
        return (DeviceParamsMode) this.surface.getModeManager ().get (Modes.DEVICE_PARAMS);
    }


    protected SelectorContext getSelectorContext ()
    {
        final ModeManager manager = this.surface.getModeManager ();
        if (manager.getActiveID () == Modes.DEVICE_CHAINS)
            return SelectorContext.CHAINS;
        return this.getPrimaryMode ().selectorContext;
    }


    protected void setSelectorContext (final SelectorContext context)
    {
        final ModeManager manager = this.surface.getModeManager ();
        final DeviceParamsMode primary = this.getPrimaryMode ();
        if (context == SelectorContext.CHAINS)
        {
            if (primary.hasAvailableChains ())
                manager.setActive (Modes.DEVICE_CHAINS);
            return;
        }
        primary.selectorContext = context;
        primary.showDevices = context == SelectorContext.DEVICES;
        manager.setActive (Modes.DEVICE_PARAMS);
    }


    protected boolean hasAvailableChains ()
    {
        final ICursorDevice device = this.model.getCursorDevice ();
        if (!device.doesExist () || !this.model.getHost ().supports (Capability.HAS_SLOT_CHAINS))
            return false;
        final String [] chains = device.getSlotChains ();
        if (chains == null)
            return false;
        for (final String chain: chains)
            if (chain != null && !chain.isBlank ())
                return true;
        return false;
    }


    private boolean isAvailablePage (final String page)
    {
        return page != null && !page.isBlank ();
    }


    protected boolean hasParameterPages ()
    {
        final ICursorDevice device = this.model.getCursorDevice ();
        if (!device.doesExist ())
            return false;
        final IParameterPageBank pages = device.getParameterPageBank ();
        final int count = Math.min (8, pages.getPageSize ());
        for (int i = 0; i < count; i++)
        {
            final String name = pages.getItem (i);
            if (name != null && !name.isBlank ())
                return true;
        }
        return false;
    }


    private void cycleSelectorContext ()
    {
        final SelectorContext current = this.getSelectorContext ();
        for (int step = 1; step <= 3; step++)
        {
            final SelectorContext candidate = SelectorContext.values ()[(current.ordinal () + step) % SelectorContext.values ().length];
            if (candidate == SelectorContext.DEVICES || candidate == SelectorContext.PAGES && this.hasParameterPages ()
                    || candidate == SelectorContext.CHAINS && this.hasAvailableChains ())
            {
                this.setSelectorContext (candidate);
                return;
            }
        }
    }


    protected void releaseAllTouchedParameters ()
    {
        for (int i = 0; i < this.touchedParameters.length; i++)
        {
            final IParameter parameter = this.touchedParameters[i];
            if (parameter != null)
            {
                parameter.touchValue (false);
                this.touchedParameters[i] = null;
                this.setTouchedKnob (i, false);
                this.checkStopAutomationOnKnobRelease (false);
            }
        }
    }


    /** {@inheritDoc} */
    @Override
    public void onDeactivate ()
    {
        // Invalidate delayed cursor callbacks before another mode owns the surface.
        this.hierarchyTransitionGeneration++;
        this.releaseAllTouchedParameters ();
        super.onDeactivate ();
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobTouch (final int index, final boolean isTouched)
    {
        if (index < 0 || index >= this.touchedParameters.length)
            return;

        if (isTouched)
        {
            if (this.touchedParameters[index] != null)
                return;
            final ICursorDevice cd = this.model.getCursorDevice ();
            if (!cd.doesExist ())
                return;
            final IParameter parameter = cd.getParameterBank ().getItem (index);
            if (parameter == null || !parameter.doesExist ())
                return;
            this.touchedParameters[index] = parameter;
            this.setTouchedKnob (index, true);
            if (this.surface.isDeletePressed ())
            {
                this.surface.setTriggerConsumed (ButtonID.DELETE);
                parameter.resetValue ();
            }
            parameter.touchValue (true);
        }
        else
        {
            final IParameter parameter = this.touchedParameters[index];
            if (parameter != null)
            {
                parameter.touchValue (false);
                this.touchedParameters[index] = null;
            }
            this.setTouchedKnob (index, false);
            this.checkStopAutomationOnKnobRelease (false);
        }
    }


    /** {@inheritDoc} */
    @Override
    public void onFirstRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.DOWN) return;
        this.hierarchyTransitionGeneration++;
        final ICursorDevice device = this.model.getCursorDevice ();
        switch (index)
        {
            case 0: if (device.doesExist ()) device.toggleEnabledState (); break;
            case 1:
                if (!this.model.getHost ().supports (Capability.HAS_PARAMETER_PAGE_SECTION)) return;
                if (device.doesExist ()) device.toggleParameterPageSectionVisible ();
                break;
            case 2: if (device.doesExist ()) device.toggleExpanded (); break;
            case 3:
                if (this.getSelectorContext () == SelectorContext.CHAINS)
                    this.setSelectorContext (SelectorContext.DEVICES);
                else
                    this.setSelectorContext (SelectorContext.CHAINS);
                break;
            case 4:
                if (!device.doesExist ()) return;
                this.cycleSelectorContext ();
                break;
            case 5:
                if (!this.model.getHost ().supports (Capability.HAS_PINNING)) return;
                if (device.doesExist ()) device.togglePinned ();
                break;
            case 6: if (device.doesExist ()) device.toggleWindowOpen (); break;
            case 7: this.moveUp (); break;
            default: break;
        }
    }

    /**
     * Move up the hierarchy.
     */
    protected void moveUp ()
    {
        final long transition = ++this.hierarchyTransitionGeneration;
        final ModeManager modeManager = this.surface.getModeManager ();
        if (this.getSelectorContext () == SelectorContext.CHAINS || this.getSelectorContext () == SelectorContext.PAGES)
        {
            this.setSelectorContext (SelectorContext.DEVICES);
            return;
        }

        // There is no device on the track move upwards to the track view
        final ICursorDevice cd = this.model.getCursorDevice ();
        if (!cd.doesExist ())
        {
            this.surface.getButton (ButtonID.TRACK).trigger (ButtonEvent.DOWN);
            return;
        }

        // Parameter banks are shown -> show devices
        final DeviceParamsMode deviceParamsMode = (DeviceParamsMode) modeManager.get (Modes.DEVICE_PARAMS);
        if (!deviceParamsMode.isShowDevices ())
        {
            deviceParamsMode.setShowDevices (true);
            return;
        }

        // Devices are shown, if nested show the layers otherwise move up to the tracks
        if (cd.isNested ())
        {
            // Bitwig updates a FOLLOW_SELECTION cursor asynchronously. A fixed
            // 300 ms delay can run against the child or a newer selection. Wait
            // for the cursor identity to change and cancel if another UI action
            // or mode transition supersedes this request.
            final String childName = cd.getName ();
            final int childPosition = cd.getPosition ();
            final boolean childNested = cd.isNested ();
            final boolean childHasLayers = cd.hasLayers ();
            cd.selectParent ();
            this.awaitParentCursor (cd, modeManager, deviceParamsMode, childName, childPosition,
                childNested, childHasLayers, transition, 0);
            return;
        }

        // Move up to the track
        if (this.model.isCursorDeviceOnMasterTrack ())
            this.surface.getButton (ButtonID.MASTERTRACK).trigger (ButtonEvent.DOWN);
        else
            this.surface.getButton (ButtonID.TRACK).trigger (ButtonEvent.DOWN);
    }


    private void awaitParentCursor (final ICursorDevice cd, final ModeManager modeManager,
        final DeviceParamsMode deviceParamsMode, final String childName, final int childPosition,
        final boolean childNested, final boolean childHasLayers, final long transition, final int attempt)
    {
        if (transition != this.hierarchyTransitionGeneration || !cd.doesExist ())
            return;

        final boolean cursorStillOnChild = childName.equals (cd.getName ())
            && childPosition == cd.getPosition ()
            && childNested == cd.isNested ()
            && childHasLayers == cd.hasLayers ();
        if (cursorStillOnChild)
        {
            // Bound the wait: an unsupported/failed host transition must not
            // leave a permanent task loop or later act on an unrelated device.
            if (attempt < 40)
                this.model.getHost ().scheduleTask (() -> this.awaitParentCursor (cd, modeManager, deviceParamsMode,
                    childName, childPosition, childNested, childHasLayers, transition, attempt + 1), 73);
            return;
        }

        if (cd.hasLayers ())
            modeManager.setActive (this.surface.getConfiguration ().getCurrentLayerMixMode ());
        else
            modeManager.setActive (Modes.DEVICE_PARAMS);
        deviceParamsMode.setShowDevices (false);
        cd.selectChannel ();
    }


    /** {@inheritDoc} */
    @Override
    public int getButtonColor (final ButtonID buttonID)
    {
        final ICursorDevice cd = this.model.getCursorDevice ();
        int index = this.isButtonRow (0, buttonID);
        if (index >= 0)
        {
            // ROW1 / CC20-27: eight distinct RGB Device actions.
            if (!cd.doesExist ())
                return index == 7 ? PushColorManager.PUSH1_COLOR2_RED_HI : PushColorManager.PUSH1_COLOR2_GREY_LO;
            final int on;
            final int off;
            switch (index)
            {
                case 0: on = PushColorManager.PUSH1_COLOR2_GREEN_HI; off = PushColorManager.PUSH1_COLOR2_GREEN_LO; break;
                case 1: on = PushColorManager.PUSH1_COLOR2_CYAN_HI; off = PushColorManager.PUSH1_COLOR2_CYAN_LO; break;
                case 2: on = PushColorManager.PUSH1_COLOR2_BLUE_HI; off = PushColorManager.PUSH1_COLOR2_BLUE_LO; break;
                case 3: on = PushColorManager.PUSH1_COLOR2_ORCHID_HI; off = PushColorManager.PUSH1_COLOR2_ORCHID_LO; break;
                case 4: on = PushColorManager.PUSH1_COLOR2_YELLOW_HI; off = PushColorManager.PUSH1_COLOR2_YELLOW_LO; break;
                case 5: on = PushColorManager.PUSH1_COLOR2_MAGENTA_HI; off = PushColorManager.PUSH1_COLOR2_MAGENTA_LO; break;
                case 6: on = PushColorManager.PUSH1_COLOR2_WHITE; off = PushColorManager.PUSH1_COLOR2_WHITE; break;
                default: return PushColorManager.PUSH1_COLOR2_RED_HI;
            }
            switch (index)
            {
                case 0: return cd.isEnabled () ? on : off;
                case 1:
                    if (!this.model.getHost ().supports (Capability.HAS_PARAMETER_PAGE_SECTION)) return PushColorManager.PUSH1_COLOR2_GREY_LO;
                    return cd.isParameterPageSectionVisible () ? on : off;
                case 2: return cd.isExpanded () ? on : off;
                case 3:
                    if (!this.hasAvailableChains ()) return PushColorManager.PUSH1_COLOR2_GREY_LO;
                    return this.getSelectorContext () == SelectorContext.CHAINS ? on : off;
                case 4: return this.getSelectorContext () == SelectorContext.DEVICES ? off : on;
                case 5:
                    if (!this.model.getHost ().supports (Capability.HAS_PINNING)) return PushColorManager.PUSH1_COLOR2_GREY_LO;
                    return cd.isPinned () ? on : off;
                case 6: return cd.isWindowOpen () ? on : off;
                default: return PushColorManager.PUSH1_COLOR2_RED_HI;
            }
        }
        index = this.isButtonRow (1, buttonID);
        if (index >= 0)
        {
            // ROW2 / CC102-109: static CC palette only, for device/page selection.
            if (!cd.doesExist ()) return PushColorManager.PUSH1_COLOR_BLACK;
            if (this.getSelectorContext () == SelectorContext.DEVICES)
            {
                if (!cd.getDeviceBank ().getItem (index).doesExist ()) return PushColorManager.PUSH1_COLOR_BLACK;
                return index == cd.getIndex () ? PushColorManager.PUSH1_COLOR_GREEN_HI : PushColorManager.PUSH1_COLOR_GREEN_LO;
            }
            final IParameterPageBank bank = cd.getParameterPageBank ();
            final String page = bank.getItem (index);
            if (!this.isAvailablePage (page)) return PushColorManager.PUSH1_COLOR_BLACK;
            return index == bank.getSelectedItemIndex () ? PushColorManager.PUSH1_COLOR_GREEN_HI : PushColorManager.PUSH1_COLOR_GREEN_LO;
        }
        return super.getButtonColor (buttonID);
    }

    /** {@inheritDoc} */
    @Override
    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (event == ButtonEvent.DOWN) return;
        this.hierarchyTransitionGeneration++;
        if (event == ButtonEvent.UP)
        {
            final ICursorDevice cd = this.model.getCursorDevice ();
            if (!cd.doesExist ()) return;
            if (this.getSelectorContext () == SelectorContext.PAGES)
            {
                final IParameterPageBank pages = cd.getParameterPageBank ();
                if (index < pages.getPageSize ())
                {
                    final String page = pages.getItem (index);
                    if (this.isAvailablePage (page) && pages.getSelectedItemIndex () != index)
                    {
                        this.releaseAllTouchedParameters ();
                        pages.selectPage (index);
                    }
                }
                return;
            }
            if (this.getSelectorContext () != SelectorContext.DEVICES)
                return;
            if (this.isButtonCombination (ButtonID.DUPLICATE))
            {
                this.releaseAllTouchedParameters ();
                cd.duplicate ();
                return;
            }
            final IDevice targetDevice = cd.getDeviceBank ().getItem (index);
            if (!targetDevice.doesExist ()) return;
            if (this.isButtonCombination (ButtonID.DELETE))
            {
                this.releaseAllTouchedParameters ();
                targetDevice.remove ();
                return;
            }
            if (this.isButtonCombination (ButtonID.MUTE))
            {
                targetDevice.toggleEnabledState ();
                return;
            }
            if (cd.getIndex () != index)
            {
                this.releaseAllTouchedParameters ();
                targetDevice.select ();
                return;
            }
            final ModeManager modeManager = this.surface.getModeManager ();
            if (!cd.hasLayers ())
            {
                this.setSelectorContext (SelectorContext.PAGES);
                return;
            }
            final Optional<?> layer = cd.getLayerBank ().getSelectedItem ();
            if (layer.isEmpty ()) cd.getLayerBank ().getItem (0).select ();
            modeManager.setActive (this.surface.getConfiguration ().getCurrentLayerMixMode ());
            return;
        }
        this.surface.setTriggerConsumed (ButtonID.get (ButtonID.ROW2_1, index));
        this.moveUp ();
    }

    /** {@inheritDoc} */
    @Override
    public void updateDisplay1 (final ITextDisplay display)
    {
        final ICursorDevice cd = this.model.getCursorDevice ();
        if (!this.checkExists1 (display, cd))
            return;

        // LCD row 1 now labels the selector row above the display (ROW2).
        if (this.showDevices)
        {
            final IDeviceBank deviceBank = cd.getDeviceBank ();
            for (int i = 0; i < 8; i++)
            {
                final IDevice device = deviceBank.getItem (i);
                final StringBuilder label = new StringBuilder ();
                if (device.doesExist ())
                {
                    if (i == cd.getIndex ())
                        label.append (Push1Display.SELECT_ARROW);
                    if (!device.isEnabled ())
                        label.append (Push1Display.DIVISION);
                    label.append (device.getName ());
                }
                display.setCell (0, i, label.toString ());
            }
        }
        else
        {
            final IParameterPageBank bank = cd.getParameterPageBank ();
            final int selected = bank.getSelectedItemIndex ();
            for (int i = 0; i < 8; i++)
            {
                final String page = bank.getItem (i);
                display.setCell (0, i, !this.isAvailablePage (page) ? "" : (i == selected ? Push1Display.SELECT_ARROW : "") + page);
            }
        }

        // Keep parameter names and values together in the two middle rows.
        final IParameterBank parameterBank = cd.getParameterBank ();
        for (int i = 0; i < 8; i++)
        {
            final IParameter param = parameterBank.getItem (i);
            display.setCell (1, i, param.doesExist () ? StringUtils.fixASCII (param.getName ()) : "")
                .setCell (2, i, StringUtils.fixASCII (param.getDisplayedValue (8)));
        }

        // The eight action labels now align with the action row below the LCD.
        final String [] actionLabels = { "On", "Params", "Expand", "Chains", "Banks", "Pin", "Window", "Up" };
        for (int i = 0; i < actionLabels.length; i++)
            display.setCell (3, i, i == 4 ? actionLabels[i] + (this.getSelectorContext () == SelectorContext.DEVICES ? " D" : " P") : actionLabels[i]);
    }

    /** {@inheritDoc} */
    @Override
    public void updateDisplay2 (final IGraphicDisplay display)
    {
        final ICursorDevice cd = this.model.getCursorDevice ();
        if (!this.checkExists2 (display, cd))
            return;

        final String channelColor = this.model.getCurrentTrackBank ().getSelectedChannelColorEntry ();
        final ColorEx bottomMenuColor = DAWColor.getColorEntry (channelColor);
        final ColorEx colorBackground = this.surface.getConfiguration ().getColorBackground ();

        final IDeviceBank deviceBank = cd.getDeviceBank ();
        final IParameterBank parameterBank = cd.getParameterBank ();
        final IParameterPageBank parameterPageBank = cd.getParameterPageBank ();
        final int selectedPage = parameterPageBank.getSelectedItemIndex ();
        final boolean hasPinning = this.model.getHost ().supports (Capability.HAS_PINNING);
        final IValueChanger valueChanger = this.model.getValueChanger ();
        for (int i = 0; i < parameterBank.getPageSize (); i++)
        {
            final boolean isTopMenuOn = this.getTopMenuEnablement (cd, hasPinning, i);

            String bottomMenu;
            final String bottomMenuIcon;
            boolean isBottomMenuOn;
            ColorEx color = bottomMenuColor;
            if (this.showDevices)
            {
                final IDevice device = deviceBank.getItem (i);
                bottomMenuIcon = device.getName ();
                bottomMenu = device.doesExist () ? device.getName (12) : "";
                isBottomMenuOn = i == cd.getIndex ();
                if (!device.isEnabled ())
                    color = colorBackground;
            }
            else
            {
                bottomMenuIcon = cd.getName ();
                bottomMenu = parameterPageBank.getItem (i);
                if (!this.isAvailablePage (bottomMenu))
                    bottomMenu = "";

                if (bottomMenu.length () > 12)
                    bottomMenu = bottomMenu.substring (0, 12);
                isBottomMenuOn = this.isAvailablePage (parameterPageBank.getItem (i)) && i == selectedPage;
            }

            final IParameter param = parameterBank.getItem (i);
            final boolean exists = param.doesExist ();
            final String parameterName = exists ? param.getName (9) : "";
            final int parameterValue = valueChanger.toDisplayValue (exists ? param.getValue () : 0);
            final String parameterValueStr = exists ? param.getDisplayedValue (8) : "";
            final boolean parameterIsActive = this.isKnobTouched (i);
            final int parameterModulatedValue = valueChanger.toDisplayValue (exists ? param.getModulatedValue () : -1);

            display.addParameterElement (this.hostMenu[i], isTopMenuOn, bottomMenu, bottomMenuIcon, color, isBottomMenuOn, parameterName, parameterValue, parameterValueStr, parameterIsActive, parameterModulatedValue);
        }
    }


    /** {@inheritDoc} */
    @Override
    public void selectPreviousItem ()
    {
        if (this.getSelectorContext () == SelectorContext.DEVICES)
        {
            final ICursorDevice cursorDevice = this.model.getCursorDevice ();
            final IDeviceBank bank = cursorDevice.getDeviceBank ();
            if (bank.canScrollBackwards ())
            {
                this.hierarchyTransitionGeneration++;
                this.releaseAllTouchedParameters ();
                bank.selectPreviousItem ();
            }
            return;
        }
        super.selectPreviousItem ();
    }


    /** {@inheritDoc} */
    @Override
    public void selectNextItem ()
    {
        if (this.getSelectorContext () == SelectorContext.DEVICES)
        {
            final ICursorDevice cursorDevice = this.model.getCursorDevice ();
            final IDeviceBank bank = cursorDevice.getDeviceBank ();
            if (bank.canScrollForwards ())
            {
                this.hierarchyTransitionGeneration++;
                this.releaseAllTouchedParameters ();
                bank.selectNextItem ();
            }
            return;
        }
        super.selectNextItem ();
    }


    /** {@inheritDoc} */
    @Override
    public void selectPreviousItemPage ()
    {
        if (this.getSelectorContext () == SelectorContext.DEVICES)
        {
            final IDeviceBank bank = this.model.getCursorDevice ().getDeviceBank ();
            if (bank.canScrollPageBackwards ())
            {
                this.hierarchyTransitionGeneration++;
                this.releaseAllTouchedParameters ();
                bank.selectPreviousPage ();
            }
            return;
        }
        if (super.hasPreviousItemPage ())
            this.releaseAllTouchedParameters ();
        super.selectPreviousItemPage ();
    }


    /** {@inheritDoc} */
    @Override
    public void selectNextItemPage ()
    {
        if (this.getSelectorContext () == SelectorContext.DEVICES)
        {
            final IDeviceBank bank = this.model.getCursorDevice ().getDeviceBank ();
            if (bank.canScrollPageForwards ())
            {
                this.hierarchyTransitionGeneration++;
                this.releaseAllTouchedParameters ();
                bank.selectNextPage ();
            }
            return;
        }
        if (super.hasNextItemPage ())
            this.releaseAllTouchedParameters ();
        super.selectNextItemPage ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasPreviousItem ()
    {
        if (this.getSelectorContext () == SelectorContext.DEVICES)
            return this.model.getCursorDevice ().getDeviceBank ().canScrollBackwards ();
        return super.hasPreviousItem ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasNextItem ()
    {
        if (this.getSelectorContext () == SelectorContext.DEVICES)
            return this.model.getCursorDevice ().getDeviceBank ().canScrollForwards ();
        return super.hasNextItem ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasPreviousItemPage ()
    {
        if (this.getSelectorContext () == SelectorContext.DEVICES)
            return this.model.getCursorDevice ().getDeviceBank ().canScrollPageBackwards ();
        return super.hasPreviousItemPage ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasNextItemPage ()
    {
        if (this.getSelectorContext () == SelectorContext.DEVICES)
            return this.model.getCursorDevice ().getDeviceBank ().canScrollPageForwards ();
        return super.hasNextItemPage ();
    }


    /** Navigate the previous item with Push 1 In/Out. */
    public void selectPreviousInOutItem ()
    {
        final ICursorDevice cursorDevice = this.model.getCursorDevice ();
        switch (this.getSelectorContext ())
        {
            case DEVICES:
                if (cursorDevice.getDeviceBank ().canScrollBackwards ())
                {
                    this.hierarchyTransitionGeneration++;
                    this.releaseAllTouchedParameters ();
                    cursorDevice.getDeviceBank ().selectPreviousItem ();
                }
                break;
            case PAGES:
                if (cursorDevice.getParameterPageBank ().canScrollPageBackwards ())
                {
                    this.hierarchyTransitionGeneration++;
                    this.releaseAllTouchedParameters ();
                    cursorDevice.getParameterPageBank ().selectPreviousPage ();
                }
                break;
            case CHAINS: break;
            default: break;
        }
    }


    /** Navigate the next item with Push 1 In/Out. */
    public void selectNextInOutItem ()
    {
        final ICursorDevice cursorDevice = this.model.getCursorDevice ();
        switch (this.getSelectorContext ())
        {
            case DEVICES:
                if (cursorDevice.getDeviceBank ().canScrollForwards ())
                {
                    this.hierarchyTransitionGeneration++;
                    this.releaseAllTouchedParameters ();
                    cursorDevice.getDeviceBank ().selectNextItem ();
                }
                break;
            case PAGES:
                if (cursorDevice.getParameterPageBank ().canScrollPageForwards ())
                {
                    this.hierarchyTransitionGeneration++;
                    this.releaseAllTouchedParameters ();
                    cursorDevice.getParameterPageBank ().selectNextPage ();
                }
                break;
            case CHAINS: break;
            default: break;
        }
    }


    /** Check whether the previous Push 1 In/Out item exists. */
    public boolean hasPreviousInOutItem ()
    {
        final ICursorDevice cursorDevice = this.model.getCursorDevice ();
        switch (this.getSelectorContext ())
        {
            case DEVICES: return cursorDevice.getDeviceBank ().canScrollBackwards ();
            case PAGES: return cursorDevice.getParameterPageBank ().canScrollPageBackwards ();
            case CHAINS: return false;
            default: return false;
        }
    }


    /** Check whether the next Push 1 In/Out item exists. */
    public boolean hasNextInOutItem ()
    {
        final ICursorDevice cursorDevice = this.model.getCursorDevice ();
        switch (this.getSelectorContext ())
        {
            case DEVICES: return cursorDevice.getDeviceBank ().canScrollForwards ();
            case PAGES: return cursorDevice.getParameterPageBank ().canScrollPageForwards ();
            case CHAINS: return false;
            default: return false;
        }
    }


    protected boolean checkExists1 (final ITextDisplay display, final ICursorDevice cd)
    {
        if (cd.doesExist ())
            return true;
        display.setBlock (1, 0, "           Select").setBlock (1, 1, "a device or press").setBlock (1, 2, "'Add Effect'...  ").allDone ();
        return false;
    }


    protected boolean checkExists2 (final IGraphicDisplay display, final ICursorDevice cd)
    {
        if (cd.doesExist ())
            return true;
        for (int i = 0; i < 8; i++)
            display.addOptionElement (i == 2 ? "Please select a device or press 'Add Device'..." : "", i == 7 ? "Up" : "", true, "", "", false, true);
        return false;
    }


    protected boolean getTopMenuEnablement (final ICursorDevice cd, final boolean hasPinning, final int index)
    {
        switch (index)
        {
            case 0:
                return cd.isEnabled ();
            case 1:
                return this.model.getHost ().supports (Capability.HAS_PARAMETER_PAGE_SECTION)
                    && cd.isParameterPageSectionVisible ();
            case 2:
                return cd.isExpanded ();
            case 3:
                return this.model.getHost ().supports (Capability.HAS_SLOT_CHAINS)
                    && this.surface.getModeManager ().isActive (Modes.DEVICE_CHAINS);
            case 4:
                return this.getSelectorContext () != SelectorContext.DEVICES;
            case 5:
                return hasPinning && cd.isPinned ();
            case 6:
                return cd.isWindowOpen ();
            case 7:
                return true;
            default:
                // Not used
                return false;
        }
    }
}