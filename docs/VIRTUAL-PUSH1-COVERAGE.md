# Virtual Push 1 acceptance coverage

This document describes what the software-only Push 1 bench actually drives and asserts. It separates protocol coverage, production-controller integration, semantic workflow scenarios, and behavior that requires the real Bitwig host or physical hardware.

## Test entry point and execution model

The CI entry point is `scripts/test_push1_extension.py`. It assembles the pinned Push 1 extension, compiles the Java virtual-device/runtime tests, and runs their unified acceptance entry point. The Python script is a build/test launcher; it is not a separate Python regression suite.

The runtime suite boots the assembled production `PushControllerSetup` with deterministic Bitwig Extension API 17 collaborators. It does not replace production modes, views, or commands with test implementations.

The virtual MIDI bench models both directions:

- Push 1 device input -> the production `IMidiInput` callback and registered hardware bindings.
- Pad note-on/note-off -> the currently active production view's `onGridNote`.
- Poly-aftertouch -> the active view's registered `executeAftertouchCommand`.
- Production MIDI/SysEx output -> the virtual device state and captured host output.

This distinction matters: protocol-only pad tests do not prove that the active production view handles those pads. Both layers are now exercised separately.

## Protocol and display coverage

| Surface / contract | Automated coverage | Confidence |
|---|---|---|
| 8x8 pad matrix, notes 36-99 | All 64 addresses; velocity 1-127; poly-aftertouch 0-127; press, pressure, release and reset | Exhaustive protocol values |
| Main and small encoders | All 9 main and 2 small controls; relative steps -63 through +63; zero delta emits no message | Exhaustive protocol values |
| Encoder touch | All 9 main and 2 small touch identities, press and release | Exhaustive identities |
| Ribbon | Touch/release and all 16,384 pitch-bend positions with exact LSB/MSB split | Exhaustive 14-bit values |
| Push buttons | Every declared Push 1 button CC, momentary press/release, Shift combinations | Exhaustive declared button map |
| RGB feedback | All 64 pads plus 8 lower-row controls; SysEx nibble decoding and invalid-address rejection | Exhaustive addresses |
| LCD | Four independent 77-byte row frames; all 4 x 68 logical characters; all 128 seven-bit character values; module and logical-cell boundaries; custom glyph preservation | Exhaustive logical character space |
| Invalid input | MIDI/SysEx ranges, malformed frames, invalid rows/addresses and invalid pad transitions | Boundary cases |
| Display formatting and firmware identity | Fixed-width LCD text, notification overlay, parameter/pan bars, Ableton firmware identity parsing, malformed identity rejection and DeviceInquiry defensive-copy behavior | Boundary and identity cases |
| API 17 MIDI adapter | CC, note, aftertouch, pitch bend and SysEx routing; channels 0 and 15; String and byte-array SysEx | Adapter contract |
| Packaged extension | Push 1-only entry point, API 17, Java 17 bytecode, required classes/resources, forbidden Push 2/foreign code and dependency resolution | Package gate |
| Reproducible assembly | Repeated generated-source assembly hash comparison | Deterministic assembly |

## Full production registration matrix

The Push 1 setup currently registers **50 modes** and **14 views**. The acceptance test checks that each expected entry exists, activates every mode, and drives the encoder/touch controls through the real production bindings. Each registered view receives the full 8x8 pad matrix through virtual MIDI note-on, pressure and note-off.

### Registered modes

- Track, Track Details, Volume, Pan, Crossfader, Master and temporary Master
- Send 1-8
- Device Parameters, Device Chains, Device Layer, Device Layer Volume, Device Layer Pan, Device Layer Details
- Device Layer Send 1-8
- Browser, Clip, Note, Frame, Groove, Record Arm, Accent, Scales, Scale Layout, Fixed, Ribbon, View Select, Automation, Transport, Markers, User, Configuration, Session, Session View Select, Note Repeat and Add Track

### Registered views

- Play, Chords, Piano, Program Change, Clip Length, Color
- Session, Sequencer, Poly Sequencer
- Drum, Drum 4, Drum 8, Scene Play and Drum 64

`RAINDROPS` is intentionally not in this list: it is excluded by the Push 1 assembly and is not part of the packaged Push 1 extension.

The runtime command matrix also exercises the production button, continuous-control, touch and pitch-bend bindings across the registered mode/view combinations. The separate mode/view sweeps prevent a recently modified feature from becoming the only part of the extension that receives input.

## Stateful functional scenarios

The virtual host model includes explicit state and assertions for these workflows:

| Workflow | Assertions |
|---|---|
| Transport | Play/stop model state and Play LED feedback |
| Track, Device and Session navigation | Track-bank item arrows, Shift+Track cursor swap, device item/page arrows, scene-bank vertical navigation and In/Out parameter-page movement |
| Mixer modes | Volume, Pan, Send 1, Master and Crossfader encoder touch/change actions reach their production parameters |
| Device/User parameters | Device parameter, layer volume/pan/send and User parameter encoders reach their production parameter objects |
| Session and Scene Play | Pad input launches a clip slot in Session and a scene in Scene Play |
| Device bank boundaries | Previous/next item and previous/next page availability at first and last boundaries |
| Device layers | Empty layer-bank safety and populated layer send-bank access |
| Browser | Row item replacement, next filter item, previous/next results, Shift content-type paging and preview toggle, encoder filter/result selection and Delete-to-reset |
| New clip | Search from selected slot, stop only the playing cursor track after a free slot is found, create clip, Shift+New overdub behavior, no-free-slot and no-cursor-track fallbacks |
| Note Repeat | Missing default MIDI input, late-created Note Repeat service, control updates and subsequent service removal |
| Sequencer | Existing-clip activation, step toggle on pad release and clip page navigation |
| Poly Sequencer | Play a note in the lower grid, then stamp the memorized note into an upper-grid step |
| Drum 4/8 | Pad selection mapping and step editing through the drum sequencer area |
| Drum 64 | 4x4 block mapping, octave bank scrolling, Select+pad selection, Mute+pad, Solo+pad, Delete+pad and Browse+pad |
| Aftertouch | Pad pressure reaches the active Play view aftertouch command and produces the configured MIDI output |

These are extension-side acceptance scenarios using a deterministic API-17 host model. They are not claims that the virtual host reproduces Bitwig's internal engine.

## CI and branch protection

The target branch is `custom`. Its GitHub branch protection requires the `quality-gate` status check. The Linux build and Intel macOS validation both run the unified virtual suite; the gate must pass on the exact proposed commit before it is merged.

Do not treat a previous green commit as evidence that a newer test revision passed. The latest pull-request commit is the acceptance candidate.

## Explicit validation limits

The virtual bench intentionally does not create an OS-level USB-MIDI port, load Push firmware, run Bitwig Studio 4.3.10 itself, or emulate its internal scheduler. It therefore cannot certify:

- USB discovery and driver behavior on every operating system
- Physical pad pressure calibration, electrical behavior or hardware timing
- Actual Push LCD glass appearance, firmware glyph rendering or LED brightness
- Undocumented behavior of a live Bitwig 4.3.10 process beyond the tested API 17 boundary

The Swing simulator is compiled and has an optional `--gui` launch path. A display-backed GUI smoke test is separate from the headless CI acceptance path.

Ordinary controller changes should be validated by this software bench and its required CI gate. Physical Push 1 qualification remains a targeted check for the explicitly listed hardware-only boundaries, not the routine development loop.
