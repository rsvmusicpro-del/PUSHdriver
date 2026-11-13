# PUSHdriver — Push 1 hardware and protocol profile

This document records the hardware facts used by the Push 1-only implementation. It deliberately separates published specifications, reverse-engineered protocol details, and facts that are not sufficiently documented to claim as exact.

## Physical controls relevant to the extension

The original Ableton Push has:

- an 8 × 8 matrix of 64 velocity- and pressure-sensitive RGB pads;
- 11 touch-sensitive rotary encoders;
- a four-line alphanumeric LCD;
- a touch strip (about 12 cm) and 24 navigation LEDs;
- dedicated buttons, two footswitch inputs, and USB connectivity.

The controller is USB-powered; the supplied external power adapter can increase display and LED brightness. Push 1 is controlled by the host computer and does not expose the Push 2 pixel-display USB/HID interface.

References:
- [Ableton — Using Push 1](https://www.ableton.com/en/manual/using-push-1/)
- [Ableton — Push minimum requirements](https://help.ableton.com/hc/fr-fr/articles/115000036770-Configuration-minimale-requise-pour-Push)
- [Push 1 hardware overview and published specifications](https://www.guitarcenter.com/Ableton/Push-Software-Controller-Instrument-1355760441050.gc)

## LCD: exact logical dimensions and known limits

The extension and the reverse-engineered Push 1 display protocol agree on these exact logical dimensions:

- **4 rows × 68 character positions** (272 character positions total);
- each row is divided into **four 17-character blocks**;
- the device accepts seven-bit character data;
- one SysEx message updates one complete row.

The four 17-character blocks are not arbitrary text columns: they correspond
to **four separate physical LCD modules placed side by side**. Each module
contains four rows × 17 character positions. The full display is therefore
a 4-module horizontal assembly, with a global coordinate system of row
0–3 and column 0–67. The virtual display exposes both coordinate systems:
module 0–3 with local column 0–16, and the combined 68-column row.

The inherited Push 1 display API also exposes eight logical cells per row.
Their physical character widths alternate **9, 8, 9, 8, 9, 8, 9, 8**.
Every adjacent pair (9 + 8) exactly fills one 17-character LCD module.
Thus a block/module maps to two logical cells, not to an independently
resizable panel. The extension and virtual device now use the same
`Push1DisplayLayout` constants and coordinate conversion methods, so module
positions, block widths, cell offsets and row dimensions cannot drift apart
silently.

The firmware's Push-specific glyph codes are seven-bit character values and
must be preserved as character codes through the LCD SysEx path. They are
not Unicode code points or ordinary ASCII substitutions on the physical
display. The simulator therefore stores and exposes raw character codes.

The reverse-engineered firmware data also provides the exact eight
programmable custom glyphs as **5 × 8 dot matrices** (five right-aligned bits
in each of eight row bytes): codes 0–7 are up arrow, down arrow, three rows,
slider-left, slider-right, slider-center, slider-empty and folder. These
bitmaps are now represented in `Push1DisplayGlyphs` and exposed by the
virtual device for pixel-level tests. Standard ROM glyphs (for example the
degree and division symbols) remain firmware-owned and are not guessed or
recreated by the project. The 5 × 8 dot matrix is a character-glyph size, not
a measurement of the LCD glass or its physical active area. Exact module
spacing, panel part number and physical dimensions remain unverified.

The original LCD is an **alphanumeric character LCD**, not a pixel-addressable colour display. Reverse-engineered firmware character data indicates 5 × 8 pixel glyph patterns for the character set, but this is not a vendor-published panel resolution. The exact LCD module part number, physical active-area dimensions in millimetres, controller IC revision, and glass technology have not been established from an authoritative Ableton service document. PUSHdriver therefore does not invent those measurements or attempt Push 2-style pixel rendering.

References:
- [Ableton forum — reverse-engineered Push LCD SysEx format](https://forum.ableton.com/viewtopic.php?t=193744)
- [Push 1 firmware character glyph extraction](https://gist.github.com/eeropic/cf679d65d3b7299668e1281ad0efcf5c)
- [Push MIDI reference — text display dimensions and chunking](https://chor.ist/push/midi.html)

## Push 1 MIDI protocol

Push 1 communicates through USB-MIDI. The implementation uses MIDI 1.0 message classes appropriate to each control:

- Note On/Off for note and pad events;
- Control Change for buttons, encoders, and LED feedback;
- pitch-bend/pressure-related messages for the touch strip and expressive controls;
- manufacturer-specific SysEx for the LCD and supported hardware configuration.

The LCD SysEx frame is:

`F0 47 7F 15 [row] 00 45 00 [68 data bytes] F7`

The row selector is `18`, `19`, `1A`, or `1B`. Each complete frame is 77 bytes: 8 header bytes, 68 seven-bit character bytes, and the `F7` terminator. Each row is sent independently. The payload must remain in the seven-bit MIDI data range.

References:
- [Push 1 LCD SysEx investigation](https://forum.ableton.com/viewtopic.php?t=193744)
- [Push MIDI message reference](https://chor.ist/push/midi.html)
- [Ableton — Push User Mode and MIDI ports](https://help.ableton.com/en-us/articles/209071249-Push-User-Mode-for-custom-MIDI-mappings)

## Directional navigation: Push 1 / Ableton parity without command collisions

Ableton documents the four arrows as context-sensitive navigation. In Note workflow, Left/Right move between tracks; Up/Down move by one scene, with workflow-dependent clip/scene launch behavior. In Session workflow, Left/Right move one track and Up/Down one scene; Shift expands the horizontal or vertical movement to an eight-track/eight-scene block.

PUSHdriver routes each physical arrow exactly once through `PushCursorCommand`. Its modifier semantics follow Ableton's documented Push 1 navigation contract in every mode:

- Left/Right without Shift select the previous/next item in the active mode. In Track mode this selects the adjacent track; in Device, Browser, parameter-bank and sequencer modes it delegates single-item navigation to that mode.
- Shift+Left/Right select the previous/next item-bank page (normally eight items/tracks).
- Up/Down without Shift scroll one scene at a time.
- Shift+Up/Down select the previous/next scene-bank page (normally eight scenes).
- Scene Play retains its larger 64-scene bank: without Shift it moves one scene, and with Shift it advances one 64-scene bank page. The normal Session bank retains its eight-scene page.
- Up/Down do not launch clips or scenes. This is intentionally non-destructive in Bitwig and avoids turning navigation into an unexpected transport action.

Arrow LED availability is calculated using exactly the same single-item/page rules as execution, preventing a lit arrow from advertising an unavailable action. The implementation uses the existing command dispatcher and API-17-compatible DrivenByMoss mode/scene interfaces; it introduces no MIDI message IDs, new button bindings, or parallel event loop.

## Second-row startup state

The Push 1's second row is its state-control row. PUSHdriver explicitly initializes this row to **Mute** at startup using the Bitwig-4.3-compatible `PushConfiguration.TrackState.MUTE` setting. This backports the later upstream Push 1 fix without importing newer Push 2/3 lock-state APIs. It prevents the second row from starting in the unset state and accidentally exposing the newer controller-generation behavior.

Reference:
- [Ableton — Using Push 1, Note and Session navigation](https://www.ableton.com/en/live-manual/12/using-push-1/)

## New button: Ableton workflow adapted safely to Bitwig

Ableton's Push 1 New button stops the selected clip and prepares a new recording. In Scene Workflow, Live can also copy the other playing clips into a new scene; holding Record while pressing New on an armed MIDI track invokes Capture MIDI.

The inherited DrivenByMoss 19.2.2 `NewCommand` already owns the dedicated `ButtonID.NEW` / `PUSH_BUTTON_NEW` binding. It creates and starts a note clip in the first available slot at or after the selected slot. A normal press enables overdub; Shift+New creates the clip without enabling overdub. Before this PUSHdriver change, it did not stop the cursor track's currently playing clip.

PUSHdriver now keeps that single native button binding and wraps the upstream command with `PushNewCommand`:

1. Resolve the cursor track and find an available slot before changing playback.
2. If no track or free slot exists, delegate to the upstream command so its existing user notification is preserved; playback is not changed.
3. If the selected slot is a playing Launcher clip, stop that cursor track before creating the new clip. If no selected Launcher clip is playing, skip the preliminary stop; the normal new-clip launch behavior still applies.
4. Create and start the new note clip using the upstream clip-length and overdub behavior.
5. Preserve Shift+New's no-overdub behavior and leave Record, Duplicate, Play, and all other button bindings untouched.

This deliberately scopes New to the cursor track. PUSHdriver does not automatically duplicate clips from other tracks into a new scene: that cross-track operation is not required to prepare a new Bitwig clip and could alter a playing arrangement unexpectedly. The Ableton-specific Record+New Capture MIDI chord is also not introduced, so the existing Record and Shift command paths remain collision-free.

## Architecture decisions in PUSHdriver

### Dedicated engine: justified

`Push1DisplayProtocol` owns the conversion from one logical LCD row to a complete Push 1 SysEx frame. `Push1DisplayLayout` is the shared geometry contract for the four physical LCD modules, 17-character blocks and alternating 9/8-character cells; production display composition and the virtual device use the same mapping. It enforces:

1. valid row indexes (0–3);
2. exactly 68 characters per row;
3. seven-bit-safe payload bytes (unsupported Unicode is replaced with `?`);
4. correct row-specific SysEx header and `F7` termination.

This separates hardware framing from display composition. `Push1Display` continues to own the existing four-row/eight-cell layout, value bars, menus, notifications, and character presentation. The shared `AbstractTextDisplay` continues to suppress unchanged rows, so the new engine does not add redundant MIDI traffic or a competing render loop.

### Additional engines: not justified

- **Pads and LEDs:** the existing Push colour manager, pad views, and MIDI mappings already own these responsibilities. A parallel LED engine would duplicate state and risk cross-mode colour regressions.
- **Sequencing:** the existing session, drum, note, and polyphonic sequencer views already own the relevant musical state and Bitwig interactions. A new sequencer engine would duplicate the framework's model.
- **Transport, device and mixer control:** these already flow through the DrivenByMoss controller model and Bitwig API adapter. No second control dispatcher is added.

The result is one isolated LCD protocol boundary, not an unnecessary stack of overlapping engines.

## Bitwig compatibility boundary

The build remains pinned to Bitwig Extension API 17 and Java 17. The Push 1 uses the MIDI adapter; Push 2 USB/HID display code is excluded. The source-to-class package verifier and the LCD protocol regression tests run in CI.

A successful build proves compilation, packaging, and the deterministic protocol checks. It does **not** prove physical LCD appearance, USB port naming on every operating system, pad velocity/pressure calibration, or timing on a real Push 1. Those require a hardware session with Bitwig Studio 4.3.10.


## In/Out controls: Ableton behavior adapted to Bitwig API 17

The two physical controls are the Push 1 CC 62 and CC 63 buttons, represented by
the upstream `PUSH_BUTTON_DEVICE_LEFT` and `PUSH_BUTTON_DEVICE_RIGHT` constants.
Ableton documents them as In/Out: in Device mode they navigate additional
devices and parameter banks; in Browser mode they shift the visible browser
hierarchy horizontally. Ableton also documents access to additional Rack
chains through these controls.

PUSHdriver keeps the physical bindings unchanged and adapts their actions to
the existing Bitwig 4.3 / Extension API 17 model:

- **Out / CC 62:** previous device in Device mode, or previous parameter-page
  bank when the parameter-page selector is active; previous filter column in
  Browser mode.
- **In / CC 63:** next device in Device mode, or next parameter-page bank when
  the parameter-page selector is active; next filter column in Browser mode.
- The browser's selected filter column and Push 1 LCD selection marker are
  updated together. Button LEDs reflect whether another item/column exists.
- In Session and sequencer contexts, the existing track-bank page and
  sequencer-page behavior is retained to avoid breaking the project's current
  Bitwig workflows. These contexts are outside Ableton's documented In/Out
  Device/Browser behavior.

Device selection and parameter-page paging use dedicated In/Out methods in
`DeviceParamsMode`. The ordinary Left/Right arrow methods and their LED
availability remain unchanged, so the parameter-page action cannot leak into
arrow navigation. Browser navigation uses the API-17 `IBrowser` filter-column
methods. In Rack-chain mode the buttons remain inactive: Extension API 17 does
not expose a reliable selected-chain cursor for safe previous/next navigation.
No MIDI IDs, extra event loop, or alternate controller dispatcher are introduced.


## Interactive virtual Push 1 simulator

The repository includes an optional desktop surface backed by the production
controller runtime. Build the extension, then run:

```sh
python3 scripts/test_push1_extension.py --gui
```

The GUI simulates pads, pressure/aftertouch, momentary buttons and Shift,
eleven encoder rotations and touch states, Ribbon contact/position, all four
LCD modules and LED feedback. Its MIDI monitor shows the messages sent by the
virtual hardware and the responses produced by PUSHdriver. The GUI is optional
and is not opened by headless CI.

The Bitwig host and model remain deterministic API-17 test proxies. This
simulator is not an OS MIDI loopback driver and cannot be selected as a
hardware MIDI port in a separate Bitwig process.
