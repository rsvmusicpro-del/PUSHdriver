# PUSHdriver virtual development and validation strategy

## Target contract

The test target is the original Ableton Push 1, Bitwig Studio 4.3.10, Bitwig Extension API 17, Java 17 and the pinned DrivenByMoss 19.2.2 source revision. The extension remains MIDI-only. Push 2 HID/display emulation is not a valid substitute for this hardware target.


## Pixel-level LCD rendering

The optional Swing simulator paints the four character LCD modules as dot matrices rather than using a desktop text font. It uses the eight extracted Push 1 custom glyphs and separately licensed HD44780U A00/A02 ROM reference bitmap sets. The profile is selectable with `-Dpush1.lcd.rom=A00|A02`; CI exercises both. The deterministic rasterizer test covers all 128 seven-bit codes, and the headless Swing smoke test paints the same component used by the interactive simulator. The exact controller ROM fitted to Push 1 production revisions is not yet confirmed; these are documented reference profiles, not a claim that every standard glyph is optically identical to every unit.

## Why a single simulator is not enough

A credible station needs independent evidence at several boundaries. A software model can validate controller-side MIDI and API contracts, but it cannot prove undocumented Bitwig scheduling, USB driver behavior, electrical pressure calibration, firmware timing or the optical appearance of a particular LCD module.

PUSHdriver therefore separates five test layers:

1. **Reference protocol oracle** — independent expected Push 1 notes, CCs, relative encoder encoding, touch identities, pitch-bend ranges, RGB SysEx and LCD framing. Expectations must not be calculated from the same production constants that are under test.
2. **Virtual endpoint** — the 8×8 pad grid, aftertouch, momentary buttons, 11 encoders and touch identities, ribbon pitch bend, RGB feedback and four 68-character LCD rows. It models the controller's MIDI contract, not the DAW.
3. **Production extension integration** — the assembled `PushControllerSetup`, actual registered MIDI bindings, production commands, views and modes, with explicit stateful API-17 host collaborators.
4. **Scenario and state-machine exploration** — hand-authored musical workflows plus seeded generated event sequences. Every generated failure includes a reproducible seed, view and recent event trace.
5. **External qualification** — optional live Bitwig/virtual-MIDI and physical-device checks. These are reported separately from deterministic CI and never silently promoted to a software-only pass.

## New scenario engine

### API-17 host capability model

The virtual `IHost.supports(Capability)` contract is modeled per capability, not as one global
"all features available / no features available" switch. The regression suite independently
disables parameter-page sections, slot chains and device pinning, verifies that production
modes do not call unsupported API methods, and confirms that one missing feature does not
silently disable the other supported features. The default virtual host advertises the API
capabilities so existing scenarios remain stable; targeted tests then remove only the
capability under examination.

This is still a deterministic API collaborator, not a claim that every Bitwig 4.3.10 internal
service is reimplemented. New capability-dependent production behavior should add a focused
unsupported/supported pair to this model before it is considered covered.

The runtime suite runs a deterministic SplitMix64 generator over every packaged Push 1 view. Sixteen rotating seeds plus every unique seed in `tests/scenarios/fuzz-seeds.txt` are executed in CI (256 generated actions per view/seed); local runs use the same defaults and can be tuned with `--fuzz-seeds` and `--fuzz-actions`. The initial corpus records the previously discovered `DeviceLayerMode` null-layer failure seed. The default CI matrix therefore runs 224 view/seed scenarios and 57,344 mixed actions per platform. Each newly promoted, previously unseen seed adds 14 view/seed scenarios and 3,584 mixed actions. Actions mix pads, pressure, modifiers, encoders, encoder touch, ribbon, navigation, row selectors, mode buttons, transport and button holds. A dedicated burst action rapidly switches among eight modes while interleaving navigation, encoder and row commands before the next host flush. Pending callbacks are drained at varied virtual-time boundaries (1, 8, 73 and 100 ms) to force late work to encounter newer state. A separate concurrent ingress test races eight producers (32,768 unique messages total) through the virtual MIDI callback adapter, verifying exactly-once delivery and serialized callback execution. Invariants check that inputs are released, the LCD remains complete and the virtual Bitwig host reports no runtime errors.

The generator is deliberately deterministic: a failure is reproducible from its seed. Replay one seed with `python3 scripts/run_virtual_push1.py --fuzz-seed=0x505553483146555A` (replace the value with the seed reported by the failure). It is not a full QuickCheck/Hypothesis implementation. When a generated scenario fails, the runner automatically replays that seed on the failing view and binary-searches the shortest failing action prefix. The reducer confirms that the selected prefix fails and its immediate predecessor passes. It does not claim that arbitrary actions can be deleted while preserving the failure; this is prefix minimization, not general delta debugging. The callback-drain stress is deterministic event-loop interleaving, not a claim that Bitwig invokes controller callbacks concurrently on multiple threads; the actual host threading contract still requires live-host qualification. Once a generated defect is fixed, `scripts/promote_push1_regression.py SEED --reason '...'` adds its deterministic seed to the permanent corpus; the promotion is idempotent and the default local/CI suite then replays it on every packaged view. The full trace is retained in the failure output and written to `target/push1-failures/`; GitHub Actions uploads that directory as an artifact whenever either platform's runtime suite fails. This makes the exact generated sequence available for triage and later promotion into the checked-in corpus.

## Defect found by generated testing

The first CI run of the generated interaction matrix found a real production exception in the inherited Push `DeviceLayerMode`: a second-row mute/solo action called `toggleMute()` / `toggleSolo()` on a null layer item. The deterministic trace identified seed `5788624465584084314`, view `DRUM64` and the sequence prefix that reached the empty slot. The Push 1 source assembly now patches this action to return safely when the layer is null or reports `doesExist() == false`. A focused regression makes the API-17 test collaborator return a literal null layer item and presses the physical lower-row CC through the production binding.

This is the important difference between a broader test count and a useful test station: generated input found an unanticipated state combination, the trace made it reproducible, and the permanent test now protects the exact production path.

## Timing fidelity

The pinned DrivenByMoss `AbstractHwButton` uses a 300 ms `TimeoutOptimizer` interval before a held button becomes `LONG`. The virtual host now has a monotonic virtual clock and tests the exact 299/300 ms boundary. It does not run `TimeoutOptimizer`'s wall-clock calibration in simulated time: doing so would make a deterministic test depend on scheduler speed and `System.currentTimeMillis()`.

IN and Device Chains dispatch the native slot-chain selection immediately. Nested ROW1 selects the exact child through the existing DeviceBank item in one host command instead of issuing multiple relative cursor steps. If a hierarchy gesture arrives before Bitwig publishes the new cursor, only that dependent intent is retained. One weakly routed cursor-name observer per IModel is shared across Push surfaces and completes it immediately when the published name changes; a bounded 8 ms probe remains as a fallback for delayed or missed notifications when the published cursor is distinguishable by the available fields, with a 600 ms ceiling. Entering the first nested level is recognized from the root-to-nested state change even when names match. The pinned API-17 wrapper exposes an empty cursor ID, so a nested-to-nested transition whose parent and child have identical names cannot be disambiguated; the intent times out safely rather than selecting on the stale parent. OUT/ROW2-UP still re-enters DrivenByMoss' native parent-return state machine, and timeout drops the intent rather than acting on the stale parent. No general input queue is introduced, and unrelated mode changes remain immediate. The 322-case matrix covers both IN and Chains-chooser entry paths, 0–300 ms host cursor delays and 23 gesture gaps; it requires zero stale-parent selections. Additional cases cover rapid ROW1 coalescing, native OUT/ROW2-UP during pending entry, timeout, late callbacks and independent TRACK changes. A separate 1,000-sample alternating DEVICE/TRACK/VOLUME/PAN-SEND benchmark measures production MIDI dispatch and controller flush. These are software-path measurements, not physical Push/Bitwig latency.

This timing represents the controller framework's button semantics after MIDI delivery. It is not a claim that Push 1 firmware debounces its MIDI buttons at 300 ms.

## Concurrent MIDI ingress and virtual host fidelity

The virtual MIDI adapter serializes short-message and SysEx callback entry, matching the event-loop contract used by the Bitwig controller API. The dedicated ingress stress launches eight producer threads behind a start barrier and injects 32,768 uniquely identifiable CC messages. It asserts exactly-once delivery and that callback execution never overlaps. This tests the adapter's queueing/serialization boundary; it does not claim that the Bitwig host itself was run concurrently or that the host's undocumented internal threading was reproduced.

The extended fuzzer separately creates dense command bursts and varies when queued callbacks are drained. Its first run exposed a test-double mismatch rather than a production extension defect: ITrackBank.getSceneBank() had been wired to the model's global 64-scene bank, although the track bank exposes an eight-slot clip-launcher page. That made the simulator ask the four-row Push 1 LCD to render rows that cannot exist. The virtual host now models the global scene bank and each track's eight-slot page separately, and a focused assertion protects that API boundary.

## Optional live Bitwig MIDI transport probe

`scripts/live_bitwig_midi_probe.py` creates two RtMidi virtual ports for a manually configured Bitwig session. It can passively capture Bitwig MIDI output or send a selected CC into Bitwig. Install the optional dependencies with `python3 -m pip install mido python-rtmidi`, then run:

```bash
python3 scripts/live_bitwig_midi_probe.py --duration=30
```

Configure Bitwig's controller MIDI input to `PUSHdriver Bitwig Input` and its MIDI output to `PUSHdriver Bitwig Output` where the controller setup permits manual port selection. To inject a deliberate control event, add `--cc=62 --value=127`; this is the Push 1 In control and can change the selected device hierarchy in a live project.

This is a manual transport diagnostic, not a Bitwig automation harness: it does not create a Push 1 firmware identity, guarantee that Bitwig will attach the PUSHdriver extension to virtual ports, or certify controller behavior. It is supported on macOS/Linux with RtMidi virtual ports; Windows requires a different virtual-MIDI backend. Physical Push 1 acceptance remains separate.

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

1. Keep the dependency-free generated scenarios, permanent seed corpus, timed-button regression and empty-clip regressions in every CI run; promote each fixed deterministic failure before merging.
2. Add a separate reference-profile test for every physical input/output mapping, sourced from the Push 1 protocol reference and pinned upstream map, never from the simulator itself.
3. Add event trace export/replay (MIDI short messages, SysEx, virtual timestamps, active view and seed) so a failure can become a permanent named regression scenario.
4. The standalone runner now has a deterministic prefix reducer and independent guard-mutation tests. Linux CI additionally injects one deliberate Java production regression (removing the shared empty-clip guard), proves the runtime test kills it, then restores and rebuilds the clean extension. This is a focused mutation smoke test; full PIT mutation coverage still follows migration of production behavior tests into discoverable test cases.
5. A manual Mido/RtMidi virtual-port probe is now available for live Bitwig transport diagnostics. The next qualification step is to connect it to a manually launched Bitwig 4.3.10 session, verify actual controller-extension attachment, callback ordering and feedback loopback, and keep that profile separate from headless CI.
6. Keep physical Push 1 qualification for facts that software cannot observe: USB enumeration quirks, electrical/noise behavior, analog pressure curve, actual LED brightness/colour, LCD module/glass and hardware-specific latency.

## Acceptance rules

- A passing virtual suite proves only the listed protocol, production-controller and simulated API-17 host contracts.
- A live Bitwig virtual-port pass is stronger evidence for host integration, but still does not prove physical Push hardware.
- A physical test is required only for properties with a physical boundary; do not use it as a substitute for repeatable software regression testing.
- Never report a percentage such as "90% hardware simulated" without a complete inventory, explicit weights and measured coverage.
- Any newly found defect must have a failing reproduction before the production correction, then a passing regression test on the final integrated branch.
 

## Visual review artifacts

Every full CI runtime pass exports a PNG and a hexadecimal row dump for each of the 14 packaged views and every mode registered by `PushControllerSetup`. PNGs use the exact dot-matrix renderer selected for the run; text companions preserve every seven-bit character code across the four 68-column rows. The runtime suite verifies the expected number of completed PNG/text pairs.

Download the `push1-display-snapshots-linux` or `push1-display-snapshots-macos` artifact from the corresponding GitHub Actions run. Compare captures across views and modes for readable hierarchy, meaningful use of all four rows, truncation, selected-state visibility and consistent action placement. The images are simulator renderings, not physical-device photographs, and cannot qualify LCD contrast, viewing angle or the exact ROM fitted to a specific Push 1 revision.
