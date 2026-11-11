// Copyright (C) 2026 PUSHdriver contributors
// Licensed under the same LGPLv3 terms as the adapted DrivenByMoss controller code.
package de.mossgrabers.controller.ableton.push.controller;

/**
 * Encodes the Push 1 four-line character LCD protocol.
 *
 * The Push 1 receives one 77-byte SysEx message per LCD row:
 * F0 47 7F 15 [18..1B] 00 45 00 [68 seven-bit characters] F7.
 */
public final class Push1DisplayProtocol
{
    private static final String[] LINE_HEADERS =
    {
        "F0 47 7F 15 18 00 45 00",
        "F0 47 7F 15 19 00 45 00",
        "F0 47 7F 15 1A 00 45 00",
        "F0 47 7F 15 1B 00 45 00"
    };

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    private static final int LINE_COUNT = 4;
    private static final int LINE_LENGTH = 68;

    private Push1DisplayProtocol()
    {
        // Utility class.
    }

    /**
     * Encode exactly one physical LCD row into the hexadecimal SysEx form
     * expected by Bitwig's IMidiOutput.sendSysex(String).
     *
     * Characters outside the Push 1's seven-bit MIDI range are replaced by
     * '?' so malformed UTF-16/Unicode text cannot corrupt the SysEx frame.
     *
     * @param row zero-based LCD row (0..3)
     * @param text exactly 68 display characters
     * @return a complete SysEx message, including F0 and F7
     */
    public static String encodeLine(final int row, final String text)
    {
        if (row < 0 || row >= LINE_COUNT)
            throw new IllegalArgumentException("Push 1 LCD row must be between 0 and 3");
        if (text == null || text.length() != LINE_LENGTH)
            throw new IllegalArgumentException("Push 1 LCD rows must contain exactly 68 characters");

        final StringBuilder message = new StringBuilder(230);
        message.append(LINE_HEADERS[row]);
        for (int i = 0; i < text.length(); i++)
        {
            final char value = text.charAt(i) <= 0x7F ? text.charAt(i) : '?';
            message.append(' ');
            message.append(HEX[(value >>> 4) & 0x0F]);
            message.append(HEX[value & 0x0F]);
        }
        message.append(" F7");
        return message.toString();
    }
}
