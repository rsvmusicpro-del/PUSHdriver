# External test-harness research for the Bitwig 4.3.10 target

Research checked on 2026-11-14.

## Finding: the Bitwig harness concept is relevant, but the published project is not a drop-in dependency

The public [unthingable/bitwig-harness](https://github.com/unthingable/bitwig-harness) project demonstrates a useful architecture for controller-extension QA:

- an OSC control/observation plane for the DAW;
- a bidirectional virtual-MIDI proxy between a driver and the extension under test;
- state snapshots and change notifications for transport, tracks, devices, remote controls and clips;
- an external script/agent that sends MIDI and asserts both controller feedback and DAW state.

This is the right next architectural boundary for PUSHdriver because it tests the actual Bitwig host, not only a Java API-17 test double.

However, its published prerequisites are **Java 21, Bitwig Studio 5.3+ and Extension API 21**. PUSHdriver is intentionally pinned to **Java 17, Bitwig Studio 4.3.10 and Extension API 17**. Installing or compiling that project unchanged would move the target environment and could introduce API behavior that does not exist in the supported host. Therefore it is a reference implementation, not a dependency to import into the current build.

## Recommended adaptation

Build an optional, separate live-host profile around the same separation of concerns, keeping the production extension and deterministic CI unchanged:

1. A small API-17-compatible Bitwig companion extension exposes only supported state observations and commands over OSC.
2. A MIDI bridge connects the companion extension and PUSHdriver through a named virtual port.
3. A driver replays the same named scenarios against a running Bitwig 4.3.10 instance, then asserts both outgoing Push feedback and observed DAW state.
4. Every test records host version, API version, OS, port names, event timestamps, scenario ID and pass/fail evidence.
5. This profile is opt-in and is not reported as passing in headless CI unless a real Bitwig 4.3.10 process is connected.

The companion must not share implementation code with PUSHdriver's expected-value oracle. Otherwise the test risks validating the same incorrect assumption twice.

## Virtual MIDI transport options

[Mido](https://mido.github.io/mido/) with [RtMidi](https://mido.github.io/mido/backends/rtmidi.html) is a practical Python driver for sending, receiving and recording MIDI. RtMidi supports virtual ports on macOS and Linux; the documented backend does not provide virtual ports on Windows, where a dedicated loopback driver such as loopMIDI is needed. Keep transport selection behind a small adapter so the test scenario format remains OS-independent.

## Property and mutation testing

- [QuickTheories](https://github.com/quicktheories/QuickTheories) is a Java property-testing candidate because it supports reproducible generated values and automatic shrinking. Integrate it only after separating the current standalone runtime suite into discoverable tests and preserving the current seed corpus as compatibility fixtures.
- [PIT](https://pitest.org/quickstart/maven/) can measure whether tests detect deliberate mutations in production overrides. Start with a small critical class allowlist (Push LED mapping, button dispatch, display framing and relative encoder decoding); do not run mutation testing over the entire assembled upstream tree initially.

## Protocol and workflow references

- [Push 1 MIDI Programming Reference](https://chor.ist/push/midi.html) documents the User Port messages, including pad notes, polyphonic aftertouch, relative encoders and feedback.
- [Ableton Push 1 manual](https://www.ableton.com/en/manual/using-push-1/) supplies realistic controller workflows. It describes Ableton Live behavior, so use it to understand the hardware interaction, not as proof that Bitwig must reproduce Live-only DAW semantics.
- [Ableton Push User Mode MIDI-port guidance](https://help.ableton.com/en-us/articles/209071249-Push-User-Mode-for-custom-MIDI-mappings) confirms the separate User Port and the distinction between encoder-touch Note messages and encoder-turn CC messages.

## Decision

Keep the current API-17 deterministic suite as the mandatory merge gate. Add a separate API-17 live-host bridge before considering any external Bitwig harness dependency. Use property shrinking and mutation score as later evidence layers, not replacements for protocol or host integration tests.