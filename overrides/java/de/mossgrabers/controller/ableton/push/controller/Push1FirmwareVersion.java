// Copyright (C) 2026 PUSHdriver contributors
// Licensed under the same LGPLv3 terms as the adapted DrivenByMoss controller code.
package de.mossgrabers.controller.ableton.push.controller;

import de.mossgrabers.framework.daw.midi.DeviceInquiry;

/** Parses the Push 1 firmware version from its 35-byte identity response. */
public record Push1FirmwareVersion(int major, int minor)
{
    /**
     * Parse the firmware version from a valid Push 1 identity response.
     *
     * @param inquiry The MIDI identity response
     * @return The parsed version, or null for an invalid or unsupported response
     */
    public static Push1FirmwareVersion parse(final DeviceInquiry inquiry)
    {
        if (inquiry == null || !inquiry.isValid())
            return null;

        final int[] data = inquiry.getData();
        if (data.length != 35)
            return null;

        // Push 1 reports major and minor versions as two decimal digits each.
        // Reject malformed seven-bit values rather than displaying impossible versions.
        for (int index = 9; index <= 12; index++)
            if (data[index] < 0 || data[index] > 9)
                return null;

        return new Push1FirmwareVersion(data[10] + data[9] * 10,
                                       data[12] + data[11] * 10);
    }
}
