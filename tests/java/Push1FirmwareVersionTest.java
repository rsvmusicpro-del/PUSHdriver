import de.mossgrabers.controller.ableton.push.controller.Push1FirmwareVersion;
import de.mossgrabers.framework.daw.midi.DeviceInquiry;

public final class Push1FirmwareVersionTest
{
    public static void main(final String[] args)
    {
        final int[] response = validResponse();
        final DeviceInquiry inquiry = new DeviceInquiry(response);
        if (!inquiry.isValid())
            throw new AssertionError("A valid 35-byte Push 1 identity response was rejected");

        // The constructor must take a defensive copy of the callback-owned packet.
        response[10] = 99;
        final Push1FirmwareVersion version = Push1FirmwareVersion.parse(inquiry);
        response[10] = 2;
        if (version == null || version.major() != 42 || version.minor() != 35)
            throw new AssertionError("Incorrect Push 1 firmware version: " + version);

        if (new DeviceInquiry(null).isValid())
            throw new AssertionError("A null MIDI identity response must be rejected safely");

        // Truncated packets must never throw, even when they end before the identity header.
        for (int length = 0; length < 15; length++)
            if (new DeviceInquiry(new int[length]).isValid())
                throw new AssertionError("A truncated " + length + "-byte identity response was accepted");

        final int[] highBitResponse = inquiry.getData();
        highBitResponse[9] = 0x80;
        if (new DeviceInquiry(highBitResponse).isValid())
            throw new AssertionError("A SysEx packet containing an eight-bit payload byte must be rejected");

        final int[] negativeByteResponse = inquiry.getData();
        negativeByteResponse[9] = -1;
        if (new DeviceInquiry(negativeByteResponse).isValid())
            throw new AssertionError("A SysEx packet containing a negative byte must be rejected");

        final int[] exposed = inquiry.getData();
        exposed[10] = 99;
        final Push1FirmwareVersion unchanged = Push1FirmwareVersion.parse(inquiry);
        if (unchanged.major() != 42)
            throw new AssertionError("DeviceInquiry.getData() leaked its mutable internal buffer");

        if (Push1FirmwareVersion.parse(null) != null)
            throw new AssertionError("Null inquiry must not produce a firmware version");

        // All four firmware digits are decimal, not arbitrary seven-bit MIDI values.
        for (int index = 12; index <= 15; index++)
        {
            final int[] malformed = validResponse();
            malformed[index] = 10;
            if (Push1FirmwareVersion.parse(new DeviceInquiry(malformed)) != null)
                throw new AssertionError("Non-decimal firmware digit at response offset " + index + " was accepted");
        }

        // Family and member fields are not firmware digits and must not affect
        // the decoded version, even when they contain non-decimal seven-bit values.
        final int[] unusualModel = validResponse();
        unusualModel[8] = 0x7F;
        unusualModel[9] = 0x7E;
        unusualModel[10] = 0x7D;
        unusualModel[11] = 0x7C;
        final Push1FirmwareVersion modelIndependent = Push1FirmwareVersion.parse(new DeviceInquiry(unusualModel));
        if (modelIndependent == null || modelIndependent.major() != 42 || modelIndependent.minor() != 35)
            throw new AssertionError("Family/member bytes were incorrectly interpreted as firmware revision");

        // A generic 35-byte identity response from another manufacturer must never
        // be interpreted as Push 1 firmware merely because its version bytes are decimal.
        for (int index = 5; index <= 7; index++)
        {
            final int[] foreignManufacturer = validResponse();
            foreignManufacturer[5] = index == 5 ? 0x01 : 0x00;
            foreignManufacturer[6] = index == 6 ? 0x20 : 0x21;
            foreignManufacturer[7] = index == 7 ? 0x6B : 0x1D;
            if (Push1FirmwareVersion.parse(new DeviceInquiry(foreignManufacturer)) != null)
                throw new AssertionError("A foreign manufacturer identity was accepted at offset " + index);
        }

        // Extended non-standard replies may still use a one-byte manufacturer ID.
        // Family/member/revision offsets must follow the actual ID width, not packet length.
        final int[] extendedShortManufacturer = new int[33];
        extendedShortManufacturer[0] = 0xF0;
        extendedShortManufacturer[1] = 0x7E;
        extendedShortManufacturer[2] = 0x7F;
        extendedShortManufacturer[3] = 0x06;
        extendedShortManufacturer[4] = 0x02;
        extendedShortManufacturer[5] = 0x41;
        extendedShortManufacturer[6] = 0x11;
        extendedShortManufacturer[7] = 0x22;
        extendedShortManufacturer[8] = 0x33;
        extendedShortManufacturer[9] = 0x44;
        extendedShortManufacturer[10] = 1;
        extendedShortManufacturer[11] = 2;
        extendedShortManufacturer[12] = 3;
        extendedShortManufacturer[13] = 4;
        extendedShortManufacturer[32] = 0xF7;
        final DeviceInquiry extendedShortInquiry = new DeviceInquiry(extendedShortManufacturer);
        if (!extendedShortInquiry.isValid())
            throw new AssertionError("An extended reply with a valid one-byte manufacturer ID was rejected");
        if (!java.util.Arrays.equals(extendedShortInquiry.getManufacturer(), new int[] { 0x41 }))
            throw new AssertionError("Extended one-byte manufacturer ID was parsed with the wrong width");
        if (!java.util.Arrays.equals(extendedShortInquiry.getDeviceFamilyCode(), new int[] { 0x11, 0x22 })
            || !java.util.Arrays.equals(extendedShortInquiry.getDeviceFamilyMemberCode(), new int[] { 0x33, 0x44 })
            || !java.util.Arrays.equals(extendedShortInquiry.getRevisionLevel(), new int[] { 1, 2, 3, 4 }))
            throw new AssertionError("Extended one-byte manufacturer shifted identity fields incorrectly");

        final int[] invalidShortManufacturer = new int[15];
        invalidShortManufacturer[0] = 0xF0;
        invalidShortManufacturer[1] = 0x7E;
        invalidShortManufacturer[3] = 0x06;
        invalidShortManufacturer[4] = 0x02;
        invalidShortManufacturer[5] = 0x00;
        invalidShortManufacturer[14] = 0xF7;
        if (new DeviceInquiry(invalidShortManufacturer).isValid())
            throw new AssertionError("A truncated three-byte manufacturer identity was accepted");

        final int[] invalidLongManufacturer = new int[17];
        invalidLongManufacturer[0] = 0xF0;
        invalidLongManufacturer[1] = 0x7E;
        invalidLongManufacturer[3] = 0x06;
        invalidLongManufacturer[4] = 0x02;
        invalidLongManufacturer[5] = 0x41;
        invalidLongManufacturer[16] = 0xF7;
        if (new DeviceInquiry(invalidLongManufacturer).isValid())
            throw new AssertionError("A one-byte manufacturer with a three-byte identity length was accepted");

        final int[] shortResponse = new int[15];
        shortResponse[0] = 0xF0;
        shortResponse[1] = 0x7E;
        shortResponse[3] = 0x06;
        shortResponse[4] = 0x02;
        shortResponse[14] = 0xF7;
        if (Push1FirmwareVersion.parse(new DeviceInquiry(shortResponse)) != null)
            throw new AssertionError("A non-Push-1 identity response must not produce a firmware version");

        final int[] invalidHeader = validResponse();
        invalidHeader[0] = 0;
        if (Push1FirmwareVersion.parse(new DeviceInquiry(invalidHeader)) != null)
            throw new AssertionError("An invalid SysEx identity response must be rejected");

        final byte[] firstQuery = DeviceInquiry.createQuery();
        firstQuery[0] = 0;
        final byte[] secondQuery = DeviceInquiry.createQuery();
        if (secondQuery[0] != (byte) 0xF0 || secondQuery[secondQuery.length - 1] != (byte) 0xF7)
            throw new AssertionError("DeviceInquiry.createQuery() leaked its mutable static packet");

        System.out.println("Push 1 firmware inquiry regression tests passed.");
    }

    private static int[] validResponse()
    {
        final int[] response = new int[35];
        response[0] = 0xF0;
        response[1] = 0x7E;
        response[2] = 0x7F;
        response[3] = 0x06;
        response[4] = 0x02;
        response[5] = 0x00;
        response[6] = 0x21;
        response[7] = 0x1D;
        // Family/member bytes are deliberately distinct from the firmware.
        response[8] = 1;
        response[9] = 7;
        response[10] = 6;
        response[11] = 4;
        response[12] = 4;
        response[13] = 2;
        response[14] = 3;
        response[15] = 5;
        response[34] = 0xF7;
        return response;
    }
}
