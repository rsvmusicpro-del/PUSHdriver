// Copyright (C) 2026 PUSHdriver contributors
// Licensed under the same LGPLv3 terms as the adapted DrivenByMoss controller code.

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

/**
 * Exports deterministic visual evidence from the production Push 1 LCD path.
 *
 * <p>Each image contains all four physical modules and all four rows, rendered
 * through the same reference glyph rasterizer as the interactive simulator.
 * A companion text file preserves the exact seven-bit character codes.</p>
 */
public final class VirtualPush1DisplaySnapshots
{
    private static final int DOT_SCALE = 3;
    private static final int MODULE_GAP = 15;
    private static final int ROW_GAP = 12;
    private static final Color BACKGROUND = new Color (29, 34, 27);
    private static final Color LIT_DOT = new Color (177, 210, 130);
    private static int captureCount;

    private VirtualPush1DisplaySnapshots ()
    {
    }

    /** Write one full-display PNG and its lossless character-code companion. */
    public static void capture (final String category, final String name, final VirtualPush1Device device)
    {
        final String configured = System.getProperty ("push1.display.snapshots");
        if (configured == null || configured.isBlank ())
            return;

        final String safeName = (category + "-" + name).replaceAll ("[^A-Za-z0-9._-]", "_");
        final Path directory = Paths.get (configured);
        final int moduleWidth = VirtualPush1LcdRasterizer.RASTER_WIDTH * DOT_SCALE;
        final int moduleHeight = VirtualPush1LcdRasterizer.RASTER_HEIGHT * DOT_SCALE;
        final int width = moduleWidth * 4 + MODULE_GAP * 3;
        final int height = moduleHeight * 4 + ROW_GAP * 3;
        final BufferedImage image = new BufferedImage (width, height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = image.createGraphics ();
        final StringBuilder codes = new StringBuilder ();

        try
        {
            graphics.setColor (BACKGROUND);
            graphics.fillRect (0, 0, width, height);
            for (int row = 0; row < 4; row++)
            {
                final String rawRow = device.getLcdRow (row);
                if (rawRow.length () != 68)
                    throw new AssertionError ("Cannot snapshot an incomplete Push 1 LCD row " + row);
                codes.append ("row ").append (row).append (":");
                for (int column = 0; column < rawRow.length (); column++)
                    codes.append (String.format (" %02X", (int) rawRow.charAt (column)));
                codes.append ('\n');

                for (int module = 0; module < 4; module++)
                {
                    final String moduleRow = device.getLcdModuleRow (row, module);
                    final boolean [][] pixels = VirtualPush1LcdRasterizer.rasterize (moduleRow);
                    final int originX = module * (moduleWidth + MODULE_GAP);
                    final int originY = row * (moduleHeight + ROW_GAP);
                    graphics.setColor (LIT_DOT);
                    for (int y = 0; y < pixels.length; y++)
                        for (int x = 0; x < pixels[y].length; x++)
                            if (pixels[y][x])
                                graphics.fillRect (originX + x * DOT_SCALE, originY + y * DOT_SCALE,
                                    DOT_SCALE, DOT_SCALE);
                }
            }

            Files.createDirectories (directory);
            final Path png = directory.resolve (safeName + ".png");
            final Path txt = directory.resolve (safeName + ".txt");
            if (!ImageIO.write (image, "png", png.toFile ()))
                throw new IOException ("No PNG writer is available");
            Files.writeString (txt, codes.toString (), StandardCharsets.UTF_8);
            captureCount++;
        }
        catch (final IOException error)
        {
            throw new IllegalStateException ("Cannot export Push 1 display snapshot " + safeName, error);
        }
        finally
        {
            graphics.dispose ();
        }
    }

    /** Number of complete PNG/text snapshot pairs exported in this JVM. */
    public static int captureCount ()
    {
        return captureCount;
    }
}
