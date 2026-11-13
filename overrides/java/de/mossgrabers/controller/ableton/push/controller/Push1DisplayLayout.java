// Copyright (C) 2026 PUSHdriver contributors
// Licensed under the same LGPLv3 terms as the adapted DrivenByMoss controller code.
package de.mossgrabers.controller.ableton.push.controller;

/**
 * Physical character geometry of the original Ableton Push 1 display.
 *
 * The display is four side-by-side LCD modules. Each module has four rows of
 * seventeen character positions. The controller API exposes those modules as
 * one 68-character row. The eight logical parameter cells use alternating
 * widths of nine and eight characters; each pair is exactly one 17-character
 * physical module.
 *
 * Pixel dimensions are deliberately not invented: the MIDI protocol and
 * pinned upstream controller source define character geometry, not a
 * host-independent pixel raster.
 */
public final class Push1DisplayLayout
{
    public static final int MODULE_COUNT = 4;
    public static final int MODULE_ROWS = 4;
    public static final int MODULE_COLUMNS = 17;
    public static final int ROWS = MODULE_ROWS;
    public static final int COLUMNS = MODULE_COUNT * MODULE_COLUMNS;
    public static final int CELLS_PER_ROW = 8;
    public static final int BLOCKS_PER_ROW = MODULE_COUNT;
    public static final int BLOCK_WIDTH = MODULE_COLUMNS;
    public static final int CELL_TEXT_WIDTH = 8;

    private Push1DisplayLayout ()
    {
    }

    public static int cellWidth (final int cell)
    {
        checkCell (cell);
        return cell % 2 == 0 ? 9 : 8;
    }

    /** Global character offset of a logical cell within its 68-character row. */
    public static int cellStartColumn (final int cell)
    {
        checkCell (cell);
        return cell * CELL_TEXT_WIDTH + (cell + 1) / 2;
    }

    /** Index of a logical cell in AbstractTextDisplay's row-major cell array. */
    public static int cellIndex (final int row, final int cell)
    {
        checkRow (row);
        checkCell (cell);
        return row * CELLS_PER_ROW + cell;
    }

    public static int firstCellForBlock (final int block)
    {
        checkBlock (block);
        return block * 2;
    }

    public static int blockStartColumn (final int block)
    {
        checkBlock (block);
        return block * BLOCK_WIDTH;
    }

    public static int moduleForColumn (final int column)
    {
        checkColumn (column);
        return column / MODULE_COLUMNS;
    }

    public static int moduleColumn (final int column)
    {
        checkColumn (column);
        return column % MODULE_COLUMNS;
    }

    public static int globalColumn (final int module, final int moduleColumn)
    {
        checkModule (module);
        if (moduleColumn < 0 || moduleColumn >= MODULE_COLUMNS)
            throw new IllegalArgumentException ("Push 1 module column must be in [0,16]");
        return module * MODULE_COLUMNS + moduleColumn;
    }

    public static String fitCell (final String value, final int cell)
    {
        return Push1DisplayText.fit (value, CELL_TEXT_WIDTH) + (cellWidth (cell) == 9 ? " " : "");
    }

    public static String fitBlock (final String value)
    {
        return Push1DisplayText.fit (value, BLOCK_WIDTH);
    }

    public static String fitRow (final String value)
    {
        return Push1DisplayText.fit (value, COLUMNS);
    }

    private static void checkRow (final int row)
    {
        if (row < 0 || row >= ROWS)
            throw new IllegalArgumentException ("Push 1 LCD row must be in [0,3]");
    }

    private static void checkCell (final int cell)
    {
        if (cell < 0 || cell >= CELLS_PER_ROW)
            throw new IllegalArgumentException ("Push 1 LCD cell must be in [0,7]");
    }

    private static void checkBlock (final int block)
    {
        if (block < 0 || block >= BLOCKS_PER_ROW)
            throw new IllegalArgumentException ("Push 1 LCD module/block must be in [0,3]");
    }

    private static void checkModule (final int module)
    {
        checkBlock (module);
    }

    private static void checkColumn (final int column)
    {
        if (column < 0 || column >= COLUMNS)
            throw new IllegalArgumentException ("Push 1 LCD column must be in [0,67]");
    }
}
