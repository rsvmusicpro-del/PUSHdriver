# Virtual Push 1 validation coverage

This document is the acceptance inventory for the software-only Push 1 bench. It separates (1) protocol correctness, (2) extension-wide command dispatch, (3) stateful functional semantics, and (4) behavior that cannot be certified without a real Bitwig host or physical hardware.

The scope is the complete Push 1 controller extension assembled from DrivenByMoss 19.2.2, pinned at `013620c1330cdcf896e96ef8fdc8a2123e4fce6a`, targeting Bitwig Studio 4.3.10 / Extension API 17 / Java 17. This is not a test plan limited to recent overrides.

## 1. Protocol and hardware-surface contract — automated

| Surface | Automated coverage | Evidence |
|---|---|---|
| 8×8 pad matrix | All 64 note addresses (36–99), note-on/off, every velocity 1–127 and every poly-aftertouch value 0–127 on every pad, release/reset | `VirtualPush1ProtocolExhaustiveTest` |
| Main and small encoders | All 11 controls, relative two's-complement steps −63…+63, zero-step silence | `VirtualPush1ProtocolExhaustiveTest` |
| Encoder touch | All 9 main and 2 small touch identities, press/release | `VirtualPush1ProtocolExhaustiveTest` |
| Ribbon | Touch/release and all 16,384 14-bit pitch-bend positions, exact LSB/MSB split | `VirtualPush1ProtocolExhaustiveTest` |
| Buttons | Every declared Push 1 button CC, momentary press/release state, malformed values | `VirtualPush1ProtocolExhaustiveTest` |
| RGB feedback | All 64 pad and 8 lower-row addresses, nibble decoding and invalid address rejection | `VirtualPush1ProtocolExhaustiveTest` |
| LCD protocol | Four 77-byte row frames, all 272 positions, all 128 seven-bit character values, module/cell geometry and custom glyph preservation | `VirtualPush1DisplayLayoutTest`, `VirtualPush1ProtocolExhaustiveTest` |
| MIDI/SysEx validation | Invalid ranges, malformed frames, invalid row/address, invalid state transitions, output channels 0 and 15, String and byte-array SysEx | Device and MIDI bench tests |

## 2. Complete extension feature inventory — command-dispatch smoke coverage

The runtime bench boots the assembled production `PushControllerSetup`, not a parallel controller implementation. It invokes DrivenByMoss' `setup.test(...)` matrix over every registered Push 1 view × every registered Push 1 mode. For every pair, the matrix executes every available button command with DOWN, LONG and UP; every available continuous command at minimum, maximum and midpoint; every touch command with DOWN, LONG and UP; and every pitch-bend command at minimum, maximum and midpoint. Exceptions are collected by the virtual host and fail the test.

The Push 1 view inventory registered by the production setup is:

- Play / keyboard
- Chords
- Piano
- Program Change
- Clip Length
- Color
- Session
- Monophonic Sequencer
- Polyphonic Sequencer
- Drum
- Drum 4
- Drum 8
- Drum 64
- Scene Play

An independent acceptance assertion now checks that all 50 expected Push 1 modes and all 14 expected Push 1 views are registered, and that Push 2-only Setup/Info modes and the excluded Raindrops view are absent. This prevents the generic matrix from passing after an entire view or mode has silently disappeared.

The Push 1 mode inventory registered by the production setup is:

- Track, Track Details, Volume, Pan, Crossfader, Send 1–8
- Master, temporary Master
- Device Parameters, Device Chains, Device Layer, Device Layer Volume, Device Layer Pan, Device Layer Send 1–8, Device Layer Details, Browser
- Clip, Note, Frame, Groove, Quantize/Record Arm, Accent
- Scales, Scale Layout, Fixed Length, Ribbon, View Select
- Automation, Transport/Metronome, Markers, User, Configuration
- Session, Session View Select, Note Repeat, Add Track

An independent runtime counter asserts that the production matrix actually executes exactly 700 pairs (50 registered modes × 14 registered views) on each run. The matrix above is broad command-dispatch and exception smoke coverage across the complete registered extension surface. It does **not** by itself prove that each host-model operation has the correct musical or editing effect. The focused runtime assertions currently additionally verify startup, mode/view switching, Play/Record/Shift+Record transport behavior, Shift state, pad/aftertouch input, encoder/touch/ribbon dispatch, LED/LCD feedback, Device and Session arrow semantics, In/Out device navigation and parameter-page navigation.

## 3. Push 1-specific production behavior — regression coverage and semantic depth

The following behaviors are implemented in the production adaptation. The labels below describe the depth of automated proof, not whether the feature exists.

| Feature | Production implementation / current evidence | Remaining semantic proof in the virtual host |
|---|---|---|
| Push 1 firmware identity | `Push1FirmwareVersion` parser and hardened `DeviceInquiry` are production code | Unified suite tests valid Ableton identities, manufacturer mismatch, malformed digits/data, defensive copies and query immutability |
| LCD geometry, glyphs and framing | Shared production geometry/protocol and exhaustive logical character tests | Unified suite also tests fixed-width labels, seven-bit sanitization, notification overlay and eight-cell value/pan bars |
| Navigation and Shift paging | Production `PushCursorCommand`, page commands; Device and Session actions asserted | Exercise first/last boundaries, empty banks, all view-specific contexts and LED/action parity |
| Session scenes | Production `SessionView` | Stateful scene model asserts Scene select+launch, Delete+Scene removal and Duplicate+Scene duplication |
| In/Out and Browser | Production Device/Browser context handling; Device item and parameter-page actions asserted | Stateful Browser tests cover previous/next filter items, previous/next preset results, encoder-selected filter movement and Shift+Preview; empty columns, selection-marker rendering and LED availability remain additional edge cases |
| New clip | Production `PushNewCommand` wrapper | Unified runtime now uses a stateful cursor-track/slot model to assert selected-slot search, stop-before-create on the cursor track, normal overdub, Shift+New without overdub, no-slot fallback without stopping playback |
| Note Repeat | Production mode and null-safe optional service handling; package verifier checks safe LED path | Unified runtime tests both absent and available optional services and asserts Latch, Pressure, Free Running and Shuffle toggles; late service creation, knob/ribbon interaction and LED feedback remain additional scenarios |
| Drum / Drum4 / Drum8 / Drum64 | Production views and modes are registered and traversed by the complete command-dispatch matrix; Drum64 Select+Pad uses a stateful drum-device/bank model to assert 16-pad page alignment and primary-pad selection | Drum4/Drum8 note mapping, drum-note assignment, mute/solo/delete combinations, nested device selection and pad LED feedback |
| Sequencer / Poly Sequencer | Production views are registered and traversed by the complete command-dispatch matrix; mono sequencer playhead-follow assertions use an existing/empty note-clip model | Stateful clip/step/note model for step editing, selected step, manual page lock/unlock, loop gestures and Poly Sequencer musical behavior |
| Track, mixer, sends, master, device and clip modes | Production modes and command matrix are exercised | Stateful bank models for first/last items, empty banks, unavailable actions, selection changes and context transitions |
| Swing simulator | GUI can be launched from the same virtual device and MIDI bench | Display-backed CI smoke test for construction, event dispatch, repaint and clean close is not yet automated |

The firmware/display regression tests, New-clip scenarios, Session scene actions, Browser navigation/preview scenarios, absent/available Note Repeat service scenarios, Drum64 page/selection scenario and mono-sequencer playhead assertions are part of the same unified suite and are compiled and invoked by `scripts/test_push1_extension.py`. The inventory assertions also make the complete 50-mode / 14-view Push 1 registration scope explicit.

## 4. Build, package and CI contract

CI assembles the pinned upstream source, verifies deterministic assembly, builds with Java 17 against Bitwig Extension API 17, verifies the Push 1-only package and resources, runs the unified virtual bench on Linux and Intel macOS, and requires both platform jobs to pass through `quality-gate`.

The required status check is configured on protected branch `custom`. A successful CI run means all tests in this inventory's automated layers passed for that commit; it does not mean unmodelled Bitwig internals or physical properties were certified.

## 5. Explicit software-only limits

The virtual endpoint has no OS-level USB-MIDI device, native MIDI driver, physical footswitch circuit, analog pressure sensor, electrical/USB timing model or Push firmware process. The host is a deterministic API-17 proxy, not Bitwig Studio 4.3.10 itself. CI therefore cannot certify undocumented Bitwig scheduler/model behavior, real port discovery on every computer, physical calibration, LCD glass appearance or firmware-specific electrical behavior.

These limits do not make the virtual bench dependent on routine hardware testing. Ordinary feature work should use the virtual bench and required CI gate. Real Push 1 qualification is reserved for exceptional verification of the physical-only boundary.

## 6. Completion criteria

The software-only bench can be called functionally complete when:

1. Every registered view/mode pair and every bound control remains covered by the production command-dispatch matrix.
2. Each user-visible workflow in Sections 2 and 3 has stateful host-model assertions for its normal path, boundary cases, unavailable services and context changes.
3. The firmware, display, protocol, adapter, package and reproducibility regressions run from one documented CI entry point.
4. The Swing GUI has an automated display-backed smoke test, or is explicitly marked as a manual diagnostic tool rather than an automated CI acceptance test.
5. Linux and Intel macOS CI both pass on the resulting commit.

No software-only test can turn criterion 5 into a physical hardware certification; the limits in Section 5 remain explicit.
