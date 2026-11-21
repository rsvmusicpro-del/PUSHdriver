// Copyright (C) 2026 PUSHdriver contributors
// Licensed under the same LGPLv3 terms as the adapted DrivenByMoss controller code.
package de.mossgrabers.controller.ableton.push.controller;

/**
 * Semantic row composition for the Push 1's fixed-width LCD surface.
 *
 * <p>Views keep ownership of their content and interactions; this class gives
 * them shared, named layouts so labels, values, actions and context use the
 * same physical geometry without erasing mode-specific visual feedback.</p>
 */
public final class Push1DisplayComposition
{
    private static final int CONTEXT_VALUE_WIDTH = Push1DisplayLayout.COLUMNS - Push1DisplayLayout.BLOCK_WIDTH;

    private Push1DisplayComposition ()
    {
    }

    /** Compose eight encoder-aligned parameter labels. */
    public static String encoderLabels (final String... labels)
    {
        return composeCells (labels);
    }

    /** Compose eight encoder-aligned parameter values or graphical glyph fields. */
    public static String encoderValues (final String... values)
    {
        return composeCells (values);
    }

    /** Compose eight encoder-aligned action hints. */
    public static String actionHints (final String... actions)
    {
        return composeCells (actions);
    }

    /**
     * Compose a persistent context row: one module for the short label and
     * three modules for the selected object or contextual detail.
     */
    public static String contextRow (final String label, final String detail)
    {
        return Push1DisplayLayout.fitBlock (label)
            + Push1DisplayText.fit (detail, CONTEXT_VALUE_WIDTH);
    }

    private static String composeCells (final String... fields)
    {
        final StringBuilder row = new StringBuilder (Push1DisplayLayout.COLUMNS);
        for (int cell = 0; cell < Push1DisplayLayout.CELLS_PER_ROW; cell++)
            row.append (Push1DisplayLayout.fitCell (fields != null && cell < fields.length ? fields[cell] : "", cell));
        return Push1DisplayLayout.fitRow (row.toString ());
    }
}
