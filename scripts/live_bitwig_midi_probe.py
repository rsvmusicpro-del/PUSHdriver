#!/usr/bin/env python3
"""Optional RtMidi virtual-port probe for a manually configured live Bitwig session.

This is a transport diagnostic, not proof that Bitwig loaded PUSHdriver or that
a physical Push 1 is emulated. The user must connect the two named ports in Bitwig.
"""
import argparse
import sys
import threading
import time


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--duration", type=float, default=30.0, help="Seconds to capture Bitwig MIDI output (1..600).")
    parser.add_argument("--cc", type=int, help="Optional CC number to send into Bitwig after the virtual ports are created.")
    parser.add_argument("--value", type=int, default=127, help="Value for --cc (default: 127).")
    parser.add_argument("--channel", type=int, default=0, help="MIDI channel 0..15 for --cc (default: 0).")
    parser.add_argument("--require-response", action="store_true", help="Return failure if Bitwig sends no MIDI response during the capture window.")
    args = parser.parse_args()

    if not 1.0 <= args.duration <= 600.0:
        parser.error("--duration must be between 1 and 600 seconds")
    if args.cc is not None and not 0 <= args.cc <= 127:
        parser.error("--cc must be between 0 and 127")
    if not 0 <= args.value <= 127:
        parser.error("--value must be between 0 and 127")
    if not 0 <= args.channel <= 15:
        parser.error("--channel must be between 0 and 15")

    try:
        import mido
        mido.set_backend("mido.backends.rtmidi")
    except (ImportError, OSError) as error:
        print("Optional live probe requires Mido and python-rtmidi: python3 -m pip install mido python-rtmidi", file=sys.stderr)
        print(f"MIDI backend error: {error}", file=sys.stderr)
        return 2

    received = []
    lock = threading.Lock()

    def on_message(message):
        with lock:
            received.append(message)
            index = len(received)
        print(f"Bitwig MIDI OUT [{index}]: {message!r}", flush=True)

    input_name = "PUSHdriver Bitwig Input"
    output_name = "PUSHdriver Bitwig Output"
    try:
        with mido.open_output(input_name, virtual=True) as to_bitwig:
            with mido.open_input(output_name, virtual=True, callback=on_message):
                print(f"Created virtual input for Bitwig: {input_name}")
                print(f"Created virtual output from Bitwig: {output_name}")
                print("In Bitwig, route the controller's MIDI input to the first port and MIDI output to the second.")
                print("This probe does not create a Push 1 device identity or guarantee the extension can be attached to virtual ports.")
                if args.cc is not None:
                    message = mido.Message("control_change", channel=args.channel, control=args.cc, value=args.value)
                    to_bitwig.send(message)
                    print(f"Sent to Bitwig: {message!r}", flush=True)
                deadline = time.monotonic() + args.duration
                while time.monotonic() < deadline:
                    time.sleep(0.02)
    except (OSError, RuntimeError) as error:
        print(f"Could not create/use RtMidi virtual ports: {error}", file=sys.stderr)
        return 2

    with lock:
        count = len(received)
    print(f"Captured {count} MIDI message(s) from Bitwig in {args.duration:.1f}s.")
    if args.require_response and count == 0:
        print("No MIDI response was observed; verify Bitwig port routing and controller activation.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
