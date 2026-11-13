# Virtual Push 1 validation coverage

This is the acceptance checklist for the software-only Push 1 bench. It distinguishes protocol-level coverage from controller-semantic coverage and from behavior that cannot honestly be certified without real hardware or a running Bitwig host.

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
| Production controller wiring | Real assembled PushControllerSetup and production bindings | Startup, registered view/mode binding matrix, transport, Shift+Record, Note/Session/Track/Clip/Device modes, Device arrows, Session arrows, In/Out device and parameter-page actions, pad/encoder/touch/ribbon input, LED/LCD feedback | Integration scenarios |
| Browser semantics | Stateful API-17 IBrowser/filter/result model | Production MIDI bindings for top/bottom filter navigation, encoder touch/selection, Shift+Left/Right content-type paging, result navigation, LCD marker composition and filter LED availability | Stateful integration scenarios |
| New clip workflow | Stateful cursor-track/slot-bank model | Free-slot search, stop only the cursor track after a slot is found, preserve other-track playback, Shift+New overdub disabled, no-slot fallback without stopping playback | Stateful integration scenarios |
| Note Repeat lifecycle | Optional API-17 INoteRepeat service model | Controller startup with no service, safe mode entry while absent, late service creation, latch/pressure/free-running/shuffle controls | Lifecycle integration scenarios |
| Drum and sequencer views | Stateful API-17 INoteClip and IDrumDevice/IDrumPadBank model | Sequencer page navigation and resolution changes, Drum sequencer-row step insertion, Drum64 4×4-block mapping for all 64 pad notes, Drum64 grid rendering | Stateful view integration scenarios |
| Packaged extension | Built JAR | Push 1-only entry point, API 17, Java 17 bytecode, required classes/resources, forbidden Push 2/foreign code, dependency resolution | Package gate |
| Build reproducibility | Generated source assembly | Repeated assembly hash comparison | Deterministic assembly |

The exhaustive protocol checks are part of VirtualPush1RuntimeTest, which is the single entry point called by scripts/test_push1_extension.py in CI. The runtime entry point invokes the device, display-layout, protocol-exhaustive and MIDI-bench checks before booting the production controller setup. The Browser, New and Note Repeat lifecycle scenarios use stateful API-17 collaborators and production Push 1 MIDI bindings.

## Run the bench locally

From the repository root, install a JDK 17, Python 3 and Apache Maven, then run:

```bash
python3 scripts/run_virtual_push1.py
```

To open the interactive Swing Push 1 after the automated tests pass:

```bash
python3 scripts/run_virtual_push1.py --gui
```

On Windows, use `py -3 scripts/run_virtual_push1.py` (add `--gui` for the
interactive window). The runner assembles the pinned source, builds the
extension, verifies the package and executes the automated virtual bench.

## Areas that are not yet fully proven by the virtual bench

These are not silently treated as passed merely because the general command binding matrix succeeds.

| Area | Current evidence | Remaining work for stronger coverage |
|---|---|---|
| Sequencer scale/key-map and playhead behavior | Resolution, clip paging, Drum step insertion and all 64 Drum64 block coordinates are covered | Add stateful scale/root/octave mapping and assert playhead-follow/selected-step LED behavior across clip and scene transitions |
| Browser, mixer, track, device and clip edge cases | Some Device/Session/transport scenarios are asserted directly | Add stateful model assertions for empty banks, first/last item boundaries, unavailable actions and context switching |
| Swing desktop UI | Compiled as part of the virtual test sources; optional --gui launch path | Add a display-backed smoke test (for example under Xvfb) for window construction, event dispatch and clean release on close |


## Explicit limits (not software regressions)

The current virtual endpoint deliberately has no OS-level USB-MIDI device, native MIDI driver, physical footswitch circuit, analog pressure sensor, electrical/USB timing model or Push firmware process. The host is a deterministic API-17 proxy, not Bitwig Studio 4.3.10 itself. CI therefore proves the controller's tested protocol and API boundary, not undocumented Bitwig scheduler behavior, physical calibration, USB discovery on every computer, LCD glass appearance or firmware-specific electrical behavior.

Ordinary feature work should be validated by this virtual bench and its CI regression gate. Physical hardware should be reserved for exceptional qualification of the explicitly listed physical-only boundaries, not used as the routine development test loop.

## Merge protection requirement

The workflow's final check is named `quality-gate` and runs on pushes to `custom` and pull requests targeting it. Direct commits to `custom` are currently accepted. GitHub's branch metadata reports `protected: false`, although the protection summary still lists `quality-gate` as a required context; do not claim server-side merge enforcement from that inconsistent metadata.