# Push 1 behavior characterization (reference contract)

This document describes the behavior implemented on branch `custom` at the time the virtual MIDI bench was introduced. It is a characterization of the code and project-maintained hardware profile, not a claim that every path has been exercised on a physical Push 1.

## Runtime and compatibility boundary

- Upstream: DrivenByMoss 19.2.2, pinned to commit `013620c1330cdcf896e96ef8fdc8a2123e4fce6a`.
- Bitwig Extension API: 17; Java target: 17.
- The extension exposes one MIDI input and one MIDI output and does not claim to emulate USB-HID.
- Push 2 display/HID functionality is excluded from the packaged Push 1 extension.

## Physical input families

| Hardware family | MIDI representation / known mapping | Current responsibility |
|---|---|---|
| 8×8 pads | Notes 36–99, velocity 1–127; polyphonic aftertouch while held | Active Push view (note, drum, sequencer, session, etc.) |
| Main buttons | Momentary CC, pressed 127 / released 0; IDs match the independent Push 1 CC map | Setup command bindings and active mode/view |
| In/Out | CC 62 / CC 63 | Device previous/next, parameter-page bank, Browser filter column; context-specific existing Session/sequencer paging is preserved |
| Four arrows | CC 44–47 | Previous/next item horizontally; previous/next scene vertically; Shift changes to page navigation; does not launch clips/scenes |
| Encoders | Relative two's-complement CC 71–79; touch notes 0–8, release as Note On velocity 0 | Active mode's continuous controls and touch handling |
| Small encoders | Relative two's-complement CC 14–15; touch notes 9–10, release as Note On velocity 0 | Existing Push command mappings |
| Touch strip | Pitch bend plus touch note 12; touch release is Note On velocity 0 | Ribbon / pitchbend command according to configured mode |
| LCD | SysEx output, four rows | Four independent 68-character rows |

The full physical ID declarations live in the pinned upstream `PushControlSurface`; PUSHdriver-specific changes must not silently renumber these controls. Independent CC mapping reference: `https://chor.ist/push/button-ccs.txt`. CC 62 is In and CC 63 is Out.

## Observable output contract

- LCD: four physical modules arranged horizontally; each module is four rows by 17 characters. The combined logical display is four rows by 68 seven-bit character cells. Each row has eight logical cells with alternating widths 9/8; each 17-character module maps to two adjacent cells. `Push1DisplayLayout` is the shared source of truth for production composition and virtual hardware coordinates. One complete row is one 77-byte SysEx frame:
  `F0 47 7F 15 [18..1B] 00 45 00 [68 data bytes] F7`.
- Short notifications overlay the rightmost 17-character LCD block while preserving the first 51 cells; longer notifications use the normal four-row display path.
- Pad/button LED feedback remains owned by the existing Push colour manager, views, modes and hardware-control bindings. No parallel LED state engine is introduced.
- MIDI output is emitted through Bitwig's API-17 `IMidiOutput` methods (CC, notes, aftertouch, pitch bend and SysEx). DrivenByMoss `AbstractMidiOutput` uses zero-based channel arguments: status = command + channel. Both String and byte-array SysEx output are supported by the virtual endpoint. LED feedback includes note/CC palette values and Push 1 RGB SysEx for the 64 pads plus the lower button row; the screen is decoded separately as four 68-character rows.

## Characterized mode-specific adaptations

- **Navigation:** Left/Right use active-mode item selection, or item pages with Shift. Up/Down scroll scenes, or scene pages with Shift. Scene Play uses a 64-scene bank; ordinary Session navigation uses the track bank's scene bank. Arrow LED availability follows the same granularity as execution.
- **In/Out:** CC 62/63 navigate device/parameter pages in Device mode and filter columns in Browser mode. Rack-chain mode remains inactive because API 17 does not provide a safe selected-chain cursor. Existing Session/sequencer fallbacks are retained.
- **New:** the selected cursor track is checked for an available slot before playback is changed. If the track is playing, that track is stopped and a note clip is created with the inherited clip length and overdub rules. Other tracks are not stopped.
- **Note Repeat:** optional MIDI note-repeat services may be absent. Configuration knobs and Groove controls remain usable; only operations requiring the missing service are guarded.
- **Second row:** defaults to Mute.
- **Sequencer:** the live playhead follows the current edit page only when an existing note clip is present; missing clip/step state is guarded.
- **Firmware inquiry:** handled by the Push 1 firmware parser and its regression checks.

## Virtual bench and regression contract

The virtual bench is split into three layers:

- `VirtualPush1Device`: physical protocol model, independent of USB/native MIDI drivers.
- `VirtualPush1MidiBench`: bidirectional API-17 `IMidiInput`/`IMidiOutput` adapter. It models Bitwig hardware binding dispatch for buttons, pads, relative encoders, touch notes and pitch bend; it routes extension LED and LCD output back to the device model.
- `VirtualPush1RuntimeTest`: boots the **real assembled `PushControllerSetup`**, production views/modes and production button/continuous commands against deterministic API-17 host, settings and model collaborators. It drives MIDI through the actual registered bindings rather than a duplicate controller implementation.

The runtime scenarios cover Device/Track/Volume/Clip modes, Note/Session view selection, Play/Record transport, Shift+Record launcher overdub, simultaneous Shift+navigation, pad note/aftertouch/release, signed relative encoder input, encoder/ribbon touch, ribbon pitch bend, and LED/LCD feedback. It also invokes DrivenByMoss' own setup test matrix for registered view/mode pairs, buttons (down/long/up), continuous controls and pitchbend commands.

The protocol suite now has two levels:

- `VirtualPush1DeviceTest` checks the hardware model's baseline mapping, state transitions, malformed input rejection and feedback decoding.
- `VirtualPush1DisplayLayoutTest` checks the exact four-module construction, all 272 global character positions, module-local coordinates, all eight 9/8-width cells per row, and preservation of Push-specific seven-bit glyph codes through the production LCD SysEx encoder and virtual hardware decoder.
- `VirtualPush1ProtocolExhaustiveTest` exhaustively walks all 64 pad addresses and pressure boundaries, every declared Push 1 button CC press/release, all 11 encoder/touch identities and signed movement limits, all 72 RGB output addresses (64 pads + 8 lower-row buttons), every LCD row and all 128 seven-bit character values, plus malformed/out-of-range SysEx and invalid control values.
- `VirtualPush1MidiBench` checks API output channels 0 and 15, String and byte-array SysEx, and distinguishes LCD frames from other Push 1 system SysEx.
- `VirtualPush1RuntimeTest` exercises the real controller setup and registered production bindings against deterministic virtual host/model collaborators.
- Existing Python regressions cover navigation/Shift semantics, In/Out, New clip workflow, Note Repeat, playhead safety, firmware parsing and package contents.

All of these tests run without a physical Push, native MIDI driver or running Bitwig Studio process. This is the required autonomous regression environment; the test doubles model the documented API boundary and the actual production controller setup, rather than claiming to be a full Bitwig host implementation.

## Compatibility and limits

- Source and tests compile against Bitwig Extension API 17 with Java 17.
- CI assembles the pinned DrivenByMoss 19.2.2 source, checks reproducibility, runs the test suite, packages the Push 1 extension, and smoke-tests extension discovery on Linux and Intel macOS.
- The runtime test uses the real controller setup and production commands, but its Bitwig host/model collaborators are deterministic proxies. It is not a running Bitwig Studio 4.3.10 process, does not exercise the host's native MIDI driver/scheduler, and cannot certify USB timing, analog pressure calibration, electrical behavior or firmware-specific quirks. Those are physical properties and cannot be truthfully certified by a software-only simulator.
- No production controller behavior is restructured by the virtual bench. Future behavior changes should add a failing scenario before the production fix and verify it green afterward.

## Regression protection

The workflow runs on pushes to `custom` and pull requests targeting `custom`. Its `quality-gate` job fails unless both Linux and Intel macOS validations succeed. For server-side enforcement, configure the `quality-gate` status check as required on the `custom` branch and disallow direct pushes/force pushes. The integration can update workflow files, but GitHub denied access to the legacy branch-protection endpoint; no repository rulesets are currently exposed, so I cannot truthfully claim that required-check enforcement has been enabled.

Local test sequence after assembling the extension:

```bash
python3 scripts/prepare_push1.py
mvn --batch-mode --no-transfer-progress clean package
python3 scripts/test_push1_extension.py
```

## External protocol references

- [Push 1 MIDI programming reference](https://chor.ist/push/midi.html): pad note/aftertouch map, relative encoder values, touch notes, button CC press/release, RGB pad SysEx and 4 × 68 LCD SysEx.
- [Ableton Push 1 user manual](https://www.ableton.com/en/manual/using-push-1/): Note/Session behavior, Shift, navigation, pads and aftertouch settings.
- [DrivenByMoss 19.2.2 compatibility notes](https://github.com/git-moss/DrivenByMoss-Documentation/blob/master/Bitwig/Changes.md): Bitwig 4.3+ requirement and Push 1-specific fixes in the pinned upstream release.
