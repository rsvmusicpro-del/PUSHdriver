# Push 1 behavior characterization (reference contract)

This document describes the behavior implemented on branch `custom` at the time the virtual MIDI bench was introduced. It is a characterization of the code and project-maintained hardware profile, not a claim that every path has been exercised on a physical Push 1.

## Runtime and compatibility boundary

- Upstream: DrivenByMoss 19.2.2, pinned to commit `013620c1330cdcf896e96ef8fdc8a2123e4fce6a`.
- Bitwig Extension API: 17; Java target: 17.
- The extension exposes one MIDI input and one MIDI output and does not claim a USB-HID device.
- Push 2 display/HID functionality is excluded from the packaged Push 1 extension.

## Physical input families

| Hardware family | MIDI representation / known mapping | Current responsibility |
|---|---|---|
| 8×8 pads | Note events; velocity and pressure-sensitive | Active Push view (note, drum, sequencer, session, etc.) |
| Main buttons | CC; exact IDs are inherited from PushControlSurface | Setup command bindings and active mode/view |
| In/Out | CC 62 / CC 63 | Device previous/next, parameter-page bank, Browser filter column; context-specific existing Session/sequencer paging is preserved |
| Four arrows | CC 44–47 | Previous/next item horizontally; previous/next scene vertically; Shift changes to page navigation; does not launch clips/scenes |
| Encoders | CC 71–79; touch notes 0–8 | Active mode's continuous controls and touch handling |
| Small encoders | CC 14–15; touch notes 9–10 | Existing Push command mappings |
| Touch strip | Pitch bend plus touch note 12 | Ribbon / pitchbend command according to configured mode |
| LCD | SysEx output, four rows | Four independent 68-character rows |

The full physical ID declarations live in the pinned upstream `PushControlSurface`; PUSHdriver-specific changes must not silently renumber these controls.

## Observable output contract

- LCD: four rows, 68 seven-bit character cells each. One complete row is one 77-byte SysEx frame:
  `F0 47 7F 15 [18..1B] 00 45 00 [68 data bytes] F7`.
- Short notifications overlay the rightmost 17-character LCD block while preserving the first 51 cells; longer notifications use the normal four-row display path.
- Pad/button LED feedback remains owned by the existing Push colour manager, views, modes and hardware-control bindings. No parallel LED state engine is introduced.
- MIDI output is emitted through Bitwig's API-17 `IMidiOutput` methods (CC, notes, aftertouch, pitch bend and SysEx).

## Characterized mode-specific adaptations

- **Navigation:** Left/Right use active-mode item selection, or item pages with Shift. Up/Down scroll scenes, or scene pages with Shift. Scene Play uses a 64-scene bank; ordinary Session navigation uses the track bank's scene bank. Arrow LED availability follows the same granularity as execution.
- **In/Out:** CC 62/63 navigate device/parameter pages in Device mode and filter columns in Browser mode. Rack-chain mode remains inactive because API 17 does not provide a safe selected-chain cursor. Existing Session/sequencer fallbacks are retained.
- **New:** the selected cursor track is checked for an available slot before playback is changed. If the track is playing, that track is stopped and a note clip is created with the inherited clip length and overdub rules. Other tracks are not stopped.
- **Note Repeat:** optional MIDI note-repeat services may be absent. Configuration knobs and Groove controls remain usable; only operations requiring the missing service are guarded.
- **Second row:** defaults to Mute.
- **Sequencer:** the live playhead follows the current edit page only when an existing note clip is present; missing clip/step state is guarded.
- **Firmware inquiry:** handled by the Push 1 firmware parser and its regression checks.

## Evidence and limits

Existing checks include structural Python regression checks, LCD protocol Java tests, package/SPI discovery, and a virtual-host LCD-output smoke test. Those checks do not yet constitute a full end-to-end simulation of every physical button through every mode transition into Bitwig's model. `VirtualPush1MidiBench` validates the API-17 transport boundary. `VirtualPush1Device` now emulates the hardware endpoint without USB or a native MIDI driver: 64 velocity-sensitive pads, CC buttons (including simultaneous Shift chords), nine main encoders, two small encoders, encoder-touch notes, ribbon touch/pitch bend, MIDI input delivery, LED note/CC feedback capture, and four decoded 68-character LCD rows. `VirtualPush1DeviceTest` checks these hardware/protocol behaviors without a physical Push 1.

Important boundary: the virtual device is a hardware emulator, not yet a complete Bitwig runtime emulator. The current automated scenarios verify event mapping, simultaneous button states, pad/encoder/ribbon transport, LCD framing and LED feedback. Existing repository regressions separately check navigation/Shift page semantics, In/Out routing, New clip workflow, Note Repeat, sequencer playhead and display. Those source checks do not yet replay every scenario against the actual PushControllerSetup/ModeManager with a deterministic Bitwig model. A full semantic integration harness needs an injectable controller-setup seam and API-17 model collaborators; it must not substitute a duplicate mock implementation and call that proof of production behavior.
