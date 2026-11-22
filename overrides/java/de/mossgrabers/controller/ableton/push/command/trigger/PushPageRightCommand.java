// Written by PUSHdriver maintainers.
// Licensed under the same LGPL-3.0 terms as the pinned DrivenByMoss sources.
package de.mossgrabers.controller.ableton.push.command.trigger;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.utils.ButtonEvent;


/**
 * Push 1 Out command: leave a nested Bitwig device chain before falling back
 * to the upstream page/sequencer behavior.
 */
public final class PushPageRightCommand extends PageRightCommand
{
    public PushPageRightCommand (final IModel model, final PushControlSurface surface)
    {
        super (model, surface);
    }


    @Override
    public void execute (final ButtonEvent event, final int velocity)
    {
        if (event == ButtonEvent.DOWN)
        {
            final var cursor = this.model.getCursorDevice ();
            if (cursor.doesExist ()
                && (cursor.isNested () || PushDeviceNavigation.isEntering (this.surface)))
            {
                PushDeviceNavigation.exitParent (this.surface, this.model);
                return;
            }
        }

        super.execute (event, velocity);
    }
}
