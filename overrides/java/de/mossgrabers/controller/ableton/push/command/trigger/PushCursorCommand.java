// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.command.trigger;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.command.trigger.Direction;
import de.mossgrabers.framework.command.trigger.mode.CursorCommand;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.bank.ISceneBank;
import de.mossgrabers.framework.featuregroup.IMode;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.view.Views;


/**
 * Context-aware navigation for the four Push 1 arrow buttons.
 *
 * In Track mode, horizontal arrows select adjacent tracks like Ableton Live's
 * Note workflow. Holding Shift changes that movement to a track-bank page.
 * Other modes retain DrivenByMoss item-page navigation. Vertical arrows keep
 * their non-destructive scene-bank scrolling behavior.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class PushCursorCommand extends CursorCommand<PushControlSurface, PushConfiguration>
{
    private final ISceneBank sceneBank64;


    /**
     * Constructor.
     *
     * @param direction The direction of the pushed cursor arrow
     * @param model The model
     * @param surface The surface
     */
    public PushCursorCommand (final Direction direction, final IModel model, final PushControlSurface surface)
    {
        super (direction, model, surface, false);
        this.sceneBank64 = model.getSceneBank (64);
    }


    /** Scroll scenes up. */
    @Override
    protected void scrollUp ()
    {
        final ISceneBank sceneBank = this.getSceneBank ();
        if (this.surface.isShiftPressed () || this.isScenePlay ())
            sceneBank.selectPreviousPage ();
        else
            sceneBank.scrollBackwards ();
    }


    /** Scroll scenes down. */
    @Override
    protected void scrollDown ()
    {
        final ISceneBank sceneBank = this.getSceneBank ();
        if (this.surface.isShiftPressed () || this.isScenePlay ())
            sceneBank.selectNextPage ();
        else
            sceneBank.scrollForwards ();
    }


    /** Navigate horizontally without stealing another mode's controls. */
    @Override
    protected void scrollLeft ()
    {
        final IMode activeMode = this.surface.getModeManager ().getActive ();
        if (activeMode == null)
            return;

        if (this.surface.getModeManager ().isActive (Modes.TRACK) && !this.surface.isShiftPressed ())
            activeMode.selectPreviousItem ();
        else
            activeMode.selectPreviousItemPage ();
    }


    /** Navigate horizontally without stealing another mode's controls. */
    @Override
    protected void scrollRight ()
    {
        final IMode activeMode = this.surface.getModeManager ().getActive ();
        if (activeMode == null)
            return;

        if (this.surface.getModeManager ().isActive (Modes.TRACK) && !this.surface.isShiftPressed ())
            activeMode.selectNextItem ();
        else
            activeMode.selectNextItemPage ();
    }


    /** {@inheritDoc} */
    @Override
    protected ISceneBank getSceneBank ()
    {
        if (this.isScenePlay ())
            return this.sceneBank64;
        return this.model.getCurrentTrackBank ().getSceneBank ();
    }


    /** Keep arrow LEDs consistent with the active navigation granularity. */
    @Override
    protected void updateArrowStates ()
    {
        final ISceneBank sceneBank = this.getSceneBank ();
        this.canScrollUp = sceneBank.canScrollBackwards ();
        this.canScrollDown = sceneBank.canScrollForwards ();

        final IMode mode = this.surface.getModeManager ().getActive ();
        final boolean shiftPressed = this.surface.isShiftPressed ();
        if (this.surface.getModeManager ().isActive (Modes.TRACK))
        {
            this.canScrollLeft = mode != null
                && (shiftPressed ? mode.hasPreviousItemPage () : mode.hasPreviousItem ());
            this.canScrollRight = mode != null
                && (shiftPressed ? mode.hasNextItemPage () : mode.hasNextItem ());
        }
        else
        {
            this.canScrollLeft = mode != null
                && (shiftPressed ? mode.hasPreviousItem () : mode.hasPreviousItemPage ());
            this.canScrollRight = mode != null
                && (shiftPressed ? mode.hasNextItem () : mode.hasNextItemPage ());
        }
    }


    private boolean isScenePlay ()
    {
        return this.surface.getViewManager ().isActive (Views.SCENE_PLAY);
    }
}
