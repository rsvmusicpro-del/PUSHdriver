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
import de.mossgrabers.framework.daw.IProject;
import de.mossgrabers.framework.daw.IBrowser;
import de.mossgrabers.framework.daw.data.IBrowserColumn;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.daw.data.bank.IDeviceBank;
import de.mossgrabers.framework.daw.data.bank.IParameterPageBank;
import de.mossgrabers.framework.daw.data.IDeviceMetadata;
import de.mossgrabers.framework.daw.data.ICursorDevice;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.IMasterTrack;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.data.IDrumDevice;
import de.mossgrabers.framework.daw.data.IDrumPad;
import de.mossgrabers.framework.daw.data.ILayer;
import de.mossgrabers.framework.daw.data.bank.IDrumPadBank;
import de.mossgrabers.framework.daw.data.bank.ILayerBank;
import de.mossgrabers.framework.daw.clip.INoteClip;
import de.mossgrabers.framework.daw.clip.IStepInfo;
import de.mossgrabers.framework.daw.clip.NotePosition;
import de.mossgrabers.framework.daw.clip.StepState;
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
import de.mossgrabers.framework.view.sequencer.AbstractSequencerView;
import de.mossgrabers.framework.view.sequencer.AbstractDrumView;
import de.mossgrabers.framework.view.AbstractDrum64View;
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
import java.util.HashMap;
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
    private static int continuousCommandBinds;
    private static int continuousParameterBinds;
    private static int continuousUnboundValueEvents;
    private static boolean navigationContext;

    private static final class State
    {
        boolean playing;
        boolean recording;
        boolean launcherOverdub;
        boolean cursorTrackExists;
        boolean cursorTrackPlaying;
        boolean emptySlotAvailable = true;
        int emptySlotIndex = 5;
        int selectedSlotIndex = 3;
        int lastEmptySearchStart = -1;
        int cursorTrackStopCount;
        int createdClipCount;
        int createdSlotIndex = -1;
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
        int drumScrollForwardCount;
        int drumScrollBackwardCount;
        int drumIndicationOnCount;
        int drumIndicationOffCount;
        boolean drumPadsExist = true;
        int drumSelectedPad = -1;
        boolean deviceCanScrollBackwards;
        boolean deviceCanScrollForwards;
        boolean deviceCanScrollPageBackwards;
        boolean deviceCanScrollPageForwards;
        boolean layersExist = true;
        int stepToggleCount;
        int stepSetCount;
        int stepClearCount;
        int stepPageForwardCount;
        int stepPageBackwardCount;
        int stepLengthChangeCount;
        int lastStepNumber = -1;
        int lastStepNote = -1;
        final Map<String, StepState> stepStates = new java.util.HashMap<> ();
        int lastLayerPage = -1;
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
                        {
                            final StringBuilder detail = new StringBuilder (String.valueOf (callArgs[0]));
                            if (callArgs.length > 1 && callArgs[1] instanceof Throwable error)
                            {
                                detail.append (": ").append (error);
                                final java.io.StringWriter trace = new java.io.StringWriter ();
                                error.printStackTrace (new java.io.PrintWriter (trace));
                                detail.append (System.lineSeparator ()).append (trace);
                            }
                            state.errors.add (detail.toString ());
                        }
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
                        final ILayerBank layerBank = layerBankProxy (state);
                        final ICursorDevice cursorDevice = cursorDeviceProxy (parameterBank, state, cursorDrumBank, layerBank);
                        final IDrumDevice drumDevice = drumDeviceProxy (state, primaryDrumBank);
                        final IMasterTrack masterTrack = masterTrackProxy ();
                        final IProject project = projectProxy ();
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
                                case "getMasterTrack" -> masterTrack;
                                case "getProject" -> project;
                                case "canSelectedTrackHoldNotes" -> state.canHoldNotes;
                                case "getBrowser" -> browser;
                                case "getNoteClip" -> noteClip;
                                case "getCursorTrack" -> cursorTrack;
                                case "createNoteClip" -> {
                                    state.createdClipCount++;
                                    state.createdSlotIndex = (int) a[1];
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

        // A real pad press and poly-aftertouch must pass through the active
        // production Play view, its key map and the registered MIDI callback.
        final Views viewBeforePadInput = surface.getViewManager ().getActiveID ();
        state.canHoldNotes = true;
        surface.getViewManager ().setActive (Views.PLAY);
        setup.flush ();
        drainScheduled (state, 1);
        drainScheduled (state, 6);
        final int [] playNoteMap = new int [128];
        Arrays.fill (playNoteMap, -1);
        playNoteMap[0] = 60;
        playNoteMap[36] = 60;
        surface.getViewManager ().get (Views.PLAY).getKeyManager ().setNoteMatrix (playNoteMap);
        require (surface.getViewManager ().get (Views.PLAY).getKeyManager ().getMidiNoteFromGrid (36) == 60,
            "Play view aftertouch fixture must translate the physical pad to a mapped MIDI note");
        final int rawOutputBeforeAftertouch = midi.capturedRawInputOutput ().size ();
        midi.device ().pressPad (0, 0, 100);
        midi.device ().pressPadPressure (0, 0, 80);
        midi.device ().releasePad (0, 0);
        final List<int []> aftertouchOutput = midi.capturedRawInputOutput ().subList (
            rawOutputBeforeAftertouch, midi.capturedRawInputOutput ().size ());
        require (aftertouchOutput.stream ().anyMatch (message ->
            (message[0] & 0xF0) == 0xA0 && message[1] == 60 && message[2] == 80),
            "Physical pad pressure must reach the active Play view aftertouch command");
        surface.getViewManager ().setActive (viewBeforePadInput);
        state.canHoldNotes = false;

        final var modes = surface.getModeManager ();
        verifyPush1FeatureInventory (surface);
        require (modes.get (Modes.TRACK) != null, "Track mode was not registered");
        require (modes.get (Modes.DEVICE_PARAMS) != null, "Device parameter mode was not registered");
        require (modes.get (Modes.BROWSER) != null, "Browser mode was not registered");
        require (modes.get (Modes.SESSION) != null, "Session mode was not registered");
        require (modes.get (Modes.NOTE) != null, "Note mode was not registered");

        // Mixer and device parameter modes must write through their actual
        // parameter providers when a physical encoder moves.
        final Modes modeBeforeParameters = modes.getActiveID ();
        modes.setActive (Modes.VOLUME);
        setup.flush ();
        MODEL_ACTIONS.clear ();
        final int volumeEncoderEventsBefore = midi.getDispatchedEncoderEvents ();
        midi.device ().turnEncoderBy (0, 1);
        require (midi.getDispatchedEncoderEvents () > volumeEncoderEventsBefore,
            "Volume encoder MIDI event was not dispatched");
        require (MODEL_ACTIONS.contains ("track.changeVolume") || MODEL_ACTIONS.contains ("track.setVolume")
                || MODEL_ACTIONS.contains ("track.volume.changeValue"),
            "Volume encoder must write the selected track's volume parameter; activeMode="
                + modes.getActiveID () + ", encoderEvents="
                + (midi.getDispatchedEncoderEvents () - volumeEncoderEventsBefore)
                + ", commandBinds=" + continuousCommandBinds
                + ", parameterBinds=" + continuousParameterBinds
                + ", unboundValueEvents=" + continuousUnboundValueEvents
                + ", modelActions=" + MODEL_ACTIONS);
        modes.setActive (Modes.PAN);
        setup.flush ();
        MODEL_ACTIONS.clear ();
        midi.device ().turnEncoderBy (0, 1);
        require (MODEL_ACTIONS.contains ("track.changePan") || MODEL_ACTIONS.contains ("track.setPan")
                || MODEL_ACTIONS.contains ("track.pan.changeValue"),
            "Pan encoder must write the selected track's pan parameter");
        modes.setActive (Modes.CROSSFADER);
        setup.flush ();
        MODEL_ACTIONS.clear ();
        midi.device ().turnEncoderBy (0, 1);
        require (MODEL_ACTIONS.contains ("track.changeCrossfade") || MODEL_ACTIONS.contains ("track.setCrossfade")
                || MODEL_ACTIONS.contains ("track.crossfade.changeValue"),
            "Crossfade encoder must write the selected track's crossfade parameter");
        modes.setActive (Modes.SEND1);
        setup.flush ();
        MODEL_ACTIONS.clear ();
        midi.device ().turnEncoderBy (0, 1);
        require (MODEL_ACTIONS.contains ("track.send.0.changeValue") || MODEL_ACTIONS.contains ("track.send.0.setValue"),
            "Send 1 encoder must write the selected track's send parameter");
        modes.setActive (Modes.MASTER);
        setup.flush ();
        MODEL_ACTIONS.clear ();
        midi.device ().turnEncoderBy (0, 1);
        require (MODEL_ACTIONS.stream ().anyMatch (action -> action.startsWith ("master.volume.")
                && List.of ("inc", "changeValue", "setValue", "setNormalizedValue", "setValueImmediatly")
                    .stream ().anyMatch (action::endsWith)),
            "Master encoder must write the master volume parameter");
        modes.setActive (Modes.DEVICE_LAYER_VOLUME);
        setup.flush ();
        MODEL_ACTIONS.clear ();
        midi.device ().turnEncoderBy (0, 1);
        require (MODEL_ACTIONS.stream ().anyMatch (action -> action.startsWith ("layer.volume.0.")
                && List.of ("inc", "changeValue", "setValue", "setNormalizedValue", "setValueImmediatly")
                    .stream ().anyMatch (action::endsWith)),
            "Device Layer Volume encoder must write the selected layer volume parameter");
        midi.device ().pressButton (VirtualPush1Device.DELETE);
        midi.device ().touchEncoder (0, true);
        midi.device ().touchEncoder (0, false);
        midi.device ().releaseButton (VirtualPush1Device.DELETE);
        require (MODEL_ACTIONS.contains ("layer.0.resetVolume") && MODEL_ACTIONS.contains ("layer.0.touchVolume"),
            "Delete+Layer Volume touch must reset the layer and report automation touch");
        modes.setActive (Modes.DEVICE_LAYER_PAN);
        setup.flush ();
        MODEL_ACTIONS.clear ();
        midi.device ().pressButton (VirtualPush1Device.DELETE);
        midi.device ().touchEncoder (0, true);
        midi.device ().touchEncoder (0, false);
        midi.device ().releaseButton (VirtualPush1Device.DELETE);
        require (MODEL_ACTIONS.contains ("layer.0.resetPan") && MODEL_ACTIONS.contains ("layer.0.touchPan"),
            "Delete+Layer Pan touch must reset the layer and report automation touch");
        modes.setActive (Modes.get (Modes.DEVICE_LAYER_SEND1, 0));
        setup.flush ();
        MODEL_ACTIONS.clear ();
        midi.device ().pressButton (VirtualPush1Device.DELETE);
        midi.device ().touchEncoder (0, true);
        midi.device ().touchEncoder (0, false);
        midi.device ().releaseButton (VirtualPush1Device.DELETE);
        require (MODEL_ACTIONS.contains ("layer.0.send.0.resetValue")
                && MODEL_ACTIONS.contains ("layer.0.send.0.touchValue"),
            "Delete+Layer Send touch must reset the send and report automation touch");
        modes.setActive (Modes.DEVICE_PARAMS);
        setup.flush ();
        MODEL_ACTIONS.clear ();
        midi.device ().turnEncoderBy (0, 1);
        require (MODEL_ACTIONS.stream ().anyMatch (action -> action.startsWith ("device.parameter.")
                && List.of ("inc", "changeValue", "setValue", "setNormalizedValue", "setValueImmediatly")
                    .stream ().anyMatch (action::endsWith)),
            "Device parameter encoder must write the selected device parameter");
        modes.setActive (modeBeforeParameters);
        setup.flush ();

        // Empty device-layer banks must safely no-op on encoder touch.
        state.layersExist = false;
        modes.setActive (Modes.DEVICE_LAYER_VOLUME);
        midi.device ().touchEncoder (0, true);
        midi.device ().touchEncoder (0, false);
        state.layersExist = true;
        midi.device ().touchEncoder (0, true);
        midi.device ().touchEncoder (0, false);

        // Device bank arrow availability must reflect the first/last item
        // and page boundaries exposed by the API-17 device bank.
        final DeviceParamsMode boundaryDeviceMode = (DeviceParamsMode) modes.get (Modes.DEVICE_PARAMS);
        boundaryDeviceMode.setShowDevices (true);
        state.deviceCanScrollBackwards = false;
        state.deviceCanScrollForwards = true;
        state.deviceCanScrollPageBackwards = false;
        state.deviceCanScrollPageForwards = true;
        require (!boundaryDeviceMode.hasPreviousItem () && boundaryDeviceMode.hasNextItem (),
            "Device navigation must disable Left at the first item and enable Right");
        require (!boundaryDeviceMode.hasPreviousItemPage () && boundaryDeviceMode.hasNextItemPage (),
            "Device paging must disable Shift+Left at the first page and enable Shift+Right");
        state.deviceCanScrollBackwards = true;
        state.deviceCanScrollForwards = false;
        state.deviceCanScrollPageBackwards = true;
        state.deviceCanScrollPageForwards = false;
        require (boundaryDeviceMode.hasPreviousItem () && !boundaryDeviceMode.hasNextItem (),
            "Device navigation must disable Right at the last item and enable Left");
        require (boundaryDeviceMode.hasPreviousItemPage () && !boundaryDeviceMode.hasNextItemPage (),
            "Device paging must disable Shift+Right at the last page and enable Shift+Left");
        boundaryDeviceMode.setShowDevices (false);

        // Track navigation and Shift+Track swapping must reach the API model.
        navigationContext = true;
        modes.setActive (Modes.TRACK);
        MODEL_ACTIONS.clear ();
        midi.device ().tapButton (VirtualPush1Device.LEFT);
        midi.device ().tapButton (VirtualPush1Device.RIGHT);
        requireAction ("track.selectPreviousItem", "Track Left must select the previous track");
        requireAction ("track.selectNextItem", "Track Right must select the next track");
        MODEL_ACTIONS.clear ();
        midi.device ().pressButton (VirtualPush1Device.SHIFT);
        midi.device ().tapButton (VirtualPush1Device.LEFT);
        midi.device ().tapButton (VirtualPush1Device.RIGHT);
        midi.device ().releaseButton (VirtualPush1Device.SHIFT);
        requireAction ("cursorTrack.swapWithPrevious", "Shift+Track Left must swap with the previous track");
        requireAction ("cursorTrack.swapWithNext", "Shift+Track Right must swap with the next track");
        navigationContext = false;
        modes.setActive (modeBeforeParameters);

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

        // Exercise the actual Browser MIDI bindings, content-type paging,
        // encoder navigation and Delete+touch reset against the model.
        modes.setActive (Modes.BROWSER);
        MODEL_ACTIONS.clear ();
        requireButtonMidiDelivery (midi, VirtualPush1Device.TOP_ROW_FIRST, "Browser previous filter item");
        requireButtonMidiDelivery (midi, VirtualPush1Device.BOTTOM_ROW_FIRST, "Browser next filter item");
        requireAction ("browser.selectPreviousFilterItem:0", "Browser top row must navigate the previous filter item");
        requireAction ("browser.selectNextFilterItem:0", "Browser bottom row must navigate the next filter item");
        requireButtonMidiDelivery (midi, VirtualPush1Device.TOP_ROW_FIRST + 7, "Browser previous result");
        requireButtonMidiDelivery (midi, VirtualPush1Device.BOTTOM_ROW_FIRST + 7, "Browser next result");
        requireAction ("browser.selectPreviousResult", "Browser top-right button must select the previous result");
        requireAction ("browser.selectNextResult", "Browser bottom-right button must select the next result");
        MODEL_ACTIONS.clear ();
        midi.device ().pressButton (VirtualPush1Device.SHIFT);
        requireButtonMidiDelivery (midi, VirtualPush1Device.LEFT, "Shift+Browser previous content type");
        requireButtonMidiDelivery (midi, VirtualPush1Device.RIGHT, "Shift+Browser next content type");
        midi.device ().releaseButton (VirtualPush1Device.SHIFT);
        requireAction ("browser.previousContentType", "Shift+Browser Left must change content type backwards");
        requireAction ("browser.nextContentType", "Shift+Browser Right must change content type forwards");
        MODEL_ACTIONS.clear ();
        browserMode.onKnobTouch (0, true);
        for (int i = 0; i < 8; i++)
            browserMode.onKnobValue (0, 8);
        browserMode.onKnobTouch (0, false);
        requireAction ("browser.selectNextFilterItem:0", "Browser filter encoder must navigate the selected column");
        MODEL_ACTIONS.clear ();
        browserMode.onKnobTouch (7, true);
        for (int i = 0; i < 8; i++)
            browserMode.onKnobValue (7, 8);
        browserMode.onKnobTouch (7, false);
        requireAction ("browser.selectNextResult", "Browser master encoder must navigate preset results");
        MODEL_ACTIONS.clear ();
        midi.device ().pressButton (VirtualPush1Device.DELETE);
        browserMode.onKnobTouch (0, true);
        browserMode.onKnobTouch (0, false);
        midi.device ().releaseButton (VirtualPush1Device.DELETE);
        requireAction ("browser.resetFilterColumn:0", "Delete+Browser encoder touch must reset the selected filter column");

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

        // Note Repeat must also refresh when Bitwig creates its optional
        // note-repeat service after the extension has already started.
        modes.setActive (Modes.REPEAT_NOTE);
        midi.device ().pressButton (VirtualPush1Device.DELETE);
        midi.device ().touchEncoder (5, true);
        midi.device ().touchEncoder (5, false);
        midi.device ().touchEncoder (6, true);
        midi.device ().touchEncoder (6, false);
        midi.device ().releaseButton (VirtualPush1Device.DELETE);
        midi.device ().tapButton (VirtualPush1Device.TOP_ROW_FIRST + 4);
        midi.device ().tapButton (VirtualPush1Device.BOTTOM_ROW_FIRST + 7);
        final INoteRepeat lateNoteRepeat = (INoteRepeat) Proxy.newProxyInstance (
            INoteRepeat.class.getClassLoader (), new Class<?> [] { INoteRepeat.class },
            (proxy, method, callArgs) -> {
                if (method.getName ().startsWith ("set") || method.getName ().startsWith ("toggle"))
                    MODEL_ACTIONS.add ("lateNoteRepeat." + method.getName ());
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
        final INoteInput lateNoteInput = (INoteInput) Proxy.newProxyInstance (
            INoteInput.class.getClassLoader (), new Class<?> [] { INoteInput.class },
            (proxy, method, callArgs) -> method.getName ().equals ("getNoteRepeat")
                ? lateNoteRepeat : defaultValue (method.getReturnType (), method.getGenericReturnType ()));
        midi.setDefaultNoteInput (lateNoteInput);
        midi.device ().pressButton (VirtualPush1Device.DELETE);
        midi.device ().touchEncoder (5, true);
        midi.device ().touchEncoder (5, false);
        midi.device ().touchEncoder (6, true);
        midi.device ().touchEncoder (6, false);
        midi.device ().releaseButton (VirtualPush1Device.DELETE);
        midi.device ().tapButton (VirtualPush1Device.TOP_ROW_FIRST + 4);
        require (MODEL_ACTIONS.contains ("lateNoteRepeat.setMode")
                && MODEL_ACTIONS.contains ("lateNoteRepeat.setOctaves")
                && MODEL_ACTIONS.contains ("lateNoteRepeat.toggleLatchActive"),
            "Late-created Note Repeat service must be discovered and receive mode, octave and latch controls");
        midi.setDefaultNoteInput (null);
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

        // Musical editing coverage: the real Sequencer, Poly Sequencer,
        // Drum 4 and Drum 8 views must edit the virtual note clip through pads.
        state.canHoldNotes = true;
        state.clipExists = true;
        state.stepToggleCount = 0;
        state.stepPageBackwardCount = 0;
        state.stepPageForwardCount = 0;
        surface.getViewManager ().setActive (Views.SEQUENCER);
        setup.flush ();
        drainScheduled (state, 1);
        drainScheduled (state, 6);
        drainScheduled (state, 100);
        final SequencerView stepSequencer = (SequencerView) surface.getViewManager ().get (Views.SEQUENCER);
        stepSequencer.setSequencerActive (true);
        stepSequencer.getKeyManager ().setNoteMatrix (new int [] { 36, 37, 38, 39, 40 });
        final int sequencerStepsBefore = state.stepToggleCount;
        midi.device ().pressPad (0, 0, 100);
        midi.device ().releasePad (0, 0);
        require (state.stepToggleCount > sequencerStepsBefore,
            "Sequencer pad release must toggle a step in the active note clip");
        stepSequencer.onLeft (ButtonEvent.DOWN);
        stepSequencer.onRight (ButtonEvent.DOWN);
        require (state.stepPageBackwardCount > 0 && state.stepPageForwardCount > 0,
            "Sequencer page controls must scroll the note clip in both directions");

        surface.getViewManager ().setActive (Views.POLY_SEQUENCER);
        setup.flush ();
        drainScheduled (state, 1);
        drainScheduled (state, 6);
        drainScheduled (state, 100);
        final AbstractSequencerView<?, ?> polySequencer =
            (AbstractSequencerView<?, ?>) surface.getViewManager ().get (Views.POLY_SEQUENCER);
        polySequencer.setSequencerActive (true);
        final int [] polyNoteMap = new int [128];
        Arrays.fill (polyNoteMap, -1);
        polyNoteMap[36] = 60;
        polySequencer.getKeyManager ().setNoteMatrix (polyNoteMap);
        final int polyStepsBefore = state.stepToggleCount;
        midi.device ().pressPad (0, 0, 100);
        midi.device ().releasePad (0, 0);
        midi.device ().pressPad (4, 0, 100);
        midi.device ().releasePad (4, 0);
        require (state.stepToggleCount > polyStepsBefore,
            "Poly Sequencer pad gestures must add a note and stamp it into a step");

        state.drumPadsExist = true;
        surface.getViewManager ().setActive (Views.DRUM);
        setup.flush ();
        drainScheduled (state, 1);
        drainScheduled (state, 6);
        drainScheduled (state, 100);
        final AbstractDrumView<?, ?> drum4 = (AbstractDrumView<?, ?>) surface.getViewManager ().get (Views.DRUM);
        drum4.getKeyManager ().setNoteMatrix (new int [] { 36, 37, 38, 39, 40 });
        midi.device ().pressPad (0, 1, 100);
        midi.device ().releasePad (0, 1);
        require (drum4.getSelectedPad () == 1,
            "Drum 4 pad grid must select the expected pad");
        surface.getViewManager ().setActive (Views.DRUM);
        drainScheduled (state, 6);
        drainScheduled (state, 100);
        ((AbstractSequencerView<?, ?>) drum4).setSequencerActive (true);
        drum4.getKeyManager ().setNoteMatrix (new int [] { 36, 37, 38, 39, 40 });
        final int drumStepsBefore = state.stepToggleCount;
        midi.device ().pressPad (4, 0, 100);
        midi.device ().releasePad (4, 0);
        require (state.stepToggleCount > drumStepsBefore && state.lastStepNote >= 0,
            "Drum 4 sequencer grid must write a note step on pad release");

        surface.getViewManager ().setActive (Views.DRUM8);
        setup.flush ();
        drainScheduled (state, 1);
        drainScheduled (state, 6);
        drainScheduled (state, 100);
        final AbstractDrumView<?, ?> drum8 = (AbstractDrumView<?, ?>) surface.getViewManager ().get (Views.DRUM8);
        midi.device ().pressPad (0, 2, 100);
        midi.device ().releasePad (0, 2);
        require (drum8.getSelectedPad () >= 0,
            "Drum 8 pad grid must map a valid selected pad");

        // Drum64 covers 4×4 block traversal, pad modifiers, bank alignment
        // and the 16-position octave-scroll behavior.
        state.clipExists = true;
        state.drumScrollPosition = 0;
        state.drumScrollCount = 0;
        state.drumScrollForwardCount = 0;
        state.drumIndicationOnCount = 0;
        state.drumIndicationOffCount = 0;
        surface.getViewManager ().setActive (Views.DRUM64);
        setup.flush ();
        drainScheduled (state, 1);
        drainScheduled (state, 6);
        drainScheduled (state, 100);
        final Drum64View fullDrum64 = (Drum64View) surface.getViewManager ().get (Views.DRUM64);
        require (state.drumIndicationOnCount > 0,
            "Drum64 activation must enable drum-pad indication");
        final int [] drum64ExpectedPads = { 0, 32, 16 };
        final int [] drum64Rows = { 0, 0, 4 };
        final int [] drum64Columns = { 0, 4, 0 };
        for (int i = 0; i < drum64ExpectedPads.length; i++)
        {
            midi.device ().pressButton (VirtualPush1Device.SELECT);
            midi.device ().pressPad (drum64Rows[i], drum64Columns[i], 100);
            midi.device ().releasePad (drum64Rows[i], drum64Columns[i]);
            midi.device ().releaseButton (VirtualPush1Device.SELECT);
            require (state.drumSelectedPad == drum64ExpectedPads[i],
                "Drum64 block mapping must select pad " + drum64ExpectedPads[i]
                    + ", got " + state.drumSelectedPad);
        }
        MODEL_ACTIONS.clear ();
        midi.device ().pressButton (VirtualPush1Device.MUTE);
        midi.device ().pressPad (0, 0, 100);
        midi.device ().releasePad (0, 0);
        midi.device ().releaseButton (VirtualPush1Device.MUTE);
        requireAction ("drumPad.toggleMute:0", "Drum64 Mute+Pad must mute the matching drum pad");
        MODEL_ACTIONS.clear ();
        midi.device ().pressButton (VirtualPush1Device.SOLO);
        midi.device ().pressPad (0, 1, 100);
        midi.device ().releasePad (0, 1);
        midi.device ().releaseButton (VirtualPush1Device.SOLO);
        requireAction ("drumPad.toggleSolo:1", "Drum64 Solo+Pad must solo the matching drum pad");
        MODEL_ACTIONS.clear ();
        midi.device ().pressButton (VirtualPush1Device.DELETE);
        midi.device ().pressPad (0, 2, 100);
        midi.device ().releasePad (0, 2);
        midi.device ().releaseButton (VirtualPush1Device.DELETE);
        requireAction ("noteClip.clearRow", "Drum64 Delete+Pad must clear the matching note row");
        MODEL_ACTIONS.clear ();
        midi.device ().pressButton (VirtualPush1Device.BROWSE);
        midi.device ().pressPad (0, 3, 100);
        midi.device ().releasePad (0, 3);
        midi.device ().releaseButton (VirtualPush1Device.BROWSE);
        requireAction ("browser.replace", "Drum64 Browse+Pad must replace the browser target with that drum pad");
        fullDrum64.onOctaveUp (ButtonEvent.DOWN);
        require (state.drumScrollForwardCount == 16,
            "Drum64 octave-up must scroll the drum bank by 16 positions");
        surface.getViewManager ().setActive (Views.PLAY);
        require (state.drumIndicationOffCount > 0,
            "Drum64 deactivation must disable drum-pad indication");
        state.canHoldNotes = false;
        state.clipExists = false;
        state.drumPadsExist = false;

        // Scene Play pads must launch and select their corresponding scenes.
        final int sceneLaunchesBeforeScenePlay = state.sceneLaunchCount;
        surface.getViewManager ().setActive (Views.SCENE_PLAY);
        setup.flush ();
        drainScheduled (state, 1);
        surface.getViewManager ().get (Views.SCENE_PLAY).onGridNote (36, 100);
        require (state.sceneLaunchCount == sceneLaunchesBeforeScenePlay + 1,
            "Scene Play pad must launch the matching scene");

        // Drum64 Select+Pad must align the cursor device's 16-pad bank with
        // the corresponding 16-pad block, then select the matching drum pad.
        final Views viewBeforeDrum = surface.getViewManager ().getActiveID ();
        final Modes modeBeforeDrum = modes.getActiveID ();
        final Drum64View drum64View = (Drum64View) surface.getViewManager ().get (Views.DRUM64);
        state.canHoldNotes = true;
        state.drumPadsExist = true;
        state.drumScrollPosition = 0;
        state.drumScrollCount = 0;
        state.drumSelectedPad = -1;
        surface.getViewManager ().setActive (Views.DRUM64);
        midi.device ().pressButton (VirtualPush1Device.SELECT);
        drum64View.onGridNote (36 + 35, 100);
        midi.device ().releaseButton (VirtualPush1Device.SELECT);
        require (state.drumScrollCount == 1 && state.drumScrollPosition == 16,
            "Drum64 Select+Pad must align the cursor drum bank to the matching 16-pad page"
                + " (scrolls=" + state.drumScrollCount
                + ", position=" + state.drumScrollPosition
                + ", selectedPad=" + state.drumSelectedPad
                + ", hasDrumPads=" + state.drumPadsExist + ")");
        require (state.drumSelectedPad == 19,
            "Drum64 Select+Pad must select the matching primary drum pad");
        modes.setActive (modeBeforeDrum);
        surface.getViewManager ().setActive (viewBeforeDrum);
        state.canHoldNotes = false;

        // Exercise every physical pad through the active production view,
        // across the complete 14-view Push 1 surface (64 pads × press/release).
        final Views [] padMatrixViews = {
            Views.PLAY, Views.CHORDS, Views.PIANO, Views.PRG_CHANGE, Views.CLIP_LENGTH,
            Views.COLOR, Views.SESSION, Views.SEQUENCER, Views.POLY_SEQUENCER,
            Views.DRUM, Views.DRUM4, Views.DRUM8, Views.DRUM64, Views.SCENE_PLAY
        };
        final Views viewBeforePadMatrix = surface.getViewManager ().getActiveID ();
        final int padEventsBeforeMatrix = midi.getDispatchedButtonEvents ();
        state.canHoldNotes = true;
        for (final Views view: padMatrixViews)
        {
            surface.getViewManager ().setActive (view);
            setup.flush ();
            drainScheduled (state, 1);
            for (int row = 0; row < 8; row++)
                for (int column = 0; column < 8; column++)
                {
                    midi.device ().pressPad (row, column, 64);
                    midi.device ().releasePad (row, column);
                }
            if (view == Views.SESSION)
                midi.device ().releaseButton (VirtualPush1Device.SESSION);
        }
        require (midi.getDispatchedButtonEvents () - padEventsBeforeMatrix >= 1792,
            "The complete 14-view pad matrix must dispatch 64 pad press/release pairs per view");
        state.canHoldNotes = false;
        surface.getViewManager ().setActive (viewBeforePadMatrix);
        drainScheduled (state, 100);

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
        require (state.createdSlotIndex == 5,
            "New must create the clip in the first free slot returned by the virtual slot bank");

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
        final int clipsBeforeMissingTrack = state.createdClipCount;
        final int stopsBeforeMissingTrack = state.cursorTrackStopCount;
        midi.device ().tapButton (VirtualPush1Device.NEW);
        require (state.createdClipCount == clipsBeforeMissingTrack
                && state.cursorTrackStopCount == stopsBeforeMissingTrack,
            "New without a cursor track must neither create a clip nor stop playback");
        state.cursorTrackExists = true;
        state.cursorTrackPlaying = false;
        state.selectedSlotIndex = 3;

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
                // This process is dedicated to a bounded GUI smoke test.
                // Avoid the host teardown path here: it can wait on Bitwig's
                // scheduler even though the Swing window has already closed.
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
                    case "doesExist": case "isActivated": return state.drumPadsExist;
                    case "isSelected": return state.drumSelectedPad == index;
                    case "toggleMute": MODEL_ACTIONS.add ("drumPad.toggleMute:" + index); return null;
                    case "toggleSolo": MODEL_ACTIONS.add ("drumPad.toggleSolo:" + index); return null;
                    case "isMute": case "isSolo": return false;
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
                    case "scrollForwards":
                        state.drumScrollForwardCount++;
                        state.drumScrollPosition++;
                        return null;
                    case "scrollBackwards":
                        state.drumScrollBackwardCount++;
                        state.drumScrollPosition--;
                        return null;
                    case "setIndication":
                        if ((boolean) args[0]) state.drumIndicationOnCount++;
                        else state.drumIndicationOffCount++;
                        return null;
                    case "scrollTo":
                        state.drumScrollPosition = (int) args[0];
                        state.drumScrollCount++;
                        return null;
                    default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
            });
    }

    private static ILayer layerProxy (final int index)
    {
        final ISendBank sendBank = (ISendBank) Proxy.newProxyInstance (ISendBank.class.getClassLoader (),
            new Class<?> [] { ISendBank.class }, (proxy, method, args) -> {
                if (method.getName ().equals ("getPageSize") || method.getName ().equals ("getItemCount"))
                    return 8;
                if (method.getName ().equals ("getItem"))
                    return sendProxy ("layer." + index + ".send." + args[0]);
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
        return (ILayer) Proxy.newProxyInstance (ILayer.class.getClassLoader (),
            new Class<?> [] { ILayer.class }, (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "doesExist": return true;
                    case "getVolumeParameter": return parameterProxy ("layer.volume." + index);
                    case "getPanParameter": return parameterProxy ("layer.pan." + index);
                    case "getSendBank": return sendBank;
                    case "getVolume": case "getPan": return 64;
                    case "getVolumeStr": case "getPanStr": return "64";
                    case "getName": return "Layer " + index;
                    case "setVolume": case "changeVolume": case "resetVolume": case "touchVolume":
                    case "setPan": case "changePan": case "resetPan": case "touchPan":
                        MODEL_ACTIONS.add ("layer." + index + "." + method.getName ());
                        return null;
                    default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
            });
    }

    private static ILayerBank layerBankProxy (final State state)
    {
        return (ILayerBank) Proxy.newProxyInstance (ILayerBank.class.getClassLoader (),
            new Class<?> [] { ILayerBank.class }, (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "getPageSize": case "getItemCount": return 8;
                    case "hasExistingItems": return state.layersExist;
                    case "getItem": return layerProxy ((int) args[0]);
                    case "getSelectedItem": return Optional.of (layerProxy (0));
                    case "scrollTo":
                        state.lastLayerPage = (int) args[0];
                        return null;
                    default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
            });
    }

    private static IDrumDevice drumDeviceProxy (final State state, final IDrumPadBank drumPadBank)
    {
        return (IDrumDevice) Proxy.newProxyInstance (IDrumDevice.class.getClassLoader (),
            new Class<?> [] { IDrumDevice.class }, (proxy, method, args) -> switch (method.getName ())
            {
                case "hasDrumPads" -> state.drumPadsExist;
                case "getDrumPadBank" -> drumPadBank;
                case "getPosition" -> 1;
                default -> defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }

    private static ICursorDevice cursorDeviceProxy (final IParameterBank parameters,
        final State state, final IDrumPadBank drumPadBank, final ILayerBank layerBank)
    {
        final IDeviceBank devices = (IDeviceBank) Proxy.newProxyInstance (
            IDeviceBank.class.getClassLoader (), new Class<?> [] { IDeviceBank.class },
            (proxy, method, args) -> {
                if (method.getName ().equals ("getPageSize"))
                    return 8;
                if (method.getName ().equals ("canScrollBackwards"))
                    return state.deviceCanScrollBackwards;
                if (method.getName ().equals ("canScrollForwards"))
                    return state.deviceCanScrollForwards;
                if (method.getName ().equals ("canScrollPageBackwards"))
                    return state.deviceCanScrollPageBackwards;
                if (method.getName ().equals ("canScrollPageForwards"))
                    return state.deviceCanScrollPageForwards;
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
                if (method.getName ().equals ("getLayerBank"))
                    return layerBank;
                if (method.getName ().equals ("hasLayers"))
                    return true;
                if (method.getName ().equals ("hasDrumPads"))
                    return false;
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
                if (method.getName ().equals ("getPageSize") || method.getName ().equals ("getItemCount"))
                    return 8;
                if (method.getName ().equals ("getItem"))
                    return parameterProxy ("device.parameter." + args[0]);
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }

    private static ISend sendProxy (final String label)
    {
        return (ISend) Proxy.newProxyInstance (ISend.class.getClassLoader (),
            new Class<?> [] { ISend.class }, (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "setValue": case "setValueImmediatly": case "setNormalizedValue":
                    case "changeValue": case "touchValue": case "inc": case "resetValue":
                        MODEL_ACTIONS.add (label + "." + method.getName ());
                        return null;
                    case "getValue": case "getModulatedValue": return 64;
                    case "getDisplayedValue": return "64";
                    case "doesExist": case "isEnabled": return true;
                    case "getName": return label;
                    case "getIndex": return 0;
                    case "getId": return label;
                    default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
            });
    }

    private static IMasterTrack masterTrackProxy ()
    {
        return (IMasterTrack) Proxy.newProxyInstance (IMasterTrack.class.getClassLoader (),
            new Class<?> [] { IMasterTrack.class }, (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "getVolumeParameter": return parameterProxy ("master.volume");
                    case "getPanParameter": return parameterProxy ("master.pan");
                    case "setVolumeIndication": case "setPanIndication":
                        MODEL_ACTIONS.add ("master." + method.getName ());
                        return null;
                    case "doesExist": return true;
                    case "getName": return "Master";
                    default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
            });
    }

    private static IProject projectProxy ()
    {
        return (IProject) Proxy.newProxyInstance (IProject.class.getClassLoader (),
            new Class<?> [] { IProject.class }, (proxy, method, args) -> switch (method.getName ())
            {
                case "getCueVolumeParameter" -> parameterProxy ("project.cue.volume");
                case "getCueMixParameter" -> parameterProxy ("project.cue.mix");
                default -> defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }

    private static IParameter parameterProxy (final String label)
    {
        return (IParameter) Proxy.newProxyInstance (IParameter.class.getClassLoader (),
            new Class<?> [] { IParameter.class }, (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "setValue": case "setValueImmediatly": case "setNormalizedValue":
                    case "changeValue": case "touchValue": case "inc": case "resetValue":
                        MODEL_ACTIONS.add (label + "." + method.getName ());
                        return null;
                    case "getValue": case "getModulatedValue": return 64;
                    case "getDisplayedValue": return "64";
                    case "doesExist": case "isEnabled": return true;
                    case "getName": return label;
                    case "getIndex": return 0;
                    case "getId": return label;
                    default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
            });
    }

    private static IScene sceneProxy (final State state, final int index)
    {
        return (IScene) Proxy.newProxyInstance (IScene.class.getClassLoader (),
            new Class<?> [] { IScene.class }, (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "doesExist": return true;
                    case "getColor": return de.mossgrabers.framework.controller.color.ColorEx.BLACK;
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

    private static IStepInfo stepInfoProxy (final State state, final NotePosition position)
    {
        return (IStepInfo) Proxy.newProxyInstance (IStepInfo.class.getClassLoader (),
            new Class<?> [] { IStepInfo.class }, (proxy, method, args) -> switch (method.getName ())
            {
                case "getState" -> state.stepStates.getOrDefault (stepKey (position), StepState.OFF);
                case "isMuted", "isRepeatEnabled" -> false;
                case "getRepeatCount" -> 0;
                default -> defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }

    private static String stepKey (final NotePosition position)
    {
        return position.getChannel () + ":" + position.getStep () + ":" + position.getNote ();
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
                    case "getColor": return de.mossgrabers.framework.controller.color.ColorEx.BLACK;
                    case "getNumSteps": return state.clipExists ? 16 : 0;
                    case "getNumRows": return state.clipExists ? 128 : 0;
                    case "getStepLength": return 0.25;
                    case "getLoopStart": return 0.0;
                    case "getLoopLength": return 16.0;
                    case "getStep": return stepInfoProxy (state, (NotePosition) args[0]);
                    case "toggleStep":
                        final NotePosition toggled = (NotePosition) args[0];
                        state.lastStepNumber = toggled.getStep ();
                        state.lastStepNote = toggled.getNote ();
                        final String toggleKey = stepKey (toggled);
                        final StepState oldState = state.stepStates.getOrDefault (toggleKey, StepState.OFF);
                        state.stepStates.put (toggleKey, oldState == StepState.OFF ? StepState.START : StepState.OFF);
                        state.stepToggleCount++;
                        MODEL_ACTIONS.add ("noteClip.toggleStep");
                        return null;
                    case "setStep":
                        state.stepSetCount++;
                        state.stepStates.put (stepKey ((NotePosition) args[0]), StepState.START);
                        MODEL_ACTIONS.add ("noteClip.setStep");
                        return null;
                    case "clearStep":
                        state.stepClearCount++;
                        state.stepStates.put (stepKey ((NotePosition) args[0]), StepState.OFF);
                        MODEL_ACTIONS.add ("noteClip.clearStep");
                        return null;
                    case "clearRow":
                        state.stepClearCount++;
                        MODEL_ACTIONS.add ("noteClip.clearRow");
                        return null;
                    case "scrollStepsPageForward":
                        state.stepPageForwardCount++;
                        MODEL_ACTIONS.add ("noteClip.scrollStepsPageForward");
                        return null;
                    case "scrollStepsPageBackwards":
                        state.stepPageBackwardCount++;
                        MODEL_ACTIONS.add ("noteClip.scrollStepsPageBackwards");
                        return null;
                    case "setStepLength":
                        state.stepLengthChangeCount++;
                        MODEL_ACTIONS.add ("noteClip.setStepLength");
                        return null;
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
                        MODEL_ACTIONS.add ("browser.selectPreviousFilterItem:" + args[0]);
                        return null;
                    case "selectNextFilterItem":
                        state.browserActions.add ("filter.next:" + args[0]);
                        MODEL_ACTIONS.add ("browser.selectNextFilterItem:" + args[0]);
                        return null;
                    case "selectPreviousResult":
                        state.browserActions.add ("result.previous");
                        MODEL_ACTIONS.add ("browser.selectPreviousResult");
                        return null;
                    case "selectNextResult":
                        state.browserActions.add ("result.next");
                        MODEL_ACTIONS.add ("browser.selectNextResult");
                        return null;
                    case "togglePreviewEnabled":
                        state.browserPreviewEnabled = !state.browserPreviewEnabled;
                        MODEL_ACTIONS.add ("browser.togglePreviewEnabled");
                        return null;
                    case "previousContentType":
                        MODEL_ACTIONS.add ("browser.previousContentType");
                        return null;
                    case "nextContentType":
                        MODEL_ACTIONS.add ("browser.nextContentType");
                        return null;
                    case "resetFilterColumn":
                        MODEL_ACTIONS.add ("browser.resetFilterColumn:" + args[0]);
                        return null;
                    case "isPreviewEnabled": return state.browserPreviewEnabled;
                    case "replace":
                        state.browserActions.add ("browser.replace");
                        MODEL_ACTIONS.add ("browser.replace");
                        return null;
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
                    case "getColor": return de.mossgrabers.framework.controller.color.ColorEx.BLACK;
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
                        ? Optional.of (slotProxy (state, state.emptySlotIndex)) : Optional.empty ();
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
                    case "getColor": return de.mossgrabers.framework.controller.color.ColorEx.BLACK;
                    case "isPlaying": return state.cursorTrackPlaying;
                    case "swapWithPrevious": MODEL_ACTIONS.add ("cursorTrack.swapWithPrevious"); return null;
                    case "swapWithNext": MODEL_ACTIONS.add ("cursorTrack.swapWithNext"); return null;
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
                if (method.getName ().equals ("getPageSize") || method.getName ().equals ("getItemCount"))
                    return 8;
                if (method.getName ().equals ("getItem"))
                    return sendProxy ("track.send." + args[0]);
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
        return (ITrack) Proxy.newProxyInstance (ITrack.class.getClassLoader (),
            new Class<?> [] { ITrack.class }, (proxy, method, args) -> {
                if (method.getName ().equals ("doesExist"))
                    return true;
                if (method.getName ().equals ("getSendBank"))
                    return sendBank;
                if (method.getName ().equals ("getColor"))
                    return de.mossgrabers.framework.controller.color.ColorEx.BLACK;
                if (method.getName ().equals ("getVolumeParameter"))
                    return parameterProxy ("track.volume");
                if (method.getName ().equals ("getPanParameter"))
                    return parameterProxy ("track.pan");
                if (method.getName ().equals ("getCrossfadeParameter"))
                    return parameterProxy ("track.crossfade");
                if (List.of ("setVolume", "changeVolume", "resetVolume", "touchVolume",
                    "setPan", "changePan", "resetPan", "touchPan",
                    "setCrossfade", "changeCrossfade", "resetCrossfade", "touchCrossfade").contains (method.getName ()))
                {
                    MODEL_ACTIONS.add ("track." + method.getName ());
                    return null;
                }
                if (method.getName ().equals ("getVolume")) return 64;
                if (method.getName ().equals ("getPan")) return 64;
                if (method.getName ().equals ("getCrossfade")) return 64;
                if (method.getName ().equals ("getVolumeStr")) return "64";
                if (method.getName ().equals ("getPanStr")) return "64";
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
        final IParameter [] parameter = { null };
        final TriggerCommand [] touch = { null };
        final PitchbendCommand [] pitchbend = { null };
        return type.cast (Proxy.newProxyInstance (type.getClassLoader (), new Class<?> [] { type },
            (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "bind":
                        if (args[0] instanceof ContinuousCommand continuous)
                        {
                            command[0] = continuous;
                            parameter[0] = null;
                            continuousCommandBinds++;
                        }
                        else if (args[0] instanceof IParameter boundParameter)
                        {
                            parameter[0] = boundParameter;
                            command[0] = null;
                            continuousParameterBinds++;
                        }
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
                        final double relative = (double) args[0] * 61.0;
                        final int steps = (int) (relative > 0 ? Math.ceil (relative) : Math.floor (relative));
                        if (command[0] != null)
                        {
                            MODEL_ACTIONS.add ("continuous.handleValue.command");
                            command[0].execute (steps < 0 ? steps + 128 : steps);
                        }
                        else if (parameter[0] != null)
                        {
                            MODEL_ACTIONS.add ("continuous.handleValue.parameter");
                            parameter[0].changeValue (steps);
                        }
                        else
                        {
                            continuousUnboundValueEvents++;
                            MODEL_ACTIONS.add ("continuous.handleValue.unbound");
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
