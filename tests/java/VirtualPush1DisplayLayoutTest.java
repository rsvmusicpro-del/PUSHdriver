import de.mossgrabers.controller.ableton.push.controller.Push1DisplayLayout;
import de.mossgrabers.controller.ableton.push.controller.Push1DisplayProtocol;
import de.mossgrabers.controller.ableton.push.controller.Push1DisplayGlyphs;

/** Verifies the four physical LCD modules and every logical cell coordinate. */
public final class VirtualPush1DisplayLayoutTest
{
    private VirtualPush1DisplayLayoutTest () { }

    public static void main (final String [] args)
    {
        check (Push1DisplayLayout.ROWS == 4, "physical LCD row count");
        check (Push1DisplayLayout.MODULE_COUNT == 4, "physical LCD module count");
        check (Push1DisplayLayout.MODULE_ROWS == 4, "rows per physical module");
        check (Push1DisplayLayout.MODULE_COLUMNS == 17, "columns per physical module");
        check (Push1DisplayLayout.COLUMNS == 68, "global row width");
        check (Push1DisplayLayout.CELLS_PER_ROW == 8, "logical cell count");
        check (Push1DisplayLayout.BLOCKS_PER_ROW == 4, "logical block count");

        int nextColumn = 0;
        for (int cell = 0; cell < Push1DisplayLayout.CELLS_PER_ROW; cell++)
        {
            check (Push1DisplayLayout.cellStartColumn (cell) == nextColumn,
                "cell start coordinate " + cell);
            final int width = cell % 2 == 0 ? 9 : 8;
            check (Push1DisplayLayout.cellWidth (cell) == width, "physical cell width " + cell);
            nextColumn += width;
        }
        check (nextColumn == Push1DisplayLayout.COLUMNS, "cell widths exactly fill the LCD row");

        for (int module = 0; module < Push1DisplayLayout.MODULE_COUNT; module++)
        {
            check (Push1DisplayLayout.blockStartColumn (module) == module * 17,
                "module horizontal position " + module);
            check (Push1DisplayLayout.firstCellForBlock (module) == module * 2,
                "module-to-cell mapping " + module);
            check (Push1DisplayLayout.cellStartColumn (module * 2) == module * 17,
                "module left cell begins at module edge " + module);
            check (Push1DisplayLayout.cellStartColumn (module * 2 + 1) + 8 == (module + 1) * 17,
                "module right cell ends at module edge " + module);
        }

        final VirtualPush1Device device = new VirtualPush1Device (
            new VirtualPush1Device.MidiLink () {
                @Override public void shortMessage (final int status, final int data1, final int data2) { }
                @Override public void sysex (final String frame) { }
            }, null);

        for (int row = 0; row < Push1DisplayLayout.ROWS; row++)
        {
            final StringBuilder expected = new StringBuilder (Push1DisplayLayout.COLUMNS);
            for (int column = 0; column < Push1DisplayLayout.COLUMNS; column++)
                expected.append ((char) ((row * 19 + column) & 0x7F));

            device.receiveSysex (Push1DisplayProtocol.encodeLine (row, expected.toString ()));
            check (device.getLcdRow (row).equals (expected.toString ()), "complete LCD row " + row);

            for (int module = 0; module < Push1DisplayLayout.MODULE_COUNT; module++)
            {
                final int start = module * Push1DisplayLayout.MODULE_COLUMNS;
                final String moduleRow = expected.substring (start, start + 17);
                check (device.getLcdModuleRow (row, module).equals (moduleRow),
                    "physical module row " + row + "/" + module);
                check (device.getLcdBlock (row, module).equals (moduleRow),
                    "logical block aliases physical module " + row + "/" + module);
                check (device.getLcdModuleSnapshot (module)[row].equals (moduleRow),
                    "physical module snapshot " + module + "/" + row);
            }

            for (int cell = 0; cell < Push1DisplayLayout.CELLS_PER_ROW; cell++)
            {
                final int start = Push1DisplayLayout.cellStartColumn (cell);
                final int width = Push1DisplayLayout.cellWidth (cell);
                check (device.getLcdCell (row, cell).equals (expected.substring (start, start + width)),
                    "logical cell " + row + "/" + cell);
            }

            for (int column = 0; column < Push1DisplayLayout.COLUMNS; column++)
            {
                final int module = column / Push1DisplayLayout.MODULE_COLUMNS;
                final int moduleColumn = column % Push1DisplayLayout.MODULE_COLUMNS;
                check (Push1DisplayLayout.moduleForColumn (column) == module,
                    "column-to-module mapping " + column);
                check (Push1DisplayLayout.moduleColumn (column) == moduleColumn,
                    "column-to-module-local mapping " + column);
                check (Push1DisplayLayout.globalColumn (module, moduleColumn) == column,
                    "module-local-to-global mapping " + column);
                check (device.getLcdCharacter (row, column) == expected.charAt (column),
                    "raw character code " + row + "/" + column);
            }
        }

        // Push 1 custom glyph codes must survive the complete SysEx round trip.
        final int [] glyphs = { 2, 3, 4, 5, 6, 7, 9, 24, 30, 127 };
        final StringBuilder glyphRow = new StringBuilder ();
        for (int i = 0; i < Push1DisplayLayout.COLUMNS; i++)
            glyphRow.append ((char) glyphs[i % glyphs.length]);
        device.receiveSysex (Push1DisplayProtocol.encodeLine (0, glyphRow.toString ()));
        for (int column = 0; column < Push1DisplayLayout.COLUMNS; column++)
            check (device.getLcdCharacter (0, column) == glyphs[column % glyphs.length],
                "custom Push glyph survives LCD transport at column " + column);

        check (Push1DisplayGlyphs.WIDTH == 5 && Push1DisplayGlyphs.HEIGHT == 8,
            "firmware custom glyph pixel dimensions");
        final int [][] expectedGlyphs = {
            { 0x00, 0x04, 0x0E, 0x15, 0x04, 0x04, 0x00, 0x00 },
            { 0x00, 0x04, 0x04, 0x15, 0x0E, 0x04, 0x00, 0x00 },
            { 0x00, 0x1F, 0x00, 0x1F, 0x00, 0x1F, 0x00, 0x00 },
            { 0x18, 0x18, 0x18, 0x1B, 0x18, 0x18, 0x18, 0x00 },
            { 0x03, 0x03, 0x03, 0x1B, 0x03, 0x03, 0x03, 0x00 },
            { 0x1B, 0x1B, 0x1B, 0x1B, 0x1B, 0x1B, 0x1B, 0x00 },
            { 0x00, 0x00, 0x00, 0x1B, 0x00, 0x00, 0x00, 0x00 },
            { 0x00, 0x18, 0x1F, 0x11, 0x11, 0x1F, 0x00, 0x00 }
        };
        for (int glyph = 0; glyph < expectedGlyphs.length; glyph++)
        {
            final int [] actualRows = Push1DisplayGlyphs.bitmapRows (glyph);
            check (java.util.Arrays.equals (actualRows, expectedGlyphs[glyph]),
                "extracted firmware glyph bitmap " + glyph);
            for (int y = 0; y < Push1DisplayGlyphs.HEIGHT; y++)
                for (int x = 0; x < Push1DisplayGlyphs.WIDTH; x++)
                    check (Push1DisplayGlyphs.isPixelOn (glyph, x, y)
                        == ((expectedGlyphs[glyph][y] & (1 << (4 - x))) != 0),
                        "glyph pixel " + glyph + "/" + x + "/" + y);
        }
        final int [] defensiveGlyph = Push1DisplayGlyphs.bitmapRows (7);
        defensiveGlyph[1] = 0;
        check (Push1DisplayGlyphs.bitmapRows (7)[1] == 0x18, "glyph bitmaps are defensive copies");
        check (device.getLcdGlyphBitmap (0, 0) != null, "virtual device exposes programmable glyph bitmap");
        check (device.getLcdGlyphBitmap (0, 9) == null, "ROM glyphs remain firmware-owned");

        check (Push1DisplayLayout.fitRow ("short").length () == 68, "row fitter uses physical width");
        check (Push1DisplayLayout.fitBlock ("short").length () == 17, "module fitter uses physical width");
        check (Push1DisplayLayout.fitCell ("short", 0).length () == 9, "left cell fitter width");
        check (Push1DisplayLayout.fitCell ("short", 1).length () == 8, "right cell fitter width");

        final String simulatorLine = Push1DisplayLayout.separateModulesForSimulator (
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz012345");
        check (simulatorLine.length () == 71, "simulator row includes exactly three module separators");
        for (int module = 0; module < Push1DisplayLayout.MODULE_COUNT; module++)
        {
            final int simulatorStart = module * (Push1DisplayLayout.MODULE_COLUMNS + 1);
            final int physicalStart = module * Push1DisplayLayout.MODULE_COLUMNS;
            check (simulatorLine.substring (simulatorStart, simulatorStart + 17)
                .equals ("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz012345"
                    .substring (physicalStart, physicalStart + 17)),
                "simulator module segment " + module);
            if (module + 1 < Push1DisplayLayout.MODULE_COUNT)
                check (simulatorLine.charAt (simulatorStart + 17) == ' ',
                    "simulator module separator " + module);
        }

        expectInvalid (() -> Push1DisplayLayout.cellWidth (8));
        expectInvalid (() -> Push1DisplayLayout.cellStartColumn (-1));
        expectInvalid (() -> Push1DisplayLayout.firstCellForBlock (4));
        expectInvalid (() -> Push1DisplayLayout.blockStartColumn (-1));
        expectInvalid (() -> Push1DisplayLayout.moduleForColumn (68));
        expectInvalid (() -> Push1DisplayLayout.globalColumn (4, 0));
        expectInvalid (() -> device.getLcdModuleRow (4, 0));
        expectInvalid (() -> device.getLcdModuleRow (0, 4));
        expectInvalid (() -> device.getLcdCell (0, 8));
        expectInvalid (() -> device.getLcdCharacter (0, 68));

        System.out.println ("Virtual Push 1 four-module LCD geometry passed: 4 modules × 4 rows × 17 columns; 8 logical cells (9/8); all 272 character coordinates and custom glyphs.");
    }

    private static void check (final boolean condition, final String label)
    {
        if (!condition)
            throw new AssertionError ("Push 1 LCD layout mismatch: " + label);
    }

    private static void expectInvalid (final Runnable action)
    {
        try { action.run (); }
        catch (final IllegalArgumentException expected) { return; }
        throw new AssertionError ("Invalid Push 1 LCD coordinate was accepted");
    }
}
