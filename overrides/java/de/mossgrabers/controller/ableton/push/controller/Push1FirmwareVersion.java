// Copyright (C) 2026 PUSHdriver contributors
// Licensed under the same LGPLv3 terms as the adapted DrivenByMoss controller code.
package de.mossgrabers.controller.ableton.push.controller;

import de.mossgrabers.framework.daw.midi.DeviceInquiry;

/** Parses the Push 1 firmware version from its 35-byte identity response. */
public record Push1FirmwareVersion(int major, int minor)
{
    private static final int[] ABLETON_MANUFACTURER_ID = { 0x00, 0x21, 0x1D };

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

        // A 35-byte universal identity reply is not sufficient to identify a Push:
        // require Ableton's registered three-byte manufacturer ID before reading
        // the controller-specific firmware fields.
        final int[] manufacturer = inquiry.getManufacturer();
        if (manufacturer.length != ABLETON_MANUFACTURER_ID.length)
            return null;
        for (int index = 0; index < ABLETON_MANUFACTURER_ID.length; index++)
            if (manufacturer[index] != ABLETON_MANUFACTURER_ID[index])
                return null;

        // Universal Identity Reply layout: manufacturer (3 bytes), family (2),
        // member (2), then the four-byte software revision. The framework exposes
        // that field directly so family/member bytes can never be mistaken for firmware.
        final int[] revision = inquiry.getRevisionLevel();
        if (revision.length != 4)
            return null;
        // Push 1 encodes its major/minor revision as two decimal digit pairs.
        for (final int digit : revision)
            if (digit < 0 || digit > 9)
                return null;

        return new Push1FirmwareVersion(revision[1] + revision[0] * 10,
                                       revision[3] + revision[2] * 10);
    }
}
