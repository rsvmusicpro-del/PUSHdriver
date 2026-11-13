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
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.daw.data.bank.IDeviceBank;
import de.mossgrabers.framework.daw.data.bank.IParameterPageBank;
import de.mossgrabers.framework.daw.data.IDeviceMetadata;
import de.mossgrabers.framework.daw.data.ICursorDevice;
import de.mossgrabers.framework.daw.data.ITrack;
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
import de.mossgrabers.framework.command.core.ContinuousCommand;
import de.mossgrabers.framework.command.core.PitchbendCommand;
import de.mossgrabers.framework.command.core.TriggerCommand;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.controller.ableton.push.mode.device.DeviceParamsMode;
import de.mossgrabers.controller.ableton.push.mode.device.DeviceBrowserMode;
import de.mossgrabers.controller.ableton.push.controller.Push1Display;
import de.mossgrabers.framework.featuregroup.AbstractFeatureGroup;
import de.mossgrabers.framework.daw.IBrowser;
import de.mossgrabers.framework.daw.data.IBrowserColumn;
import de.mossgrabers.framework.daw.data.IBrowserColumnItem;
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
        final BrowserModel browserModel = new BrowserModel ();
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
                        final ICursorDevice cursorDevice = cursorDeviceProxy (parameterBank);
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
                                case "getBrowser" -> browserModel.browser ();
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

        // Browser mode is exercised against a stateful API-17 IBrowser model:
        // filter columns, result list, content-type paging and selection state
        // all change in response to the real Push 1 MIDI bindings.
        browserModel.setActive (true);
        modes.setActive (Modes.BROWSER);
        final DeviceBrowserMode browserMode = (DeviceBrowserMode) modes.get (Modes.BROWSER);
        require (browserModel.isActive (), "Browser model did not enter the active state");
        final String availableFilterLed = browserMode.getButtonColorID (ButtonID.ROW1_1);
        require (AbstractFeatureGroup.BUTTON_COLOR_ON.equals (availableFilterLed),
            "Existing Browser filter column did not advertise its top-row LED");

        final int filterBefore = browserModel.filterCursor (0);
        requireButtonMidiDelivery (midi, VirtualPush1Device.TOP_ROW_FIRST, "Browser previous filter item");
        require (browserModel.filterCursor (0) == filterBefore - 1,
            "Push 1 top row did not select the previous Browser filter item");
        requireButtonMidiDelivery (midi, VirtualPush1Device.BOTTOM_ROW_FIRST, "Browser next filter item");
        require (browserModel.filterCursor (0) == filterBefore,
            "Push 1 bottom row did not select the next Browser filter item");

        // Encoder touch selects a filter column; eight relative detents pass
        // the production Browser mode's deliberate movement-rate limiter.
        midi.device ().touchEncoder (0, true);
        for (int i = 0; i < 8; i++)
            midi.device ().turnEncoderBy (0, 1);
        midi.device ().touchEncoder (0, false);
        require (browserModel.filterCursor (0) > filterBefore,
            "Touched Browser encoder did not change the selected filter item");
        browserMode.updateDisplay ();
        require (midi.device ().getLcdRow (0).contains (">Filter"),
            "Browser selected-column marker was not rendered on the Push 1 LCD: " + midi.device ().getLcdRow (0));

        // Shift+Left/Right pages browser content types; plain row buttons
        // navigate filter entries and results rather than the content tabs.
        final int contentBefore = browserModel.contentTypeIndex ();
        midi.device ().pressButton (VirtualPush1Device.SHIFT);
        requireButtonMidiDelivery (midi, VirtualPush1Device.LEFT, "Browser previous content type");
        require (browserModel.contentTypeIndex () == contentBefore - 1,
            "Shift+Left did not page to the previous Browser content type");
        requireButtonMidiDelivery (midi, VirtualPush1Device.RIGHT, "Browser next content type");
        require (browserModel.contentTypeIndex () == contentBefore,
            "Shift+Right did not page to the next Browser content type");
        midi.device ().releaseButton (VirtualPush1Device.SHIFT);

        final int resultBefore = browserModel.resultCursor ();
        requireButtonMidiDelivery (midi, VirtualPush1Device.TOP_ROW_FIRST + 7, "Browser previous result");
        require (browserModel.resultCursor () == Math.max (0, resultBefore - 1),
            "Push 1 top-row result button did not select the previous result");
        requireButtonMidiDelivery (midi, VirtualPush1Device.BOTTOM_ROW_FIRST + 7, "Browser next result");
        require (browserModel.resultCursor () == resultBefore,
            "Push 1 bottom-row result button did not select the next result");
        modes.setActive (Modes.TRACK);

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
        require (state.errors.isEmpty (), "Production mode matrix reported runtime errors: " + state.errors);

        if (args.length > 0 && "--gui".equals (args[0]))
            VirtualPush1Simulator.show (midi, setup::exit);
        else
            setup.exit ();
        if (!state.errors.isEmpty ())
            throw new AssertionError ("Virtual Bitwig host reported errors: " + state.errors);
        System.out.println ("Virtual Push 1 runtime passed: real PushControllerSetup initialized; "
            + "real mode/view/transport commands, Shift chords, pad aftertouch, encoder/touch/ribbon input and LED/LCD feedback passed.");
    }


    /**
     * Deterministic but stateful browser implementation for the API-17 runtime
     * bench. It intentionally models selection, page bounds, filters and
     * result lists rather than returning generic empty proxy defaults.
     */
    private static final class BrowserModel
    {
        private static final String [] CONTENT_TYPES = { "Devices", "Presets", "Samples" };
        private final int [] filterCursors = { 2, 1, 3 };
        private final IBrowserColumn [] columns = new IBrowserColumn [3];
        private final int [] resultCursor = { 2 };
        private final int [] contentType = { 1 };
        private final boolean [] active = { false };
        private final boolean [] preview = { true };
        private final int [] selectedColumn = { 0 };
        private final IBrowser browser;

        BrowserModel ()
        {
            for (int i = 0; i < this.columns.length; i++)
            {
                final int column = i;
                this.columns[i] = (IBrowserColumn) Proxy.newProxyInstance (
                    IBrowserColumn.class.getClassLoader (), new Class<?> [] { IBrowserColumn.class },
                    (proxy, method, args) -> {
                        switch (method.getName ())
                        {
                            case "doesExist": case "doesCursorExist": return true;
                            case "getIndex": case "getPosition": return column;
                            case "getName": return "Filter " + column;
                            case "getWildcard": return "All";
                            case "getCursorName": return "Category " + this.filterCursors[column];
                            case "getItems":
                                final IBrowserColumnItem [] items = new IBrowserColumnItem [16];
                                for (int item = 0; item < items.length; item++)
                                    items[item] = this.filterItem (column, item);
                                return items;
                            case "getCursorIndex": return this.filterCursors[column];
                            case "setCursorIndex":
                                this.filterCursors[column] = Math.max (0, Math.min (15, (int) args[0]));
                                return null;
                            case "selectPreviousItem":
                                this.filterCursors[column] = Math.max (0, this.filterCursors[column] - 1);
                                return null;
                            case "selectNextItem":
                                this.filterCursors[column] = Math.min (15, this.filterCursors[column] + 1);
                                return null;
                            case "scrollItemPageUp":
                                this.filterCursors[column] = Math.max (0, this.filterCursors[column] - 8);
                                return null;
                            case "scrollItemPageDown":
                                this.filterCursors[column] = Math.min (15, this.filterCursors[column] + 8);
                                return null;
                            case "resetFilter": this.filterCursors[column] = 0; return null;
                            default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                        }
                    });
            }
            this.browser = (IBrowser) Proxy.newProxyInstance (IBrowser.class.getClassLoader (),
                new Class<?> [] { IBrowser.class }, (proxy, method, args) -> {
                    switch (method.getName ())
                    {
                        case "isActive": return this.active[0];
                        case "addActiveObserver":
                            ((IValueObserver<Boolean>) args[0]).update (Boolean.valueOf (this.active[0]));
                            return null;
                        case "getSelectedContentTypeIndex": return this.contentType[0];
                        case "getSelectedContentType": return CONTENT_TYPES[this.contentType[0]];
                        case "getContentTypeNames": return CONTENT_TYPES.clone ();
                        case "hasPreviousContentType": return this.contentType[0] > 0;
                        case "hasNextContentType": return this.contentType[0] < CONTENT_TYPES.length - 1;
                        case "previousContentType": this.contentType[0] = Math.max (0, this.contentType[0] - 1); return null;
                        case "nextContentType": this.contentType[0] = Math.min (CONTENT_TYPES.length - 1, this.contentType[0] + 1); return null;
                        case "isPresetContentType": return this.contentType[0] == 1;
                        case "isPreviewEnabled": return this.preview[0];
                        case "togglePreviewEnabled": this.preview[0] = !this.preview[0]; return null;
                        case "setPreviewEnabled": this.preview[0] = (boolean) args[0]; return null;
                        case "stopBrowsing": this.active[0] = false; return null;
                        case "replace": case "addDevice": case "insertBeforeCursorDevice": case "insertAfterCursorDevice":
                            this.active[0] = true; return null;
                        case "getFilterColumnCount": return this.columns.length;
                        case "getFilterColumnNames": return new String [] { "Category", "Vendor", "Type" };
                        case "getFilterColumn": return this.columns[(int) args[0]];
                        case "getSelectedFilterColumn": return this.columns[this.selectedColumn[0]];
                        case "selectPreviousFilterColumn":
                            this.selectedColumn[0] = Math.max (0, this.selectedColumn[0] - 1); return null;
                        case "selectNextFilterColumn":
                            this.selectedColumn[0] = Math.min (this.columns.length - 1, this.selectedColumn[0] + 1); return null;
                        case "selectPreviousFilterItem":
                            this.filterCursors[(int) args[0]] = Math.max (0, this.filterCursors[(int) args[0]] - 1); return null;
                        case "selectNextFilterItem":
                            this.filterCursors[(int) args[0]] = Math.min (15, this.filterCursors[(int) args[0]] + 1); return null;
                        case "previousFilterItemPage":
                            this.filterCursors[(int) args[0]] = Math.max (0, this.filterCursors[(int) args[0]] - 8); return null;
                        case "nextFilterItemPage":
                            this.filterCursors[(int) args[0]] = Math.min (15, this.filterCursors[(int) args[0]] + 8); return null;
                        case "getSelectedFilterItemIndex": return this.filterCursors[(int) args[0]];
                        case "resetFilterColumn": this.filterCursors[(int) args[0]] = 0; return null;
                        case "getResultColumnItems":
                            final IBrowserColumnItem [] results = new IBrowserColumnItem [16];
                            for (int item = 0; item < results.length; item++)
                                results[item] = this.resultItem (item);
                            return results;
                        case "selectPreviousResult": this.resultCursor[0] = Math.max (0, this.resultCursor[0] - 1); return null;
                        case "selectNextResult": this.resultCursor[0] = Math.min (15, this.resultCursor[0] + 1); return null;
                        case "getSelectedResult": return "Preset " + this.resultCursor[0];
                        case "getNumResults": case "getNumFilterColumnEntries": return 16;
                        case "getInfoText": return "Insert device: Choose a category";
                        default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                    }
                });
        }

        IBrowser browser () { return this.browser; }
        void setActive (final boolean value) { this.active[0] = value; }
        boolean isActive () { return this.active[0]; }
        int filterCursor (final int column) { return this.filterCursors[column]; }
        int resultCursor () { return this.resultCursor[0]; }
        int contentTypeIndex () { return this.contentType[0]; }

        private IBrowserColumnItem filterItem (final int column, final int item)
        {
            return this.item (item, "Category " + item, () -> this.filterCursors[column] == item);
        }

        private IBrowserColumnItem resultItem (final int item)
        {
            return this.item (item, "Preset " + item, () -> this.resultCursor[0] == item);
        }

        private IBrowserColumnItem item (final int index, final String name, final Supplier<Boolean> selected)
        {
            return (IBrowserColumnItem) Proxy.newProxyInstance (
                IBrowserColumnItem.class.getClassLoader (), new Class<?> [] { IBrowserColumnItem.class },
                (proxy, method, args) -> {
                    switch (method.getName ())
                    {
                        case "doesExist": return true;
                        case "getIndex": case "getPosition": return index;
                        case "getName": return args != null && args.length > 0
                            ? name.substring (0, Math.min (name.length (), (int) args[0])) : name;
                        case "isSelected": return selected.get ();
                        case "getHitCount": return 12 + index;
                        case "setSelected": case "select": return null;
                        default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                    }
                });
        }
    }

    private static ICursorDevice cursorDeviceProxy (final IParameterBank parameters)
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
