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
- [Ableton — Push User Mode and MIDI ports](https://help.ableton.com/hc/en-us/articles/209071249-Push-User-Mode-for-custom-MIDI-mappings)

## Architecture decisions in PUSHdriver

### Dedicated engine: justified

`Push1DisplayProtocol` is the one additional protocol engine introduced by this change. It owns only the conversion from one logical LCD row to a complete Push 1 SysEx frame. It enforces:

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
