# Virtual Push 1 validation coverage

This is the acceptance checklist for the software-only Push 1 bench. It separates protocol-level coverage, production-controller integration, the Bitwig host model and behavior that cannot honestly be certified without a running Bitwig host or physical hardware.

## Automatically exercised in CI

| Push 1 surface / contract | Virtual model | Automated coverage | Confidence |
|---|---|---|---|
| 8×8 pad matrix (notes 36–99) | Note On/Off, pressed state | All 64 addresses; every velocity 1–127 and every poly-aftertouch value 0–127 on every pad; release/reset | Exhaustive protocol values |
| Main and small encoders | CC 71–79 and 14–15, relative two's-complement values | All 11 controls; all signed deltas −63…+63; zero delta emits no message | Exhaustive protocol values |
| Encoder touch | Note On velocity 127/0 | All 9 main and 2 small touch identities | Exhaustive identities |
| Ribbon | Touch note 12 and 14-bit pitch bend | Touch/release plus every value 0…16383 and exact LSB/MSB split | Exhaustive 14-bit values |
| Push buttons and footswitches | Momentary CC press 127/release 0 | Every declared Push 1 button CC; Shift chords; Footswitch 2 CC69 mapped to its default Sustain Pedal action (CC64 press/release); Footswitch 1 CC64 kept distinct and unbound in this extension setup | Exhaustive declared map + footswitch integration |
| Pad and lower-row RGB feedback | Push RGB SysEx | All 72 addresses (64 pads + 8 lower-row controls), nibble decoding and invalid address rejection | Exhaustive addresses |
| LCD | Four independent 77-byte row SysEx frames | All 4 rows × 68 columns; all 128 seven-bit character values; four 17-character modules; 9/8 logical cells; custom glyph preservation | Exhaustive logical character space |
| Malformed protocol input | MIDI and SysEx validation | Invalid ranges, invalid row/address, malformed frames and invalid pad state transitions | Boundary cases |
| Extension output adapter | API 17 MIDI input/output doubles | CC/note/aftertouch/pitch-bend/SysEx routing, channels 0 and 15, String and byte-array SysEx | Adapter contract |
| Production controller wiring | Real assembled PushControllerSetup and production bindings | Startup, registered view/mode binding matrix, transport, Shift+Record, Note/Session/Track/Clip/Device modes, Device arrows, Session arrows and page buttons, In/Out navigation, sequencer auto-follow across pages, manual page lock and follow restoration, pad/encoder/touch/ribbon/footswitch input, LED/LCD feedback | Integration scenarios |
| Transport | Stateful API-17 ITransport model | Play/stop, record, launcher overdub, metronome on/off and Shift+Metronome tick toggle | Stateful integration scenarios |
| Mixer and track parameters | Eight distinct virtual tracks with volume, pan and send-1 parameters | All eight encoders independently change their corresponding volume, pan and send-1; positive and negative relative values; Delete+touch resets a parameter; track mute state changes | Stateful parameter integration |
| Session clips and scenes | Eight tracks × eight clip slots plus 64 scene states | Session pad launches the correctly mapped slot; Duplicate+pad copies source to target; Delete+pad removes a clip; Stop+pad stops its track; Shift+Stop stops all clips; Scene Play pad launches and selects a scene | Stateful view integration |
| Browser semantics | Stateful API-17 IBrowser/filter/result model | Top/bottom filter navigation, first/last filter and result boundaries, encoder touch/selection, Shift+Left/Right content-type paging, result navigation, LCD marker composition and filter LED availability; all eight static ROW2 selector colors and color identifiers are checked against blink/RGB commands | Stateful integration + static-row safety invariant |
| New clip workflow | Stateful cursor-track/slot-bank model | Free-slot search, wrap-around search, stop only the cursor track after a slot is found, preserve other-track playback, Shift+New overdub disabled, no-slot fallback without stopping playback, missing cursor-track fallback | Stateful integration scenarios |
| Device, track, scene and parameter navigation | Stateful API-17 bank models | First/last device boundaries, In/Out device paging, scene scroll boundaries, Session track-page boundaries, parameter-page boundaries; Device actions on all eight RGB-row positions, static-only device/page selectors, Device Chains activation/selection through physical MIDI bindings, empty-slot guards and unsupported capability guards | Stateful integration scenarios + LED palette invariant |
| Poly Sequencer | Stateful API-17 INoteClip model and production Push 1 view | Persistent scale-mapped 32-note selection; additive multi-note step programming; long-press step inspection; individual note removal; duplicate/mute/repeat compatibility; track-colored Scale fundamental; single-note duration editing and preserved whole-step duration gesture; clip notes survive Scale changes | Stateful production-view integration scenarios |
| Drum and sequencer views | Stateful API-17 INoteClip and IDrumDevice/IDrumPadBank model | Sequencer clip-page boundaries and resolution changes; D-major and G-minor-pentatonic mapping; octave-up and chromatic mapping; playhead LED movement on page one and beyond; manual page lock and live-follow restoration; Drum sequencer-row insertion; Drum64 4×4-block mapping for all 64 pad notes and grid rendering | Stateful view integration scenarios |
| Swing desktop simulator | Same VirtualPush1Device and VirtualPush1MidiBench used by the runtime suite | Headless CI constructs the complete Swing component tree, refreshes it and paints it to an in-memory image; interactive UI exposes pads, pressure, velocity, encoders, ribbon, buttons and both footswitches | UI construction/paint smoke test |
| Firmware identity parser | Extended 35-byte universal MIDI identity response | Ableton three-byte manufacturer ID, separate family/member fields and four software-revision digits; rejects foreign manufacturers, malformed digits and unsupported lengths | Dedicated parser regression test |
| LCD text and value formatting | Push 1 68-cell LCD text and 8-cell value-bar helpers | Fixed-width fitting, Unicode replacement by code point, truncation, 17-cell right-block overlays, bounded modulation markers, pan symmetry and integer extremes | Dedicated formatter regression tests |
| Packaged extension | Built JAR | Push 1-only entry point, API 17, Java 17 bytecode, required classes/resources, forbidden Push 2/foreign code, dependency resolution | Package gate |
| Build reproducibility | Generated source assembly | Repeated assembly hash comparison | Deterministic assembly |

The exhaustive protocol checks are part of VirtualPush1RuntimeTest, the single entry point called by scripts/test_push1_extension.py in CI. It invokes the device, display-layout, protocol-exhaustive, firmware-identity, display-value-formatter, display-text and MIDI-bench checks before booting the production controller setup. Browser, mixer, Session, Scene Play, New Clip, navigation, Drum and Sequencer scenarios use stateful API-17 collaborators and production Push 1 MIDI bindings.

## Run the bench locally

From the repository root, install a JDK 17, Python 3 and Apache Maven, then run:

```bash
python3 scripts/run_virtual_push1.py
```

To open the interactive Swing Push 1 after the automated tests pass:

```bash
python3 scripts/run_virtual_push1.py --gui
```

On Windows, use `py -3 scripts/run_virtual_push1.py` (add `--gui` for the interactive window). The runner assembles the pinned source, builds the extension, verifies the package and executes the automated virtual bench.

## What the fidelity claims mean

The bench exhaustively checks the declared digital protocol value spaces for pads, poly-aftertouch, relative encoders, ribbon pitch bend, RGB addresses and LCD character coordinates. Its production-controller tests cover the explicitly listed workflows above.

That is not the same as a measured claim that the complete physical Push 1 is simulated to 90%. No defensible 90% score exists until the project defines a complete, weighted inventory of hardware, firmware, DAW and timing behaviors. The bench currently models selected Bitwig API 17 interactions; it is not a full Bitwig runtime or a physical-device emulator.

## Remaining boundaries (not software regressions)

| Area | What the bench proves | What still requires a richer host or physical test |
|---|---|---|
| Live Bitwig Studio 4.3.10 | Controller startup and tested commands against deterministic Bitwig Extension API 17 proxies | The actual Bitwig 4.3.10 process, scheduler, project state, device graph, real-time transport, device parameter values and every empty/unavailable host-bank behavior are not launched by CI |
| Real clip/scene playback | Production Session and Scene Play views launch/select virtual slots and scenes; clip duplication, deletion and stop chords are exercised | Actual arranger/launcher scheduling, clip launch quantization, latency, scene transition timing and interactions with real plug-ins remain Bitwig-host integration tests |
| Physical Push 1 | All tested software-visible MIDI addresses and Push 1 LCD/RGB protocol behavior are emulated; footswitch CC actions are exercised | USB-MIDI discovery, electrical timing, footswitch contact bounce, analog pressure/velocity response curves, firmware quirks, LED brightness and LCD glass appearance require hardware |
| Operating-system MIDI | The virtual endpoint routes MIDI callbacks without a native device | Driver installation, device enumeration, hot-plug, USB disconnect/reconnect and OS-specific MIDI behavior are not simulated |

The virtual endpoint deliberately has no OS-level USB-MIDI device, native MIDI driver, physical switch circuit, analog pressure sensor, electrical/USB timing model or Push firmware process. CI proves the tested protocol and API boundary, not undocumented Bitwig scheduler behavior or physical calibration.

Ordinary feature work should be validated by this virtual bench and its CI regression gate. Physical hardware should be reserved for qualification of the explicitly listed physical-only boundaries, not used as the routine development test loop.

## Merge protection requirement

The workflow's final check is named `quality-gate` and runs on pushes to `custom` and pull requests targeting it. GitHub's branch metadata currently reports `protected: true`. The connected integration cannot read the detailed branch-protection endpoint (GitHub returns HTTP 403), so the exact required status checks and bypass permissions cannot be independently verified here. Confirm in repository Settings that `quality-gate` is required for `custom` and that no role can bypass it before treating the gate as fully server-enforced.
