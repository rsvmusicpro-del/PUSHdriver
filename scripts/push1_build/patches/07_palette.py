# Internal source-assembly patch batch. Executed in order by prepare_push1.py.
# PUSHdriver custom palette: 64 distinct, vivid Push 1 hardware colors.
# Every selected entry is an exact DEFAULT_PALETTE RGB triplet. The 64 menu
# slots are split into 32 high-saturation colors and 32 vivid, less forceful
# colors. No synthetic RGB, grayscale, pastel or low-output LED value is used.
import re

push_color_manager = root / "src/main/java/de/mossgrabers/controller/ableton/push/controller/PushColorManager.java"
push_color_source = push_color_manager.read_text()
palette_match = re.search(r"DEFAULT_PALETTE\s*=\s*\{([\s\S]*?)\n\s*\};", push_color_source)
if palette_match is None:
    raise SystemExit("Push 1 color palette: DEFAULT_PALETTE was not found")
push_rgb = re.findall(r"\{\s*0x([0-9A-Fa-f]+),\s*0x([0-9A-Fa-f]+),\s*0x([0-9A-Fa-f]+)\s*\}", palette_match.group(1))
if len(push_rgb) < 128:
    raise SystemExit(f"Push 1 color palette: expected at least 128 RGB entries, found {len(push_rgb)}")

# Push 1 stores fixed RGB output levels, not arbitrary 24-bit colors. Select
# only hardware entries with at least 160/255 output on one LED channel and
# at least 45% chroma; the checker independently enforces both constraints.
push1_hardware_indices = [72, 120, 60, 84, 9, 96, 126, 124, 13, 74, 85, 98, 75, 17, 122, 87, 25, 29, 77, 33, 78, 41, 79, 67, 69, 80, 49, 81, 53, 95, 57, 106, 4, 107, 108, 8, 109, 12, 113, 73, 110, 111, 86, 16, 88, 20, 24, 89, 28, 114, 32, 90, 36, 40, 91, 92, 44, 93, 116, 48, 94, 52, 82, 56]
if len(set(push1_hardware_indices)) != 64:
    raise SystemExit("Push 1 color palette: hardware indices must be unique")

push1_color_names = [
    ("DAW_COLOR_DARK_GRAY", "Deep Red"),
    ("DAW_COLOR_GRAY", "Scarlet"),
    ("DAW_COLOR_GRAY_HALF", "Flame Orange"),
    ("DAW_COLOR_LIGHT_GRAY", "Electric Orange"),
    ("DAW_COLOR_SILVER", "Tangerine"),
    ("DAW_COLOR_DARK_BROWN", "Burnt Amber"),
    ("DAW_COLOR_BROWN", "Golden Olive"),
    ("DAW_COLOR_DARK_BLUE", "Electric Yellow"),
    ("DAW_COLOR_PURPLE_BLUE", "Acid Yellow"),
    ("DAW_COLOR_PURPLE", "Neon Chartreuse"),
    ("DAW_COLOR_PINK", "Electric Lime"),
    ("DAW_COLOR_RED", "Laser Lime"),
    ("DAW_COLOR_ORANGE", "Lime"),
    ("DAW_COLOR_LIGHT_ORANGE", "Vivid Green"),
    ("DAW_COLOR_MOSS_GREEN", "Electric Green"),
    ("DAW_COLOR_GREEN", "Neon Green"),
    ("DAW_COLOR_COLD_GREEN", "Spring Green"),
    ("DAW_COLOR_BLUE", "Bright Mint"),
    ("DAW_COLOR_LIGHT_PURPLE", "Emerald"),
    ("DAW_COLOR_LIGHT_PINK", "Azure"),
    ("DAW_COLOR_ROSE", "Electric Blue"),
    ("DAW_COLOR_REDDISH_BROWN", "Cobalt Blue"),
    ("DAW_COLOR_LIGHT_BROWN", "Pure Blue"),
    ("DAW_COLOR_LIGHT_GREEN", "Indigo"),
    ("DAW_COLOR_BLUISH_GREEN", "Electric Indigo"),
    ("DAW_COLOR_GREEN_BLUE", "Ultramarine"),
    ("DAW_COLOR_LIGHT_BLUE", "Violet"),
    ("PUSH1_COLOR_28_MINT_GREEN", "Electric Magenta"),
    ("PUSH1_COLOR_29_SPRING_GREEN", "Hot Pink"),
    ("PUSH1_COLOR_30_DARK_TEAL", "Fuchsia"),
    ("PUSH1_COLOR_31_DEEP_TEAL", "Crimson"),
    ("PUSH1_COLOR_32_AQUA_MINT", "Coral"),
    ("PUSH1_COLOR_33_AQUA", "Vermilion"),
    ("PUSH1_COLOR_34_DEEP_AQUA", "Copper Orange"),
    ("PUSH1_COLOR_35_NIGHT_TEAL", "Apricot Orange"),
    ("PUSH1_COLOR_36_SKY_BLUE", "Golden Yellow"),
    ("PUSH1_COLOR_37_AZURE", "Lemon"),
    ("PUSH1_COLOR_38_DEEP_TEAL_BLUE", "Lime Yellow"),
    ("PUSH1_COLOR_39_MIDNIGHT_BLUE", "Chartreuse"),
    ("PUSH1_COLOR_40_CORNFLOWER", "Citrus Green"),
    ("PUSH1_COLOR_41_BLUE", "Leaf Green"),
    ("PUSH1_COLOR_42_NAVY", "Neon Lime"),
    ("PUSH1_COLOR_43_MIDNIGHT_NAVY", "Bright Chartreuse"),
    ("PUSH1_COLOR_44_PERIWINKLE", "Electric Green Light"),
    ("PUSH1_COLOR_45_ROYAL_BLUE", "Bright Green"),
    ("PUSH1_COLOR_46_DEEP_BLUE", "Spring Green Light"),
    ("PUSH1_COLOR_47_DARK_NAVY", "Fresh Green"),
    ("PUSH1_COLOR_48_VIOLET", "Mint Green"),
    ("PUSH1_COLOR_49_PURPLE", "Aqua Mint"),
    ("PUSH1_COLOR_50_DEEP_PURPLE", "Sea Green"),
    ("PUSH1_COLOR_51_DARK_PURPLE", "Turquoise"),
    ("PUSH1_COLOR_52_MAGENTA", "Sky Blue"),
    ("PUSH1_COLOR_53_FUCHSIA", "Cornflower Blue"),
    ("PUSH1_COLOR_54_DEEP_MAGENTA", "Azure Blue"),
    ("PUSH1_COLOR_55_DARK_MAGENTA", "Royal Blue"),
    ("PUSH1_COLOR_56_HOT_PINK", "Periwinkle Blue"),
    ("PUSH1_COLOR_57_PINK", "Periwinkle"),
    ("PUSH1_COLOR_58_DEEP_PINK", "Lavender Violet"),
    ("PUSH1_COLOR_59_WINE", "Purple"),
    ("PUSH1_COLOR_60_VERMILION", "Orchid"),
    ("PUSH1_COLOR_61_BURNT_ORANGE", "Magenta Pink"),
    ("PUSH1_COLOR_62_OCHRE", "Berry"),
    ("PUSH1_COLOR_63_OLIVE_GREEN", "Rose Pink"),
    ("PUSH1_COLOR_64_ROSE_EXTRA", "Rose Red"),
]

daw_color = root / "src/main/java/de/mossgrabers/framework/daw/DAWColor.java"
daw_source = daw_color.read_text()
enum_start = daw_source.index("{", daw_source.index("public enum DAWColor")) + 1
enum_end = daw_source.index("\n\n\n    private static final List", enum_start)
enum_entries = ['    /** Internal fallback color; not displayed in the 64-color menu. */\n    COLOR_OFF("Off", new ColorEx (0.5, 0.5, 0.5)),']
for index, (constant, display_name) in enumerate(push1_color_names):
    hardware_index = push1_hardware_indices[index]
    red, green, blue = (int(component, 16) for component in push_rgb[hardware_index])
    suffix = ";" if index == len(push1_color_names) - 1 else ","
    enum_entries.append(
        f'    /** Push 1 palette color {index}: {display_name}. */\n'
        f'    {constant}("{display_name}", new ColorEx ({red / 255.0:.12g}, {green / 255.0:.12g}, {blue / 255.0:.12g})){suffix}'
    )
daw_source = daw_source[:enum_start] + "\n" + "\n".join(enum_entries) + daw_source[enum_end:]
daw_color.write_text(daw_source)

# Register every menu color to its exact static Push 1 LED palette index.
registration_start = push_color_source.index("        this.registerColorIndex (DAWColor.COLOR_OFF,")
registration_end_marker = "        this.registerColorIndex (DAWColor.DAW_COLOR_LIGHT_BLUE, isPush2 ? 96 : 41);"
registration_end = push_color_source.index(registration_end_marker, registration_start) + len(registration_end_marker)
registration = (
    "        final int [] push1PaletteIndices = new int [] { 0, 72, 120, 60, 84, 9, 96, 126, 124, 13, 74, 85, 98, 75, 17, 122, 87, 25, 29, 77, 33, 78, 41, 79, 67, 69, 80, 49, 81, 53, 95, 57, 106, 4, 107, 108, 8, 109, 12, 113, 73, 110, 111, 86, 16, 88, 20, 24, 89, 28, 114, 32, 90, 36, 40, 91, 92, 44, 93, 116, 48, 94, 52, 82, 56 };\n"
    "        for (int i = 0; i < DAWColor.values ().length; i++)\n"
    "            this.registerColorIndex (DAWColor.values ()[i], push1PaletteIndices[i]);"
)
push_color_source = push_color_source[:registration_start] + registration + push_color_source[registration_end:]
push_color_manager.write_text(push_color_source)

# Keep COLOR_OFF as the internal no-color fallback, but expose only the 64
# chromatic hardware choices in the existing one-page Color view.
color_view = root / "src/main/java/de/mossgrabers/framework/view/ColorView.java"
replace_once(color_view,
    "        this.pages = 1 + (dawColors.length - 1) / this.pageSize;",
    "        this.pages = 1 + (dawColors.length - 2) / this.pageSize;",
    "ColorView excludes internal COLOR_OFF fallback from page count")
replace_once(color_view,
    "            padGrid.light (36 + i, index < dawColors.length ? dawColors[index].name () : IPadGrid.GRID_OFF);",
    "            padGrid.light (36 + i, index + 1 < dawColors.length ? dawColors[index + 1].name () : IPadGrid.GRID_OFF);",
    "ColorView excludes internal COLOR_OFF fallback from grid")
replace_once(color_view,
    "        if (color < dawColors.length)\n        {\n            final ColorEx entry = dawColors[color].getColor ();",
    "        if (color + 1 < dawColors.length)\n        {\n            final ColorEx entry = dawColors[color + 1].getColor ();",
    "ColorView excludes internal COLOR_OFF fallback from selection")


# Defer all Push 1 label shortening until the physical cell/block width is known.
# Keep the upstream ASCII transliteration and selected-item markers unchanged.
track_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/track/AbstractTrackMode.java"
replace_once(track_mode,
    "            final String n = StringUtils.shortenAndFixASCII (trackName, isSel ? 7 : 8);\n"
    "            d.setCell (3, i, isSel ? Push1Display.SELECT_ARROW + n : n);",
    "            final String n = de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fitAscii ((isSel ? Push1Display.SELECT_ARROW : \"\") + trackName, 8);\n"
    "            d.setCell (3, i, n);",
    "Push 1 track names truncate after selection marker")

device_layer_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceLayerMode.java"
replace_once(device_layer_mode,
    "            final String sendName = StringUtils.optimizeName (layerBank.getEditSendName (i), 12);",
    "            final String sendName = de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fit (layerBank.getEditSendName (i), 12);",
    "Push 1 layer send names use display fitter")
replace_once(device_layer_mode,
    "            final String n = StringUtils.shortenAndFixASCII (layer.getName (), layer.isSelected () ? 7 : 8);\n"
    "            display.setCell (3, i, layer.isSelected () ? Push1Display.SELECT_ARROW + n : n);",
    "            final String n = de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fitAscii ((layer.isSelected () ? Push1Display.SELECT_ARROW : \"\") + layer.getName (), 8);\n"
    "            display.setCell (3, i, n);",
    "Push 1 layer names truncate after selection marker")

session_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/SessionMode.java"
replace_once(session_mode,
    "                final String n = StringUtils.shortenAndFixASCII (scene.getName (8), isSel ? 7 : 8);\n"
    "                display.setCell (row, col, isSel ? Push1Display.SELECT_ARROW + n : n);",
    "                final String n = de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fitAscii ((isSel ? Push1Display.SELECT_ARROW : \"\") + scene.getName (), 8);\n"
    "                display.setCell (row, col, n);",
    "Push 1 scene names truncate after selection marker")
replace_once(session_mode,
    "                String name = slot.getName (8);",
    "                String name = slot.getName ();",
    "Push 1 clip names retain full source text")
replace_once(session_mode,
    "                display.setCell (row, col, StringUtils.shortenAndFixASCII (name, 8));",
    "                display.setCell (row, col, de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fitAscii (name, 8));",
    "Push 1 clip names use display fitter")

marker_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/MarkerMode.java"
replace_once(marker_mode,
    "                display.setCell (3, i, StringUtils.shortenAndFixASCII (getMarkerName (marker), 8));",
    "                display.setCell (3, i, de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fitAscii (getMarkerName (marker), 8));",
    "Push 1 marker names use display fitter")
replace_once(marker_mode,
    "            final String menuBottomName = StringUtils.shortenAndFixASCII (getMarkerName (marker), 12);",
    "            final String menuBottomName = de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fitAscii (getMarkerName (marker), 12);",
    "Push 1 marker menu names use display fitter")

browser_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceBrowserMode.java"
replace_once(browser_mode,
    "                selectedResult = selectedResult == null || selectedResult.isBlank () ? \"None\" : StringUtils.shortenAndFixASCII (selectedResult, 18);",
    "                selectedResult = selectedResult == null || selectedResult.isBlank () ? \"None\" : de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fitAscii (selectedResult, 18);",
    "Push 1 browser selected result uses display fitter")
replace_once(browser_mode,
    "                    String name = column.isEmpty () ? \"\" : StringUtils.shortenAndFixASCII (column.get ().getName (), 8);\n"
    "                    if (i == this.filterColumn)\n"
    "                        name = Push1Display.SELECT_ARROW + name;\n"
    "                    display.setCell (0, i, name).setCell (1, i, getColumnName (column));",
    "                    String name = column.isEmpty () ? \"\" : column.get ().getName ();\n"
    "                    if (i == this.filterColumn)\n"
    "                        name = Push1Display.SELECT_ARROW + name;\n"
    "                    display.setCell (0, i, de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fitAscii (name, 8)).setCell (1, i, getColumnName (column));",
    "Push 1 browser filter names use display fitter")
replace_once(browser_mode,
    "                        display.setBlock (i % 4, i / 4, (results[i].isSelected () ? Push1Display.SELECT_ARROW : \" \") + StringUtils.shortenAndFixASCII (results[i].getName (), 16));",
    "                        display.setBlock (i % 4, i / 4, de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fitAscii ((results[i].isSelected () ? Push1Display.SELECT_ARROW : \" \") + results[i].getName (), 17));",
    "Push 1 browser preset names use complete block width")
replace_once(browser_mode,
    "                    final String itemName = items[i].getName ();\n"
    "                    String text = (items[i].isSelected () ? Push1Display.SELECT_ARROW : \" \") + StringUtils.shortenAndFixASCII (itemName, 18) + \"                \";\n"
    "                    if (itemName != null && !itemName.isEmpty ())\n"
    "                    {\n"
    "                        final String hitStr = \"(\" + items[i].getHitCount () + \")\";\n"
    "                        text = text.substring (0, 17 - hitStr.length ()) + hitStr;\n"
    "                    }\n"
    "                    display.setBlock (i % 4, i / 4, text);",
    "                    final String itemName = items[i].getName ();\n"
    "                    final String hitStr = itemName == null || itemName.isEmpty () ? \"\" : \"(\" + items[i].getHitCount () + \")\";\n"
    "                    final String text = (items[i].isSelected () ? Push1Display.SELECT_ARROW : \" \")\n"
    "                        + de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fitAscii (itemName, 16 - hitStr.length ()) + hitStr;\n"
    "                    display.setBlock (i % 4, i / 4, text);",
    "Push 1 browser filter names preserve hit counts after ellipsis fitting")

note_view_select = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/NoteViewSelectMode.java"
replace_once(note_view_select,
    "                    value = (viewManager.isActive (VIEWS[i]) ? Push1Display.SELECT_ARROW : \"\") + StringUtils.optimizeName (view.getName (), 8);",
    "                    value = de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fit ((viewManager.isActive (VIEWS[i]) ? Push1Display.SELECT_ARROW : \"\") + view.getName (), 8);",
    "Push 1 note view names use complete cell width")
replace_once(note_view_select,
    "                    value = (viewManager.isActive (VIEWS_TOP[i]) ? Push1Display.SELECT_ARROW : \"\") + StringUtils.optimizeName (view.getName (), 8);",
    "                    value = de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fit ((viewManager.isActive (VIEWS_TOP[i]) ? Push1Display.SELECT_ARROW : \"\") + view.getName (), 8);",
    "Push 1 top note view names use complete cell width")

note_repeat = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/NoteRepeatMode.java"
old_mode_name = "StringUtils.optimizeName (mode.getName (), 8)"
new_mode_name = "de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fit (mode.getName (), 8)"
source = note_repeat.read_text ()
if new_mode_name not in source or old_mode_name in source:
    if source.count (old_mode_name) != 2:
        raise SystemExit ("Push 1 arpeggiator mode names: expected exactly two early-truncation call sites")
    note_repeat.write_text (source.replace (old_mode_name, new_mode_name))

note_mode = root / "src/main/java/de/mossgrabers/controller/ableton/push/mode/NoteMode.java"
replace_once(note_mode,
    "StringUtils.optimizeName (occurrence.getName (), 8)",
    "de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fit (occurrence.getName (), 8)",
    "Push 1 occurrence names use display fitter")
replace_once(note_mode,
    "StringUtils.optimizeName (occurrence.getName (), 9)",
    "de.mossgrabers.controller.ableton.push.controller.Push1DisplayText.fit (occurrence.getName (), 9)",
    "Push 1 occurrence menu names use display fitter")

# The layout helper owns all physical row widths; do not duplicate 68 here.
push_display = root / "src/main/java/de/mossgrabers/controller/ableton/push/controller/Push1Display.java"
replace_once(push_display,
    "Push1DisplayProtocol.encodeLine (row, Push1DisplayText.fit (text, 68))",
    "Push1DisplayProtocol.encodeLine (row, Push1DisplayLayout.fitRow (text))",
    "Push 1 row fitting uses shared geometry")
