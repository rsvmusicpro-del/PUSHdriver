import de.mossgrabers.controller.ableton.push.controller.Push1DisplayProtocol;

/** Standalone regression checks for the Push 1 LCD SysEx encoder. */
public final class Push1DisplayProtocolTest
{
    public static void main(final String[] args)
    {
        final String text = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz012345";
        if (text.length() != 68)
            throw new AssertionError("test fixture must be exactly 68 characters");

        final String[] headers =
        {
            "F0 47 7F 15 18 00 45 00",
            "F0 47 7F 15 19 00 45 00",
            "F0 47 7F 15 1A 00 45 00",
            "F0 47 7F 15 1B 00 45 00"
        };

        for (int row = 0; row < 4; row++)
        {
            final String encoded = Push1DisplayProtocol.encodeLine(row, text);
            final String[] bytes = encoded.split(" ");
            if (bytes.length != 77 || !bytes[0].equals("F0") || !bytes[bytes.length - 1].equals("F7"))
                throw new AssertionError("incorrect SysEx frame length or boundaries");
            if (!String.join(" ", java.util.Arrays.copyOfRange(bytes, 0, 8)).equals(headers[row]))
                throw new AssertionError("incorrect row header for row " + row);
            for (int i = 8; i < 76; i++)
            {
                final int value = Integer.parseInt(bytes[i], 16);
                if (value > 0x7F)
                    throw new AssertionError("non-seven-bit payload byte");
            }
        }

        final String unicode = "é" + text.substring(1);
        if (!Push1DisplayProtocol.encodeLine(0, unicode).contains(" 3F "))
            throw new AssertionError("non-ASCII input was not safely replaced");

        expectInvalid(-1, text);
        expectInvalid(4, text);
        expectInvalid(0, text.substring(1));
        expectInvalid(0, text + "x");
        expectInvalid(0, null);
        System.out.println("Push 1 LCD SysEx protocol tests passed.");
    }

    private static void expectInvalid(final int row, final String text)
    {
        try
        {
            Push1DisplayProtocol.encodeLine(row, text);
            throw new AssertionError("invalid LCD input was accepted");
        }
        catch (final IllegalArgumentException expected)
        {
            // Expected validation failure.
        }
    }
}
