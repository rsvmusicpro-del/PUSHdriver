# Virtual Push 1 validation coverage

This is the acceptance checklist for the software-only Push 1 bench. It separates protocol-level coverage, controller-semantic coverage, and behavior that cannot honestly be certified without a real Push 1 or a running Bitwig host.

## Automatically exercised in CI

| Push 1 surface / contract | Virtual model | Automated coverage | Confidence |
|---|---|---|---|
| 8×8 pad matrix (notes 36–99) | Note On/Off, pressed state | All 64 addresses; every velocity 1–127 and every poly-aftertouch value 0–127 on every pad; release/reset | Exhaustive protocol values |
| Main and small encoders | CC 71–79 and 14–15, relative two's-complement values | All 11 controls; all signed deltas −63…+63; zero delta emits no message | Exhaustive protocol values |
| Encoder touch | Note On velocity 127/0 | All 9 main and 2 small touch identities | Exhaustive identities |
| Ribbon | Touch note 12 and 14-bit pitch bend | Touch/release plus every value 0…16383 and exact LSB/MSB split | Exhaustive 14-bit values |
| Push buttons | Momentary CC press 127/release 0 | Every declared button CC in the independent Push 1 map; Shift chords and state | Exhaustive declared map |
| Pad and lower-row RGB feedback | Push RGB SysEx | All 72 addresses (64 pads + 8 lower-row controls), nibble decoding and invalid address rejection | Exhaustive addresses |
| LCD | Four independent 77-byte row SysEx frames | All 4 rows × 68 columns; all 128 seven-bit character values; four 17-character modules; 9/8 logical cells; custom glyph preservation | Exhaustive logical character space |
| Malformed protocol input | MIDI and SysEx validation | Invalid ranges, invalid row/address, malformed frames and invalid pad state transitions | Boundary cases |
| Extension output adapter | API 17 MIDI input/output doubles | CC/note/aftertouch/pitch-bend/SysEx routing, channels 0 and 15, String and byte-array SysEx | Adapter contract |
| Production controller wiring | Real assembled PushControllerSetup and production bindings | Startup, registered view/mode binding matrix, transport, Shift+Record, Note/Session/Track/Clip/Device modes, Device arrows, Session arrows and page buttons, In/Out navigation, pad/encoder/touch/ribbon input, LED/LCD feedback | Integration scenarios |
| Browser semantics | Stateful API-17 IBrowser/filter/result model | Top/bottom filter navigation, first/last filter and result boundaries, encoder touch/selection, Shift+Left/Right content-type paging, result navigation, LCD marker composition and filter LED availability | Stateful integration scenarios |
| New clip workflow | Stateful cursor-track/slot-bank model | Free-slot search, wrap-around search, stop only the cursor track after a slot is found, preserve other-track playback, Shift+New overdub disabled, no-slot fallback without stopping playback | Stateful integration scenarios |
| Device, track, scene and parameter navigation | Stateful API-17 bank models | First/last device boundaries, In/Out device paging, scene scroll boundaries, Session track-page boundaries, parameter-page boundaries | Stateful integration scenarios |
| Drum and sequencer views | Stateful API-17 INoteClip and IDrumDevice/IDrumPadBank model | Sequencer clip-page boundaries and resolution changes; D-major and G-minor-pentatonic mapping; octave-up and chromatic mapping; occupied-step playhead LED follow; Drum sequencer-row insertion; Drum64 4×4-block mapping for all 64 pad notes and grid rendering | Stateful view integration scenarios |
| Swing desktop simulator | Same VirtualPush1Device and VirtualPush1MidiBench used by the runtime suite | Headless CI constructs the complete Swing component tree, refreshes it and paints it to an in-memory image; optional interactive window remains available | UI construction/paint smoke test |
| Packaged extension | Built JAR | Push 1-only entry point, API 17, Java 17 bytecode, required classes/resources, forbidden Push 2/foreign code, dependency resolution | Package gate |
| Build reproducibility | Generated source assembly | Repeated assembly hash comparison | Deterministic assembly |

The exhaustive protocol checks are part of VirtualPush1RuntimeTest, the single entry point called by scripts/test_push1_extension.py in CI. It invokes the device, display-layout, protocol-exhaustive and MIDI-bench checks before booting the production controller setup. The Browser, New Clip, navigation and Note Repeat lifecycle scenarios use stateful API-17 collaborators and production Push 1 MIDI bindings.

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

## Remaining boundaries (not software regressions)

| Area | What the bench proves | What still requires a richer host or physical test |
|---|---|---|
| Live Bitwig Studio 4.3.10 | Controller startup and tested commands against deterministic Bitwig Extension API 17 proxies | The actual Bitwig 4.3.10 process, scheduler, project state, device graph and real-time transport are not launched by CI |
| Mixer values and empty host banks | Mixer-related commands are exercised by the production binding matrix; track/device/scene navigation has stateful first/last boundaries | Real volume, pan, send and device parameter values, plus every zero-item/unavailable-bank behavior, need a fuller DAW model if those features are changed |
| Real clip/scene playback | Sequencer page bounds and playhead LED following occupied adjacent steps are tested against the production view | Actual arranger/launcher scheduling, clip launch timing and scene transitions remain Bitwig-host integration tests |
| Physical Push 1 | All software-visible MIDI addresses and Push 1 LCD/RGB protocol behavior are emulated | USB-MIDI discovery, electrical timing, analog pressure calibration, firmware quirks and the physical LCD glass/brightness require hardware |

The virtual endpoint deliberately has no OS-level USB-MIDI device, native MIDI driver, physical footswitch circuit, analog pressure sensor, electrical/USB timing model or Push firmware process. CI proves the controller's tested protocol and API boundary, not undocumented Bitwig scheduler behavior or physical calibration.

Ordinary feature work should be validated by this virtual bench and its CI regression gate. Physical hardware should be reserved for qualification of the explicitly listed physical-only boundaries, not used as the routine development test loop.

## Merge protection requirement

The workflow's final check is named `quality-gate` and runs on pushes to `custom` and pull requests targeting it. GitHub's branch metadata currently reports `protected: false`; therefore the presence of the workflow does not prevent direct pushes or merges. Configure `quality-gate` as a required status check in GitHub branch protection before treating it as server-enforced merge protection.
