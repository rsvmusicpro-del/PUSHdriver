# PUSHdriver virtual development and validation strategy

## Target contract

The test target is the original Ableton Push 1, Bitwig Studio 4.3.10, Bitwig Extension API 17, Java 17 and the pinned DrivenByMoss 19.2.2 source revision. The extension remains MIDI-only. Push 2 HID/display emulation is not a valid substitute for this hardware target.

## Why a single simulator is not enough

A credible station needs independent evidence at several boundaries. A software model can validate controller-side MIDI and API contracts, but it cannot prove undocumented Bitwig scheduling, USB driver behavior, electrical pressure calibration, firmware timing or the optical appearance of a particular LCD module.

PUSHdriver therefore separates five test layers:

1. **Reference protocol oracle** — independent expected Push 1 notes, CCs, relative encoder encoding, touch identities, pitch-bend ranges, RGB SysEx and LCD framing. Expectations must not be calculated from the same production constants that are under test.
2. **Virtual endpoint** — the 8×8 pad grid, aftertouch, momentary buttons, 11 encoders and touch identities, ribbon pitch bend, RGB feedback and four 68-character LCD rows. It models the controller's MIDI contract, not the DAW.
3. **Production extension integration** — the assembled `PushControllerSetup`, actual registered MIDI bindings, production commands, views and modes, with explicit stateful API-17 host collaborators.
4. **Scenario and state-machine exploration** — hand-authored musical workflows plus seeded generated event sequences. Every generated failure includes a reproducible seed, view and recent event trace.
5. **External qualification** — optional live Bitwig/virtual-MIDI and physical-device checks. These are reported separately from deterministic CI and never silently promoted to a software-only pass.

## New scenario engine

The runtime suite now runs a deterministic SplitMix64 generator over every packaged Push 1 view. Four fixed seeds produce 56 view/seed scenarios and 3,584 mixed actions in each run. Actions mix pads, pressure, modifiers, encoders, encoder touch, ribbon, navigation, row selectors, mode buttons, transport and button holds. Invariants check that inputs are released, the LCD remains complete and the virtual Bitwig host reports no runtime errors.

The generator is deliberately deterministic: a failure is reproducible from its seed. Replay one seed with `python3 scripts/run_virtual_push1.py --fuzz-seed=0x505553483146555A` (replace the value with the seed reported by the failure). It is not yet a full QuickCheck/Hypothesis implementation and does not claim automatic shrinking to a minimal sequence. The recent action trace helps isolate a failing prefix; a future JUnit/property-test migration can add automatic shrinking.

## Timing fidelity

The pinned DrivenByMoss `AbstractHwButton` uses a 300 ms `TimeoutOptimizer` interval before a held button becomes `LONG`. The virtual host now has a monotonic virtual clock and tests the exact 299/300 ms boundary. It does not run `TimeoutOptimizer`'s wall-clock calibration in simulated time: doing so would make a deterministic test depend on scheduler speed and `System.currentTimeMillis()`.

This timing represents the controller framework's button semantics after MIDI delivery. It is not a claim that Push 1 firmware debounces its MIDI buttons at 300 ms.

## External techniques and why they matter

- **Ableton Push 1 manual** — use it to derive musical workflows: Browser navigation, Session clip launching, Note/Drum layouts, sequencer editing, Duplicate/Mute/Delete chords, device control and touch-strip behavior. Only adopt behaviors that the PUSHdriver/Bitwig project actually intends to support; Ableton Live-only semantics are not automatically Bitwig requirements.
  - https://www.ableton.com/en/manual/using-push-1/
- **Push MIDI Programming Reference** — community reverse-engineered reference for Push 1 User Port notes, CCs, relative encoders, touch notes, RGB SysEx and LCD rows. Cross-check it against the project's pinned controller source and hardware profile; it is not an Ableton service specification.
  - https://chor.ist/push/midi.html
- **QuickTheories** — Java property-based testing with deterministic seeds and automatic shrinking. It is a candidate for the next testing-framework step, once the existing standalone `main`-based suite has a clean test lifecycle and dependency integration.
  - https://github.com/quicktheories/QuickTheories
- **Hypothesis** — the same useful property-testing principles: generate broad input spaces, preserve failing examples and shrink failures to simpler counterexamples. The Python library is a methodology reference here, not a dependency in the Java extension.
  - https://github.com/HypothesisWorks/hypothesis
- **Mido + RtMidi** — a possible optional OS-level virtual MIDI bridge for macOS/Linux, allowing a running Bitwig instance to see virtual MIDI ports and receive recorded event streams. RtMidi virtual ports are not available on Windows, and this bridge must remain an opt-in external integration rather than a required CI dependency.
  - https://mido.github.io/mido/intro.html
  - https://mido.github.io/mido/backends/rtmidi.html
- **PIT mutation testing** — a future way to measure whether tests actually detect realistic code mutations, rather than merely execute lines. It should be introduced only after the test runner exposes test methods that PIT can discover; the current bench is launched as a standalone Java main class.
  - https://pitest.org/quickstart/maven/
- **MIDI Tester loopback** — a useful reference for byte-for-byte MIDI round-trip, decoded message kind and field assertions. A loopback proves the transport/codec path, not the semantics of a Bitwig controller extension.
  - https://github.com/ericting/midi-tester

## Future implementation order

1. Keep the current dependency-free generated scenarios and timed-button regression in every CI run.
2. Add a separate reference-profile test for every physical input/output mapping, sourced from the Push 1 protocol reference and pinned upstream map, never from the simulator itself.
3. Add event trace export/replay (MIDI short messages, SysEx, virtual timestamps, active view and seed) so a failure can become a permanent named regression scenario.
4. Migrate the standalone test runner into discoverable test cases, then evaluate QuickTheories shrinking and PIT mutation coverage on production Push 1 overrides and critical framework adapters.
5. Add an optional macOS live-host profile using Mido/RtMidi virtual ports and a manually launched Bitwig 4.3.10 instance. Verify port discovery, actual API callbacks, event ordering and feedback loopback. Keep this profile separate from headless CI.
6. Keep physical Push 1 qualification for facts that software cannot observe: USB enumeration quirks, electrical/noise behavior, analog pressure curve, actual LED brightness/colour, LCD module/glass and hardware-specific latency.

## Acceptance rules

- A passing virtual suite proves only the listed protocol, production-controller and simulated API-17 host contracts.
- A live Bitwig virtual-port pass is stronger evidence for host integration, but still does not prove physical Push hardware.
- A physical test is required only for properties with a physical boundary; do not use it as a substitute for repeatable software regression testing.
- Never report a percentage such as "90% hardware simulated" without a complete inventory, explicit weights and measured coverage.
- Any newly found defect must have a failing reproduction before the production correction, then a passing regression test on the final integrated branch.
