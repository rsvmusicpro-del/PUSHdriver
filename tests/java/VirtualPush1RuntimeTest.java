import de.mossgrabers.controller.ableton.push.PushControllerSetup;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.controller.Push1DisplayLayout;
import de.mossgrabers.controller.ableton.push.mode.NoteRepeatMode;
import de.mossgrabers.framework.configuration.ISettingsUI;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.ContinuousID;
import de.mossgrabers.framework.controller.ISetupFactory;
import de.mossgrabers.framework.controller.color.ColorManager;
import de.mossgrabers.framework.controller.hardware.BindType;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwContinuousControl;
import de.mossgrabers.framework.controller.hardware.IHwFader;
import de.mossgrabers.framework.controller.hardware.IHwLight;
import de.mossgrabers.framework.controller.hardware.IHwRelativeKnob;
import de.mossgrabers.framework.controller.valuechanger.RelativeEncoding;
import de.mossgrabers.framework.controller.hardware.IHwSurfaceFactory;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.daw.IBrowser;
import de.mossgrabers.framework.daw.data.IBrowserColumn;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.daw.data.bank.IDeviceBank;
import de.mossgrabers.framework.daw.data.bank.IParameterPageBank;
import de.mossgrabers.framework.daw.data.IDeviceMetadata;
import de.mossgrabers.framework.daw.data.ICursorDevice;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.data.IDrumDevice;
import de.mossgrabers.framework.daw.data.IDrumPad;
import de.mossgrabers.framework.daw.data.bank.IDrumPadBank;
import de.mossgrabers.framework.daw.clip.INoteClip;
import de.mossgrabers.framework.daw.data.ISlot;
import de.mossgrabers.framework.daw.data.bank.ISlotBank;
import de.mossgrabers.framework.daw.data.ISend;
import de.mossgrabers.framework.daw.data.IScene;
import de.mossgrabers.framework.daw.data.bank.ISceneBank;
import de.mossgrabers.framework.daw.data.bank.ISendBank;
import de.mossgrabers.framework.daw.data.bank.IParameterBank;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.daw.midi.ArpeggiatorMode;
import de.mossgrabers.framework.daw.midi.IMidiAccess;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.midi.IMidiOutput;
import de.mossgrabers.framework.daw.midi.INoteInput;
import de.mossgrabers.framework.daw.midi.INoteRepeat;
import de.mossgrabers.framework.command.core.ContinuousCommand;
import de.mossgrabers.framework.command.core.PitchbendCommand;
import de.mossgrabers.framework.command.core.TriggerCommand;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.controller.ableton.push.mode.device.DeviceParamsMode;
import de.mossgrabers.controller.ableton.push.mode.device.DeviceBrowserMode;
import de.mossgrabers.controller.ableton.push.view.SequencerView;
import de.mossgrabers.controller.ableton.push.view.Drum64View;
import de.mossgrabers.controller.ableton.push.view.SessionView;
import de.mossgrabers.framework.view.Views;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.utils.TestCallback;
import de.mossgrabers.framework.observer.IValueObserver;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Iterator;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.function.Consumer;

/**
 * Boots the real PushControllerSetup against deterministic API-17 host/model
 * collaborators, then drives its bound production commands through virtual MIDI.
 */
public final class VirtualPush1RuntimeTest
{
    private static int lightsCreated;
    private static int lightsUpdated;
    private static final List<String> MODEL_ACTIONS = new java.util.ArrayList<> ();
    private static boolean navigationContext;

    private static final class State
    {
        boolean playing;
        boolean recording;
        boolean launcherOverdub;
        boolean cursorTrackExists;
        boolean cursorTrackPlaying;
        boolean emptySlotAvailable = true;
        int selectedSlotIndex = 3;
        int lastEmptySearchStart = -1;
        int cursorTrackStopCount;
        int createdClipCount;
        int lastCreatedClipLength = -1;
        boolean lastCreatedClipOverdub;
        boolean browserPreviewEnabled;
        final List<String> noteRepeatActions = new java.util.ArrayList<> ();
        boolean clipExists;
        int clipCurrentStep;
        int clipEditPage;
        int clipScrollToPageCount;
        int clipExistenceQueries;
        int clipCurrentStepQueries;
        int transportPlayingQueries;
        int lastClipPage = -1;
        int featureMatrixPairs;
        boolean canHoldNotes;
        int drumScrollPosition;
        int drumScrollCount;
        int drumSelectedPad = -1;
        int sceneLaunchCount;
        int sceneRemoveCount;
        int sceneDuplicateCount;
        int sceneSelectedIndex = -1;
        int slotLaunchCount;
        int slotLaunchImmediatelyCount;
        int slotSelectCount;
        int slotItemRequests;
        int trackSlotBankRequests;
        final List<String> browserActions = new java.util.ArrayList<> ();
        final ArrayDeque<ScheduledTask> scheduled = new ArrayDeque<> ();
        final List<String> errors = new java.util.ArrayList<> ();
    }

    private record ScheduledTask (long delay, Runnable task) { }

    private VirtualPush1RuntimeTest () { }

    public static void main (final String [] args)
    {
        // Protocol and device-contract checks are subtests of the same virtual
        // Push bench entry point; none opens a separate hardware/test path.
        VirtualPush1MidiBench.main (new String [0]);
        VirtualPush1DeviceTest.main (new String [0]);
        VirtualPush1DisplayLayoutTest.main (new String [0]);
        VirtualPush1ProtocolExhaustiveTest.main (new String [0]);
        VirtualPush1FormattingAndFirmwareTest.main (new String [0]);

        lightsCreated = 0;
        lightsUpdated = 0;
        final State state = new State ();
        final VirtualPush1MidiBench midi = new VirtualPush1MidiBench ();
        final IHwSurfaceFactory hardware = surfaceFactory (midi);
        final IHost host = (IHost) Proxy.newProxyInstance (IHost.class.getClassLoader (),
            new Class<?> [] { IHost.class }, (proxy, method, callArgs) -> {
                switch (method.getName ())
                {
                    case "createSurfaceFactory": return hardware;
                    case "getName": return "Virtual Bitwig API 17";
                    case "supports": return Boolean.TRUE;
                    case "scheduleTask": state.scheduled.add (new ScheduledTask (((Number) callArgs[1]).longValue (), (Runnable) callArgs[0])); return null;
                    case "error":
                        if (state.errors.size () < 30)
                            state.errors.add (String.valueOf (callArgs[0])
                                + (callArgs.length > 1 && callArgs[1] instanceof Throwable error ? ": " + error : ""));
                        return null;
                    case "println":
                        if (callArgs.length > 0 && String.valueOf (callArgs[0]).startsWith ("- View "))
                            state.featureMatrixPairs++;
                        return null;
                    case "showNotification": case "releaseUsbDevices": return null;
                    case "getInstrumentMetadata": return List.of (deviceMetadata ("Virtual Instrument"));
                    case "getAudioEffectMetadata": return List.of (deviceMetadata ("Virtual Effect"));
                    default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
            });

        final ITransport transport = (ITransport) Proxy.newProxyInstance (ITransport.class.getClassLoader (),
            new Class<?> [] { ITransport.class }, (proxy, method, callArgs) -> {
                switch (method.getName ())
                {
                    case "play": state.playing = true; return null;
                    case "stop": case "stopAndRewind": state.playing = false; state.recording = false; return null;
                    case "isPlaying":
                        state.transportPlayingQueries++;
                        return state.playing;
                    case "startRecording": state.recording = true; return null;
                    case "isRecording": return state.recording;
                    case "isLauncherOverdub": return state.launcherOverdub;
                    case "toggleLauncherOverdub": state.launcherOverdub = !state.launcherOverdub; return null;
                    case "setLauncherOverdub": state.launcherOverdub = (boolean) callArgs[0]; return null;
                    default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
            });

        final ISetupFactory factory = (ISetupFactory) Proxy.newProxyInstance (ISetupFactory.class.getClassLoader (),
            new Class<?> [] { ISetupFactory.class }, (proxy, method, callArgs) -> {
                switch (method.getName ())
                {
                    case "getArpeggiatorModes": return Arrays.asList (ArpeggiatorMode.values ());
                    case "createMidiAccess":
                        return Proxy.newProxyInstance (IMidiAccess.class.getClassLoader (),
                            new Class<?> [] { IMidiAccess.class }, (p, m, a) -> switch (m.getName ())
                            {
                                case "createOutput" -> midi.output ();
                                case "createInput" -> midi.input ();
                                default -> defaultValue (m.getReturnType (), m.getGenericReturnType ());
                            });
                    case "createModel":
                        final Object valueChanger = callArgs[2];
                        final Object colorManager = callArgs[1];
                        final Object scales = callArgs[3];
                        final ISceneBank sceneBank = sceneBankProxy (state);
                        final ITrackBank trackBank = trackBankProxy (state, sceneBank);
                        final IParameterBank parameterBank = parameterBankProxy ();
                        final IDrumPadBank primaryDrumBank = drumPadBankProxy (state);
                        final IDrumPadBank cursorDrumBank = drumPadBankProxy (state);
                        final ICursorDevice cursorDevice = cursorDeviceProxy (parameterBank, state, cursorDrumBank);
                        final IDrumDevice drumDevice = drumDeviceProxy (primaryDrumBank);
                        final IBrowser browser = browserProxy (state);
                        final INoteClip noteClip = noteClipProxy (state);
                        final ISlotBank slotBank = slotBankProxy (state);
                        final ITrack cursorTrack = cursorTrackProxy (state, slotBank);
                        return Proxy.newProxyInstance (IModel.class.getClassLoader (),
                            new Class<?> [] { IModel.class }, (p, m, a) -> switch (m.getName ())
                            {
                                case "getHost" -> host;
                                case "getTransport" -> transport;
                                case "getValueChanger" -> valueChanger;
                                case "getColorManager" -> colorManager;
                                case "getScales" -> scales;
                                case "getCurrentTrackBank", "getTrackBank", "getEffectTrackBank" -> trackBank;
                                case "getSceneBank" -> sceneBank;
                                case "getCursorDevice" -> cursorDevice;
                                case "getDrumDevice" -> drumDevice;
                                case "canSelectedTrackHoldNotes" -> state.canHoldNotes;
                                case "getBrowser" -> browser;
                                case "getNoteClip" -> noteClip;
                                case "getCursorTrack" -> cursorTrack;
                                case "createNoteClip" -> {
                                    state.createdClipCount++;
                                    state.lastCreatedClipLength = (int) a[2];
                                    state.lastCreatedClipOverdub = (boolean) a[3];
                                    yield null;
                                }
                                default -> defaultValue (m.getReturnType (), m.getGenericReturnType ());
                            });
                    default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
            });

        final ISettingsUI global = settingsUI ();
        final ISettingsUI document = settingsUI ();
        final PushControllerSetup setup = new PushControllerSetup (host, factory, global, document);
        setup.init ();
        setup.startup ();

        final PushControlSurface surface = setup.getSurface ();
        final var modes = surface.getModeManager ();
        verifyPush1FeatureInventory (surface);
        require (modes.get (Modes.TRACK) != null, "Track mode was not registered");
        require (modes.get (Modes.DEVICE_PARAMS) != null, "Device parameter mode was not registered");
        require (modes.get (Modes.BROWSER) != null, "Browser mode was not registered");
        require (modes.get (Modes.SESSION) != null, "Session mode was not registered");
        require (modes.get (Modes.NOTE) != null, "Note mode was not registered");

        // Session scene buttons must launch, delete and duplicate the
        // selected scene through the production SessionView.
        final SessionView sessionView = (SessionView) surface.getViewManager ().get (Views.SESSION);
        sessionView.onButton (ButtonID.SCENE1, ButtonEvent.DOWN, 127);
        require (state.sceneSelectedIndex == 0 && state.sceneLaunchCount == 1,
            "Session scene button must select and launch its scene");
        midi.device ().pressButton (VirtualPush1Device.DELETE);
        sessionView.onButton (ButtonID.SCENE2, ButtonEvent.DOWN, 127);
        midi.device ().releaseButton (VirtualPush1Device.DELETE);
        require (state.sceneRemoveCount == 1,
            "Delete+Scene must remove the selected scene instead of launching it");
        midi.device ().pressButton (VirtualPush1Device.DUPLICATE);
        sessionView.onButton (ButtonID.SCENE3, ButtonEvent.DOWN, 127);
        midi.device ().releaseButton (VirtualPush1Device.DUPLICATE);
        require (state.sceneDuplicateCount == 1,
            "Duplicate+Scene must duplicate the selected scene instead of launching it");
        final Views viewBeforeSessionPads = surface.getViewManager ().getActiveID ();
        midi.device ().tapButton (VirtualPush1Device.SESSION);
        require (Views.isSessionView (surface.getViewManager ().getActiveID ()),
            "Session button must activate the Session view before slot tests");
        midi.device ().releaseButton (VirtualPush1Device.DELETE);
        midi.device ().releaseButton (VirtualPush1Device.DUPLICATE);
        midi.device ().releaseButton (VirtualPush1Device.SHIFT);
        midi.device ().releaseButton (VirtualPush1Device.SELECT);
        sessionView.onGridNote (36, 0);
        require (state.slotLaunchCount == 1,
            "Session pad release must launch the corresponding clip slot"
                + " (slotItems=" + state.slotItemRequests
                + ", trackSlotBanks=" + state.trackSlotBankRequests
                + ", launches=" + state.slotLaunchCount
                + ", deletePressed=" + surface.isDeletePressed ()
                + ", duplicatePressed=" + surface.isPressed (ButtonID.DUPLICATE)
                + ", selectPressed=" + surface.isSelectPressed () + ")");
        midi.device ().pressButton (VirtualPush1Device.SELECT);
        sessionView.onGridNote (36, 100);
        midi.device ().releaseButton (VirtualPush1Device.SELECT);
        require (state.slotLaunchImmediatelyCount == 1,
            "Select+Session pad press must launch the clip slot immediately");
        midi.device ().releaseButton (VirtualPush1Device.SESSION);
        require (surface.getViewManager ().getActiveID () == viewBeforeSessionPads,
            "Releasing the temporary Session view must restore the previously active view");

        // Browser filter columns and preset-result navigation are stateful
        // model interactions, not merely a registered-mode smoke check.
        final DeviceBrowserMode browserMode = (DeviceBrowserMode) modes.get (Modes.BROWSER);
        state.browserActions.clear ();
        browserMode.onFirstRow (0, ButtonEvent.DOWN);
        browserMode.onSecondRow (0, ButtonEvent.DOWN);
        browserMode.onFirstRow (7, ButtonEvent.DOWN);
        browserMode.onSecondRow (7, ButtonEvent.DOWN);
        require (state.browserActions.contains ("filter.previous:0"),
            "Browser first row must select the previous item in its filter column");
        require (state.browserActions.contains ("filter.next:0"),
            "Browser second row must select the next item in its filter column");
        require (state.browserActions.contains ("result.previous"),
            "Browser first row preset control must select the previous result");
        require (state.browserActions.contains ("result.next"),
            "Browser second row preset control must select the next result");

        browserMode.onKnobTouch (0, true);
        browserMode.changeSelectedColumnValue (1);
        browserMode.onKnobTouch (0, false);
        require (state.browserActions.contains ("filter.next:0"),
            "Touching a Browser filter encoder must route its value to the selected filter column");

        midi.device ().pressButton (VirtualPush1Device.SHIFT);
        browserMode.onFirstRow (7, ButtonEvent.DOWN);
        midi.device ().releaseButton (VirtualPush1Device.SHIFT);
        require (state.browserPreviewEnabled,
            "Shift+Browser preset row must toggle browser preview");

        // Verify both optional Note Repeat service states. The production
        // setup has no default note input in this bench; it must remain safe.
        final NoteRepeatMode unavailableNoteRepeat = (NoteRepeatMode) modes.get (Modes.REPEAT_NOTE);
        unavailableNoteRepeat.onFirstRow (4, ButtonEvent.UP);
        require (state.noteRepeatActions.isEmpty (),
            "Note Repeat controls must remain inert when the optional service is absent");

        final INoteRepeat noteRepeat = (INoteRepeat) Proxy.newProxyInstance (
            INoteRepeat.class.getClassLoader (), new Class<?> [] { INoteRepeat.class },
            (proxy, method, callArgs) -> {
                if (method.getName ().startsWith ("toggle") || method.getName ().startsWith ("set"))
                    state.noteRepeatActions.add (method.getName ());
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
        final INoteInput noteInput = (INoteInput) Proxy.newProxyInstance (
            INoteInput.class.getClassLoader (), new Class<?> [] { INoteInput.class },
            (proxy, method, callArgs) -> method.getName ().equals ("getNoteRepeat")
                ? noteRepeat : defaultValue (method.getReturnType (), method.getGenericReturnType ()));
        // The service appears after the controller has already been created.
        // NoteRepeatMode must refresh the optional service instead of retaining
        // the null reference captured during startup.
        final Modes modeBeforeNoteRepeat = modes.getActiveID ();
        modes.setActive (Modes.REPEAT_NOTE);
        setup.flush ();
        drainScheduled (state, 1);
        midi.setDefaultNoteInput (noteInput);
        midi.device ().pressButton (VirtualPush1Device.DELETE);
        midi.device ().touchEncoder (5, true);
        midi.device ().touchEncoder (5, false);
        midi.device ().touchEncoder (6, true);
        midi.device ().touchEncoder (6, false);
        midi.device ().releaseButton (VirtualPush1Device.DELETE);
        midi.device ().tapButton (VirtualPush1Device.TOP_ROW_FIRST + 4);
        midi.device ().tapButton (VirtualPush1Device.TOP_ROW_FIRST + 5);
        midi.device ().tapButton (VirtualPush1Device.TOP_ROW_FIRST + 6);
        midi.device ().tapButton (VirtualPush1Device.TOP_ROW_FIRST + 7);
        for (final String action: List.of (
            "setMode", "setOctaves", "toggleLatchActive", "toggleUsePressure",
            "toggleIsFreeRunning", "toggleShuffle"))
            require (state.noteRepeatActions.contains (action),
                "Late-created Note Repeat service did not receive " + action);
        midi.setDefaultNoteInput (null);
        midi.device ().pressButton (VirtualPush1Device.DELETE);
        midi.device ().touchEncoder (5, true);
        midi.device ().touchEncoder (5, false);
        midi.device ().touchEncoder (6, true);
        midi.device ().touchEncoder (6, false);
        midi.device ().releaseButton (VirtualPush1Device.DELETE);
        modes.setActive (modeBeforeNoteRepeat);

        // The live sequencer playhead must follow only an existing note clip;
        // an empty cursor slot must never cause an edit-page scroll.
        final Views previousView = surface.getViewManager ().getActiveID ();
        final SequencerView sequencerView = (SequencerView) surface.getViewManager ().get (Views.SEQUENCER);
        surface.getViewManager ().setActive (Views.SEQUENCER);
        require (surface.getViewManager ().isActive (Views.SEQUENCER),
            "Sequencer view did not become active for its playhead test");
        sequencerView.setSequencerActive (true);
        state.playing = true;
        state.clipExists = false;
        state.clipScrollToPageCount = 0;
        state.lastClipPage = -1;
        sequencerView.drawGrid ();
        require (state.clipScrollToPageCount == 0,
            "Sequencer playhead follow must not scroll an empty note clip");
        state.clipExists = true;
        state.clipCurrentStep = 20;
        state.clipEditPage = 0;
        sequencerView.drawGrid ();
        require (state.clipScrollToPageCount == 1 && state.lastClipPage > 0,
            "Sequencer must follow the playing page when a real note clip exists"
                + " (scrolls=" + state.clipScrollToPageCount
                + ", page=" + state.lastClipPage
                + ", currentStep=" + state.clipCurrentStep
                + ", editPage=" + state.clipEditPage
                + ", clipExists=" + state.clipExists
                + ", clipExistenceQueries=" + state.clipExistenceQueries
                + ", clipCurrentStepQueries=" + state.clipCurrentStepQueries
                + ", transportPlayingQueries=" + state.transportPlayingQueries
                + ", activeView=" + surface.getViewManager ().getActiveID ()
                + ", transportPlaying=" + state.playing + ")");
        state.playing = false;
        surface.getViewManager ().setActive (previousView);

        // Drum64 Select+Pad must align the cursor device's 16-pad bank with
        // the corresponding 16-pad block, then select the matching drum pad.
        final Views viewBeforeDrum = surface.getViewManager ().getActiveID ();
        final Modes modeBeforeDrum = modes.getActiveID ();
        final Drum64View drum64View = (Drum64View) surface.getViewManager ().get (Views.DRUM64);
        state.canHoldNotes = true;
        state.drumScrollPosition = 0;
        state.drumScrollCount = 0;
        state.drumSelectedPad = -1;
        surface.getViewManager ().setActive (Views.DRUM64);
        midi.device ().pressButton (VirtualPush1Device.SELECT);
        drum64View.onGridNote (36 + 35, 100);
        midi.device ().releaseButton (VirtualPush1Device.SELECT);
        require (state.drumScrollCount == 1 && state.drumScrollPosition == 16,
            "Drum64 Select+Pad must align the cursor drum bank to the matching 16-pad page");
        require (state.drumSelectedPad == 19,
            "Drum64 Select+Pad must select the matching primary drum pad");
        modes.setActive (modeBeforeDrum);
        surface.getViewManager ().setActive (viewBeforeDrum);
        state.canHoldNotes = false;

        // These are the production PushControllerSetup button commands, not
        // test doubles: MIDI CC -> IMidiInput binding -> IHwButton -> command.
        midi.device ().tapButton (VirtualPush1Device.DEVICE);
        require (modes.isActive (Modes.DEVICE_PARAMS), "Device CC did not activate DEVICE_PARAMS");
        midi.device ().tapButton (VirtualPush1Device.TRACK);
        require (modes.isActive (Modes.TRACK), "Track CC did not restore TRACK");
        midi.device ().tapButton (VirtualPush1Device.PLAY);
        require (state.playing, "Play CC did not update the Bitwig transport model");
        setup.flush ();
        drainScheduled (state, 1);
        require (midi.device ().getCCLed (VirtualPush1Device.PLAY) > 0,
            "Play LED feedback missing: lightsCreated=" + lightsCreated + ", lightsUpdated=" + lightsUpdated
                + ", captured=" + midi.capturedOutput ().stream ().filter (m -> m.contains ("85") || m.contains ("sendCCEx")).toList ());
        midi.device ().tapButton (VirtualPush1Device.PLAY);
        require (!state.playing, "Second Play CC did not stop the Bitwig transport");
        setup.flush ();
        midi.device ().tapButton (VirtualPush1Device.RECORD);
        require (state.recording, "Record CC did not start recording in the Bitwig model");
        midi.device ().pressButton (VirtualPush1Device.SHIFT);
        midi.device ().tapButton (VirtualPush1Device.RECORD);
        require (state.launcherOverdub, "Shift+Record CC did not toggle launcher overdub");
        midi.device ().releaseButton (VirtualPush1Device.SHIFT);

        // Shift chord is delivered while both physical buttons remain down.
        midi.device ().pressButton (VirtualPush1Device.SHIFT);
        require (surface.isShiftPressed (), "Shift CC did not hold the production Shift state");
        midi.device ().pressButton (VirtualPush1Device.LEFT);
        require (surface.isShiftPressed (), "Shift state was lost while the navigation chord was held");
        midi.device ().releaseButton (VirtualPush1Device.LEFT);
        midi.device ().releaseButton (VirtualPush1Device.SHIFT);
        require (!surface.isShiftPressed (), "Shift state did not clear after physical release");

        midi.device ().tapButton (VirtualPush1Device.VOLUME);
        require (modes.isActive (Modes.VOLUME), "Volume CC did not activate VOLUME mode");
        midi.device ().tapButton (VirtualPush1Device.CLIP);
        require (modes.isActive (Modes.CLIP), "Clip CC did not activate CLIP mode");
        midi.device ().tapButton (VirtualPush1Device.TRACK);
        require (modes.isActive (Modes.TRACK), "Track CC did not restore TRACK after CLIP");

        midi.device ().tapButton (VirtualPush1Device.NOTE);
        require (Views.isNoteView (surface.getViewManager ().getActiveID ()), "Note CC did not select a note view");
        midi.device ().tapButton (VirtualPush1Device.SESSION);
        require (Views.isSessionView (surface.getViewManager ().getActiveID ()), "Session CC did not select a session view");
        midi.device ().tapButton (VirtualPush1Device.NOTE);

        // Exercise real pad callbacks, poly-aftertouch, relative encoder,
        // touch-note, ribbon and pitchbend bindings against the active view/modes.
        midi.device ().pressPad (0, 0, 100);
        midi.device ().pressPadPressure (0, 0, 80);
        midi.device ().releasePad (0, 0);
        midi.device ().turnEncoderBy (0, 1);
        midi.device ().touchEncoder (0, true);
        midi.device ().touchEncoder (0, false);
        midi.device ().turnSmallEncoderBy (0, -1);
        midi.device ().touchSmallEncoder (1, true);
        midi.device ().touchSmallEncoder (1, false);
        midi.device ().touchRibbon (true);
        midi.device ().moveRibbon (8192);
        midi.device ().touchRibbon (false);
        require (midi.getDispatchedButtonEvents () > 10, "Virtual MIDI did not reach the production pad/button handlers");
        require (midi.getDispatchedEncoderEvents () >= 2, "Relative encoder MIDI did not reach production commands");
        require (midi.getDispatchedTouchEvents () >= 6, "Encoder/ribbon touch MIDI did not reach production commands");
        require (midi.getDispatchedPitchbendEvents () >= 1, "Ribbon pitchbend MIDI did not reach its production command");
        require (midi.capturedDeviceInput ().stream ().anyMatch (event -> event[0] == VirtualPush1Device.POLY_AFTERTOUCH),
            "Pad polyphonic aftertouch was not emitted");
        require (midi.device ().getLcdRow (0).length () == 68,
            "Push 1 LCD feedback was not decoded into the virtual screen");
        for (int module = 0; module < Push1DisplayLayout.MODULE_COUNT; module++)
            require (midi.device ().getLcdModuleRow (0, module).length () == Push1DisplayLayout.MODULE_COLUMNS,
                "Production PushControllerSetup LCD output did not preserve physical module " + module);
        require (midi.capturedOutput ().stream ().anyMatch (message -> message.startsWith ("sendSysex ")),
            "Production setup did not emit any Push 1 SysEx feedback");

        // Exercise arrows end-to-end in Device mode, where horizontal
        // navigation selects devices and vertical navigation scrolls scenes.
        requireButtonMidiDelivery (midi, VirtualPush1Device.DEVICE, "Device mode for arrows");
        require (modes.isActive (Modes.DEVICE_PARAMS), "Device mode was not active for arrow navigation");
        MODEL_ACTIONS.clear ();
        navigationContext = true;
        for (final int arrow: new int [] {
            VirtualPush1Device.LEFT, VirtualPush1Device.RIGHT,
            VirtualPush1Device.UP, VirtualPush1Device.DOWN })
            requireButtonMidiDelivery (midi, arrow, "Track arrow " + arrow);
        requireAction ("device.selectPreviousItem", "Left must select the previous device");
        requireAction ("device.selectNextItem", "Right must select the next device");
        requireAction ("scene.scrollBackwards", "Up must move one scene backwards");
        requireAction ("scene.scrollForwards", "Down must move one scene forwards");
        navigationContext = false;

        // Shift changes arrows from one item to one bank page.
        MODEL_ACTIONS.clear ();
        navigationContext = true;
        midi.device ().pressButton (VirtualPush1Device.SHIFT);
        for (final int arrow: new int [] {
            VirtualPush1Device.LEFT, VirtualPush1Device.RIGHT,
            VirtualPush1Device.UP, VirtualPush1Device.DOWN })
            requireButtonMidiDelivery (midi, arrow, "Shift arrow " + arrow);
        midi.device ().releaseButton (VirtualPush1Device.SHIFT);
        requireAction ("device.selectPreviousPage", "Shift+Left must select the previous device page");
        requireAction ("device.selectNextPage", "Shift+Right must select the next device page");
        requireAction ("scene.selectPreviousPage", "Shift+Up must select the previous scene page");
        requireAction ("scene.selectNextPage", "Shift+Down must select the next scene page");
        navigationContext = false;

        // The same arrow controls must retain their intended behavior in the
        // Session view rather than launching clips or leaking into another mode.
        requireButtonMidiDelivery (midi, VirtualPush1Device.SESSION, "Session view");
        require (Views.isSessionView (surface.getViewManager ().getActiveID ()),
            "Session button did not activate Session view for directional navigation");
        MODEL_ACTIONS.clear ();
        navigationContext = true;
        for (final int arrow: new int [] {
            VirtualPush1Device.LEFT, VirtualPush1Device.RIGHT,
            VirtualPush1Device.UP, VirtualPush1Device.DOWN })
            requireButtonMidiDelivery (midi, arrow, "Session arrow " + arrow);
        requireAction ("scene.scrollBackwards", "Session Up must move one scene backwards");
        requireAction ("scene.scrollForwards", "Session Down must move one scene forwards");
        navigationContext = false;

        // In/Out are separate from arrows: first navigate devices, then
        // parameter pages, using the actual DeviceParamsMode production path.
        requireButtonMidiDelivery (midi, VirtualPush1Device.DEVICE, "Device mode");
        require (modes.isActive (Modes.DEVICE_PARAMS), "Device button did not activate Device mode for In/Out tests");
        MODEL_ACTIONS.clear ();
        requireButtonMidiDelivery (midi, VirtualPush1Device.OUT, "Out");
        requireButtonMidiDelivery (midi, VirtualPush1Device.IN, "In");
        requireAction ("device.selectPreviousItem", "Out must select the previous device");
        requireAction ("device.selectNextItem", "In must select the next device");
        ((DeviceParamsMode) modes.get (Modes.DEVICE_PARAMS)).setShowDevices (false);
        MODEL_ACTIONS.clear ();
        requireButtonMidiDelivery (midi, VirtualPush1Device.OUT, "Out parameter page");
        requireButtonMidiDelivery (midi, VirtualPush1Device.IN, "In parameter page");
        requireAction ("parameterPage.selectPreviousPage", "Out must select the previous parameter page");
        requireAction ("parameterPage.selectNextPage", "In must select the next parameter page");

        // Exercise the Push 1 New workflow against a stateful cursor-track and
        // slot model. The test asserts side effects, not just command dispatch.
        state.cursorTrackExists = true;
        state.cursorTrackPlaying = true;
        state.emptySlotAvailable = true;
        state.selectedSlotIndex = 3;
        int createdBefore = state.createdClipCount;
        midi.device ().tapButton (VirtualPush1Device.NEW);
        require (state.createdClipCount == createdBefore + 1,
            "New must create one note clip when a free cursor-track slot exists");
        require (state.lastEmptySearchStart == 3,
            "New must search for an empty slot at the selected slot index");
        require (state.cursorTrackStopCount == 1 && !state.cursorTrackPlaying,
            "New must stop the playing cursor track before creating its clip");
        require (state.lastCreatedClipOverdub,
            "A normal New press must enable overdub for the new clip");

        state.cursorTrackPlaying = false;
        state.selectedSlotIndex = -1;
        createdBefore = state.createdClipCount;
        midi.device ().pressButton (VirtualPush1Device.SHIFT);
        midi.device ().tapButton (VirtualPush1Device.NEW);
        midi.device ().releaseButton (VirtualPush1Device.SHIFT);
        require (state.createdClipCount == createdBefore + 1,
            "Shift+New must still create a note clip in an available slot");
        require (!state.lastCreatedClipOverdub,
            "Shift+New must create the clip without enabling overdub");
        require (state.cursorTrackStopCount == 1,
            "New must not stop a cursor track that is not playing");

        state.cursorTrackPlaying = true;
        state.emptySlotAvailable = false;
        createdBefore = state.createdClipCount;
        midi.device ().tapButton (VirtualPush1Device.NEW);
        require (state.createdClipCount == createdBefore,
            "New must not create a clip when the cursor track has no free slot");
        require (state.cursorTrackPlaying && state.cursorTrackStopCount == 1,
            "A failed free-slot search must not stop playback");

        state.cursorTrackExists = false;
        state.cursorTrackPlaying = false;
        state.emptySlotAvailable = true;

        // DrivenByMoss' own setup test traverses every registered view/mode
        // pair, presses every real bound button (down/long/up), and executes
        // each bound continuous and pitchbend command.
        final boolean [] matrixComplete = { false };
        setup.test (new TestCallback () {
            @Override public void startTesting () { }
            @Override public void endTesting () { matrixComplete[0] = true; }
        });
        drainScheduled (state, 100);
        require (matrixComplete[0], "Production view/mode command matrix did not finish");
        require (state.featureMatrixPairs == 700,
            "Expected the complete 50-mode × 14-view command matrix (700 pairs), got "
                + state.featureMatrixPairs);
        require (state.errors.isEmpty (), "Production mode matrix reported runtime errors: " + state.errors);

        if (args.length > 0 && "--gui".equals (args[0]))
            VirtualPush1Simulator.show (midi, setup::exit);
        else if (args.length > 0 && "--gui-smoke".equals (args[0]))
            VirtualPush1Simulator.smoke (midi, () -> {
                setup.exit ();
                // The host test harness may retain non-daemon scheduler
                // threads after its UI has been disposed; this process is
                // dedicated to the bounded GUI smoke test.
                System.exit (0);
            });
        else
            setup.exit ();
        if (!state.errors.isEmpty ())
            throw new AssertionError ("Virtual Bitwig host reported errors: " + state.errors);
        System.out.println ("Virtual Push 1 runtime passed: real PushControllerSetup initialized; "
            + "real mode/view/transport commands, Shift chords, pad aftertouch, encoder/touch/ribbon input and LED/LCD feedback passed.");
    }

    /**
     * Fail if any part of the production Push 1 view/mode inventory silently
     * disappears. The generic setup matrix can only test components that were
     * actually registered, so this independent acceptance list guards its scope.
     */
    private static void verifyPush1FeatureInventory (final PushControlSurface surface)
    {
        final var modes = surface.getModeManager ();
        final Modes [] requiredModes = {
            Modes.TRACK, Modes.TRACK_DETAILS, Modes.VOLUME, Modes.PAN, Modes.CROSSFADER,
            Modes.MASTER, Modes.MASTER_TEMP,
            Modes.DEVICE_PARAMS, Modes.DEVICE_CHAINS, Modes.DEVICE_LAYER,
            Modes.DEVICE_LAYER_VOLUME, Modes.DEVICE_LAYER_PAN, Modes.DEVICE_LAYER_DETAILS,
            Modes.BROWSER, Modes.CLIP, Modes.NOTE, Modes.FRAME, Modes.GROOVE, Modes.REC_ARM,
            Modes.ACCENT, Modes.SCALES, Modes.SCALE_LAYOUT, Modes.FIXED, Modes.RIBBON,
            Modes.VIEW_SELECT, Modes.AUTOMATION, Modes.TRANSPORT, Modes.MARKERS,
            Modes.USER, Modes.CONFIGURATION, Modes.SESSION, Modes.SESSION_VIEW_SELECT,
            Modes.REPEAT_NOTE, Modes.ADD_TRACK
        };
        for (final Modes mode: requiredModes)
            require (modes.get (mode) != null, "Required Push 1 mode is not registered: " + mode);
        for (int index = 0; index < 8; index++)
        {
            require (modes.get (Modes.get (Modes.SEND1, index)) != null,
                "Track Send mode is not registered at index " + index);
            require (modes.get (Modes.get (Modes.DEVICE_LAYER_SEND1, index)) != null,
                "Device Layer Send mode is not registered at index " + index);
        }
        require (modes.get (Modes.SETUP) == null && modes.get (Modes.INFO) == null,
            "Push 2-only Setup/Info modes must not be registered by the Push 1 extension");

        final var views = surface.getViewManager ();
        final Views [] requiredViews = {
            Views.PLAY, Views.CHORDS, Views.PIANO, Views.PRG_CHANGE, Views.CLIP_LENGTH,
            Views.COLOR, Views.SESSION, Views.SEQUENCER, Views.POLY_SEQUENCER,
            Views.DRUM, Views.DRUM4, Views.DRUM8, Views.DRUM64, Views.SCENE_PLAY
        };
        for (final Views view: requiredViews)
            require (views.get (view) != null, "Required Push 1 view is not registered: " + view);
    }


    private static IDrumPad drumPadProxy (final State state, final int index)
    {
        return (IDrumPad) Proxy.newProxyInstance (IDrumPad.class.getClassLoader (),
            new Class<?> [] { IDrumPad.class }, (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "isSelected": return state.drumSelectedPad == index;
                    case "select":
                        state.drumSelectedPad = index;
                        return null;
                    default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
            });
    }

    private static IDrumPadBank drumPadBankProxy (final State state)
    {
        return (IDrumPadBank) Proxy.newProxyInstance (IDrumPadBank.class.getClassLoader (),
            new Class<?> [] { IDrumPadBank.class }, (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "getPageSize": return 16;
                    case "getScrollPosition": return state.drumScrollPosition;
                    case "getItem": return drumPadProxy (state, (int) args[0]);
                    case "scrollTo":
                        state.drumScrollPosition = (int) args[0];
                        state.drumScrollCount++;
                        return null;
                    default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
            });
    }

    private static IDrumDevice drumDeviceProxy (final IDrumPadBank drumPadBank)
    {
        return (IDrumDevice) Proxy.newProxyInstance (IDrumDevice.class.getClassLoader (),
            new Class<?> [] { IDrumDevice.class }, (proxy, method, args) -> switch (method.getName ())
            {
                case "hasDrumPads" -> true;
                case "getDrumPadBank" -> drumPadBank;
                case "getPosition" -> 1;
                default -> defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }

    private static ICursorDevice cursorDeviceProxy (final IParameterBank parameters,
        final State state, final IDrumPadBank drumPadBank)
    {
        final IDeviceBank devices = (IDeviceBank) Proxy.newProxyInstance (
            IDeviceBank.class.getClassLoader (), new Class<?> [] { IDeviceBank.class },
            (proxy, method, args) -> {
                if (method.getName ().equals ("getPageSize"))
                    return 8;
                if (method.getName ().equals ("getItem"))
                    return Proxy.newProxyInstance (de.mossgrabers.framework.daw.data.IDevice.class.getClassLoader (),
                        new Class<?> [] { de.mossgrabers.framework.daw.data.IDevice.class },
                        (p, m, a) -> defaultValue (m.getReturnType (), m.getGenericReturnType ()));
                if (List.of ("selectPreviousItem", "selectNextItem", "selectPreviousPage", "selectNextPage").contains (method.getName ()))
                {
                    MODEL_ACTIONS.add ("device." + method.getName ());
                    return null;
                }
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
        final IParameterPageBank pages = (IParameterPageBank) Proxy.newProxyInstance (
            IParameterPageBank.class.getClassLoader (), new Class<?> [] { IParameterPageBank.class },
            (proxy, method, args) -> {
                if (method.getName ().equals ("getPageSize"))
                    return 8;
                if (method.getName ().equals ("getItem"))
                    return "";
                if (List.of ("selectPreviousPage", "selectNextPage").contains (method.getName ()))
                {
                    MODEL_ACTIONS.add ("parameterPage." + method.getName ());
                    return null;
                }
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
        return (ICursorDevice) Proxy.newProxyInstance (ICursorDevice.class.getClassLoader (),
            new Class<?> [] { ICursorDevice.class }, (proxy, method, args) -> {
                if (method.getName ().equals ("getDrumPadBank"))
                    return drumPadBank;
                if (method.getName ().equals ("isNested"))
                    return false;
                if (method.getName ().equals ("getPosition"))
                    return 1;
                if (method.getName ().equals ("getParameterBank"))
                    return parameters;
                if (method.getName ().equals ("getDeviceBank"))
                    return devices;
                if (method.getName ().equals ("getParameterPageBank"))
                    return pages;
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }

    private static IParameterBank parameterBankProxy ()
    {
        return (IParameterBank) Proxy.newProxyInstance (IParameterBank.class.getClassLoader (),
            new Class<?> [] { IParameterBank.class }, (proxy, method, args) -> {
                if (method.getName ().equals ("getPageSize"))
                    return 8;
                if (method.getName ().equals ("getItem"))
                    return Proxy.newProxyInstance (IParameter.class.getClassLoader (),
                        new Class<?> [] { IParameter.class },
                        (p, m, a) -> defaultValue (m.getReturnType (), m.getGenericReturnType ()));
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }

    private static IScene sceneProxy (final State state, final int index)
    {
        return (IScene) Proxy.newProxyInstance (IScene.class.getClassLoader (),
            new Class<?> [] { IScene.class }, (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "doesExist": return true;
                    case "isSelected": return state.sceneSelectedIndex == index;
                    case "select":
                        state.sceneSelectedIndex = index;
                        return null;
                    case "launch":
                        state.sceneLaunchCount++;
                        return null;
                    case "remove":
                        state.sceneRemoveCount++;
                        return null;
                    case "duplicate":
                        state.sceneDuplicateCount++;
                        return null;
                    default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
            });
    }

    private static ISceneBank sceneBankProxy (final State state)
    {
        return (ISceneBank) Proxy.newProxyInstance (ISceneBank.class.getClassLoader (),
            new Class<?> [] { ISceneBank.class }, (proxy, method, args) -> {
                if (List.of ("scrollBackwards", "scrollForwards", "selectPreviousPage", "selectNextPage").contains (method.getName ()))
                {
                    MODEL_ACTIONS.add ("scene." + method.getName ());
                    return null;
                }
                if (method.getName ().equals ("getPageSize"))
                    return 8;
                if (method.getName ().equals ("getSelectedItem"))
                    return state.sceneSelectedIndex < 0 ? Optional.empty () : Optional.of (sceneProxy (state, state.sceneSelectedIndex));
                if (method.getName ().equals ("getItem"))
                    return sceneProxy (state, (int) args[0]);
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }

    private static ITrackBank trackBankProxy (final State state, final ISceneBank sceneBank)
    {
        return (ITrackBank) Proxy.newProxyInstance (ITrackBank.class.getClassLoader (),
            new Class<?> [] { ITrackBank.class }, (proxy, method, args) -> {
                if (List.of ("selectPreviousItem", "selectNextItem", "selectPreviousPage", "selectNextPage").contains (method.getName ()))
                {
                    MODEL_ACTIONS.add ("track." + method.getName ());
                    return null;
                }
                if (method.getName ().equals ("getPageSize"))
                    return 8;
                if (method.getName ().equals ("getSelectedItem"))
                    return navigationContext ? Optional.of (trackProxy (state, sceneBank)) : Optional.empty ();
                if (method.getName ().equals ("getSceneBank"))
                    return sceneBank;
                if (method.getName ().equals ("getItem"))
                    return trackProxy (state, sceneBank);
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }

    private static INoteClip noteClipProxy (final State state)
    {
        return (INoteClip) Proxy.newProxyInstance (INoteClip.class.getClassLoader (),
            new Class<?> [] { INoteClip.class }, (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "doesExist":
                        state.clipExistenceQueries++;
                        return state.clipExists;
                    case "getCurrentStep":
                        state.clipCurrentStepQueries++;
                        return state.clipCurrentStep;
                    case "getEditPage": return state.clipEditPage;
                    case "scrollToPage":
                        state.clipScrollToPageCount++;
                        state.lastClipPage = (int) args[0];
                        state.clipEditPage = (int) args[0];
                        return null;
                    default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
            });
    }

    private static IBrowserColumn browserColumnProxy (final int index)
    {
        return (IBrowserColumn) Proxy.newProxyInstance (IBrowserColumn.class.getClassLoader (),
            new Class<?> [] { IBrowserColumn.class }, (proxy, method, args) -> switch (method.getName ())
            {
                case "doesExist", "doesCursorExist" -> true;
                case "getIndex" -> index;
                case "getName" -> "Filter " + index;
                case "getCursorName" -> "Selected " + index;
                case "getWildcard" -> "*";
                default -> defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }

    private static IBrowser browserProxy (final State state)
    {
        return (IBrowser) Proxy.newProxyInstance (IBrowser.class.getClassLoader (),
            new Class<?> [] { IBrowser.class }, (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "getFilterColumnCount": return 8;
                    case "getFilterColumn": return browserColumnProxy ((int) args[0]);
                    case "selectPreviousFilterItem":
                        state.browserActions.add ("filter.previous:" + args[0]);
                        return null;
                    case "selectNextFilterItem":
                        state.browserActions.add ("filter.next:" + args[0]);
                        return null;
                    case "selectPreviousResult":
                        state.browserActions.add ("result.previous");
                        return null;
                    case "selectNextResult":
                        state.browserActions.add ("result.next");
                        return null;
                    case "togglePreviewEnabled":
                        state.browserPreviewEnabled = !state.browserPreviewEnabled;
                        return null;
                    case "isPreviewEnabled": return state.browserPreviewEnabled;
                    case "isActive": return true;
                    default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
            });
    }

    private static ISlot slotProxy (final State state, final int index)
    {
        return (ISlot) Proxy.newProxyInstance (ISlot.class.getClassLoader (),
            new Class<?> [] { ISlot.class }, (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "getIndex": return index;
                    case "hasContent": return false;
                    case "testAndClearLaunchedImmediately": return false;
                    case "launch":
                        state.slotLaunchCount++;
                        return null;
                    case "launchImmediately":
                        state.slotLaunchImmediatelyCount++;
                        return null;
                    case "select":
                        state.slotSelectCount++;
                        return null;
                    case "getName": return "Virtual Clip";
                    default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
            });
    }

    private static ISlotBank slotBankProxy (final State state)
    {
        return (ISlotBank) Proxy.newProxyInstance (ISlotBank.class.getClassLoader (),
            new Class<?> [] { ISlotBank.class }, (proxy, method, args) -> {
                if (method.getName ().equals ("getSelectedItem"))
                    return state.selectedSlotIndex < 0
                        ? Optional.empty () : Optional.of (slotProxy (state, state.selectedSlotIndex));
                if (method.getName ().equals ("getEmptySlot"))
                {
                    state.lastEmptySearchStart = (int) args[0];
                    return state.emptySlotAvailable
                        ? Optional.of (slotProxy (state, Math.max (0, state.selectedSlotIndex))) : Optional.empty ();
                }
                if (method.getName ().equals ("getItem"))
                {
                    state.slotItemRequests++;
                    return slotProxy (state, (int) args[0]);
                }
                if (method.getName ().equals ("getPageSize"))
                    return 8;
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }

    private static ICursorTrack cursorTrackProxy (final State state, final ISlotBank slotBank)
    {
        return (ICursorTrack) Proxy.newProxyInstance (ICursorTrack.class.getClassLoader (),
            new Class<?> [] { ICursorTrack.class }, (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "doesExist": return state.cursorTrackExists;
                    case "isPlaying": return state.cursorTrackPlaying;
                    case "getSlotBank": return slotBank;
                    case "stop":
                        state.cursorTrackStopCount++;
                        state.cursorTrackPlaying = false;
                        return null;
                    default:
                        return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
            });
    }

    private static ITrack trackProxy (final State state, final ISceneBank sceneBank)
    {
        final ISendBank sendBank = (ISendBank) Proxy.newProxyInstance (ISendBank.class.getClassLoader (),
            new Class<?> [] { ISendBank.class }, (proxy, method, args) -> {
                if (method.getName ().equals ("getPageSize"))
                    return 8;
                if (method.getName ().equals ("getItem"))
                    return Proxy.newProxyInstance (ISend.class.getClassLoader (),
                        new Class<?> [] { ISend.class },
                        (p, m, a) -> defaultValue (m.getReturnType (), m.getGenericReturnType ()));
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
        return (ITrack) Proxy.newProxyInstance (ITrack.class.getClassLoader (),
            new Class<?> [] { ITrack.class }, (proxy, method, args) -> {
                if (method.getName ().equals ("doesExist"))
                    return true;
                if (method.getName ().equals ("getSendBank"))
                    return sendBank;
                if (method.getName ().equals ("getSceneBank"))
                    return sceneBank;
                if (method.getName ().equals ("getSlotBank"))
                {
                    state.trackSlotBankRequests++;
                    return slotBankProxy (state);
                }
                if (method.getName ().equals ("isRecArm"))
                    return false;
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }

    private static IDeviceMetadata deviceMetadata (final String name)
    {
        return (IDeviceMetadata) Proxy.newProxyInstance (IDeviceMetadata.class.getClassLoader (),
            new Class<?> [] { IDeviceMetadata.class }, (proxy, method, args) -> switch (method.getName ())
            {
                case "name" -> name;
                case "fullName" -> name;
                default -> defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }

    private static ISettingsUI settingsUI ()
    {
        return (ISettingsUI) Proxy.newProxyInstance (ISettingsUI.class.getClassLoader (),
            new Class<?> [] { ISettingsUI.class }, (proxy, method, args) -> {
                if (method.getReturnType ().isInterface ())
                {
                    Object initial = args == null || args.length == 0 ? null : args[args.length - 1];
                    // Choose STOP for the Play button's pause behavior so the
                    // test has a deterministic start/stop transport contract.
                    if (method.getName ().equals ("getEnumSetting") && args != null
                            && args.length > 0 && "Behaviour on Pause".equals (args[0]))
                        initial = "Stop";
                    return settingProxy (method.getReturnType (), initial);
                }
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }

    private static Object settingProxy (final Class<?> type, final Object initial)
    {
        final Object [] value = { initial };
        final List<IValueObserver<Object>> observers = new java.util.ArrayList<> ();
        return Proxy.newProxyInstance (type.getClassLoader (), new Class<?> [] { type },
            (proxy, method, args) -> {
                if (method.getName ().equals ("get") || method.getName ().equals ("getValue"))
                    return value[0] == null ? defaultValue (method.getReturnType (), method.getGenericReturnType ()) : value[0];
                if (method.getName ().equals ("addValueObserver"))
                {
                    @SuppressWarnings ("unchecked")
                    final IValueObserver<Object> observer = (IValueObserver<Object>) args[0];
                    observers.add (observer);
                    observer.update (value[0]);
                    return null;
                }
                if (method.getName ().equals ("set") || method.getName ().equals ("setValue"))
                {
                    if (args != null && args.length > 0)
                    {
                        value[0] = args[0];
                        observers.forEach (observer -> observer.update (value[0]));
                    }
                    return null;
                }
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }

    private static IHwSurfaceFactory surfaceFactory (final VirtualPush1MidiBench midi)
    {
        final List<IHwLight> lights = new java.util.ArrayList<> ();
        return (IHwSurfaceFactory) Proxy.newProxyInstance (IHwSurfaceFactory.class.getClassLoader (),
            new Class<?> [] { IHwSurfaceFactory.class }, (proxy, method, args) -> switch (method.getName ())
            {
                case "createButton" -> buttonProxy (midi);
                case "createRelativeKnob" -> continuousProxy (IHwRelativeKnob.class, midi);
                case "createFader" -> continuousProxy (IHwFader.class, midi);
                case "createAbsoluteKnob" -> continuousProxy (de.mossgrabers.framework.controller.hardware.IHwAbsoluteKnob.class, midi);
                case "createLight" -> {
                    final IHwLight light = lightProxy (args);
                    lightsCreated++;
                    lights.add (light);
                    yield light;
                }
                case "flush" -> {
                    lights.forEach (IHwLight::update);
                    yield null;
                }
                default -> defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }

    private static IHwLight lightProxy (final Object [] args)
    {
        if (args[2] instanceof IntSupplier supplier && args[3] instanceof IntConsumer consumer)
        {
            final int [] last = { Integer.MIN_VALUE };
            return (IHwLight) Proxy.newProxyInstance (IHwLight.class.getClassLoader (),
                new Class<?> [] { IHwLight.class }, (proxy, method, callArgs) -> {
                    switch (method.getName ())
                    {
                        case "update":
                            lightsUpdated++;
                            final int value = supplier.getAsInt ();
                            if (value != last[0])
                            {
                                last[0] = value;
                                consumer.accept (value);
                            }
                            return null;
                        case "forceFlush": last[0] = Integer.MIN_VALUE; return null;
                        case "turnOff": last[0] = 0; consumer.accept (0); return null;
                        default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                    }
                });
        }
        if (args[2] instanceof Supplier<?> supplier && args[3] instanceof Consumer<?> consumer)
            return (IHwLight) Proxy.newProxyInstance (IHwLight.class.getClassLoader (),
                new Class<?> [] { IHwLight.class }, (proxy, method, callArgs) -> {
                    if (method.getName ().equals ("update"))
                    {
                        @SuppressWarnings ("unchecked")
                        final Consumer<Object> output = (Consumer<Object>) consumer;
                        output.accept (supplier.get ());
                    }
                    return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                });
        return (IHwLight) defaultValue (IHwLight.class, IHwLight.class);
    }

    private static IHwButton buttonProxy (final VirtualPush1MidiBench midi)
    {
        final TriggerCommand [] command = { null };
        final boolean [] pressed = { false };
        final int [] velocity = { 0 };
        final IHwLight [] light = { null };
        final IHwButton [] self = new IHwButton [1];
        self[0] = (IHwButton) Proxy.newProxyInstance (IHwButton.class.getClassLoader (),
            new Class<?> [] { IHwButton.class }, (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "bind":
                        if (args[0] instanceof TriggerCommand trigger) command[0] = trigger;
                        else if (args[0] instanceof IMidiInput input)
                        {
                            final BindType bindType = (BindType) args[1];
                            final int channel = (int) args[2];
                            final int control = (int) args[3];
                            if (args.length > 4)
                                input.bind (self[0], bindType, channel, control, (int) args[4]);
                            else
                                input.bind (self[0], bindType, channel, control);
                        }
                        return null;
                    case "trigger":
                        final ButtonEvent event = args == null || args.length == 0 ? ButtonEvent.DOWN : (ButtonEvent) args[0];
                        final double amount = args != null && args.length > 1 ? (double) args[1] : 1.0;
                        pressed[0] = event == ButtonEvent.DOWN || event == ButtonEvent.LONG;
                        velocity[0] = (int) (amount * 127.0);
                        if (command[0] != null) command[0].execute (event, velocity[0]);
                        return null;
                    case "getCommand": return command[0];
                    case "isPressed": return pressed[0];
                    case "isLongPressed": return false;
                    case "getPressedVelocity": return velocity[0];
                    case "addLight": light[0] = (IHwLight) args[0]; return null;
                    case "getLight": return light[0];
                    case "isBound": return command[0] != null;
                    case "toString": return "Virtual Push hardware button";
                    default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
            });
        return self[0];
    }

    private static <T> T continuousProxy (final Class<T> type, final VirtualPush1MidiBench midi)
    {
        final ContinuousCommand [] command = { null };
        final TriggerCommand [] touch = { null };
        final PitchbendCommand [] pitchbend = { null };
        return type.cast (Proxy.newProxyInstance (type.getClassLoader (), new Class<?> [] { type },
            (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "bind":
                        if (args[0] instanceof ContinuousCommand continuous) command[0] = continuous;
                        else if (args[0] instanceof PitchbendCommand bend) pitchbend[0] = bend;
                        else if (args[0] instanceof IMidiInput input)
                        {
                            if (proxy instanceof IHwRelativeKnob knob)
                                input.bind (knob, (BindType) args[1], (int) args[2], (int) args[3], RelativeEncoding.TWOS_COMPLEMENT);
                            else if (proxy instanceof IHwFader fader)
                                input.bind (fader, (BindType) args[1], (int) args[2], (int) args[3]);
                        }
                        return null;
                    case "bindTouch":
                        touch[0] = (TriggerCommand) args[0];
                        if (args[1] instanceof IMidiInput input)
                        {
                            if (proxy instanceof IHwRelativeKnob knob)
                                input.bindTouch (knob, (BindType) args[2], (int) args[3], (int) args[4]);
                            else if (proxy instanceof IHwFader fader)
                                input.bindTouch (fader, (BindType) args[2], (int) args[3], (int) args[4]);
                        }
                        return null;
                    case "getCommand": return command[0];
                    case "getTouchCommand": return touch[0];
                    case "getPitchbendCommand": return pitchbend[0];
                    case "triggerTouch":
                        if (touch[0] != null)
                            touch[0].execute ((boolean) args[0] ? ButtonEvent.DOWN : ButtonEvent.UP, (boolean) args[0] ? 127 : 0);
                        return null;
                    case "handleValue":
                        if (command[0] != null)
                        {
                            final double relative = (double) args[0] * 61.0;
                            final int steps = (int) (relative > 0 ? Math.ceil (relative) : Math.floor (relative));
                            command[0].execute (steps < 0 ? steps + 128 : steps);
                        }
                        return null;
                    case "isTouched": case "isLongTouched": case "shouldAdaptSensitivity": return false;
                    case "toString": return "Virtual Push continuous control";
                    default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
            }));
    }

    private static Object defaultValue (final Class<?> type, final Type genericType)
    {
        if (type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return (char) 0;
        if (type == String.class) return "";
        if (type == Optional.class) return Optional.empty ();
        if (type == List.class || type == Collection.class) return List.of ();
        if (type == Set.class) return Set.of ();
        if (type == Map.class) return Map.of ();
        if (type.isArray ()) return Array.newInstance (type.getComponentType (), 0);
        if (type.isEnum ())
        {
            final Object [] constants = type.getEnumConstants ();
            return constants.length == 0 ? null : constants[0];
        }
        if (type.isInterface ())
            return Proxy.newProxyInstance (type.getClassLoader (), new Class<?> [] { type },
                (proxy, method, args) -> {
                    if (method.getName ().equals ("getPageSize") || method.getName ().equals ("size"))
                        return 8;
                    if (method.getName ().equals ("getSelectedItem"))
                        return Optional.empty ();
                    return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                });
        return null;
    }

    /** Run only tasks for the requested virtual delay (1 ms flush or 100 ms test tick). */
    private static void drainScheduled (final State state, final long delay)
    {
        int steps = 0;
        while (steps++ < 100000)
        {
            ScheduledTask next = null;
            final Iterator<ScheduledTask> iterator = state.scheduled.iterator ();
            while (iterator.hasNext ())
            {
                final ScheduledTask candidate = iterator.next ();
                if (candidate.delay () == delay)
                {
                    next = candidate;
                    iterator.remove ();
                    break;
                }
            }
            if (next == null)
                return;
            next.task ().run ();
        }
        throw new AssertionError ("Virtual Bitwig scheduler exceeded 100000 tasks at delay " + delay);
    }

    private static void requireAction (final String action, final String message)
    {
        require (MODEL_ACTIONS.contains (action), message + "; observed model calls: " + MODEL_ACTIONS);
    }

    private static void requireButtonMidiDelivery (final VirtualPush1MidiBench midi,
        final int control, final String label)
    {
        final int before = midi.getDispatchedButtonEvents ();
        midi.device ().tapButton (control);
        final int dispatched = midi.getDispatchedButtonEvents () - before;
        require (dispatched == 2, label + " did not dispatch exactly DOWN+UP through production MIDI bindings; got " + dispatched);
    }

    private static void require (final boolean condition, final String message)
    {
        if (!condition) throw new AssertionError (message);
    }
}
