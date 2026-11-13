import de.mossgrabers.controller.ableton.push.PushControllerSetup;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.controller.Push1DisplayLayout;
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
import de.mossgrabers.framework.daw.midi.INoteInput;
import de.mossgrabers.framework.daw.midi.INoteRepeat;
import de.mossgrabers.framework.daw.IBrowser;
import de.mossgrabers.framework.daw.data.IBrowserColumn;
import de.mossgrabers.framework.daw.data.IBrowserColumnItem;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.daw.data.bank.IDeviceBank;
import de.mossgrabers.framework.daw.data.bank.IParameterPageBank;
import de.mossgrabers.framework.daw.data.IDeviceMetadata;
import de.mossgrabers.framework.daw.data.ICursorDevice;
import de.mossgrabers.framework.daw.data.IDrumDevice;
import de.mossgrabers.framework.daw.data.IDrumPad;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.data.ILayer;
import de.mossgrabers.framework.daw.data.bank.ILayerBank;
import de.mossgrabers.framework.daw.data.bank.IDrumPadBank;
import de.mossgrabers.framework.daw.data.ISend;
import de.mossgrabers.framework.daw.data.ISlot;
import de.mossgrabers.framework.daw.clip.INoteClip;
import de.mossgrabers.framework.daw.clip.IStepInfo;
import de.mossgrabers.framework.daw.clip.NotePosition;
import de.mossgrabers.framework.daw.clip.StepState;
import de.mossgrabers.framework.daw.data.IScene;
import de.mossgrabers.framework.daw.data.bank.ISceneBank;
import de.mossgrabers.framework.daw.data.bank.ISendBank;
import de.mossgrabers.framework.daw.data.bank.ISlotBank;
import de.mossgrabers.framework.daw.data.bank.IParameterBank;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.daw.midi.ArpeggiatorMode;
import de.mossgrabers.framework.daw.midi.IMidiAccess;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.midi.IMidiOutput;
import de.mossgrabers.framework.command.core.ContinuousCommand;
import de.mossgrabers.framework.command.core.PitchbendCommand;
import de.mossgrabers.framework.command.core.TriggerCommand;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.controller.ableton.push.mode.device.DeviceParamsMode;
import de.mossgrabers.framework.view.Views;
import de.mossgrabers.framework.view.sequencer.AbstractSequencerView;
import de.mossgrabers.framework.view.AbstractDrum64View;
import de.mossgrabers.framework.view.sequencer.AbstractDrumView;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.utils.TestCallback;
import de.mossgrabers.framework.observer.IValueObserver;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayDeque;
import java.util.HashMap;
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
        boolean cursorTrackExists = true;
        boolean cursorTrackPlaying = true;
        boolean emptySlotAvailable = true;
        boolean selectedSlotExists = true;
        int selectedSlotIndex = 2;
        int emptySlotSearchStart = -1;
        int cursorTrackStopCount;
        int noteClipCreateCount;
        int createdSlotIndex = -1;
        boolean createdWithOverdub;
        boolean noteClipExists;
        int stepToggleCount;
        int stepSetCount;
        int stepClearCount;
        int stepPageForwardCount;
        int stepPageBackwardCount;
        int stepLengthChangeCount;
        int lastStepNumber = -1;
        int lastStepNote = -1;
        final Map<String, StepState> stepStates = new HashMap<> ();
        int selectedDrumPad = -1;
        int drumScrollPosition;
        int drumScrollForwardCount;
        int drumScrollBackwardCount;
        int drumIndicationOnCount;
        int drumIndicationOffCount;
        boolean deviceCanScrollBackwards;
        boolean deviceCanScrollForwards;
        boolean deviceCanScrollPageBackwards;
        boolean deviceCanScrollPageForwards;
        boolean layersExist = true;
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
                    case "println": case "showNotification": case "releaseUsbDevices": return null;
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
                    case "isPlaying": return state.playing;
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
                        final ITrackBank trackBank = trackBankProxy ();
                        final ISceneBank sceneBank = sceneBankProxy ();
                        final IParameterBank parameterBank = parameterBankProxy ();
                        final ICursorDevice cursorDevice = cursorDeviceProxy (parameterBank, state);
                        final IBrowser browser = browserProxy ();
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
                                case "getUserParameterBank" -> parameterBankProxy ();
                                case "getCursorDevice" -> cursorDevice;
                                case "getDrumDevice" -> drumDeviceProxy (state);
                                case "getCursorTrack" -> cursorTrackProxy (state);
                                case "canSelectedTrackHoldNotes" -> state.noteClipExists;
                                case "getNoteClip" -> noteClipProxy (state);
                                case "getBrowser" -> browser;
                                case "createNoteClip" -> {
                                    state.noteClipCreateCount++;
                                    state.createdSlotIndex = ((ISlot) a[1]).getIndex ();
                                    state.createdWithOverdub = (boolean) a[3];
                                    MODEL_ACTIONS.add ("model.createNoteClip");
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
        require (modes.get (Modes.TRACK) != null, "Track mode was not registered");
        require (modes.get (Modes.DEVICE_PARAMS) != null, "Device parameter mode was not registered");
        require (modes.get (Modes.BROWSER) != null, "Browser mode was not registered");
        require (modes.get (Modes.SESSION) != null, "Session mode was not registered");
        require (modes.get (Modes.NOTE) != null, "Note mode was not registered");

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
                + ", scheduled=" + state.scheduled.size () + ", hostErrors=" + state.errors
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

        // Full Push 1 registration inventory, taken from the Push 1 branch
        // of PushControllerSetup rather than recent feature changes alone.
        final List<Modes> expectedModes = new java.util.ArrayList<> (List.of (
            Modes.TRACK, Modes.TRACK_DETAILS, Modes.VOLUME, Modes.PAN, Modes.CROSSFADER,
            Modes.MASTER, Modes.MASTER_TEMP,
            Modes.DEVICE_PARAMS, Modes.DEVICE_CHAINS, Modes.DEVICE_LAYER,
            Modes.DEVICE_LAYER_VOLUME, Modes.DEVICE_LAYER_PAN, Modes.DEVICE_LAYER_DETAILS,
            Modes.BROWSER, Modes.CLIP, Modes.NOTE, Modes.FRAME, Modes.GROOVE,
            Modes.REC_ARM, Modes.ACCENT, Modes.SCALES, Modes.SCALE_LAYOUT, Modes.FIXED,
            Modes.RIBBON, Modes.VIEW_SELECT, Modes.AUTOMATION, Modes.TRANSPORT,
            Modes.MARKERS, Modes.USER, Modes.CONFIGURATION, Modes.SESSION,
            Modes.SESSION_VIEW_SELECT, Modes.REPEAT_NOTE, Modes.ADD_TRACK));
        for (int i = 0; i < 8; i++)
        {
            expectedModes.add (Modes.get (Modes.SEND1, i));
            expectedModes.add (Modes.get (Modes.DEVICE_LAYER_SEND1, i));
        }
        for (final Modes mode: expectedModes)
        {
            require (modes.get (mode) != null, "Push 1 production mode was not registered: " + mode);
            modes.setActive (mode);
            setup.flush ();
            for (int encoder = 0; encoder < 8; encoder++)
            {
                midi.device ().turnEncoderBy (encoder, 1);
                midi.device ().turnEncoderBy (encoder, -1);
                midi.device ().touchEncoder (encoder, true);
                midi.device ().touchEncoder (encoder, false);
            }
            midi.device ().turnEncoderBy (8, 1);
            midi.device ().turnEncoderBy (8, -1);
            midi.device ().touchEncoder (8, true);
            midi.device ().touchEncoder (8, false);
            for (int encoder = 0; encoder < 2; encoder++)
            {
                midi.device ().turnSmallEncoderBy (encoder, 1);
                midi.device ().turnSmallEncoderBy (encoder, -1);
                midi.device ().touchSmallEncoder (encoder, true);
                midi.device ().touchSmallEncoder (encoder, false);
            }
            setup.flush ();
        }

        // Empty device-layer banks are a valid host state: layer mode knob
        // touch must safely no-op instead of dereferencing a missing layer.
        state.layersExist = false;
        modes.setActive (Modes.DEVICE_LAYER_VOLUME);
        midi.device ().touchEncoder (0, true);
        midi.device ().touchEncoder (0, false);
        state.layersExist = true;
        midi.device ().touchEncoder (0, true);
        midi.device ().touchEncoder (0, false);

        // Device bank boundaries: arrow availability must follow the
        // host bank's first/last-item and first/last-page state.
        final DeviceParamsMode deviceParamsMode = (DeviceParamsMode) modes.get (Modes.DEVICE_PARAMS);
        deviceParamsMode.setShowDevices (true);
        state.deviceCanScrollBackwards = false;
        state.deviceCanScrollForwards = true;
        state.deviceCanScrollPageBackwards = false;
        state.deviceCanScrollPageForwards = true;
        require (!deviceParamsMode.hasPreviousItem () && deviceParamsMode.hasNextItem (),
            "Device item navigation must disable Left at the first item and enable Right");
        require (!deviceParamsMode.hasPreviousItemPage () && deviceParamsMode.hasNextItemPage (),
            "Device page navigation must disable Shift+Left at the first page and enable Shift+Right");
        state.deviceCanScrollBackwards = true;
        state.deviceCanScrollForwards = false;
        state.deviceCanScrollPageBackwards = true;
        state.deviceCanScrollPageForwards = false;
        require (deviceParamsMode.hasPreviousItem () && !deviceParamsMode.hasNextItem (),
            "Device item navigation must disable Right at the last item and enable Left");
        require (deviceParamsMode.hasPreviousItemPage () && !deviceParamsMode.hasNextItemPage (),
            "Device page navigation must disable Shift+Right at the last page and enable Shift+Left");
        deviceParamsMode.setShowDevices (false);

        // Note Repeat must tolerate no default MIDI input, then bind a
        // service that appears later, and tolerate its subsequent removal.
        modes.setActive (Modes.REPEAT_NOTE);
        midi.device ().pressButton (VirtualPush1Device.DELETE);
        midi.device ().touchEncoder (5, true);
        midi.device ().touchEncoder (5, false);
        midi.device ().touchEncoder (6, true);
        midi.device ().touchEncoder (6, false);
        midi.device ().releaseButton (VirtualPush1Device.DELETE);
        midi.device ().tapButton (VirtualPush1Device.TOP_ROW_FIRST + 4);
        midi.device ().tapButton (VirtualPush1Device.BOTTOM_ROW_FIRST + 7);

        final INoteRepeat noteRepeat = (INoteRepeat) Proxy.newProxyInstance (
            INoteRepeat.class.getClassLoader (), new Class<?> [] { INoteRepeat.class },
            (proxy, method, callArgs) -> {
                if (method.getName ().startsWith ("set") || method.getName ().startsWith ("toggle"))
                    MODEL_ACTIONS.add ("noteRepeat." + method.getName ());
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
        final INoteInput noteInput = (INoteInput) Proxy.newProxyInstance (
            INoteInput.class.getClassLoader (), new Class<?> [] { INoteInput.class },
            (proxy, method, callArgs) -> method.getName ().equals ("getNoteRepeat")
                ? noteRepeat : defaultValue (method.getReturnType (), method.getGenericReturnType ()));
        midi.setDefaultNoteInput (noteInput);
        midi.device ().pressButton (VirtualPush1Device.DELETE);
        midi.device ().touchEncoder (5, true);
        midi.device ().touchEncoder (5, false);
        midi.device ().touchEncoder (6, true);
        midi.device ().touchEncoder (6, false);
        midi.device ().releaseButton (VirtualPush1Device.DELETE);
        midi.device ().tapButton (VirtualPush1Device.TOP_ROW_FIRST + 4);
        require (MODEL_ACTIONS.contains ("noteRepeat.setMode")
            && MODEL_ACTIONS.contains ("noteRepeat.setOctaves")
            && MODEL_ACTIONS.contains ("noteRepeat.toggleLatchActive"),
            "Late-created Note Repeat service was not refreshed and exercised");

        midi.setDefaultNoteInput (null);
        midi.device ().pressButton (VirtualPush1Device.DELETE);
        midi.device ().touchEncoder (5, true);
        midi.device ().touchEncoder (5, false);
        midi.device ().touchEncoder (6, true);
        midi.device ().touchEncoder (6, false);
        midi.device ().releaseButton (VirtualPush1Device.DELETE);

        // New clip workflow: free-slot discovery precedes stopping only the
        // cursor track, and the inherited Shift+New overdub distinction stays intact.
        state.cursorTrackExists = true;
        state.cursorTrackPlaying = true;
        state.emptySlotAvailable = true;
        state.selectedSlotExists = true;
        state.selectedSlotIndex = 2;
        state.cursorTrackStopCount = 0;
        state.noteClipCreateCount = 0;
        midi.device ().tapButton (VirtualPush1Device.NEW);
        require (state.emptySlotSearchStart == 2, "New must search from the selected slot");
        require (state.cursorTrackStopCount == 1 && !state.cursorTrackPlaying,
            "New must stop the playing cursor track after finding a free slot");
        require (state.noteClipCreateCount == 1 && state.createdSlotIndex == 5,
            "New must create the clip in the first available slot returned by the slot bank");
        require (state.createdWithOverdub, "Normal New must preserve the upstream overdub behavior");

        state.cursorTrackPlaying = true;
        midi.device ().pressButton (VirtualPush1Device.SHIFT);
        midi.device ().tapButton (VirtualPush1Device.NEW);
        midi.device ().releaseButton (VirtualPush1Device.SHIFT);
        require (state.cursorTrackStopCount == 2 && state.noteClipCreateCount == 2,
            "Shift+New must stop the cursor track and create a clip");
        require (!state.createdWithOverdub, "Shift+New must create the clip without overdub");

        state.cursorTrackPlaying = true;
        state.emptySlotAvailable = false;
        final int stopsBeforeNoSlot = state.cursorTrackStopCount;
        final int clipsBeforeNoSlot = state.noteClipCreateCount;
        midi.device ().tapButton (VirtualPush1Device.NEW);
        require (state.cursorTrackStopCount == stopsBeforeNoSlot && state.cursorTrackPlaying,
            "New with no free slot must not stop the playing cursor track");
        require (state.noteClipCreateCount == clipsBeforeNoSlot,
            "New with no free slot must not create a clip");

        state.emptySlotAvailable = true;
        state.cursorTrackExists = false;
        final int stopsBeforeNoTrack = state.cursorTrackStopCount;
        final int clipsBeforeNoTrack = state.noteClipCreateCount;
        midi.device ().tapButton (VirtualPush1Device.NEW);
        require (state.cursorTrackStopCount == stopsBeforeNoTrack
            && state.noteClipCreateCount == clipsBeforeNoTrack,
            "New without a cursor track must not stop playback or create a clip");
        state.cursorTrackExists = true;
        state.cursorTrackPlaying = false;

        // A real note clip turns the Sequencer view on. Pad release must
        // toggle a step, and the page controls must call the clip paging API.
        state.noteClipExists = true;
        state.stepToggleCount = 0;
        state.stepPageForwardCount = 0;
        state.stepPageBackwardCount = 0;
        surface.getViewManager ().setActive (Views.SEQUENCER);
        setup.flush ();
        midi.device ().pressPad (0, 0, 100);
        midi.device ().releasePad (0, 0);
        require (state.stepToggleCount > 0,
            "Sequencer pad release must toggle a step in an existing note clip");
        final AbstractSequencerView<?, ?> sequencer =
            (AbstractSequencerView<?, ?>) surface.getViewManager ().get (Views.SEQUENCER);
        sequencer.onLeft (ButtonEvent.DOWN);
        sequencer.onRight (ButtonEvent.DOWN);
        require (state.stepPageBackwardCount > 0 && state.stepPageForwardCount > 0,
            "Sequencer left/right navigation must page the note clip");
        state.noteClipExists = false;
        surface.getViewManager ().setActive (Views.PLAY);
        setup.flush ();

        // Poly sequencer uses the same note-clip API but its own row mapping.
        state.noteClipExists = true;
        final int polyStepsBefore = state.stepToggleCount;
        surface.getViewManager ().setActive (Views.POLY_SEQUENCER);
        setup.flush ();
        midi.device ().pressPad (0, 0, 100);
        midi.device ().releasePad (0, 0);
        require (state.stepToggleCount > polyStepsBefore,
            "Poly sequencer pad release must edit the active note clip");
        state.noteClipExists = false;
        surface.getViewManager ().setActive (Views.PLAY);
        setup.flush ();

        // Drum 4/8: verify pad selection and the sequencer half of the
        // grid writes a note step on release using the selected drum-pad row.
        state.noteClipExists = true;
        state.stepToggleCount = 0;
        state.lastStepNumber = -1;
        state.lastStepNote = -1;
        surface.getViewManager ().setActive (Views.DRUM);
        final AbstractDrumView<?, ?> drumView =
            (AbstractDrumView<?, ?>) surface.getViewManager ().get (Views.DRUM);
        midi.device ().pressPad (0, 1, 100);
        midi.device ().releasePad (0, 1);
        require (drumView.getSelectedPad () == 1, "Drum 4 pad mapping must select the expected lower-block pad");
        ((AbstractSequencerView<?, ?>) drumView).setSequencerActive (true);
        midi.device ().pressPad (4, 0, 100);
        midi.device ().releasePad (4, 0);
        require (state.stepToggleCount == 1 && state.lastStepNumber == 24,
            "Drum sequencer grid must toggle the expected clip step on pad release");
        require (state.lastStepNote >= 0,
            "Drum sequencer step must retain a valid mapped note row");

        surface.getViewManager ().setActive (Views.DRUM8);
        final AbstractDrumView<?, ?> drum8View =
            (AbstractDrumView<?, ?>) surface.getViewManager ().get (Views.DRUM8);
        midi.device ().pressPad (0, 2, 100);
        midi.device ().releasePad (0, 2);
        require (drum8View.getSelectedPad () >= 0,
            "Drum 8 pad grid must map a valid selected pad");

        surface.getViewManager ().setActive (Views.PLAY);
        state.noteClipExists = false;

        // Drum 64: verify the 4x4 block traversal across the 8x8 grid
        // and the octave bank scroll contract (16 drum-pad bank steps).
        state.noteClipExists = true;
        state.selectedDrumPad = -1;
        state.drumScrollForwardCount = 0;
        state.drumIndicationOnCount = 0;
        surface.getViewManager ().setActive (Views.DRUM64);
        final AbstractDrum64View<?, ?> drum64 =
            (AbstractDrum64View<?, ?>) surface.getViewManager ().get (Views.DRUM64);
        require (state.drumIndicationOnCount > 0, "Drum64 activation must enable drum-pad indication");
        midi.device ().pressButton (VirtualPush1Device.SELECT);
        midi.device ().pressPad (0, 0, 100);
        midi.device ().releasePad (0, 0);
        midi.device ().releaseButton (VirtualPush1Device.SELECT);
        require (state.selectedDrumPad == 0, "Drum64 lower-left pad must map to pad 0");

        midi.device ().pressButton (VirtualPush1Device.SELECT);
        midi.device ().pressPad (0, 4, 100);
        midi.device ().releasePad (0, 4);
        midi.device ().releaseButton (VirtualPush1Device.SELECT);
        require (state.selectedDrumPad == 32, "Drum64 second 4x4 block must map to pad 32");

        midi.device ().pressButton (VirtualPush1Device.SELECT);
        midi.device ().pressPad (4, 0, 100);
        midi.device ().releasePad (4, 0);
        midi.device ().releaseButton (VirtualPush1Device.SELECT);
        require (state.selectedDrumPad == 16, "Drum64 upper block must map to pad 16");
        drum64.onOctaveUp (ButtonEvent.DOWN);
        require (state.drumScrollForwardCount == 16,
            "Drum64 octave-up must scroll the drum bank by exactly 16 pad positions");
        surface.getViewManager ().setActive (Views.PLAY);
        require (state.drumIndicationOffCount > 0, "Drum64 deactivation must disable drum-pad indication");
        state.noteClipExists = false;

        // Browser: exercise both filter-column and result selection, the
        // preset selector, preview chord, and browser content-type paging.
        modes.setActive (Modes.BROWSER);
        MODEL_ACTIONS.clear ();
        requireButtonMidiDelivery (midi, VirtualPush1Device.TOP_ROW_FIRST, "Browser previous filter item");
        requireButtonMidiDelivery (midi, VirtualPush1Device.BOTTOM_ROW_FIRST, "Browser next filter item");
        requireAction ("browser.selectPreviousFilterItem:0", "Browser top row must move to the previous filter item");
        requireAction ("browser.selectNextFilterItem:0", "Browser bottom row must move to the next filter item");
        requireButtonMidiDelivery (midi, VirtualPush1Device.TOP_ROW_FIRST + 7, "Browser previous result");
        requireButtonMidiDelivery (midi, VirtualPush1Device.BOTTOM_ROW_FIRST + 7, "Browser next result");
        requireAction ("browser.selectPreviousResult", "Browser top-right button must move to the previous result");
        requireAction ("browser.selectNextResult", "Browser bottom-right button must move to the next result");
        midi.device ().pressButton (VirtualPush1Device.SHIFT);
        requireButtonMidiDelivery (midi, VirtualPush1Device.TOP_ROW_FIRST + 7, "Shift+Browser preview");
        midi.device ().releaseButton (VirtualPush1Device.SHIFT);
        require (MODEL_ACTIONS.contains ("browser.togglePreviewEnabled"),
            "Shift+top-right Browser button must toggle preview");
        MODEL_ACTIONS.clear ();
        midi.device ().pressButton (VirtualPush1Device.SHIFT);
        for (final int arrow: new int [] { VirtualPush1Device.LEFT, VirtualPush1Device.RIGHT })
            requireButtonMidiDelivery (midi, arrow, "Shift+Browser content type navigation");
        midi.device ().releaseButton (VirtualPush1Device.SHIFT);
        requireAction ("browser.previousContentType", "Shift+Browser Left must move to previous content type");
        requireAction ("browser.nextContentType", "Shift+Browser Right must move to next content type");

        // Exercise every registered Push 1 view with real pad note-on,
        // pressure and release traffic through the production bindings.
        final Views [] expectedViews = {
            Views.PLAY, Views.CHORDS, Views.PIANO, Views.PRG_CHANGE,
            Views.CLIP_LENGTH, Views.COLOR, Views.SESSION, Views.SEQUENCER,
            Views.POLY_SEQUENCER, Views.DRUM, Views.DRUM4, Views.DRUM8,
            Views.SCENE_PLAY, Views.DRUM64
        };
        for (final Views view: expectedViews)
        {
            require (surface.getViewManager ().get (view) != null,
                "Push 1 production view was not registered: " + view);
            surface.getViewManager ().setActive (view);
            for (int row = 0; row < 8; row++)
                for (int column = 0; column < 8; column++)
                {
                    midi.device ().pressPad (row, column, 96);
                    midi.device ().pressPadPressure (row, column, 64);
                    midi.device ().releasePad (row, column);
                }
            setup.flush ();
            require (midi.device ().getLcdRow (0).length () == 68,
                "Push 1 LCD output failed while exercising view " + view);
        }

        require (state.errors.isEmpty (),
            "Production mode/view acceptance matrix reported runtime errors: " + state.errors);

        if (args.length > 0 && "--gui".equals (args[0]))
            VirtualPush1Simulator.show (midi, setup::exit);
        else
            setup.exit ();
        if (!state.errors.isEmpty ())
            throw new AssertionError ("Virtual Bitwig host reported errors: " + state.errors);
        System.out.println ("Virtual Push 1 runtime passed: real PushControllerSetup initialized; "
            + "real mode/view/transport commands, Shift chords, pad aftertouch, encoder/touch/ribbon input and LED/LCD feedback passed.");
    }

    private static IDrumDevice drumDeviceProxy (final State state)
    {
        return (IDrumDevice) Proxy.newProxyInstance (IDrumDevice.class.getClassLoader (),
            new Class<?> [] { IDrumDevice.class }, (proxy, method, args) -> switch (method.getName ())
            {
                case "hasDrumPads" -> true;
                case "getDrumPadBank" -> drumPadBankProxy (state);
                case "getPosition" -> 0;
                default -> defaultValue (method.getReturnType (), method.getGenericReturnType ());
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
                    case "scrollTo":
                        state.drumScrollPosition = (int) args[0];
                        return null;
                    case "setIndication":
                        if ((boolean) args[0])
                            state.drumIndicationOnCount++;
                        else
                            state.drumIndicationOffCount++;
                        return null;
                    default:
                        return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
            });
    }


    private static IDrumPad drumPadProxy (final State state, final int index)
    {
        return (IDrumPad) Proxy.newProxyInstance (IDrumPad.class.getClassLoader (),
            new Class<?> [] { IDrumPad.class }, (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "doesExist", "isActivated": return true;
                    case "isSelected": return state.selectedDrumPad == index;
                    case "select":
                        state.selectedDrumPad = index;
                        MODEL_ACTIONS.add ("drumPad.select:" + index);
                        return null;
                    case "toggleMute":
                        MODEL_ACTIONS.add ("drumPad.toggleMute:" + index);
                        return null;
                    case "toggleSolo":
                        MODEL_ACTIONS.add ("drumPad.toggleSolo:" + index);
                        return null;
                    case "isMute", "isSolo": return false;
                    default:
                        return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
            });
    }


    private static INoteClip noteClipProxy (final State state)
    {
        return (INoteClip) Proxy.newProxyInstance (INoteClip.class.getClassLoader (),
            new Class<?> [] { INoteClip.class }, (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "doesExist": return state.noteClipExists;
                    case "getNumSteps": return 16;
                    case "getNumRows": return 128;
                    case "getCurrentStep": return 0;
                    case "getEditPage": return 0;
                    case "getStepLength": return 0.25;
                    case "getLoopStart": return 0.0;
                    case "getLoopLength": return 16.0;
                    case "getStep":
                        final NotePosition position = (NotePosition) args[0];
                        return stepInfoProxy (state, position);
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
                    default:
                        return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
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


    private static ICursorTrack cursorTrackProxy (final State state)
    {
        final ISlotBank slots = (ISlotBank) Proxy.newProxyInstance (
            ISlotBank.class.getClassLoader (), new Class<?> [] { ISlotBank.class },
            (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "getSelectedItem":
                        return state.selectedSlotExists ? Optional.of (slotProxy (state.selectedSlotIndex)) : Optional.empty ();
                    case "getEmptySlot":
                        state.emptySlotSearchStart = (int) args[0];
                        return state.emptySlotAvailable ? Optional.of (slotProxy (state.emptySlotSearchStart + 3)) : Optional.empty ();
                    case "getPageSize": return 8;
                    case "getItem": return slotProxy ((int) args[0]);
                    default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
            });
        return (ICursorTrack) Proxy.newProxyInstance (ICursorTrack.class.getClassLoader (),
            new Class<?> [] { ICursorTrack.class }, (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "doesExist": return state.cursorTrackExists;
                    case "getSlotBank": return slots;
                    case "isPlaying": return state.cursorTrackPlaying;
                    case "stop":
                        state.cursorTrackStopCount++;
                        state.cursorTrackPlaying = false;
                        MODEL_ACTIONS.add ("cursorTrack.stop");
                        return null;
                    default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
            });
    }


    private static ISlot slotProxy (final int index)
    {
        return (ISlot) Proxy.newProxyInstance (ISlot.class.getClassLoader (),
            new Class<?> [] { ISlot.class }, (proxy, method, args) -> {
                if (method.getName ().equals ("getIndex"))
                    return index;
                if (method.getName ().equals ("doesExist"))
                    return true;
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }


    private static IBrowser browserProxy ()
    {
        final boolean [] preview = { false };
        return (IBrowser) Proxy.newProxyInstance (IBrowser.class.getClassLoader (),
            new Class<?> [] { IBrowser.class }, (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "isActive": return true;
                    case "getFilterColumnCount": return 7;
                    case "getFilterColumn": return browserColumnProxy ((int) args[0]);
                    case "getResultColumnItems": return browserItems ();
                    case "getInfoText": return "Type: Instruments";
                    case "getSelectedContentType": return "Instrument";
                    case "getSelectedResult": return "Virtual Preset";
                    case "isPreviewEnabled": return preview[0];
                    case "togglePreviewEnabled": preview[0] = !preview[0]; return null;
                    case "nextContentType": case "previousContentType":
                    case "selectNextResult": case "selectPreviousResult":
                    case "selectNextFilterItem": case "selectPreviousFilterItem":
                    case "resetFilterColumn": case "stopBrowsing":
                        MODEL_ACTIONS.add ("browser." + method.getName ()
                            + (args == null || args.length == 0 ? "" : ":" + args[0]));
                        return null;
                    case "hasPreviousContentType": case "hasNextContentType":
                        return true;
                    default:
                        return defaultValue (method.getReturnType (), method.getGenericReturnType ());
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
                case "getCursorName" -> "All";
                case "getWildcard" -> "*";
                case "getItems" -> browserItems ();
                default -> defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }


    private static IBrowserColumnItem [] browserItems ()
    {
        final IBrowserColumnItem [] items = new IBrowserColumnItem [16];
        for (int i = 0; i < items.length; i++)
        {
            final int index = i;
            items[i] = (IBrowserColumnItem) Proxy.newProxyInstance (
                IBrowserColumnItem.class.getClassLoader (), new Class<?> [] { IBrowserColumnItem.class },
                (proxy, method, args) -> switch (method.getName ())
                {
                    case "doesExist" -> true;
                    case "isSelected" -> index == 0;
                    case "getName" -> "Browser item " + index;
                    case "getHitCount" -> 12;
                    default -> defaultValue (method.getReturnType (), method.getGenericReturnType ());
                });
        }
        return items;
    }


    private static ICursorDevice cursorDeviceProxy (final IParameterBank parameters, final State state)
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
        final ILayerBank layers = layerBankProxy (state);
        return (ICursorDevice) Proxy.newProxyInstance (ICursorDevice.class.getClassLoader (),
            new Class<?> [] { ICursorDevice.class }, (proxy, method, args) -> {
                if (method.getName ().equals ("getParameterBank"))
                    return parameters;
                if (method.getName ().equals ("getDeviceBank"))
                    return devices;
                if (method.getName ().equals ("getParameterPageBank"))
                    return pages;
                if (method.getName ().equals ("getLayerBank"))
                    return layers;
                if (method.getName ().equals ("getDrumPadBank"))
                    return drumPadBankProxy (state);
                if (method.getName ().equals ("hasDrumPads"))
                    return true;
                if (method.getName ().equals ("isNested"))
                    return false;
                if (method.getName ().equals ("getPosition"))
                    return 0;
                if (method.getName ().equals ("hasLayers"))
                    return true;
                if (method.getName ().equals ("doesExist"))
                    return true;
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }


    private static ILayerBank layerBankProxy (final State state)
    {
        return (ILayerBank) Proxy.newProxyInstance (ILayerBank.class.getClassLoader (),
            new Class<?> [] { ILayerBank.class }, (proxy, method, args) -> {
                if (method.getName ().equals ("getPageSize"))
                    return 8;
                if (method.getName ().equals ("hasExistingItems"))
                    return state.layersExist;
                if (method.getName ().equals ("getSelectedItem"))
                    return state.layersExist ? Optional.of (layerProxy (state)) : Optional.empty ();
                if (method.getName ().equals ("getSelectedItemIndex"))
                    return 0;
                if (method.getName ().equals ("getItem"))
                    return layerProxy (state);
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }


    private static ISendBank sendBankProxy ()
    {
        return (ISendBank) Proxy.newProxyInstance (ISendBank.class.getClassLoader (),
            new Class<?> [] { ISendBank.class }, (proxy, method, args) -> {
                if (method.getName ().equals ("getPageSize"))
                    return 8;
                if (method.getName ().equals ("getItem"))
                    return sendProxy ();
                if (method.getName ().equals ("getSelectedItem"))
                    return Optional.of (sendProxy ());
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }


    private static ISend sendProxy ()
    {
        return (ISend) Proxy.newProxyInstance (ISend.class.getClassLoader (),
            new Class<?> [] { ISend.class }, (proxy, method, args) -> {
                if (method.getName ().equals ("doesExist"))
                    return true;
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }


    private static ILayer layerProxy (final State state)
    {
        return (ILayer) Proxy.newProxyInstance (ILayer.class.getClassLoader (),
            new Class<?> [] { ILayer.class }, (proxy, method, args) -> {
                if (method.getName ().equals ("doesExist") || method.getName ().equals ("isSelected"))
                    return state.layersExist;
                if (method.getName ().equals ("getIndex"))
                    return args != null && args.length > 0 && args[0] instanceof Integer index ? index : 0;
                if (method.getName ().equals ("hasDevices"))
                    return false;
                if (method.getName ().equals ("getSendBank"))
                    return sendBankProxy ();
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

    private static ISceneBank sceneBankProxy ()
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
                    return Optional.empty ();
                if (method.getName ().equals ("getItem"))
                    return Proxy.newProxyInstance (IScene.class.getClassLoader (),
                        new Class<?> [] { IScene.class },
                        (p, m, a) -> defaultValue (m.getReturnType (), m.getGenericReturnType ()));
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }

    private static ITrackBank trackBankProxy ()
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
                    return navigationContext ? Optional.of (trackProxy ()) : Optional.empty ();
                if (method.getName ().equals ("getSceneBank"))
                    return sceneBankProxy ();
                if (method.getName ().equals ("getItem"))
                    return trackProxy ();
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }

    private static ITrack trackProxy ()
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
                if (method.getName ().equals ("getSendBank"))
                    return sendBank;
                if (method.getName ().equals ("getSceneBank"))
                    return sceneBankProxy ();
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
