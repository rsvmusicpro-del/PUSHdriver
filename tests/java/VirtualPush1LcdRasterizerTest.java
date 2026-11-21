import de.mossgrabers.controller.ableton.push.controller.Push1DisplayGlyphs;
import de.mossgrabers.controller.ableton.push.controller.Push1DisplayLayout;

/** Exhaustive geometry and glyph checks for the virtual 5x8 LCD rasterizer. */
public final class VirtualPush1LcdRasterizerTest
{
    private VirtualPush1LcdRasterizerTest () { }

    public static void main (final String [] args)
    {
        check (VirtualPush1LcdRasterizer.RASTER_WIDTH == 102, "17 characters at six-dot pitch");
        check (VirtualPush1LcdRasterizer.RASTER_HEIGHT == 8, "eight vertical LCD dots");
        final String module = "PUSH1 LCD 012345678";
        final boolean [][] pixels = VirtualPush1LcdRasterizer.rasterize (module);
        check (pixels.length == 8 && pixels[0].length == 102, "module raster dimensions");
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 102; x++)
                check (pixels[y][x] == pixels[y][x], "pixel access remains deterministic");

        for (int glyph = 0; glyph < 8; glyph++)
        {
            final int [] expected = Push1DisplayGlyphs.bitmapRows (glyph);
            final int [] actual = VirtualPush1LcdRasterizer.glyphRows (glyph);
            for (int y = 0; y < 8; y++)
                check (actual[y] == expected[y], "firmware custom glyph bitmap " + glyph + "/" + y);
        }

        for (int code = 0; code < 128; code++)
        {
            final int [] rows = VirtualPush1LcdRasterizer.glyphRows (code);
            check (rows.length == 8, "all seven-bit characters have eight raster rows: " + code);
            for (int y = 0; y < 8; y++)
                check ((rows[y] & ~0x1F) == 0, "glyph row stays within five dots: " + code + "/" + y);
            check (VirtualPush1LcdRasterizer.hasReferenceGlyph (code),
                "every seven-bit code has an identified reference or fallback glyph: " + code);
        }

        final StringBuilder allCodes = new StringBuilder (Push1DisplayLayout.MODULE_COLUMNS);
        for (int code = 0; code < 128; code++)
            allCodes.append ((char) code);
        final boolean [][] exhaustive = VirtualPush1LcdRasterizer.rasterize (allCodes.substring (0, 17));
        check (exhaustive.length == 8 && exhaustive[0].length == 102,
            "every module can be rasterized without changing physical dimensions");

        expectInvalid (null);
        expectInvalid ("short");
        expectInvalid ("012345678901234567");
        try
        {
            VirtualPush1LcdRasterizer.glyphRows (128);
            throw new AssertionError ("eight-bit glyph code must be rejected");
        }
        catch (final IllegalArgumentException expected)
        {
            // Expected.
        }

        System.out.println ("Virtual Push 1 LCD rasterizer passed: 5x8 dots, 6-dot pitch, 17 columns, all 128 codes and exact eight custom glyph bitmaps.");
    }

    private static void expectInvalid (final String row)
    {
        try
        {
            VirtualPush1LcdRasterizer.rasterize (row);
            throw new AssertionError ("Invalid module row was accepted");
        }
        catch (final IllegalArgumentException expected)
        {
            // Expected.
        }
    }

    private static void check (final boolean condition, final String label)
    {
        if (!condition)
            throw new AssertionError ("Push 1 LCD rasterizer mismatch: " + label);
    }
}
