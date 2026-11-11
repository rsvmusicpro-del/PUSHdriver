import de.mossgrabers.controller.ableton.push.controller.Push1FirmwareVersion;
import de.mossgrabers.framework.daw.midi.DeviceInquiry;

public final class Push1FirmwareVersionTest
{
    public static void main(final String[] args)
    {
        final int[] response = new int[35];
        response[0] = 0xF0;
        response[1] = 0x7E;
        response[2] = 0x7F;
        response[3] = 0x06;
        response[4] = 0x02;
        response[9] = 4;
        response[10] = 2;
        response[11] = 3;
        response[12] = 5;
        response[34] = 0xF7;

        final DeviceInquiry inquiry = new DeviceInquiry(response);
        if (!inquiry.isValid())
            throw new AssertionError("A valid 35-byte Push 1 identity response was rejected");

        final Push1FirmwareVersion version = Push1FirmwareVersion.parse(inquiry);
        if (version == null || version.major() != 42 || version.minor() != 53)
            throw new AssertionError("Incorrect Push 1 firmware version: " + version);

        final int[] exposed = inquiry.getData();
        exposed[10] = 99;
        final Push1FirmwareVersion unchanged = Push1FirmwareVersion.parse(inquiry);
        if (unchanged.major() != 42)
            throw new AssertionError("DeviceInquiry.getData() leaked its mutable internal buffer");

        if (Push1FirmwareVersion.parse(null) != null)
            throw new AssertionError("Null inquiry must not produce a firmware version");

        final int[] shortResponse = new int[15];
        shortResponse[0] = 0xF0;
        shortResponse[1] = 0x7E;
        shortResponse[3] = 0x06;
        shortResponse[4] = 0x02;
        shortResponse[14] = 0xF7;
        if (Push1FirmwareVersion.parse(new DeviceInquiry(shortResponse)) != null)
            throw new AssertionError("A non-Push-1 identity response must not produce a firmware version");

        response[0] = 0;
        if (Push1FirmwareVersion.parse(new DeviceInquiry(response)) != null)
            throw new AssertionError("An invalid SysEx identity response must be rejected");

        System.out.println("Push 1 firmware inquiry regression tests passed.");
    }
}
