// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.device;

import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.display.ITextDisplay;
import de.mossgrabers.framework.daw.DAWColor;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.constants.Capability;
import de.mossgrabers.framework.daw.data.ICursorDevice;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.utils.ButtonEvent;


/**
 * Mode for selecting the device slot chains.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class DeviceChainsMode extends DeviceParamsMode
{
    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     */
    public DeviceChainsMode (final PushControlSurface surface, final IModel model)
    {
        super (surface, model);
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobValue (final int index, final int value)
    {
        // Intentionally empty
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobTouch (final int index, final boolean isTouched)
    {
        // Intentionally empty
    }


    /** {@inheritDoc} */
    private boolean isAvailableChain (final String [] slotChains, final int index)
    {
        return slotChains != null && index >= 0 && index < slotChains.length
            && slotChains[index] != null && !slotChains[index].isBlank ();
    }


    @Override
    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (event == ButtonEvent.DOWN)
            return;

        if (event == ButtonEvent.UP)
        {
            final ICursorDevice cd = this.model.getCursorDevice ();
            if (!cd.doesExist () || !this.model.getHost ().supports (Capability.HAS_SLOT_CHAINS))
                return;
            final String [] slotChains = cd.getSlotChains ();
            if (!this.isAvailableChain (slotChains, index))
                return;
            this.selectSlotChainAndAwaitCursor (cd, slotChains[index]);
            return;
        }

        // LONG press - move upwards
        this.surface.setTriggerConsumed (ButtonID.get (ButtonID.ROW2_1, index));
        this.moveUp ();
    }


    /** {@inheritDoc} */
    @Override
    public int getButtonColor (final ButtonID buttonID)
    {
        final int index = this.isButtonRow (1, buttonID);
        if (index >= 0)
        {
            final ICursorDevice cursorDevice = this.model.getCursorDevice ();
            if (!cursorDevice.doesExist () || !this.model.getHost ().supports (Capability.HAS_SLOT_CHAINS))
                return PushColorManager.PUSH1_COLOR_BLACK;
            final String [] slotChains = cursorDevice.getSlotChains ();
            return this.isAvailableChain (slotChains, index)
                ? PushColorManager.PUSH1_COLOR_GREEN_LO
                : PushColorManager.PUSH1_COLOR_BLACK;
        }

        // The bottom row remains the inherited Device action/hierarchy row.
        return super.getButtonColor (buttonID);
    }


    /** {@inheritDoc} */
    @Override
    public void updateDisplay1 (final ITextDisplay display)
    {
        final ICursorDevice cd = this.model.getCursorDevice ();
        if (!this.checkExists1 (display, cd))
            return;

        // Chain selectors are now physically above the LCD (ROW2).
        final String [] slotChains = this.model.getHost ().supports (Capability.HAS_SLOT_CHAINS) ? cd.getSlotChains () : new String [0];
        for (int i = 0; i < 8; i++)
        {
            final String chain = this.isAvailableChain (slotChains, i) ? slotChains[i] : "";
            display.setCell (0, i, chain);
        }

        // The RGB action row is below the LCD.
        final String [] actionLabels = { "On", "Params", "Expand", "Chains", "Banks", "Pin", "Window", "Up" };
        for (int i = 0; i < actionLabels.length; i++)
            display.setCell (3, i, i == 4 ? actionLabels[i] + " C" : actionLabels[i]);
    }


    /** {@inheritDoc} */
    @Override
    public void updateDisplay2 (final IGraphicDisplay display)
    {
        final ICursorDevice cd = this.model.getCursorDevice ();
        if (!this.checkExists2 (display, cd))
            return;

        final String color = this.model.getCurrentTrackBank ().getSelectedChannelColorEntry ();
        final ColorEx bottomMenuColor = DAWColor.getColorEntry (color);
        final boolean hasPinning = this.model.getHost ().supports (Capability.HAS_PINNING);
        final String [] slotChains = this.model.getHost ().supports (Capability.HAS_SLOT_CHAINS) ? cd.getSlotChains () : new String [0];
        for (int i = 0; i < 8; i++)
        {
            final boolean isTopMenuOn = this.getTopMenuEnablement (cd, hasPinning, i);
            final String bottomMenu = this.isAvailableChain (slotChains, i) ? slotChains[i] : "";
            final String bottomMenuIcon = "";
            final boolean isBottomMenuOn = this.isAvailableChain (slotChains, i);
            display.addParameterElement (this.hostMenu[i], isTopMenuOn, bottomMenu, bottomMenuIcon, bottomMenuColor, isBottomMenuOn, "", 0, "", false, -1);
        }
    }
}