# Push 1 LCD architecture and rendering contract

## Physical interface

The original Push 1 has four character LCD modules side by side. Each module is 17 columns by 4 rows, giving the controller a physical text surface of 68 columns by 4 rows. A module is not a separate display protocol endpoint: each physical row is transmitted as one 68-character SysEx payload.

The eight parameter cells are a logical partition of that geometry. Their widths alternate 9, 8, 9, 8, 9, 8, 9, 8 columns. Each adjacent pair exactly fills one 17-column module. The geometry is centralized in `Push1DisplayLayout`; display writers must not hard-code 8, 9, 17 or 68.

## One rendering pipeline for every view

The assembled `Push1Display` enforces the same formatter at each public text-writing seam:

| API | Normalization |
|---|---|
| `setCell(row, cell, text)` | `Push1DisplayLayout.fitCell`: transliterate first, fit to eight text columns, and retain the ninth separator column on even cells |
| `setBlock(row, module, text)` | `Push1DisplayLayout.fitBlock`: fit exactly 17 columns, then split at the physical 9/8 cell boundary |
| `setRow(row, text)` | `Push1DisplayLayout.fitRow`: normalize to exactly 68 columns before the base display stores the row |
| MIDI `writeLine` | `Push1DisplayLayout.fitRow` followed by the Push 1 row-specific SysEx encoder |
| Simulator conversion | The same 68-column fitter, followed by display-font substitutions including the one-column firmware ellipsis |

This is deliberately enforced at the display boundary, rather than relying on each mode to remember to truncate correctly. It covers inherited menu helpers and upstream views as well as the explicitly refined track, scene, clip, marker, browser, note-view, arpeggiator and occurrence labels.


## Shared semantic composition

`Push1DisplayComposition` names the common row patterns used by views and menus without forcing every mode into identical content:

- `encoderLabels` aligns parameter names to the eight physical encoder cells.
- `encoderValues` uses the same 9/8-column geometry for numbers and firmware glyph-based meters.
- `actionHints` aligns soft-action labels to the same encoder positions.
- `contextRow` reserves one 17-character module for a stable context label and the remaining 51 columns for the selected object or detail.

The common composer is now used by Note View Select, Session View Select, Scales, Fixed Length, Quantize, Note Repeat and Marker menus. Their selected-state arrows, menu-specific values and action semantics remain intact; each menu chooses its own content while sharing the same row construction rules.

All helpers return exactly 68 seven-bit-safe character positions. They preserve the established glyph pipeline: value bars and custom glyph codes remain available, while the simulator continues to rasterize the actual character codes. Views retain ownership of mode-specific information, live feedback and interactions; the shared composition layer standardizes placement rather than flattening visual identity.

## Pixel-level virtual LCD

The Swing simulator no longer delegates LCD text to the host operating-system font. Each of the four physical modules is painted as 17 independent 5×8 dot-matrix characters at six-dot horizontal pitch. The eight programmable glyphs (codes 0–7) use the Push 1 firmware bitmaps already checked into the project. The known low-code glyph map includes the accented Latin characters available in the extended reference font, degree, division, ellipsis, full block, directional arrows and selection bullet; characters whose exact bitmap is not present are visibly marked as approximate fallbacks in the simulator's per-character diagnostic tooltip. Printable character ROM glyphs are loaded from separately licensed HD44780U A00 and A02 5×7 reference bitmap sets in `tests/resources/fonts/`; their SIL Open Font License is included as `tests/resources/fonts/OFL-1.1.txt`. The eighth dot row is blank for these 5×7 ROM profiles. Select the simulator profile with `python3 scripts/run_virtual_push1.py --gui --lcd-rom=A00` (default) or `--lcd-rom=A02`. Push-specific codes 9, 24, 28, 29, 30, 31 and 127 have explicit virtual glyphs (degree, division, ellipsis, full block, left/right arrows and selection bullet).

The rasterizer is deterministic and independent of AWT fonts. Its test checks all 128 seven-bit character codes, 5-bit row bounds, the six-dot pitch, the 17-character module width, and byte-for-byte equality of the eight custom glyphs against the firmware bitmap table. The Swing smoke test paints the actual dot-matrix component in headless mode.

Font data attribution: [Darren Embry's display-module-fonts](https://github.com/dse/display-module-fonts), distributed under SIL Open Font License 1.1 (license included beside the font files). The character patterns are based on the HD44780U datasheet's A00/A02 ROM tables: [Hitachi HD44780U datasheet](https://dfimg.dfrobot.com/enshop/image/data/DFR0009/HD44780.pdf).

The A00 and A02 ROMs are documented HD44780U references, not confirmed Push 1 BOM identifications. HD44780-compatible controllers have A00/A02 and older ROM variants with slightly different characters. Both profiles are selectable without changing the MIDI/protocol model. Until the Push 1 LCD controller marking or a photographed glyph calibration set is available, the exact standard ROM profile remains a declared hardware uncertainty.

## Text and glyph rules

- Text is transliterated before width fitting, so expansions such as `Ü → Ue`, `ß → ss` and `→ → ->` cannot overflow a cell after truncation.
- Long labels use the Push firmware's single-cell ellipsis character (code `0x1C`) and retain context from both ends.
- Supplementary Unicode code points are replaced as one display cell; malformed surrogate halves are never sent as separate characters.
- Push-specific seven-bit glyphs are retained. Codes 0–7 are the eight programmable 5×8 glyphs; the controller also uses firmware glyphs for degree (9), division (24), ellipsis (28), full block (29), left/right arrows (30/31) and selection bullet (127).
- The simulator converts the firmware ellipsis to one visible Unicode ellipsis cell. It does not expand it to three ASCII dots and shift the module geometry.

## Values, bars and parameter feedback

The Push 1 parameter display has eight logical value cells. `Push1DisplayValueFormatter` bounds values to their declared range, renders half-step fill, places modulation markers without writing outside the eight cells, and renders pan symmetrically around the center (including odd-sized ranges). These formatters are tested independently of the display protocol.

The firmware glyph assignments used for value bars are preserved: empty (6), half-right (3), full (5) and half-left (4). They are not passed through ordinary Unicode transliteration.

## Refresh and bandwidth

The pinned `AbstractTextDisplay.flush()` already compares each completed row with its previous message and sends only changed rows. `forceFlush()` invalidates that cache so all rows are resent when a full refresh is required. PUSHdriver retains this proven row-level behavior rather than adding a second cache that could suppress a necessary redraw after a reconnect or forced refresh.

## Four-row composition and notifications

Modes continue to decide what information belongs on each of the four rows; the renderer guarantees that every resulting row is physically valid. Module writes stay within their 17-column module, cell writes stay within their 9/8 logical cell, and full-row writes occupy the complete 68-column surface. A full-row context (such as the selected device name) is rendered as a normal 68-column row; it does not paint over another row or use a notification overlay.

Notifications use the base full-display notification path; no notification is painted over a persistent row or module.

## Verification boundary

The virtual bench checks all four rows, all 68 columns, all 128 seven-bit character values, module and cell coordinates, custom glyph transport, value formatting, and production display writes across registered modes. The static menu/display contract additionally checks that the assembled Push 1 display overrides `setCell`, `setBlock`, `setRow`, simulator conversion and row transmission with the shared geometry helpers.

A virtual bench cannot prove LCD glass contrast, viewing angle, backlight aging, firmware-specific optical glyph rendering, or whether a particular physical unit has a damaged segment. Those checks still require the actual Push 1.
