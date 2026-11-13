// Copyright (C) 2026 PUSHdriver contributors
// Licensed under the same LGPLv3 terms as the adapted DrivenByMoss controller code.

import de.mossgrabers.controller.ableton.push.controller.Push1FirmwareVersion;
import de.mossgrabers.framework.daw.midi.DeviceInquiry;

import java.util.Arrays;


/** Regression tests for the Push 1 extended universal identity response. */
public final class VirtualPush1FirmwareVersionTest
{
    private VirtualPush1FirmwareVersionTest ()
    {
    }


    public static void main (final String [] args)
    {
        final int [] packet = createAbletonPush1Identity ();
        final DeviceInquiry inquiry = new DeviceInquiry (packet);
        require (inquiry.isValid (), "Valid extended Push 1 identity packet was rejected");
        require (Arrays.equals (inquiry.getManufacturer (), new int [] { 0x00, 0x21, 0x1D }),
            "Ableton's three-byte manufacturer ID was not decoded");
        require (Arrays.equals (inquiry.getRevisionLevel (), new int [] { 0, 3, 0, 7 }),
            "Firmware revision bytes were not read from the MIDI revision field");

        final Push1FirmwareVersion version = Push1FirmwareVersion.parse (inquiry);
        require (version != null, "Valid Push 1 firmware identity was rejected");
        require (version.major () == 3 && version.minor () == 7,
            "Push 1 firmware parser read family/member bytes instead of revision digits: "
                + version.major () + "." + version.minor ());

        final int [] wrongManufacturer = packet.clone ();
        wrongManufacturer[7] = 0x20;
        require (Push1FirmwareVersion.parse (new DeviceInquiry (wrongManufacturer)) == null,
            "A non-Ableton manufacturer was accepted as Push 1");

        final int [] malformedRevision = packet.clone ();
        malformedRevision[13] = 10;
        require (Push1FirmwareVersion.parse (new DeviceInquiry (malformedRevision)) == null,
            "A non-decimal firmware revision digit was accepted");

        final int [] standardLength = Arrays.copyOf (packet, 17);
        standardLength[16] = 0xF7;
        require (Push1FirmwareVersion.parse (new DeviceInquiry (standardLength)) == null,
            "A standard-length identity reply was accepted as the extended Push 1 identity");

        require (Push1FirmwareVersion.parse (null) == null,
            "A null identity inquiry was not rejected");

        System.out.println ("Virtual Push 1 firmware identity parser passed: Ableton ID, extended packet and revision digits.");
    }


    private static int [] createAbletonPush1Identity ()
    {
        final int [] data = new int [35];
        data[0] = 0xF0;
        data[1] = 0x7E;
        data[2] = 0x01;
        data[3] = 0x06;
        data[4] = 0x02;
        data[5] = 0x00;
        data[6] = 0x21;
        data[7] = 0x1D;

        // Device family/member bytes are deliberately non-decimal. They must
        // never be interpreted as firmware version digits.
        data[8] = 0x21;
        data[9] = 0x2A;
        data[10] = 0x31;
        data[11] = 0x3F;

        // Device-specific software revision: major 03, minor 07.
        data[12] = 0;
        data[13] = 3;
        data[14] = 0;
        data[15] = 7;
        data[34] = 0xF7;
        return data;
    }


    private static void require (final boolean condition, final String message)
    {
        if (!condition)
            throw new AssertionError (message);
    }
}
