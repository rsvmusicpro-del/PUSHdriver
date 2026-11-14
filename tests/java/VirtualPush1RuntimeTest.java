import de.mossgrabers.controller.ableton.push.PushControllerSetup;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.controller.Push1DisplayLayout;
import de.mossgrabers.framework.configuration.ISettingsUI;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.ContinuousID;
import de.mossgrabers.framework.controller.ISetupFactory;
import de.mossgrabers.framework.controller.display.ITextDisplay;
import de.mossgrabers.framework.controller.color.ColorManager;
import de.mossgrabers.framework.controller.color.ColorEx;
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
import de.mossgrabers.framework.daw.data.IDrumDevice;
import de.mossgrabers.framework.daw.data.IDrumPad;
import de.mossgrabers.framework.daw.data.bank.IDrumPadBank;
import de.mossgrabers.framework.daw.clip.INoteClip;
import de.mossgrabers.framework.daw.clip.IStepInfo;
import de.mossgrabers.framework.daw.clip.NotePosition;
import de.mossgrabers.framework.daw.clip.StepState;
import de.mossgrabers.framework.daw.constants.Resolution;
import de.mossgrabers.controller.ableton.push.view.SequencerView;
import de.mossgrabers.controller.ableton.push.view.PolySequencerView;
import de.mossgrabers.framework.featuregroup.AbstractView;
import de.mossgrabers.framework.scale.Scales;
import de.mossgrabers.framework.view.sequencer.AbstractPolySequencerView;
import de.mossgrabers.controller.ableton.push.view.DrumView;
import de.mossgrabers.controller.ableton.push.view.Drum64View;
import de.mossgrabers.framework.daw.data.ICursorTrack;
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
import de.mossgrabers.framework.scale.Scale;
import de.mossgrabers.framework.scale.Scales;
import de.mossgrabers.framework.command.core.ContinuousCommand;
import de.mossgrabers.framework.command.core.PitchbendCommand;
import de.mossgrabers.framework.command.core.TriggerCommand;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.controller.ableton.push.mode.device.DeviceParamsMode;
import de.mossgrabers.controller.ableton.push.mode.device.DeviceBrowserMode;
import de.mossgrabers.controller.ableton.push.controller.Push1Display;
import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
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
    private static int deviceIndex = 4;
    private static int sceneIndex = 4;
    private static int trackIndex = 4;
    private static int parameterPageIndex = 1;
    private static boolean cursorDeviceExists;
    private static boolean deviceSlotExists;
    private static boolean deviceCapabilitiesSupported = true;

    private static final class State
    {
        boolean playing;
        boolean recording;
        boolean launcherOverdub;
        boolean metronome;
        boolean metronomeTicks;
        final ArrayDeque<ScheduledTask> scheduled = new ArrayDeque<> ();
        final List<String> errors = new java.util.ArrayList<> ();
    }

    private record ScheduledTask (long delay, Runnable task) { }

    private VirtualPush1RuntimeTest () { }

    public static void main (final String [] args) throws Exception
    {
        // Protocol and device-contract checks are subtests of the same virtual
        // Push bench entry point; none opens a separate hardware/test path.
        VirtualPush1MidiBench.main (new String [0]);
        VirtualPush1DeviceTest.main (new String [0]);
        VirtualPush1DisplayLayoutTest.main (new String [0]);
        VirtualPush1ProtocolExhaustiveTest.main (new String [0]);
        VirtualPush1FirmwareVersionTest.main (new String [0]);
        VirtualPush1DisplayValueFormatterTest.main (new String [0]);
        VirtualPush1DisplayTextTest.main (new String [0]);

        lightsCreated = 0;
        lightsUpdated = 0;
        final State state = new State ();
        final BrowserModel browserModel = new BrowserModel ();
        final NewClipModel newClipModel = new NewClipModel ();
        newClipModel.setTrackExists (true);
        final MusicalModel musicalModel = new MusicalModel ();
        final MixerModel mixerModel = new MixerModel ();
        final Scales [] scaleModel = new Scales [1];
        final VirtualPush1MidiBench midi = new VirtualPush1MidiBench ();
        final IHwSurfaceFactory hardware = surfaceFactory (midi);
        final IHost host = (IHost) Proxy.newProxyInstance (IHost.class.getClassLoader (),
            new Class<?> [] { IHost.class }, (proxy, method, callArgs) -> {
                switch (method.getName ())
                {
                    case "createSurfaceFactory": return hardware;
                    case "getName": return "Virtual Bitwig API 17";
                    case "supports": return Boolean.valueOf (deviceCapabilitiesSupported);
                    case "scheduleTask": state.scheduled.add (new ScheduledTask (((Number) callArgs[1]).longValue (), (Runnable) callArgs[0])); return null;
                    case "error":
                        if (state.errors.size () < 30)
                        {
                            String detail = "";
                            if (callArgs.length > 1 && callArgs[1] instanceof Throwable error)
                            {
                                final java.io.StringWriter trace = new java.io.StringWriter ();
                                error.printStackTrace (new java.io.PrintWriter (trace));
                                detail = ": " + trace;
                            }
                            state.errors.add (String.valueOf (callArgs[0]) + detail);
                        }
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
                    case "getQuartersPerMeasure": return 4;
                    case "isLauncherOverdub": return state.launcherOverdub;
                    case "toggleLauncherOverdub": state.launcherOverdub = !state.launcherOverdub; return null;
                    case "setLauncherOverdub": state.launcherOverdub = (boolean) callArgs[0]; return null;
                    case "isMetronomeOn": return state.metronome;
                    case "toggleMetronome": state.metronome = !state.metronome; return null;
                    case "setMetronome": state.metronome = (boolean) callArgs[0]; return null;
                    case "isMetronomeTicksOn": return state.metronomeTicks;
                    case "toggleMetronomeTicks": state.metronomeTicks = !state.metronomeTicks; return null;
                    case "setMetronomeTicks": state.metronomeTicks = (boolean) callArgs[0]; return null;
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
                        scaleModel[0] = (Scales) scales;
                        final ITrackBank trackBank = trackBankProxy (mixerModel);
                        final ISceneBank sceneBank = mixerModel.sceneBank ();
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
                                case "getNoteClip" -> musicalModel.noteClip ();
                                case "canSelectedTrackHoldNotes" -> musicalModel.canHoldNotes ();
                                case "getDrumDevice" -> musicalModel.drumDevice ();
                                case "getCursorTrack" -> newClipModel.cursorTrack ();
                                case "createNoteClip" -> { newClipModel.createNoteClip (a); yield null; }
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

        // Relative encoders must update real API-17 parameters when a parameter
        // mode binds them, not only dispatch standalone ContinuousCommands.
        modes.setActive (Modes.VOLUME);
        final MixerModel.ParameterState volume = mixerModel.volume (0);
        final int volumeChanges = volume.changes;
        midi.device ().turnEncoderBy (0, 1);
        require (volume.changes == volumeChanges + 1 && volume.lastControl == 1,
            "Volume mode encoder did not update the bound track volume parameter; changes="
                + volume.changes + ", lastControl=" + volume.lastControl
                + ", active=" + modes.isActive (Modes.VOLUME)
                + ", encoderEvents=" + midi.getDispatchedEncoderEvents ());
        for (int encoder = 1; encoder < 8; encoder++)
        {
            final MixerModel.ParameterState channelVolume = mixerModel.volume (encoder);
            final int before = channelVolume.changes;
            midi.device ().turnEncoderBy (encoder, 1);
            require (channelVolume.changes == before + 1 && channelVolume.lastControl == 1,
                "Volume encoder " + (encoder + 1) + " did not target its corresponding track");
        }
        midi.device ().touchEncoder (0, true);
        require (volume.touched, "Volume encoder touch did not reach the bound parameter");
        midi.device ().touchEncoder (0, false);
        require (!volume.touched, "Volume parameter touch state did not release");
        volume.value = 91;
        midi.device ().pressButton (VirtualPush1Device.DELETE);
        midi.device ().touchEncoder (0, true);
        midi.device ().touchEncoder (0, false);
        midi.device ().releaseButton (VirtualPush1Device.DELETE);
        require (volume.value == 64, "Delete+encoder touch did not reset the bound volume parameter");
        modes.setActive (Modes.PAN);
        final MixerModel.ParameterState pan = mixerModel.pan (0);
        final int panChanges = pan.changes;
        midi.device ().turnEncoderBy (0, -1);
        require (pan.changes == panChanges + 1 && pan.lastControl == 127,
            "Pan mode encoder did not preserve the negative relative parameter delta");
        for (int encoder = 1; encoder < 8; encoder++)
        {
            final MixerModel.ParameterState channelPan = mixerModel.pan (encoder);
            final int before = channelPan.changes;
            midi.device ().turnEncoderBy (encoder, -1);
            require (channelPan.changes == before + 1 && channelPan.lastControl == 127,
                "Pan encoder " + (encoder + 1) + " did not target its corresponding track");
        }
        modes.setActive (Modes.SEND1);
        final MixerModel.ParameterState send = mixerModel.send (0, 0);
        final int sendChanges = send.changes;
        midi.device ().turnEncoderBy (0, 1);
        require (send.changes == sendChanges + 1 && send.lastControl == 1,
            "Send 1 mode encoder did not update the first track's send parameter");
        for (int encoder = 1; encoder < 8; encoder++)
        {
            final MixerModel.ParameterState channelSend = mixerModel.send (encoder, 0);
            final int before = channelSend.changes;
            midi.device ().turnEncoderBy (encoder, 1);
            require (channelSend.changes == before + 1 && channelSend.lastControl == 1,
                "Send 1 encoder " + (encoder + 1) + " did not target its corresponding track");
        }
        modes.setActive (Modes.TRACK);
        midi.device ().tapButton (VirtualPush1Device.BOTTOM_ROW_FIRST);
        require (mixerModel.isMuted (0), "Track second-row button did not toggle the selected track mute state");
        midi.device ().tapButton (VirtualPush1Device.METRONOME);
        require (state.metronome, "Metronome CC did not enable the Bitwig transport metronome");
        midi.device ().tapButton (VirtualPush1Device.METRONOME);
        require (!state.metronome, "Second Metronome CC did not disable the transport metronome");
        midi.device ().pressButton (VirtualPush1Device.SHIFT);
        midi.device ().tapButton (VirtualPush1Device.METRONOME);
        midi.device ().releaseButton (VirtualPush1Device.SHIFT);
        require (state.metronomeTicks && !state.metronome,
            "Shift+Metronome did not toggle metronome ticks independently of the metronome");

        // Push 1 footswitch 2 is a configurable control; its default setting
        // is Sustain Pedal and must emit CC64 on both press and release.
        final int rawEventsBeforeFootswitch = midi.rawInputOutput ().size ();
        midi.device ().pressButton (VirtualPush1Device.FOOTSWITCH2);
        midi.device ().releaseButton (VirtualPush1Device.FOOTSWITCH2);
        final List<int []> rawFootswitchEvents = midi.rawInputOutput ();
        require (rawFootswitchEvents.size () == rawEventsBeforeFootswitch + 2
                && rawFootswitchEvents.get (rawEventsBeforeFootswitch)[0] == 0xB0
                && rawFootswitchEvents.get (rawEventsBeforeFootswitch)[1] == 64
                && rawFootswitchEvents.get (rawEventsBeforeFootswitch)[2] == 127
                && rawFootswitchEvents.get (rawEventsBeforeFootswitch + 1)[0] == 0xB0
                && rawFootswitchEvents.get (rawEventsBeforeFootswitch + 1)[1] == 64
                && rawFootswitchEvents.get (rawEventsBeforeFootswitch + 1)[2] == 0,
            "Footswitch 2 did not emit the Sustain Pedal CC64 press/release pair");
        final int rawEventsBeforeFootswitch1 = midi.rawInputOutput ().size ();
        midi.device ().tapButton (VirtualPush1Device.FOOTSWITCH1);
        require (midi.rawInputOutput ().size () == rawEventsBeforeFootswitch1,
            "Unassigned Footswitch 1 CC64 unexpectedly triggered Footswitch 2's action");

        midi.device ().tapButton (VirtualPush1Device.PLAY);
        require (state.playing, "Play CC did not update the Bitwig transport model");
        setup.flush ();
        drainScheduled (state, 1);
        require (midi.device ().getCCLed (VirtualPush1Device.PLAY) > 0,
            "Play LED feedback missing: lightsCreated=" + lightsCreated + ", lightsUpdated=" + lightsUpdated
                + ", pendingTasks=" + state.scheduled.stream ().map (ScheduledTask::delay).toList ()
                + ", errors=" + state.errors + ", captured=" + midi.capturedOutput ().stream ().filter (m -> m.contains ("85") || m.contains ("sendCCEx")).toList ());
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

        // Use a note-capable cursor track for both Note/Session view transitions.
        musicalModel.setCanHoldNotes (true);
        require (newClipModel.cursorTrack ().doesExist (), "Virtual cursor track should exist before Note/Session view selection");
        surface.getViewManager ().setActive (Views.PLAY);
        surface.getViewManager ().setPreferredView (newClipModel.cursorTrack ().getPosition (), Views.PLAY);
        midi.device ().tapButton (VirtualPush1Device.NOTE);
        require (Views.isNoteView (surface.getViewManager ().getActiveID ()), "Note CC did not select a note view");
        midi.device ().tapButton (VirtualPush1Device.SESSION);
        require (Views.isSessionView (surface.getViewManager ().getActiveID ()), "Session CC did not select a session view");
        midi.device ().tapButton (VirtualPush1Device.NOTE);
        require (Views.isNoteView (surface.getViewManager ().getActiveID ()),
            "Returning from Session did not restore a note view; active view="
                + surface.getViewManager ().getActiveID ());

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

        // Session pads must launch the selected track/slot through the real
        // SessionView, while Scene Play pads launch and select their scene.
        // MIDI note 36 is the bottom-left pad, so SessionView maps it to
        // track 0, slot row 7 (the physical grid is vertically inverted).
        final MixerModel.SlotState sessionSlot = mixerModel.slot (0, 7);
        sessionSlot.hasContent = true;
        surface.getViewManager ().setActive (Views.SESSION);
        midi.device ().pressPad (0, 0, 100);
        midi.device ().releasePad (0, 0);
        require (sessionSlot.launches == 1 && sessionSlot.playing,
            "Session pad release did not launch the corresponding virtual clip slot");

        // Duplicate copies the selected source clip into the next empty slot;
        // Delete removes the addressed slot; Stop+pad stops only its track.
        final MixerModel.SlotState duplicateTarget = mixerModel.slot (0, 6);
        midi.device ().pressButton (VirtualPush1Device.DUPLICATE);
        require (surface.isPressed (ButtonID.DUPLICATE), "Duplicate button press did not remain held for the pad chord");
        midi.device ().pressPad (0, 0, 100);
        midi.device ().releasePad (0, 0);
        midi.device ().releaseButton (VirtualPush1Device.DUPLICATE);
        require (sessionSlot.hasContent, "Duplicate source clip unexpectedly disappeared");
        midi.device ().pressButton (VirtualPush1Device.DUPLICATE);
        require (surface.isPressed (ButtonID.DUPLICATE), "Second Duplicate button press did not remain held");
        midi.device ().pressPad (1, 0, 100);
        midi.device ().releasePad (1, 0);
        require (duplicateTarget.hasContent && duplicateTarget.pastes == 1,
            "Duplicate+pad did not paste the selected clip into the target slot; pastes="
                + duplicateTarget.pastes + ", totalPastes=" + mixerModel.totalPastes ()
                + ", content=" + duplicateTarget.hasContent + ", sourceContent=" + sessionSlot.hasContent
                + ", view=" + surface.getViewManager ().getActiveID ());
        midi.device ().releaseButton (VirtualPush1Device.DUPLICATE);
        midi.device ().pressButton (VirtualPush1Device.DELETE);
        midi.device ().pressPad (0, 0, 100);
        midi.device ().releasePad (0, 0);
        midi.device ().releaseButton (VirtualPush1Device.DELETE);
        require (!sessionSlot.hasContent && sessionSlot.removals == 1,
            "Delete+pad did not remove the selected Session clip");
        sessionSlot.hasContent = true;
        sessionSlot.playing = true;
        midi.device ().pressButton (VirtualPush1Device.STOP);
        midi.device ().pressPad (0, 0, 100);
        midi.device ().releasePad (0, 0);
        midi.device ().releaseButton (VirtualPush1Device.STOP);
        require (!sessionSlot.playing && !mixerModel.isTrackPlaying (0),
            "Stop+pad did not stop the corresponding track's clip");

        surface.getViewManager ().setActive (Views.SCENE_PLAY);
        midi.device ().pressPad (0, 0, 100);
        midi.device ().releasePad (0, 0);
        require (mixerModel.sceneLaunchCount (0) == 1 && mixerModel.selectedScene () == 0,
            "Scene Play pad did not launch and select the corresponding scene");
        surface.getViewManager ().setActive (Views.SESSION);
        sessionSlot.playing = true;
        midi.device ().pressButton (VirtualPush1Device.SHIFT);
        requireButtonMidiDelivery (midi, VirtualPush1Device.STOP, "Shift+Stop all Session clips");
        midi.device ().releaseButton (VirtualPush1Device.SHIFT);
        require (!sessionSlot.playing, "Shift+Stop did not stop the playing Session clip");
        surface.getViewManager ().setActive (Views.PLAY);

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

        // First/last device and scene boundaries must remain clamped while the
        // real Device mode arrow bindings are active.
        deviceIndex = 0;
        requireButtonMidiDelivery (midi, VirtualPush1Device.LEFT, "Device lower boundary");
        require (deviceIndex == 0, "Device navigation moved before the first device");
        deviceIndex = 15;
        requireButtonMidiDelivery (midi, VirtualPush1Device.RIGHT, "Device upper boundary");
        require (deviceIndex == 15, "Device navigation moved beyond the last device");
        sceneIndex = 0;
        requireButtonMidiDelivery (midi, VirtualPush1Device.UP, "Scene lower boundary");
        require (sceneIndex == 0, "Scene navigation moved before the first scene");
        sceneIndex = 15;
        requireButtonMidiDelivery (midi, VirtualPush1Device.DOWN, "Scene upper boundary");
        require (sceneIndex == 15, "Scene navigation moved beyond the last scene");
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
        // In Session view the dedicated In/Out buttons become track-bank
        // page navigation; their first/last pages must remain clamped.
        trackIndex = 0;
        requireButtonMidiDelivery (midi, VirtualPush1Device.IN, "Track page lower boundary");
        require (trackIndex == 0, "Track bank moved before its first page");
        trackIndex = 15;
        requireButtonMidiDelivery (midi, VirtualPush1Device.OUT, "Track page upper boundary");
        require (trackIndex == 15, "Track bank moved beyond its last page");
        sceneIndex = 0;
        requireButtonMidiDelivery (midi, VirtualPush1Device.UP, "Session scene lower boundary");
        require (sceneIndex == 0, "Session scene navigation moved before the first scene");
        sceneIndex = 15;
        requireButtonMidiDelivery (midi, VirtualPush1Device.DOWN, "Session scene upper boundary");
        require (sceneIndex == 15, "Session scene navigation moved beyond the last scene");
        navigationContext = false;

        // In/Out must not duplicate arrow-based device selection. They are
        // reserved for parameter-page navigation while the parameter panel is shown.
        requireButtonMidiDelivery (midi, VirtualPush1Device.DEVICE, "Device mode");
        require (modes.isActive (Modes.DEVICE_PARAMS), "Device button did not activate Device mode for In/Out tests");
        final DeviceParamsMode inOutDeviceMode = (DeviceParamsMode) modes.get (Modes.DEVICE_PARAMS);
        inOutDeviceMode.setShowDevices (true);
        MODEL_ACTIONS.clear ();
        requireButtonMidiDelivery (midi, VirtualPush1Device.OUT, "Out while device list is shown");
        requireButtonMidiDelivery (midi, VirtualPush1Device.IN, "In while device list is shown");
        require (MODEL_ACTIONS.isEmpty (), "In/Out duplicated arrow-based device selection while the device list was shown");
        inOutDeviceMode.setShowDevices (false);
        MODEL_ACTIONS.clear ();
        requireButtonMidiDelivery (midi, VirtualPush1Device.OUT, "Out parameter page");
        requireButtonMidiDelivery (midi, VirtualPush1Device.IN, "In parameter page");
        requireAction ("parameterPage.selectPreviousPage", "In must select the previous parameter page");
        requireAction ("parameterPage.selectNextPage", "Out must select the next parameter page");
        parameterPageIndex = 0;
        requireButtonMidiDelivery (midi, VirtualPush1Device.IN, "Parameter page lower boundary");
        require (parameterPageIndex == 0, "Parameter page moved before its first page");
        parameterPageIndex = 3;
        requireButtonMidiDelivery (midi, VirtualPush1Device.OUT, "Parameter page upper boundary");
        require (parameterPageIndex == 3, "Parameter page moved beyond its last page");

        // DeviceParamsMode must ignore empty device slots and must not call
        // API methods whose capabilities are unavailable on the host.
        final DeviceParamsMode deviceParamsMode = (DeviceParamsMode) modes.get (Modes.DEVICE_PARAMS);
        cursorDeviceExists = true;
        deviceSlotExists = false;
        deviceCapabilitiesSupported = false;
        deviceParamsMode.setShowDevices (true);
        MODEL_ACTIONS.clear ();
        deviceParamsMode.onFirstRow (1, ButtonEvent.UP);
        deviceParamsMode.onSecondRow (1, ButtonEvent.DOWN);
        deviceParamsMode.onSecondRow (3, ButtonEvent.DOWN);
        deviceParamsMode.onSecondRow (5, ButtonEvent.DOWN);
        deviceParamsMode.getButtonColor (ButtonID.get (ButtonID.ROW2_1, 1));
        deviceParamsMode.getButtonColor (ButtonID.get (ButtonID.ROW2_1, 3));
        deviceParamsMode.getButtonColor (ButtonID.get (ButtonID.ROW2_1, 5));
        require (deviceParamsMode.getButtonColor (ButtonID.get (ButtonID.ROW1_1, 1)) == PushColorManager.PUSH1_COLOR2_GREY_LO,
            "Unavailable device slots must use the neutral unavailable LED color");
        require (deviceParamsMode.getButtonColor (ButtonID.get (ButtonID.ROW2_1, 3)) == PushColorManager.PUSH1_COLOR2_GREY_LO,
            "Unsupported hierarchy navigation must use the neutral unavailable LED color");
        require (deviceParamsMode.getButtonColor (ButtonID.get (ButtonID.ROW2_1, 7)) == PushColorManager.PUSH1_COLOR2_ORCHID_HI,
            "Up must use the hierarchy color");
        require (MODEL_ACTIONS.isEmpty (),
            "DeviceParamsMode acted on an empty device row or an unsupported host capability");
        require (modes.isActive (Modes.DEVICE_PARAMS),
            "Unsupported slot-chain capability unexpectedly activated Device Chains");

        deviceCapabilitiesSupported = true;
        deviceSlotExists = true;
        deviceIndex = 4;
        require (deviceParamsMode.getButtonColor (ButtonID.get (ButtonID.ROW1_1, 4)) == PushColorManager.PUSH1_COLOR2_GREEN_HI,
            "The selected device must use the strong selection green");
        deviceParamsMode.onFirstRow (1, ButtonEvent.UP);
        requireAction ("device.select", "Selecting an existing device row must select that device");
        deviceParamsMode.onSecondRow (1, ButtonEvent.DOWN);
        deviceParamsMode.onSecondRow (5, ButtonEvent.DOWN);
        requireAction ("cursorDevice.toggleParameterPageSectionVisible",
            "Supported parameter-page-section capability must remain actionable");
        requireAction ("cursorDevice.togglePinned",
            "Supported pinning capability must remain actionable");
        cursorDeviceExists = false;
        deviceSlotExists = false;
        deviceCapabilitiesSupported = true;

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
        require (browserMode.getButtonColor (ButtonID.ROW1_1) == PushColorManager.PUSH1_COLOR2_BLUE_LO,
            "Browser previous-item buttons must use the navigation blue");
        require (browserMode.getButtonColor (ButtonID.get (ButtonID.ROW2_1, 0)) == PushColorManager.PUSH1_COLOR2_BLUE_HI,
            "Browser next-item buttons must use the stronger navigation blue");

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
        final List<String> browserDisplayText = new java.util.ArrayList<> ();
        final ITextDisplay browserDisplay = (ITextDisplay) Proxy.newProxyInstance (
            ITextDisplay.class.getClassLoader (), new Class<?> [] { ITextDisplay.class },
            (proxy, method, displayArgs) -> {
                if (displayArgs != null)
                    for (final Object argument: displayArgs)
                        if (argument instanceof String text)
                            browserDisplayText.add (text);
                return method.getReturnType ().isInstance (proxy) ? proxy
                    : defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
        browserMode.updateDisplay1 (browserDisplay);
        require (browserDisplayText.stream ().anyMatch (text -> text.startsWith (String.valueOf (Push1Display.SELECT_ARROW))),
            "Browser selected-column marker was not composed for the Push 1 LCD: " + browserDisplayText);

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

        // Navigation must clamp at the first/last filter and result entries.
        browserModel.setFilterCursor (0, 0);
        requireButtonMidiDelivery (midi, VirtualPush1Device.TOP_ROW_FIRST, "Browser filter lower boundary");
        require (browserModel.filterCursor (0) == 0, "Browser filter cursor moved below its first item");
        browserModel.setFilterCursor (0, 15);
        requireButtonMidiDelivery (midi, VirtualPush1Device.BOTTOM_ROW_FIRST, "Browser filter upper boundary");
        require (browserModel.filterCursor (0) == 15, "Browser filter cursor moved beyond its last item");
        browserModel.setResultCursor (0);
        requireButtonMidiDelivery (midi, VirtualPush1Device.TOP_ROW_FIRST + 7, "Browser result lower boundary");
        require (browserModel.resultCursor () == 0, "Browser result cursor moved below its first item");
        browserModel.setResultCursor (15);
        requireButtonMidiDelivery (midi, VirtualPush1Device.BOTTOM_ROW_FIRST + 7, "Browser result upper boundary");
        require (browserModel.resultCursor () == 15, "Browser result cursor moved beyond its last item");

        // The API-17 note input exists before its optional Note Repeat service.
        // The production mode must tolerate absence and resolve the service if
        // Bitwig creates it after controller startup.
        require (midi.input ().getDefaultNoteInput () != null
                && midi.input ().getDefaultNoteInput ().getNoteRepeat () == null,
            "Virtual MIDI input should begin without the optional Note Repeat service");
        require (modes.get (Modes.REPEAT_NOTE) != null, "Note Repeat mode was not registered");
        modes.setActive (Modes.REPEAT_NOTE);
        requireButtonMidiDelivery (midi, VirtualPush1Device.TOP_ROW_FIRST + 4, "Note Repeat latch without service");
        require (!midi.noteRepeat ().isLatchActive (),
            "Note Repeat attempted to mutate an unavailable service");
        midi.setNoteRepeatAvailable (true);
        require (midi.input ().getDefaultNoteInput ().getNoteRepeat () == midi.noteRepeat (),
            "Late-created Note Repeat service was not exposed by the MIDI input");
        requireButtonMidiDelivery (midi, VirtualPush1Device.TOP_ROW_FIRST + 4, "Note Repeat latch after service creation");
        require (midi.noteRepeat ().isLatchActive (), "Late-created Note Repeat service was not resolved by the active mode");
        requireButtonMidiDelivery (midi, VirtualPush1Device.TOP_ROW_FIRST + 5, "Note Repeat pressure velocity");
        require (midi.noteRepeat ().usePressure (), "Note Repeat pressure-velocity state did not toggle");
        requireButtonMidiDelivery (midi, VirtualPush1Device.TOP_ROW_FIRST + 6, "Note Repeat free running");
        require (midi.noteRepeat ().isFreeRunning (), "Note Repeat free-running state did not toggle");
        requireButtonMidiDelivery (midi, VirtualPush1Device.TOP_ROW_FIRST + 7, "Note Repeat shuffle");
        require (midi.noteRepeat ().isShuffle (), "Note Repeat shuffle state did not toggle");
        modes.setActive (Modes.TRACK);

        // The Push 1 New override must locate a free slot before stopping the
        // cursor track, stop only that track, and preserve the Shift+New
        // no-overdub behavior. The other track is deliberately playing.
        newClipModel.resetScenario ();
        newClipModel.setSlotAvailable (1, false);
        newClipModel.setSlotAvailable (2, false);
        newClipModel.setSlotAvailable (3, true);
        newClipModel.setTrackPlaying (true);
        requireButtonMidiDelivery (midi, VirtualPush1Device.NEW, "New clip with a free slot");
        require (newClipModel.createCount () == 1 && newClipModel.lastSlotIndex () == 3,
            "New did not create the clip in the first available slot at/after the selected slot");
        require (newClipModel.stopCount () == 1 && !newClipModel.isTrackPlaying (),
            "New did not stop the playing cursor track before creating its clip");
        require (newClipModel.isOtherTrackPlaying (),
            "New incorrectly stopped playback on another track");
        require (newClipModel.lastOverdub (),
            "Normal New did not enable overdub on the new note clip");

        newClipModel.resetScenario ();
        newClipModel.setTrackPlaying (false);
        midi.device ().pressButton (VirtualPush1Device.SHIFT);
        requireButtonMidiDelivery (midi, VirtualPush1Device.NEW, "Shift+New clip");
        midi.device ().releaseButton (VirtualPush1Device.SHIFT);
        require (newClipModel.createCount () == 1 && !newClipModel.lastOverdub (),
            "Shift+New did not create a clip with overdub disabled");

        newClipModel.resetScenario ();
        newClipModel.setTrackPlaying (true);
        for (int slot = 0; slot < 8; slot++)
            newClipModel.setSlotAvailable (slot, false);
        requireButtonMidiDelivery (midi, VirtualPush1Device.NEW, "New with no free slot");
        require (newClipModel.createCount () == 0 && newClipModel.stopCount () == 0
                && newClipModel.isTrackPlaying (),
            "New with no free slot changed playback or created a clip");

        // Slot search wraps from the selected slot to the start of the page.
        newClipModel.resetScenario ();
        newClipModel.setSelectedSlot (6);
        for (int slot = 6; slot < 8; slot++)
            newClipModel.setSlotAvailable (slot, false);
        newClipModel.setSlotAvailable (0, true);
        for (int slot = 1; slot < 6; slot++)
            newClipModel.setSlotAvailable (slot, false);
        requireButtonMidiDelivery (midi, VirtualPush1Device.NEW, "New clip wrap-around search");
        require (newClipModel.createCount () == 1 && newClipModel.lastSlotIndex () == 0,
            "New did not wrap its free-slot search to the first slot");

        // A missing cursor track must leave playback untouched and must not
        // attempt to create a clip. Restore a real cursor track for view tests.
        newClipModel.resetScenario ();
        newClipModel.setTrackExists (false);
        newClipModel.setTrackPlaying (true);
        requireButtonMidiDelivery (midi, VirtualPush1Device.NEW, "New without cursor track");
        require (newClipModel.createCount () == 0 && newClipModel.stopCount () == 0
                && newClipModel.isTrackPlaying (),
            "New without a cursor track changed playback or created a clip");
        newClipModel.setTrackExists (true);
        modes.setActive (Modes.TRACK);

        // Stateful musical model: exercise the production note sequencer,
        // hybrid Drum view and 64-pad block mapping through Push 1 pad MIDI.
        musicalModel.setCanHoldNotes (true);
        final var viewManager = surface.getViewManager ();
        viewManager.setActive (Views.SEQUENCER);
        final SequencerView sequencerView = (SequencerView) viewManager.get (Views.SEQUENCER);
        sequencerView.updateNoteMapping ();
        drainScheduled (state, 6);
        sequencerView.updateControlSurface ();
        sequencerView.onLeft (ButtonEvent.DOWN);
        sequencerView.onRight (ButtonEvent.DOWN);
        require (musicalModel.pageBackCount () == 1 && musicalModel.pageForwardCount () == 1,
            "Sequencer page navigation did not reach the note clip model");
        musicalModel.setEditPage (0);
        sequencerView.onLeft (ButtonEvent.DOWN);
        require (musicalModel.editPage () == 0, "Sequencer clip page moved before its first page");
        musicalModel.setEditPage (7);
        sequencerView.onRight (ButtonEvent.DOWN);
        require (musicalModel.editPage () == 7, "Sequencer clip page moved beyond its last page");
        sequencerView.setResolutionIndex (4);
        require (Math.abs (musicalModel.stepLength () - Resolution.values ()[4].getValue ()) < 0.000001,
            "Sequencer resolution selection did not update the clip step length");

        // Exercise the production Poly Sequencer's persistent note-selection,
        // additive step programming, per-note removal, Scale mapping and both
        // whole-step and individual-note duration gestures.
        viewManager.setActive (Views.POLY_SEQUENCER);
        final PolySequencerView polySequencerView = (PolySequencerView) viewManager.get (Views.POLY_SEQUENCER);
        scaleModel[0].setChromatic (true);
        scaleModel[0].setScaleOffsetByName ("C");
        polySequencerView.updateNoteMapping ();
        drainScheduled (state, 6);
        polySequencerView.updateControlSurface ();

        final ITrack rootTrack = newClipModel.cursorTrack ();
        final int mappedRoot = polySequencerView.getKeyManager ().map (36);
        require (mappedRoot >= 0 && Math.floorMod (mappedRoot, 12) == scaleModel[0].getScaleOffset (),
            "Poly Sequencer lower grid did not map its root note from the active Scale");
        final java.lang.reflect.Method polyGridColor = AbstractPolySequencerView.class.getDeclaredMethod (
            "getGridColor", boolean.class, boolean.class, ITrack.class, int.class);
        polyGridColor.setAccessible (true);
        final String rootColor = (String) polyGridColor.invoke (polySequencerView, true, false, rootTrack, 36);
        final String expectedRootColor = AbstractView.replaceOctaveColorWithTrackColor (rootTrack, Scales.SCALE_COLOR_OCTAVE);
        require (expectedRootColor.equals (rootColor),
            "Poly Sequencer fundamental did not retain the selected track color");

        for (int note = 36; note <= 39; note++)
        {
            polySequencerView.onGridNote (note, 100);
            polySequencerView.onGridNote (note, 0);
        }
        // Hardware pad 92 is the first step (step zero) in the upper grid.
        polySequencerView.onGridNote (92, 100);
        polySequencerView.onGridNote (92, 0);
        for (int note = 36; note <= 39; note++)
            require (musicalModel.noteClip ().getStep (new NotePosition (0, 0, note)).getState () == StepState.START,
                "Poly Sequencer did not write selected MIDI note " + note + " to the first step");

        // A second step tap must add notes without toggling existing notes off.
        polySequencerView.onGridNote (92, 100);
        polySequencerView.onGridNote (92, 0);
        for (int note = 36; note <= 39; note++)
            require (musicalModel.noteClip ().getStep (new NotePosition (0, 0, note)).getState () == StepState.START,
                "Poly Sequencer erased an existing note while adding to an occupied step");

        polySequencerView.onGridNoteLongPress (92);
        polySequencerView.onGridNote (92, 0);
        polySequencerView.onGridNote (36, 100);
        polySequencerView.onGridNote (36, 0);
        require (musicalModel.noteClip ().getStep (new NotePosition (0, 0, 36)).getState () == StepState.OFF,
            "Poly Sequencer did not remove the tapped note from the edited step");
        for (int note = 37; note <= 39; note++)
            require (musicalModel.noteClip ().getStep (new NotePosition (0, 0, note)).getState () == StepState.START,
                "Poly Sequencer removed a neighbouring note while editing one pitch");

        // Select notes on both sides of a delay longer than the former
        // 500 ms note-memory timeout. All four pitches must remain selected.
        Thread.sleep (550);

        // Seed the selected-note target to isolate the individual-duration
        // path; the virtual button proxy models the held source-step gesture.
        final java.lang.reflect.Field durationEditNote = PolySequencerView.class.getDeclaredField ("durationEditNote");
        durationEditNote.setAccessible (true);
        final java.lang.reflect.Method changePolyLength = AbstractPolySequencerView.class.getDeclaredMethod (
            "changeLengthOfNotes", INoteClip.class, NotePosition.class);
        changePolyLength.setAccessible (true);
        final IHwButton sourceStepButton = surface.getButton (ButtonID.get (ButtonID.PAD1, 56));
        sourceStepButton.trigger (ButtonEvent.DOWN);
        sourceStepButton.trigger (ButtonEvent.LONG);
        // Long-press opens the step editor and initializes its note selection;
        // choose the individual pitch after that edit context is established.
        durationEditNote.setInt (polySequencerView, 37);
        changePolyLength.invoke (polySequencerView, musicalModel.noteClip (), new NotePosition (0, 2, 0));
        sourceStepButton.trigger (ButtonEvent.UP);
        final double individualDuration = 3 * Resolution.getValueAt (polySequencerView.getResolutionIndex ());
        require (Math.abs (musicalModel.noteClip ().getStep (new NotePosition (0, 0, 37)).getDuration () - individualDuration) < 0.000001,
            "Poly Sequencer did not resize the selected note's duration");
        require (Math.abs (musicalModel.noteClip ().getStep (new NotePosition (0, 0, 38)).getDuration () - 0.25) < 0.000001,
            "Poly Sequencer individual-note resize changed a neighbouring note");

        // With no individual note selected, the existing whole-step duration
        // gesture must continue to resize every note in the source step.
        durationEditNote.setInt (polySequencerView, -1);
        sourceStepButton.trigger (ButtonEvent.DOWN);
        sourceStepButton.trigger (ButtonEvent.LONG);
        changePolyLength.invoke (polySequencerView, musicalModel.noteClip (), new NotePosition (0, 3, 0));
        sourceStepButton.trigger (ButtonEvent.UP);
        final double wholeStepDuration = 4 * Resolution.getValueAt (polySequencerView.getResolutionIndex ());
        for (int note = 37; note <= 39; note++)
            require (Math.abs (musicalModel.noteClip ().getStep (new NotePosition (0, 0, note)).getDuration () - wholeStepDuration) < 0.000001,
                "Poly Sequencer whole-step length gesture did not resize every note");

        // Changing the active Scale changes the keyboard mapping, not notes
        // already committed to the clip (including pitches outside the Scale).
        scaleModel[0].setChromatic (false);
        scaleModel[0].setScale (Scale.MAJOR);
        scaleModel[0].setScaleOffsetByName ("D");
        polySequencerView.updateNoteMapping ();
        drainScheduled (state, 6);
        require (polySequencerView.getKeyManager ().map (36) == 38
                && polySequencerView.getKeyManager ().map (37) == 40,
            "Poly Sequencer's 32-note keyboard did not follow the newly selected D-major Scale");
        require (musicalModel.noteClip ().getStep (new NotePosition (0, 0, 39)).getState () == StepState.START,
            "Changing Poly Sequencer Scale deleted a previously programmed out-of-scale note");
        viewManager.setActive (Views.SEQUENCER);

        // Assert the actual production sequencer's row-to-MIDI mapping against
        // independent musical expectations, including root, scale and octave.
        require (scaleModel[0] != null, "Production Scales model was not wired into the runtime");
        scaleModel[0].setChromatic (false);
        scaleModel[0].setScale (Scale.MAJOR);
        scaleModel[0].setScaleOffsetByName ("D");
        sequencerView.updateNoteMapping ();
        drainScheduled (state, 6);
        assertSequencerNotes (sequencerView, musicalModel,
            new int [] { 38, 40, 42, 43, 45, 47, 49 }, "D major");

        scaleModel[0].setScale (Scale.MINOR_PENTATONIC);
        scaleModel[0].setScaleOffsetByName ("G");
        sequencerView.updateNoteMapping ();
        drainScheduled (state, 6);
        assertSequencerNotes (sequencerView, musicalModel,
            new int [] { 43, 46, 48, 50, 53, 55, 58 }, "G minor pentatonic");

        scaleModel[0].setScale (Scale.MAJOR);
        scaleModel[0].setScaleOffsetByName ("C");
        final java.lang.reflect.Method updateOctave = sequencerView.getClass ().getSuperclass ()
            .getDeclaredMethod ("updateOctave", int.class);
        updateOctave.setAccessible (true);
        updateOctave.invoke (sequencerView, 48);
        drainScheduled (state, 6);
        assertSequencerNotes (sequencerView, musicalModel,
            new int [] { 48, 50, 52, 53, 55, 57, 59 }, "C major octave-up");

        updateOctave.invoke (sequencerView, 36);
        scaleModel[0].setChromatic (true);
        sequencerView.updateNoteMapping ();
        drainScheduled (state, 6);
        assertSequencerNotes (sequencerView, musicalModel,
            new int [] { 36, 37, 38, 39, 40, 41, 42 }, "chromatic");

        // A moving playhead must alter the production pad-grid feedback, not
        // merely update an internal clip cursor.
        scaleModel[0].setChromatic (false);
        musicalModel.setEditPage (0);
        sequencerView.updateNoteMapping ();
        drainScheduled (state, 6);
        // The production sequencer highlights an occupied step; create the
        // same note on adjacent steps so the playhead has visible feedback to follow.
        musicalModel.noteClip ().setStep (new NotePosition (0, 5, 36), 100, 0.25);
        musicalModel.noteClip ().setStep (new NotePosition (0, 6, 36), 100, 0.25);
        musicalModel.setCurrentStep (5);
        sequencerView.drawGrid ();
        setup.flush ();
        drainScheduled (state, 6);
        hardware.flush ();
        final int [] padColorsAtStepFive = new int [64];
        for (int i = 0; i < padColorsAtStepFive.length; i++)
            padColorsAtStepFive[i] = surface.getPadGrid ().getLightInfo (36 + i).getColor ();
        musicalModel.setCurrentStep (6);
        sequencerView.drawGrid ();
        setup.flush ();
        drainScheduled (state, 6);
        hardware.flush ();
        int changedPlayheadLeds = 0;
        for (int i = 0; i < padColorsAtStepFive.length; i++)
            if (padColorsAtStepFive[i] != surface.getPadGrid ().getLightInfo (36 + i).getColor ())
                changedPlayheadLeds++;
        require (changedPlayheadLeds > 0,
            "Sequencer playhead movement did not update the production pad-grid LED state");

        // Regression: Bitwig reports the clip's full 128-step length, while
        // the Push 1 grid displays eight columns per page. The playhead must
        // remain visible after auto-follow moves beyond page zero.
        state.playing = true;
        musicalModel.setEditPage (0);
        // Earlier boundary checks deliberately disabled auto-follow through
        // manual arrows. Selecting the current page restores the live-follow mode.
        musicalModel.setCurrentStep (5);
        sequencerView.onGridNote (92, 100);
        sequencerView.onGridNote (92, 0);
        musicalModel.noteClip ().setStep (new NotePosition (0, 0, 36), 100, 0.25);
        musicalModel.noteClip ().setStep (new NotePosition (0, 1, 36), 100, 0.25);
        musicalModel.setCurrentStep (8);
        sequencerView.drawGrid ();
        setup.flush ();
        drainScheduled (state, 6);
        hardware.flush ();
        require (musicalModel.editPage () == 1,
            "Sequencer playhead did not auto-follow from page zero to page one");
        final int [] pageOneAtStepEight = new int [64];
        for (int i = 0; i < pageOneAtStepEight.length; i++)
            pageOneAtStepEight[i] = surface.getPadGrid ().getLightInfo (36 + i).getColor ();
        musicalModel.setCurrentStep (9);
        sequencerView.drawGrid ();
        setup.flush ();
        drainScheduled (state, 6);
        hardware.flush ();
        int pageOnePlayheadChanges = 0;
        for (int i = 0; i < pageOneAtStepEight.length; i++)
            if (pageOneAtStepEight[i] != surface.getPadGrid ().getLightInfo (36 + i).getColor ())
                pageOnePlayheadChanges++;
        require (pageOnePlayheadChanges > 0,
            "Sequencer playhead LED stopped following steps on page one");

        // Manual arrow paging locks the selected page; selecting the live
        // playhead page on the top row restores automatic following.
        musicalModel.setCurrentStep (16);
        sequencerView.drawGrid ();
        setup.flush ();
        drainScheduled (state, 6);
        require (musicalModel.editPage () == 2,
            "Sequencer playhead did not auto-follow to page two");
        sequencerView.onRight (ButtonEvent.DOWN);
        require (musicalModel.editPage () == 3,
            "Manual sequencer paging did not select page three");
        sequencerView.drawGrid ();
        setup.flush ();
        drainScheduled (state, 6);
        require (musicalModel.editPage () == 3,
            "Sequencer auto-follow overrode a manually selected page");
        sequencerView.onGridNote (94, 100);
        sequencerView.onGridNote (94, 0);
        require (musicalModel.editPage () == 2,
            "Selecting the current playhead page did not restore the live page");
        musicalModel.setCurrentStep (24);
        sequencerView.drawGrid ();
        setup.flush ();
        drainScheduled (state, 6);
        require (musicalModel.editPage () == 3,
            "Sequencer did not resume auto-follow after selecting the playhead page");

        final int togglesBeforeDrum = musicalModel.toggleCount ();
        viewManager.setActive (Views.DRUM);
        final DrumView drumView = (DrumView) viewManager.get (Views.DRUM);
        drumView.updateNoteMapping ();
        drainScheduled (state, 6);
        drumView.updateControlSurface ();
        require (drumView.isActive () && musicalModel.canHoldNotes (), "Drum view or note-capable track was not active");
        drumView.onGridNote (36, 96);
        drumView.onGridNote (36, 0);
        drumView.onGridNote (68, 96);
        drumView.onGridNote (68, 0);
        require (musicalModel.toggleCount () == togglesBeforeDrum + 1 && musicalModel.lastToggle ().getStep () == 24,
            "Drum view did not map its sequencer row to the expected clip step: toggles="
                + musicalModel.toggleCount () + ", last=" + musicalModel.lastToggle ().getStep ()
                + ", note=" + musicalModel.lastToggle ().getNote ());
        require (musicalModel.lastToggle ().getNote () >= 0 && musicalModel.lastToggle ().getNote () <= 127,
            "Drum view produced an invalid drum note row");

        viewManager.setActive (Views.DRUM64);
        final Drum64View drum64View = (Drum64View) viewManager.get (Views.DRUM64);
        drum64View.updateNoteMapping ();
        drainScheduled (state, 6);
        drum64View.updateControlSurface ();
        final java.lang.reflect.Field selectedPadField = drum64View.getClass ().getSuperclass ().getDeclaredField ("selectedPad");
        selectedPadField.setAccessible (true);
        for (int note = 36; note < 100; note++)
        {
            final int index = note - 36;
            final int x = index % 8;
            final int y = index / 8;
            final int expectedPad = x / 4 * 32 + y / 4 * 16 + y % 4 * 4 + x % 4;
            drum64View.onGridNote (note, 100);
            drum64View.onGridNote (note, 0);
            require (selectedPadField.getInt (drum64View) == expectedPad,
                "Drum64 pad note " + note + " mapped to " + selectedPadField.getInt (drum64View)
                    + " instead of " + expectedPad);
        }
        drum64View.drawGrid ();
        setup.flush ();
        drainScheduled (state, 1);
        hardware.flush ();
        musicalModel.setCanHoldNotes (false);
        viewManager.setActive (Views.PLAY);

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

        // Exercise the complete Swing simulator in memory on every CI run.
        VirtualPush1Simulator.smoke (midi);

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
     * Stateful cursor-track/slot model for the Push 1 New command integration
     * tests. Slot search wraps within the visible page, and track playback is
     * deliberately independent from the selected slot's playback state.
     */

    /**
     * Stateful API-17 musical host model for the production Sequencer, Drum
     * and Drum64 views. Notes, step length, clip paging and drum-pad selection
     * are retained across real Push 1 MIDI events.
     */
    private static final class MusicalModel
    {
        private final Map<String, StepState> steps = new java.util.HashMap<> ();
        private final Map<String, Boolean> muted = new java.util.HashMap<> ();
        private final Map<String, Double> durations = new java.util.HashMap<> ();
        private final boolean [] canHold = { false };
        private final int [] currentStep = { 5 };
        private final int [] editPage = { 0 };
        private final int [] pageBack = { 0 };
        private final int [] pageForward = { 0 };
        private final int [] selectedPad = { 0 };
        private final int [] scrollPosition = { 0 };
        private final boolean [] indication = { false };
        private final double [] stepLength = { 0.25 };
        private final double [] loopStart = { 0.0 };
        private final double [] loopLength = { 16.0 };
        private final int [] toggleCount = { 0 };
        private final int [] transpose = { 0 };
        private NotePosition lastToggle;
        private final INoteClip noteClip;
        private final IDrumPad [] drumPads = new IDrumPad [64];
        private final IDrumPadBank drumPadBank;
        private final IDrumDevice drumDevice;

        MusicalModel ()
        {
            this.noteClip = (INoteClip) Proxy.newProxyInstance (
                INoteClip.class.getClassLoader (), new Class<?> [] { INoteClip.class },
                (proxy, method, args) -> {
                    switch (method.getName ())
                    {
                        case "doesExist": return true;
                        case "getName": return "Virtual Push note clip";
                        case "getColor": return ColorEx.BLUE;
                        case "getNumRows", "getNumSteps": return 128;
                        case "getCurrentStep": return this.currentStep[0];
                        case "getStepLength": return this.stepLength[0];
                        case "setStepLength": this.stepLength[0] = ((Number) args[0]).doubleValue (); return null;
                        case "getLoopStart": return this.loopStart[0];
                        case "setLoopStart": this.loopStart[0] = ((Number) args[0]).doubleValue (); return null;
                        case "getLoopLength": return this.loopLength[0];
                        case "setLoopLength": this.loopLength[0] = ((Number) args[0]).doubleValue (); return null;
                        case "getEditPage": return this.editPage[0];
                        case "scrollToPage": this.editPage[0] = (int) args[0]; return null;
                        case "scrollStepsPageBackwards": this.pageBack[0]++; this.editPage[0] = Math.max (0, this.editPage[0] - 1); return null;
                        case "scrollStepsPageForward": this.pageForward[0]++; this.editPage[0] = Math.min (7, this.editPage[0] + 1); return null;
                        case "getStep": return this.stepInfo ((NotePosition) args[0]);
                        case "toggleStep":
                        {
                            final NotePosition position = (NotePosition) args[0];
                            final String key = this.key (position);
                            final StepState previous = this.steps.getOrDefault (key, StepState.OFF);
                            this.steps.put (key, previous == StepState.OFF ? StepState.START : StepState.OFF);
                            this.lastToggle = new NotePosition (position);
                            this.toggleCount[0]++;
                            return null;
                        }
                        case "setStep":
                        {
                            final NotePosition position = (NotePosition) args[0];
                            this.steps.put (this.key (position), StepState.START);
                            this.lastToggle = new NotePosition (position);
                            return null;
                        }
                        case "clearStep":
                            this.steps.remove (this.key ((NotePosition) args[0]));
                            return null;
                        case "updateStepMuteState":
                            this.muted.put (this.key ((NotePosition) args[0]), (boolean) args[1]);
                            return null;
                        case "updateStepDuration":
                            this.durations.put (this.key ((NotePosition) args[0]), ((Number) args[1]).doubleValue ());
                            return null;
                        case "clearRow":
                        {
                            final int note = (int) args[1];
                            this.steps.keySet ().removeIf (key -> key.endsWith (":" + note));
                            return null;
                        }
                        case "transpose": this.transpose[0] += (int) args[0]; return null;
                        case "setPlayRange": return null;
                        case "getLoopEnd": return this.loopStart[0] + this.loopLength[0];
                        case "getLength": return 32.0;
                        case "getStart": return 0.0;
                        default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                    }
                });

            for (int i = 0; i < this.drumPads.length; i++)
            {
                final int index = i;
                this.drumPads[i] = (IDrumPad) Proxy.newProxyInstance (
                    IDrumPad.class.getClassLoader (), new Class<?> [] { IDrumPad.class },
                    (proxy, method, args) -> {
                        switch (method.getName ())
                        {
                            case "doesExist", "isActivated": return true;
                            case "isMute", "isSolo", "isPlaying": return false;
                            case "isSelected": return this.selectedPad[0] == index;
                            case "getColor": return ColorEx.BLUE;
                            case "getIndex", "getPosition": return index;
                            case "getName": return "Drum pad " + index;
                            case "getVolume": return 1.0;
                            case "select": this.selectedPad[0] = index; return null;
                            default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                        }
                    });
            }

            this.drumPadBank = (IDrumPadBank) Proxy.newProxyInstance (
                IDrumPadBank.class.getClassLoader (), new Class<?> [] { IDrumPadBank.class },
                (proxy, method, args) -> {
                    switch (method.getName ())
                    {
                        case "getItem": return this.drumPads[Math.max (0, Math.min (63, (int) args[0]))];
                        case "getPageSize": return 16;
                        case "getItemCount", "size": return this.drumPads.length;
                        case "getScrollPosition": return this.scrollPosition[0];
                        case "scrollTo": this.scrollPosition[0] = (int) args[0]; return null;
                        case "scrollForwards": this.scrollPosition[0]++; return null;
                        case "scrollBackwards": this.scrollPosition[0] = Math.max (0, this.scrollPosition[0] - 1); return null;
                        case "getSelectedItem": return Optional.of (this.drumPads[this.selectedPad[0]]);
                        case "hasSoloedPads", "hasMutedPads": return false;
                        case "setIndication": this.indication[0] = (boolean) args[0]; return null;
                        case "clearMute", "clearSolo": return null;
                        default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                    }
                });
            this.drumDevice = (IDrumDevice) Proxy.newProxyInstance (
                IDrumDevice.class.getClassLoader (), new Class<?> [] { IDrumDevice.class },
                (proxy, method, args) -> switch (method.getName ())
                {
                    case "doesExist", "hasDrumPads": yield true;
                    case "getDrumPadBank": yield this.drumPadBank;
                    case "getPosition": yield 0;
                    case "getName": yield "Virtual Drum Machine";
                    default: yield defaultValue (method.getReturnType (), method.getGenericReturnType ());
                });
        }

        private IStepInfo stepInfo (final NotePosition position)
        {
            final String key = this.key (position);
            return (IStepInfo) Proxy.newProxyInstance (
                IStepInfo.class.getClassLoader (), new Class<?> [] { IStepInfo.class },
                (proxy, method, args) -> switch (method.getName ())
                {
                    case "getState" -> this.steps.getOrDefault (key, StepState.OFF);
                    case "isMuted" -> this.muted.getOrDefault (key, false);
                    case "getDuration" -> this.durations.getOrDefault (key, 0.25);
                    case "getVelocity" -> 100.0;
                    case "getVelocitySpread", "getReleaseVelocity", "getPressure", "getTimbre", "getPan",
                         "getTranspose", "getGain", "getChance", "getRepeatCurve", "getRepeatVelocityCurve",
                         "getRepeatVelocityEnd" -> 0.0;
                    case "isChanceEnabled", "isOccurrenceEnabled", "isRecurrenceEnabled", "isRepeatEnabled" -> false;
                    case "getRecurrenceLength" -> 1;
                    case "getRecurrenceMask", "getRepeatCount" -> 0;
                    case "getFormattedRepeatCount" -> "0";
                    case "createCopy" -> this.stepInfo (position);
                    default -> defaultValue (method.getReturnType (), method.getGenericReturnType ());
                });
        }

        private String key (final NotePosition position)
        {
            return position.getChannel () + ":" + position.getStep () + ":" + position.getNote ();
        }

        INoteClip noteClip () { return this.noteClip; }
        IDrumDevice drumDevice () { return this.drumDevice; }
        void setCanHoldNotes (final boolean value) { this.canHold[0] = value; }
        boolean canHoldNotes () { return this.canHold[0]; }
        int toggleCount () { return this.toggleCount[0]; }
        NotePosition lastToggle () { return this.lastToggle == null ? new NotePosition () : new NotePosition (this.lastToggle); }
        int pageBackCount () { return this.pageBack[0]; }
        int pageForwardCount () { return this.pageForward[0]; }
        int editPage () { return this.editPage[0]; }
        void setEditPage (final int value) { this.editPage[0] = Math.max (0, Math.min (7, value)); }
        double stepLength () { return this.stepLength[0]; }
        void setCurrentStep (final int value) { this.currentStep[0] = value; }
    }

    private static final class NewClipModel
    {
        private final boolean [] available = new boolean [8];
        private final int [] selectedSlot = { 1 };
        private final boolean [] trackPlaying = { true };
        private final boolean [] trackExists = { false };
        private final boolean [] otherTrackPlaying = { true };
        private final int [] stopCount = { 0 };
        private final int [] createCount = { 0 };
        private final int [] lastSlotIndex = { -1 };
        private final int [] lastLength = { -1 };
        private final boolean [] lastOverdub = { false };
        private final ISlot [] slots = new ISlot [8];
        private final ISlotBank slotBank;
        private final ICursorTrack cursorTrack;

        NewClipModel ()
        {
            Arrays.fill (this.available, true);
            for (int i = 0; i < this.slots.length; i++)
            {
                final int index = i;
                this.slots[i] = (ISlot) Proxy.newProxyInstance (
                    ISlot.class.getClassLoader (), new Class<?> [] { ISlot.class },
                    (proxy, method, args) -> switch (method.getName ())
                    {
                        case "doesExist" -> true;
                        case "getIndex", "getPosition" -> index;
                        case "getName" -> "Clip slot " + index;
                        case "hasContent" -> !this.available[index];
                        case "isPlaying", "isRecording", "isPlayingQueued", "isRecordingQueued", "isStopQueued" -> false;
                        default -> defaultValue (method.getReturnType (), method.getGenericReturnType ());
                    });
            }
            this.slotBank = (ISlotBank) Proxy.newProxyInstance (
                ISlotBank.class.getClassLoader (), new Class<?> [] { ISlotBank.class },
                (proxy, method, args) -> {
                    switch (method.getName ())
                    {
                        case "getPageSize", "size": return this.slots.length;
                        case "getSelectedItem": return Optional.of (this.slots[this.selectedSlot[0]]);
                        case "getItem": return this.slots[(int) args[0]];
                        case "getEmptySlot":
                            final int start = Math.max (0, Math.min (this.slots.length - 1, (int) args[0]));
                            for (int offset = 0; offset < this.slots.length; offset++)
                            {
                                final int index = (start + offset) % this.slots.length;
                                if (this.available[index])
                                    return Optional.of (this.slots[index]);
                            }
                            return Optional.empty ();
                        default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                    }
                });
            this.cursorTrack = (ICursorTrack) Proxy.newProxyInstance (
                ICursorTrack.class.getClassLoader (), new Class<?> [] { ICursorTrack.class },
                (proxy, method, args) -> {
                    switch (method.getName ())
                    {
                        case "doesExist": return this.trackExists[0];
                        case "getSlotBank": return this.slotBank;
                        case "getColor": return ColorEx.BLUE;
                        case "isPlaying": return this.trackPlaying[0];
                        case "stop": this.trackPlaying[0] = false; this.stopCount[0]++; return null;
                        default: return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                    }
                });
        }

        ICursorTrack cursorTrack () { return this.cursorTrack; }
        void setSlotAvailable (final int index, final boolean value) { this.available[index] = value; }
        void setSelectedSlot (final int index) { this.selectedSlot[0] = Math.max (0, Math.min (this.slots.length - 1, index)); }
        void setTrackPlaying (final boolean value) { this.trackPlaying[0] = value; }
        void setTrackExists (final boolean value) { this.trackExists[0] = value; }
        boolean isTrackPlaying () { return this.trackPlaying[0]; }
        boolean isOtherTrackPlaying () { return this.otherTrackPlaying[0]; }
        int stopCount () { return this.stopCount[0]; }
        int createCount () { return this.createCount[0]; }
        int lastSlotIndex () { return this.lastSlotIndex[0]; }
        boolean lastOverdub () { return this.lastOverdub[0]; }

        void createNoteClip (final Object [] args)
        {
            this.createCount[0]++;
            this.lastSlotIndex[0] = ((ISlot) args[1]).getIndex ();
            this.lastLength[0] = (int) args[2];
            this.lastOverdub[0] = (boolean) args[3];
            this.available[this.lastSlotIndex[0]] = false;
        }

        void resetScenario ()
        {
            Arrays.fill (this.available, true);
            this.selectedSlot[0] = 1;
            this.trackExists[0] = true;
            this.trackPlaying[0] = false;
            this.otherTrackPlaying[0] = true;
            this.stopCount[0] = 0;
            this.createCount[0] = 0;
            this.lastSlotIndex[0] = -1;
            this.lastLength[0] = -1;
            this.lastOverdub[0] = false;
        }
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
        void setFilterCursor (final int column, final int value) { this.filterCursors[column] = Math.max (0, Math.min (15, value)); }
        void setResultCursor (final int value) { this.resultCursor[0] = Math.max (0, Math.min (15, value)); }

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
                        (p, m, a) -> switch (m.getName ())
                        {
                            case "doesExist" -> Boolean.valueOf (deviceSlotExists);
                            case "select" -> { MODEL_ACTIONS.add ("device.select"); yield null; }
                            default -> defaultValue (m.getReturnType (), m.getGenericReturnType ());
                        });
                if (List.of ("selectPreviousItem", "selectNextItem", "selectPreviousPage", "selectNextPage").contains (method.getName ()))
                {
                    MODEL_ACTIONS.add ("device." + method.getName ());
                    switch (method.getName ())
                    {
                        case "selectPreviousItem": deviceIndex = Math.max (0, deviceIndex - 1); break;
                        case "selectNextItem": deviceIndex = Math.min (15, deviceIndex + 1); break;
                        case "selectPreviousPage": deviceIndex = Math.max (0, deviceIndex - 8); break;
                        case "selectNextPage": deviceIndex = Math.min (15, deviceIndex + 8); break;
                        default: break;
                    }
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
                    parameterPageIndex = method.getName ().equals ("selectPreviousPage")
                        ? Math.max (0, parameterPageIndex - 1) : Math.min (3, parameterPageIndex + 1);
                    return null;
                }
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
        return (ICursorDevice) Proxy.newProxyInstance (ICursorDevice.class.getClassLoader (),
            new Class<?> [] { ICursorDevice.class }, (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "doesExist": return Boolean.valueOf (cursorDeviceExists);
                    case "getIndex": return Integer.valueOf (deviceIndex);
                    case "getParameterBank": return parameters;
                    case "getDeviceBank": return devices;
                    case "getParameterPageBank": return pages;
                    case "toggleParameterPageSectionVisible":
                        if (!deviceCapabilitiesSupported)
                            throw new AssertionError ("Unsupported parameter-page-section capability was invoked");
                        MODEL_ACTIONS.add ("cursorDevice.toggleParameterPageSectionVisible");
                        return null;
                    case "isParameterPageSectionVisible":
                        if (!deviceCapabilitiesSupported)
                            throw new AssertionError ("Unsupported parameter-page-section state was read");
                        return Boolean.TRUE;
                    case "togglePinned":
                        if (!deviceCapabilitiesSupported)
                            throw new AssertionError ("Unsupported pinning capability was invoked");
                        MODEL_ACTIONS.add ("cursorDevice.togglePinned");
                        return null;
                    case "isPinned":
                        if (!deviceCapabilitiesSupported)
                            throw new AssertionError ("Unsupported pinning state was read");
                        return Boolean.TRUE;
                    default:
                        return defaultValue (method.getReturnType (), method.getGenericReturnType ());
                }
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
                    switch (method.getName ())
                    {
                        case "scrollBackwards": sceneIndex = Math.max (0, sceneIndex - 1); break;
                        case "scrollForwards": sceneIndex = Math.min (15, sceneIndex + 1); break;
                        case "selectPreviousPage": sceneIndex = Math.max (0, sceneIndex - 8); break;
                        case "selectNextPage": sceneIndex = Math.min (15, sceneIndex + 8); break;
                        default: break;
                    }
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

    private static ITrackBank trackBankProxy (final MixerModel mixer)
    {
        return (ITrackBank) Proxy.newProxyInstance (ITrackBank.class.getClassLoader (),
            new Class<?> [] { ITrackBank.class }, (proxy, method, args) -> {
                if (List.of ("selectPreviousItem", "selectNextItem", "selectPreviousPage", "selectNextPage").contains (method.getName ()))
                {
                    MODEL_ACTIONS.add ("track." + method.getName ());
                    switch (method.getName ())
                    {
                        case "selectPreviousItem": trackIndex = Math.max (0, trackIndex - 1); break;
                        case "selectNextItem": trackIndex = Math.min (15, trackIndex + 1); break;
                        case "selectPreviousPage": trackIndex = Math.max (0, trackIndex - 8); break;
                        case "selectNextPage": trackIndex = Math.min (15, trackIndex + 8); break;
                        default: break;
                    }
                    return null;
                }
                if (method.getName ().equals ("getPageSize"))
                    return 8;
                if (method.getName ().equals ("stop"))
                { mixer.stopAll (); return null; }
                if (method.getName ().equals ("getSelectedItem"))
                    return navigationContext ? Optional.of (mixer.track (Math.floorMod (trackIndex, 8))) : Optional.empty ();
                if (method.getName ().equals ("getSceneBank"))
                    return mixer.sceneBank ();
                if (method.getName ().equals ("getItem"))
                    return mixer.track (Math.max (0, Math.min (7, (int) args[0])));
                if (method.getName ().equals ("canEditSend"))
                    return (int) args[0] >= 0 && (int) args[0] < 4;
                if (method.getName ().equals ("getEditSendName"))
                    return "Send " + ((int) args[0] + 1);
                return defaultValue (method.getReturnType (), method.getGenericReturnType ());
            });
    }

    /** Stateful track/channel/parameter bank used by real Push mixer modes. */
    private static final class MixerModel
    {
        private static final class ParameterState
        {
            int value = 64;
            int changes;
            int lastControl = -1;
            boolean touched;
            final IParameter parameter;

            ParameterState (final String name)
            {
                this.parameter = (IParameter) Proxy.newProxyInstance (
                    IParameter.class.getClassLoader (), new Class<?> [] { IParameter.class },
                    (proxy, method, args) -> this.invoke (name, method.getName (), args,
                        method.getReturnType (), method.getGenericReturnType ()));
            }

            Object invoke (final String name, final String method, final Object [] args,
                final Class<?> returnType, final Type genericType)
            {
                switch (method)
                {
                    case "doesExist": return true;
                    case "getName": return args != null && args.length > 0
                        ? name.substring (0, Math.min (name.length (), (int) args[0])) : name;
                    case "getValue": case "getModulatedValue": return this.value;
                    case "getDisplayedValue": return Integer.toString (this.value);
                    case "getIndex": case "getPosition": return 0;
                    case "getColor": return ColorEx.BLUE;
                    case "touchValue": this.touched = (boolean) args[0]; return null;
                    case "changeValue":
                    {
                        final int encoded = ((Number) args[args.length - 1]).intValue ();
                        final int delta = encoded > 64 ? encoded - 128 : encoded;
                        this.lastControl = encoded;
                        this.changes++;
                        this.value = Math.max (0, Math.min (127, this.value + delta));
                        return null;
                    }
                    case "setValue": case "setValueImmediatly":
                        this.value = Math.max (0, Math.min (127, ((Number) args[args.length - 1]).intValue ())); return null;
                    case "setNormalizedValue":
                        this.value = Math.max (0, Math.min (127, (int) Math.round (((Number) args[0]).doubleValue () * 127))); return null;
                    case "inc":
                        this.value = Math.max (0, Math.min (127, this.value + (int) Math.round (((Number) args[0]).doubleValue () * 127))); return null;
                    case "resetValue": this.value = 64; return null;
                    case "isSelected": return false;
                    default: return defaultValue (returnType, genericType);
                }
            }
        }

        private final ParameterState [] volumes = new ParameterState [8];
        private final ParameterState [] pans = new ParameterState [8];
        private final ParameterState [][] sends = new ParameterState [8][4];
        private static final class SlotState
        {
            boolean hasContent;
            boolean playing;
            boolean recording;
            boolean playingQueued;
            boolean recordingQueued;
            boolean stopQueued;
            boolean launchedImmediately;
            boolean selected;
            int launches;
            int immediateLaunches;
            int recordings;
            int pastes;
            int removals;
        }

        private final boolean [] muted = new boolean [8];
        private final boolean [] solo = new boolean [8];
        private final boolean [] trackPlaying = new boolean [8];
        private final boolean [] recArmed = new boolean [8];
        private final ITrack [] tracks = new ITrack [8];
        private final SlotState [][] slotStates = new SlotState [8][8];
        private final ISlotBank [] slotBanks = new ISlotBank [8];
        private final IScene [] scenes = new IScene [64];
        private final int [] sceneLaunches = new int [64];
        private final boolean [] sceneExists = new boolean [64];
        private final ISceneBank sceneBank;
        private int selectedTrack;
        private int selectedScene = -1;

        MixerModel ()
        {
            for (int scene = 0; scene < this.scenes.length; scene++)
            {
                final int sceneIndex = scene;
                this.scenes[scene] = (IScene) Proxy.newProxyInstance (
                    IScene.class.getClassLoader (), new Class<?> [] { IScene.class },
                    (p, m, a) -> switch (m.getName ())
                    {
                        case "doesExist" -> true;
                        case "getIndex", "getPosition" -> sceneIndex;
                        case "getName" -> a == null || a.length == 0 ? "Scene " + (sceneIndex + 1)
                            : ("Scene " + (sceneIndex + 1)).substring (0, Math.min (("Scene " + (sceneIndex + 1)).length (), (int) a[0]));
                        case "getColor" -> ColorEx.BLUE;
                        case "isSelected" -> this.selectedScene == sceneIndex;
                        case "select" -> { this.selectedScene = sceneIndex; yield null; }
                        case "launch" -> { this.selectedScene = sceneIndex; this.sceneLaunches[sceneIndex]++; yield null; }
                        case "remove" -> { this.sceneExists[sceneIndex] = false; yield null; }
                        default -> defaultValue (m.getReturnType (), m.getGenericReturnType ());
                    });
            }
            this.sceneBank = (ISceneBank) Proxy.newProxyInstance (
                ISceneBank.class.getClassLoader (), new Class<?> [] { ISceneBank.class },
                (p, m, a) -> switch (m.getName ())
                {
                    case "getPageSize", "getItemCount", "size" -> 64;
                    case "getItem" -> this.scenes[Math.max (0, Math.min (63, (int) a[0]))];
                    case "getSelectedItem" -> this.selectedScene < 0 ? Optional.empty () : Optional.of (this.scenes[this.selectedScene]);
                    case "scrollBackwards" -> { MODEL_ACTIONS.add ("scene.scrollBackwards"); sceneIndex = Math.max (0, sceneIndex - 1); yield null; }
                    case "scrollForwards" -> { MODEL_ACTIONS.add ("scene.scrollForwards"); sceneIndex = Math.min (15, sceneIndex + 1); yield null; }
                    case "selectPreviousPage" -> { MODEL_ACTIONS.add ("scene.selectPreviousPage"); sceneIndex = Math.max (0, sceneIndex - 8); yield null; }
                    case "selectNextPage" -> { MODEL_ACTIONS.add ("scene.selectNextPage"); sceneIndex = Math.min (15, sceneIndex + 8); yield null; }
                    case "canScrollBackwards" -> sceneIndex > 0;
                    case "canScrollForwards" -> sceneIndex < 15;
                    case "canScrollPageBackwards" -> sceneIndex >= 8;
                    case "canScrollPageForwards" -> sceneIndex <= 7;
                    case "stop" -> { this.trackPlayingStopAll (); yield null; }
                    default -> defaultValue (m.getReturnType (), m.getGenericReturnType ());
                });
            for (int i = 0; i < this.tracks.length; i++)
            {
                final int index = i;
                this.volumes[i] = new ParameterState ("Track " + (i + 1) + " Volume");
                this.pans[i] = new ParameterState ("Track " + (i + 1) + " Pan");
                for (int send = 0; send < 4; send++)
                    this.sends[i][send] = new ParameterState ("Send " + (send + 1));
                final ISend [] sendItems = new ISend [4];
                for (int send = 0; send < sendItems.length; send++)
                {
                    final int sendIndex = send;
                    sendItems[send] = (ISend) Proxy.newProxyInstance (ISend.class.getClassLoader (),
                        new Class<?> [] { ISend.class }, (p, m, a) -> {
                            if (m.getName ().equals ("getColor")) return ColorEx.BLUE;
                            return this.sends[index][sendIndex].invoke ("Send " + (sendIndex + 1),
                                m.getName (), a, m.getReturnType (), m.getGenericReturnType ());
                        });
                }
                final ISlot [] slotItems = new ISlot [8];
                for (int slot = 0; slot < slotItems.length; slot++)
                {
                    final int slotIndex = slot;
                    final SlotState slotState = new SlotState ();
                    this.slotStates[index][slot] = slotState;
                    slotItems[slot] = (ISlot) Proxy.newProxyInstance (
                        ISlot.class.getClassLoader (), new Class<?> [] { ISlot.class },
                        (p, m, a) -> switch (m.getName ())
                        {
                            case "doesExist" -> true;
                            case "getIndex", "getPosition" -> slotIndex;
                            case "getName" -> "Clip " + (slotIndex + 1);
                            case "hasContent" -> slotState.hasContent;
                            case "isPlaying" -> slotState.playing;
                            case "isRecording" -> slotState.recording;
                            case "isPlayingQueued" -> slotState.playingQueued;
                            case "isRecordingQueued" -> slotState.recordingQueued;
                            case "isStopQueued" -> slotState.stopQueued;
                            case "getColor" -> ColorEx.BLUE;
                            case "select" -> { slotState.selected = true; yield null; }
                            case "launch" -> { slotState.launches++; slotState.playing = true; this.trackPlaying[index] = true; yield null; }
                            case "launchImmediately" -> { slotState.immediateLaunches++; slotState.launchedImmediately = true; slotState.playing = true; this.trackPlaying[index] = true; yield null; }
                            case "testAndClearLaunchedImmediately" -> { final boolean value = slotState.launchedImmediately; slotState.launchedImmediately = false; yield value; }
                            case "startRecording" -> { slotState.recordings++; slotState.recording = true; slotState.hasContent = true; this.trackPlaying[index] = true; yield null; }
                            case "remove" -> { slotState.removals++; slotState.hasContent = false; slotState.playing = false; slotState.recording = false; yield null; }
                            case "duplicate" -> { slotState.hasContent = true; yield null; }
                            case "paste" -> { slotState.pastes++; slotState.hasContent = true; yield null; }
                            case "setColor" -> null;
                            default -> defaultValue (m.getReturnType (), m.getGenericReturnType ());
                        });
                }
                this.slotBanks[index] = (ISlotBank) Proxy.newProxyInstance (
                    ISlotBank.class.getClassLoader (), new Class<?> [] { ISlotBank.class },
                    (p, m, a) -> switch (m.getName ())
                    {
                        case "getPageSize", "getItemCount", "size" -> 8;
                        case "getItem" -> slotItems[Math.max (0, Math.min (7, (int) a[0]))];
                        case "getSelectedItem" -> Optional.of (slotItems[0]);
                        case "getEmptySlot" -> {
                            final int start = Math.max (0, Math.min (7, (int) a[0]));
                            ISlot empty = null;
                            for (int offset = 0; offset < 8; offset++)
                            {
                                final ISlot candidate = slotItems[(start + offset) % 8];
                                if (!candidate.hasContent ()) { empty = candidate; break; }
                            }
                            yield Optional.ofNullable (empty);
                        }
                        default -> defaultValue (m.getReturnType (), m.getGenericReturnType ());
                    });
                final ISendBank sendBank = (ISendBank) Proxy.newProxyInstance (
                    ISendBank.class.getClassLoader (), new Class<?> [] { ISendBank.class },
                    (p, m, a) -> switch (m.getName ())
                    {
                        case "getPageSize", "getItemCount", "size" -> 4;
                        case "getItem" -> sendItems[Math.max (0, Math.min (3, (int) a[0]))];
                        case "getSelectedItem" -> Optional.of (sendItems[0]);
                        default -> defaultValue (m.getReturnType (), m.getGenericReturnType ());
                    });
                this.tracks[i] = (ITrack) Proxy.newProxyInstance (
                    ITrack.class.getClassLoader (), new Class<?> [] { ITrack.class },
                    (p, m, a) -> switch (m.getName ())
                    {
                        case "doesExist", "isActivated", "canHoldNotes", "canHoldAudioData" -> true;
                        case "getIndex", "getPosition" -> index;
                        case "getName" -> a == null || a.length == 0 ? "Virtual Track " + (index + 1)
                            : ("Virtual Track " + (index + 1)).substring (0, Math.min (("Virtual Track " + (index + 1)).length (), (int) a[0]));
                        case "isSelected" -> this.selectedTrack == index;
                        case "select" -> { this.selectedTrack = index; yield null; }
                        case "getVolumeParameter" -> this.volumes[index].parameter;
                        case "getPanParameter" -> this.pans[index].parameter;
                        case "getVolume", "getModulatedVolume" -> this.volumes[index].value;
                        case "getPan", "getModulatedPan" -> this.pans[index].value;
                        case "getVolumeStr" -> Integer.toString (this.volumes[index].value);
                        case "getPanStr" -> Integer.toString (this.pans[index].value);
                        case "getSendBank" -> sendBank;
                        case "getColor" -> ColorEx.BLUE;
                        case "isMute" -> this.muted[index];
                        case "setMute" -> { this.muted[index] = (boolean) a[0]; yield null; }
                        case "toggleMute" -> { this.muted[index] = !this.muted[index]; yield null; }
                        case "isSolo" -> this.solo[index];
                        case "setSolo" -> { this.solo[index] = (boolean) a[0]; yield null; }
                        case "toggleSolo" -> { this.solo[index] = !this.solo[index]; yield null; }
                        case "isGroup", "isGroupExpanded", "hasParent", "isMonitor", "isAutoMonitor", "hasDrumDevice" -> false;
                        case "isRecArm" -> this.recArmed[index];
                        case "setRecArm" -> { this.recArmed[index] = (boolean) a[0]; yield null; }
                        case "toggleRecArm" -> { this.recArmed[index] = !this.recArmed[index]; yield null; }
                        case "isPlaying" -> this.trackPlaying[index];
                        case "stop" -> { this.trackPlaying[index] = false; for (final SlotState slot : this.slotStates[index]) { slot.playing = false; slot.recording = false; } yield null; }
                        case "returnToArrangement" -> { this.trackPlaying[index] = false; yield null; }
                        case "getSceneBank" -> this.sceneBank;
                        case "getSlotBank" -> this.slotBanks[index];
                        case "getVu", "getVuLeft", "getVuRight", "getVuPeakLeft", "getVuPeakRight" -> 0;
                        default -> defaultValue (m.getReturnType (), m.getGenericReturnType ());
                    });
            }
        }

        ITrack track (final int index) { return this.tracks[index]; }
        ISceneBank sceneBank () { return this.sceneBank; }
        SlotState slot (final int track, final int index) { return this.slotStates[track][index]; }
        int sceneLaunchCount (final int index) { return this.sceneLaunches[index]; }
        int selectedScene () { return this.selectedScene; }
        int totalPastes ()
        {
            int count = 0;
            for (final SlotState [] track : this.slotStates)
                for (final SlotState slot : track)
                    count += slot.pastes;
            return count;
        }
        private void trackPlayingStopAll ()
        {
            for (int track = 0; track < this.trackPlaying.length; track++)
            {
                this.trackPlaying[track] = false;
                for (final SlotState slot : this.slotStates[track])
                { slot.playing = false; slot.recording = false; }
            }
        }
        ParameterState volume (final int index) { return this.volumes[index]; }
        ParameterState pan (final int index) { return this.pans[index]; }
        ParameterState send (final int track, final int index) { return this.sends[track][index]; }
        boolean isMuted (final int index) { return this.muted[index]; }
        boolean isTrackPlaying (final int index) { return this.trackPlaying[index]; }
        void stopAll () { this.trackPlayingStopAll (); }
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
        final boolean [] longPressed = { false };
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
                        longPressed[0] = event == ButtonEvent.LONG;
                        velocity[0] = (int) (amount * 127.0);
                        if (command[0] != null) command[0].execute (event, velocity[0]);
                        return null;
                    case "getCommand": return command[0];
                    case "isPressed": return pressed[0];
                    case "isLongPressed": return longPressed[0];
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
                        if (args[0] instanceof ContinuousCommand continuous) { command[0] = continuous; parameter[0] = null; }
                        else if (args[0] instanceof PitchbendCommand bend) pitchbend[0] = bend;
                        else if (args[0] == null || args[0] instanceof IParameter)
                        {
                            // HwRelativeKnobImpl keeps the command binding but
                            // switches its simple-parameter target independently.
                            // Prefer the active parameter while one is bound; when
                            // it is cleared, restore the still-registered command.
                            parameter[0] = (IParameter) args[0];
                        }
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
                        final int encoded = steps < 0 ? steps + 128 : steps;
                        if (parameter[0] != null)
                            parameter[0].changeValue (encoded);
                        else if (command[0] != null)
                            command[0].execute (encoded);
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

    private static void assertSequencerNotes (final SequencerView view, final MusicalModel model,
        final int [] expected, final String label)
    {
        for (int row = 0; row < expected.length; row++)
        {
            final int padNote = 36 + row * 8;
            view.onGridNote (padNote, 100);
            view.onGridNote (padNote, 0);
            require (model.lastToggle ().getNote () == expected[row],
                label + " row " + row + " mapped to MIDI note " + model.lastToggle ().getNote ()
                    + " instead of " + expected[row]);
        }
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
