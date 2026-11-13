# Push 1 behavior characterization and virtual acceptance contract

This document describes the Push 1 behavior preserved by PUSHdriver and the software-only evidence used to validate it. It distinguishes implementation behavior, virtual API-boundary tests, and physical behavior that cannot be certified without the real controller.

## Runtime and compatibility boundary

- Upstream: DrivenByMoss 19.2.2, pinned to commit `013620c1330cdcf896e96ef8fdc8a2123e4fce6a`.
- Bitwig Studio target: 4.3.10.
- Bitwig Extension API: 17; Java target: 17.
- The extension exposes MIDI input/output and does not claim to emulate USB-HID.
- Push 2 display/HID functionality and unrelated controller implementations are excluded from the packaged Push 1 extension.

## Physical input families

| Hardware family | MIDI representation / known mapping | Production responsibility |
|---|---|---|
| 8x8 pads | Notes 36-99, velocity 1-127; polyphonic aftertouch while held | Active Push view (Play, Piano, Drum, Sequencer, Session, etc.) |
| Main buttons | Momentary CC, pressed 127 / released 0; IDs match the independent Push 1 CC map | Production setup command bindings and active mode/view |
| In/Out | CC 62 / CC 63 | Device selection and parameter-page navigation in Device mode |
| Four arrows | CC 44-47 | Previous/next item horizontally; previous/next scene vertically; Shift changes to page navigation; does not launch clips/scenes |
| Encoders | Relative two's-complement CC 71-79; touch notes 0-8, release as Note On velocity 0 | Active mode's continuous controls and touch handling |
| Small encoders | Relative two's-complement CC 14-15; touch notes 9-10, release as Note On velocity 0 | Tempo and play-position command mappings |
| Touch strip | Pitch bend plus touch note 12; touch release is Note On velocity 0 | Ribbon / pitchbend command according to configured mode |
| LCD | SysEx output, four rows | Four independent 68-character rows |

Browser navigation uses the Browser mode's row selectors, encoders and Shift+arrow content-type paging. Do not describe CC 62/63 as Browser filter-column controls.

The full physical ID declarations live in the pinned upstream `PushControlSurface`; PUSHdriver-specific changes must not silently renumber these controls. Independent CC mapping reference: `https://chor.ist/push/button-ccs.txt`. CC 62 is In and CC 63 is Out.

## Observable output contract

- LCD: four physical modules arranged horizontally; each module is four rows by 17 characters. The combined logical display is four rows by 68 seven-bit character cells. Each row has eight logical cells with alternating widths 9/8; each 17-character module maps to two adjacent cells. `Push1DisplayLayout` is the shared source of truth for production composition and virtual hardware coordinates. One complete row is one 77-byte SysEx frame:
  `F0 47 7F 15 [18..1B] 00 45 00 [68 data bytes] F7`.
- Short notifications overlay the rightmost 17-character LCD block while preserving the first 51 cells; longer notifications use the normal four-row display path.
- Pad/button LED feedback remains owned by the existing Push colour manager, views, modes and hardware-control bindings. No parallel LED state engine is introduced.
- MIDI output is emitted through Bitwig API-17 MIDI interfaces (CC, notes, aftertouch, pitch bend and SysEx). DrivenByMoss `AbstractMidiOutput` uses zero-based channel arguments: status = command + channel. Both String and byte-array SysEx output are supported by the virtual endpoint. LED feedback includes note/CC palette values and Push 1 RGB SysEx for the 64 pads plus the lower button row; the screen is decoded separately as four 68-character rows.

## Characterized mode-specific adaptations

- **Navigation:** Left/Right use active-mode item selection, or item pages with Shift. Up/Down scroll scenes, or scene pages with Shift. Scene Play uses a 64-scene bank; ordinary Session navigation uses the track bank's scene bank. Arrow LED availability follows the same granularity as execution.
- **In/Out:** CC 62/63 navigate device selection and parameter pages in Device mode. Browser filter/result navigation is handled by Browser mode row selectors and encoders; Shift+Left/Right changes Browser content type. Rack-chain mode remains inactive where API 17 does not expose a safe selected-chain cursor.
- **New:** the selected cursor track is checked for an available slot before playback is changed. If the track is playing, that track is stopped and a note clip is created with the inherited clip length and overdub rules. Other tracks are not stopped.
- **Note Repeat:** optional MIDI note-repeat services may be absent. Configuration knobs and Groove controls remain usable; only operations requiring the missing service are guarded.
- **Second row:** defaults to Mute.
- **Sequencer:** the live playhead follows the current edit page only when an existing note clip is present; missing clip/step state is guarded.
- **Firmware inquiry:** handled by the Push 1 firmware parser and its regression checks.

## Virtual bench and regression contract

The virtual bench has three layers:

- `VirtualPush1Device`: physical protocol model, independent of USB/native MIDI drivers.
- `VirtualPush1MidiBench`: bidirectional API-17 `IMidiInput`/`IMidiOutput` adapter. It dispatches production hardware bindings, forwards pad note-on/note-off to the active production view's `onGridNote`, forwards poly-aftertouch to the active view's registered aftertouch command, and routes extension LED/LCD output back to the device model.
- `VirtualPush1RuntimeTest`: boots the real assembled `PushControllerSetup`, production views/modes and production button/continuous commands against deterministic API-17 host/model collaborators.

The acceptance matrix checks all 50 registered Push 1 modes and all 14 packaged views. It activates each mode, drives all encoder/touch identities, and sends the full pad matrix through each view. The detailed scenario inventory—including Browser, New Clip, Note Repeat, Sequencer/Poly Sequencer, Drum 4/8/64, Device bank boundaries, empty device layers, transport and aftertouch—is maintained in `docs/VIRTUAL-PUSH1-COVERAGE.md`.

Protocol and runtime test layers:

- `VirtualPush1FormattingAndFirmwareTest` checks fixed-width LCD text, notification overlays, parameter/pan bars, firmware identity parsing and DeviceInquiry defensive-copy behavior.
- `VirtualPush1DeviceTest` checks hardware mapping, state transitions, malformed input rejection and feedback decoding.
- `VirtualPush1DisplayLayoutTest` checks four-module construction, all 272 global character positions, module-local coordinates, all eight 9/8-width cells per row, preservation of Push-specific seven-bit glyph codes through production LCD SysEx encoding/decoding, and the eight extracted 5x8 custom-glyph bitmaps.
- `VirtualPush1ProtocolExhaustiveTest` exhaustively walks all 64 pad addresses and pressure/velocity values, every declared Push 1 button CC press/release, all 11 encoder/touch identities and signed movement limits, all 72 RGB output addresses, every LCD row and all 128 seven-bit character values, plus malformed/out-of-range SysEx and invalid control values.
- `VirtualPush1MidiBench` checks API output channels 0 and 15, String and byte-array SysEx, and distinguishes LCD frames from other Push 1 system SysEx.
- `VirtualPush1RuntimeTest` exercises the production controller setup and registered production bindings against deterministic virtual host/model collaborators.

`scripts/test_push1_extension.py` is the CI test launcher; it is not a separate Python regression suite. The executable behavior assertions live in the Java virtual-device/runtime tests.

All software tests run without a physical Push, native MIDI driver or running Bitwig Studio process. The host/model collaborators are deterministic API-17 proxies, not a complete Bitwig engine implementation. They validate the tested controller-side contract, not every undocumented host behavior.

## Compatibility, CI and branch protection

- Source and tests compile against Bitwig Extension API 17 with Java 17.
- CI assembles the pinned DrivenByMoss 19.2.2 source, checks reproducibility, runs the protocol/display/runtime suite, packages the Push 1 extension and validates Linux and Intel macOS.
- The final GitHub Actions job is named `quality-gate`.
- Branch `custom` is protected and requires `quality-gate` as a status check before merging. Changes are proposed through pull requests targeting `custom`; `main` is not a work branch.

A green CI run does not launch Bitwig Studio 4.3.10 itself, emulate the Bitwig scheduler/model in full, test native MIDI port discovery on every machine, or certify USB timing, analog pressure calibration, electrical behavior or firmware-specific quirks. Those are physical/host boundaries and cannot be truthfully certified by a software-only simulator.

## External protocol references

- [Push 1 MIDI programming reference](https://chor.ist/push/midi.html): pad note/aftertouch map, relative encoder values, touch notes, button CC press/release, RGB pad SysEx and 4 x 68 LCD SysEx.
- [Ableton Push 1 user manual](https://www.ableton.com/en/manual/using-push-1/): Note/Session behavior, Shift, navigation, pads and aftertouch settings.
- [DrivenByMoss 19.2.2 compatibility notes](https://github.com/git-moss/DrivenByMoss-Documentation/blob/master/Bitwig/Changes.md): Bitwig 4.3+ requirement and Push 1-specific fixes in the pinned upstream release.

## Local test sequence

```bash
python3 scripts/prepare_push1.py
mvn --batch-mode --no-transfer-progress clean package
python3 scripts/test_push1_extension.py
```
