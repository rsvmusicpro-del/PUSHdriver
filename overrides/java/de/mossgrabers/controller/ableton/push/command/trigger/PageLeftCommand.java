// Written by PUSHdriver maintainers.
// Licensed under the same LGPL-3.0 terms as the pinned DrivenByMoss sources.

package de.mossgrabers.controller.ableton.push.command.trigger;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.device.DeviceBrowserMode;
import de.mossgrabers.framework.command.core.AbstractTriggerCommand;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.featuregroup.IMode;
import de.mossgrabers.framework.featuregroup.ViewManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.view.Views;
import de.mossgrabers.framework.view.sequencer.AbstractSequencerView;


/** Push 1 Out control: navigate outward in Device/Browser mode. */
public class PageLeftCommand extends AbstractTriggerCommand<PushControlSurface, PushConfiguration>
{
    public PageLeftCommand (final IModel model, final PushControlSurface surface)
    {
        super (model, surface);
    }


    /** {@inheritDoc} */
    @Override
    public void execute (final ButtonEvent event, final int velocity)
    {
        final ViewManager viewManager = this.surface.getViewManager ();
        final var modeManager = this.surface.getModeManager ();
        final IMode activeMode = modeManager.getActive ();

        if (activeMode instanceof final DeviceBrowserMode browserMode)
        {
            if (event == ButtonEvent.DOWN)
                browserMode.navigateFilterColumn (false);
            return;
        }

        if (Modes.isDeviceMode (modeManager.getActiveID ()))
        {
            if (event == ButtonEvent.DOWN && activeMode != null)
                activeMode.selectPreviousItem ();
            return;
        }

        // Preserve the established Bitwig Session and sequencer paging paths.
        if (viewManager.isActive (Views.SESSION))
        {
            if (event == ButtonEvent.DOWN)
                this.model.getCurrentTrackBank ().selectPreviousPage ();
            return;
        }

        final var activeView = viewManager.getActive ();
        if (activeView instanceof final AbstractSequencerView<?, ?> sequencerView)
            sequencerView.onLeft (event);
    }
}
