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

- LCD: four rows, 68 seven-bit character cells each. One complete row is one 77-byte SysEx frame:
  `F0 47 7F 15 [18..1B] 00 45 00 [68 data bytes] F7`.
- Short notifications overlay the rightmost 17-character LCD block while preserving the first 51 cells; longer notifications use the normal four-row display path.
- Pad/button LED feedback remains owned by the existing Push colour manager, views, modes and hardware-control bindings. No parallel LED state engine is introduced.
- MIDI output is emitted through Bitwig's API-17 `IMidiOutput` methods (CC, notes, aftertouch, pitch bend and SysEx). DrivenByMoss `AbstractMidiOutput` uses zero-based channel arguments: status = command + channel. Both String and byte-array SysEx output are supported by the virtual endpoint.

## Characterized mode-specific adaptations

- **Navigation:** Left/Right use active-mode item selection, or item pages with Shift. Up/Down scroll scenes, or scene pages with Shift. Scene Play uses a 64-scene bank; ordinary Session navigation uses the track bank's scene bank. Arrow LED availability follows the same granularity as execution.
- **In/Out:** CC 62/63 navigate device/parameter pages in Device mode and filter columns in Browser mode. Rack-chain mode remains inactive because API 17 does not provide a safe selected-chain cursor. Existing Session/sequencer fallbacks are retained.
- **New:** the selected cursor track is checked for an available slot before playback is changed. If the track is playing, that track is stopped and a note clip is created with the inherited clip length and overdub rules. Other tracks are not stopped.
- **Note Repeat:** optional MIDI note-repeat services may be absent. Configuration knobs and Groove controls remain usable; only operations requiring the missing service are guarded.
- **Second row:** defaults to Mute.
- **Sequencer:** the live playhead follows the current edit page only when an existing note clip is present; missing clip/step state is guarded.
- **Firmware inquiry:** handled by the Push 1 firmware parser and its regression checks.

## Evidence and limits

Existing checks include structural Python regression checks, LCD protocol Java tests, package/SPI discovery, and a virtual-host LCD-output smoke test. Those checks do not yet constitute a full end-to-end simulation of every physical button through every mode transition into Bitwig's model. `VirtualPush1MidiBench` validates the API-17 transport boundary. `VirtualPush1Device` emulates the hardware endpoint without USB or a native MIDI driver: 64 velocity-sensitive pads with polyphonic aftertouch, the documented button CC groups (including simultaneous Shift chords), nine main encoders and two small encoders with signed relative movement, encoder-touch notes with Note-On-zero release, ribbon touch/pitch bend, MIDI input delivery, LED note/CC feedback capture, and four decoded 68-character LCD rows. `VirtualPush1MidiBench` connects this virtual surface bidirectionally to API-17 `IMidiInput`/`IMidiOutput` proxies: device actions invoke the registered extension callbacks, and extension output updates the virtual LEDs/LCD. `VirtualPush1DeviceTest` checks all 64 pad locations including aftertouch, button CC groups and Shift, signed relative encoder encoding, encoder/ribbon touch-release messages, ribbon pitch bend, LCD decoding using the production SysEx encoder, and LED feedback without a physical Push 1. `VirtualPush1MidiBench` also checks channel 0 and channel 15 output routing and both String and byte-array SysEx forms.

Important boundary: the virtual device is a hardware emulator, not yet a complete Bitwig runtime emulator. The current automated scenarios verify event mapping, simultaneous button states, pad/encoder/ribbon transport, LCD framing and LED feedback. `scripts/test_push1_scenarios.py` also checks that the production API-17 setup still wires the physical IDs to the real mode commands, registers the expected mode families, binds encoders/touch notes, and derives Play/Record LEDs from Bitwig transport state. Existing repository regressions separately check navigation/Shift page semantics, In/Out routing, New clip workflow, Note Repeat, sequencer playhead and display. These source guards plus the virtual endpoint do not yet replay every mode and playback scenario through the actual PushControllerSetup/ModeManager with a deterministic Bitwig model. A full semantic integration harness needs an injectable controller-setup seam and API-17 model collaborators; it must not substitute a duplicate mock implementation and call that proof of production behavior.
