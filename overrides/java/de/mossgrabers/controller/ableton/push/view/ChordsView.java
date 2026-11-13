// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.controller.ableton.push.view;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.configuration.AbstractConfiguration;
import de.mossgrabers.framework.configuration.Configuration;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IScene;
import de.mossgrabers.framework.daw.data.bank.ISceneBank;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.midi.MidiConstants;
import de.mossgrabers.framework.featuregroup.AbstractFeatureGroup;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.view.AbstractChordView;
import de.mossgrabers.framework.view.AbstractSessionView;
import de.mossgrabers.framework.view.Views;

/** Push Chords view with timing-only strumming humanization. */
public class ChordsView extends AbstractChordView<PushControlSurface, PushConfiguration>
{
    private static final int [][] CHORD_INTERVALS = {
        { 3, 5 }, { 5, 8 }, { 2, 5 }, { 4, 5 }, { 3, 5, 6 }, { 3, 5, 7 }, { 3, 5, 9 }, { 3, 5, 11 }
    };
    private final ChordStrummingEngine strumming = new ChordStrummingEngine ();

    public ChordsView (final PushControlSurface surface, final IModel model) { this (Views.NAME_CHORDS, surface, model); }

    public ChordsView (final String name, final PushControlSurface surface, final IModel model)
    {
        super (name, surface, model, true);
        final Configuration configuration = this.surface.getConfiguration ();
        configuration.addSettingObserver (AbstractConfiguration.ACTIVATE_FIXED_ACCENT, this::initMaxVelocity);
        configuration.addSettingObserver (AbstractConfiguration.FIXED_ACCENT_VALUE, this::initMaxVelocity);
    }

    @Override
    public void onButton (final ButtonID buttonID, final ButtonEvent event, final int velocity)
    {
        if (!ButtonID.isSceneButton (buttonID) || event != ButtonEvent.DOWN) return;
        final int index = buttonID.ordinal () - ButtonID.SCENE1.ordinal ();
        if (this.surface.isPressed (ButtonID.REPEAT))
        {
            NoteRepeatSceneHelper.handleNoteRepeatSelection (this.surface, 7 - index);
            return;
        }
        final IScene scene = this.model.getCurrentTrackBank ().getSceneBank ().getItem (index);
        if (this.isButtonCombination (ButtonID.DELETE)) { scene.remove (); return; }
        if (this.isButtonCombination (ButtonID.DUPLICATE)) { scene.duplicate (); return; }
        scene.select ();
        scene.launch ();
    }

    @Override
    public String getButtonColorID (final ButtonID buttonID)
    {
        if (!ButtonID.isSceneButton (buttonID)) return AbstractFeatureGroup.BUTTON_COLOR_OFF;
        if (this.surface.isPressed (ButtonID.REPEAT)) return NoteRepeatSceneHelper.getButtonColorID (this.surface, buttonID);
        final ISceneBank sceneBank = this.model.getSceneBank ();
        final int scene = buttonID.ordinal () - ButtonID.SCENE1.ordinal ();
        final IScene item = sceneBank.getItem (scene);
        if (item.doesExist ()) return item.isSelected () ? AbstractSessionView.COLOR_SELECTED_SCENE : AbstractSessionView.COLOR_SCENE;
        return AbstractSessionView.COLOR_SCENE_OFF;
    }

    @Override
    public void onGridNote (final int key, final int velocity)
    {
        if (!this.model.canSelectedTrackHoldNotes ()) return;
        // Preserve stock pad state and the original velocity/accent calculation.
        super.onGridNote (key, velocity);
        if (velocity <= 0)
        {
            this.strumming.noteOff (key, this.surface::scheduleTask);
            return;
        }
        final int row = (key - 36) / this.surface.getPadGrid ().getCols ();
        if (row < 0 || row >= CHORD_INTERVALS.length) return;
        final int note = this.keyManager.map (key);
        final int [] chord = this.scales.getChord (note, CHORD_INTERVALS[row]);
        final int chordVelocity = this.surface.getConfiguration ().isAccentActive ()
            ? this.surface.getConfiguration ().getFixedAccentValue () : velocity;
        final int status = MidiConstants.CMD_NOTE_ON + this.surface.getConfiguration ().getMidiEditChannel ();
        final IMidiInput input = this.surface.getMidiInput ();
        this.strumming.noteOn (key, chord, chordVelocity, status, this.surface::scheduleTask, input::sendRawMidiEvent);
    }

    @Override
    public void onDeactivate ()
    {
        this.strumming.deactivate ();
        super.onDeactivate ();
    }
}
