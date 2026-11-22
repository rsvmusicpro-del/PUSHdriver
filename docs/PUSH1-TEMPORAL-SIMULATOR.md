# Push 1 Temporal & Feedback Simulator

## Purpose

The temporal simulator complements (and does not replace) the deterministic API-17 virtual Push runtime. It models the end-to-end timing envelope of a physical button's *electrical event* through USB/MIDI, Bitwig scheduling, navigation and separate LED/LCD feedback paths.

It is a Monte-Carlo sensitivity model with a monotonic event queue. It is not a hardware emulator, not a measurement, and not evidence that a given latency profile matches a production Push 1. The checked-in profile is intentionally labelled **theoretical-configurable**. Replace it only with documented measurements and preserve the evidence class in generated reports.

## Run it

```bash
python3 scripts/push1_temporal_simulator.py
python3 scripts/push1_temporal_simulator.py --scenario rapid-chain --samples 10000 --seed 42
python3 scripts/push1_temporal_simulator.py --profile path/to/measured-profile.json --scenario mode-burst --samples 10000 --json > temporal-report.json
python3 -m unittest discover -s tests/python -p 'test_push1_temporal_simulator.py' -v
```

Scenarios cover IN → rapid ROW1 changes → ROW2-UP, repeated chain traversal, fast DEVICE/TRACK/VOLUME/PAN-SEND mode bursts, and repeated short taps followed by a long hold. Each simulated press has an explicit release and hold duration; hold lengths are classified against the configurable 300 ms default long-press threshold (a framework semantic, not a claim about Push firmware debounce). The model reports command, nested-cursor, LED and LCD latency distributions separately, including P50/P95/P99, maximum, standard deviation and P95−P50 jitter. It also reports loss by stage, command completion and stale feedback reordering.

## Profile format

The JSON profile defines a probability distribution for each stage. Supported distributions are `fixed`, `uniform`, `normal`, and `lognormal`. Delay values are milliseconds and are clamped at zero. Optional `loss_probability` entries model dropped events in the range [0,1]. Use distinct distributions for input USB and feedback USB, MIDI input and feedback, LED and LCD rendering. The simulation uses an explicit seed so a report is reproducible.

A profile should record its evidence class and assumptions. Never copy the illustrative defaults into release claims. A calibrated profile should document the operating system, Bitwig version, Push serial/revision (where safe), USB topology, MIDI backend, sample count, timestamp method and raw measurement artifact. Keep correlations in mind: the built-in model samples stages independently, so it can overstate or understate tails when stages share a loaded scheduler or bus.

## Hardware calibration path

1. **Electrical button/MIDI boundary:** record physical actuation and incoming USB-MIDI packet timestamps with a common monotonic clock. On Linux, usbmon/Wireshark can capture USB transactions; on macOS use an appropriate USB capture tool. A timestamped MIDI loopback alone measures MIDI transport but not the mechanical actuation edge.
2. **Bitwig boundary:** use the existing optional RtMidi probe to capture outgoing MIDI and timestamp controller feedback. Instrument extension callbacks only in a diagnostic build or use a passive MIDI monitor; do not add blocking logging to production callbacks.
3. **LED boundary:** use a photodiode/phototransistor taped over a selected LED and a logic analyzer/DAQ timestamped against the input event. A camera can help inspect patterns but is usually less precise than a photodiode.
4. **LCD boundary:** use a high-frame-rate camera or optical sensor with a visible time reference. Record the first stable changed pixels, not merely the MIDI SysEx send time.
5. **Mechanical feel:** measure force/displacement using a force gauge or load cell and displacement sensor. This is separate from timing and cannot be inferred from MIDI timestamps.

Store raw captures outside the repository if they contain device identifiers. Convert measured stage samples into empirical distributions (or a documented approximation), retain provenance, and rerun the same seeded scenarios. Compare model output with held-out hardware traces before calling it calibrated.

## Interpretation and limits

- The model starts at electrical button detection. It cannot simulate key travel, force curve, tactile click or the user's subjective sensation.
- LED and LCD feedback are separate branches; LED completion is not a proxy for LCD refresh.
- A simulated cursor publication is a parameterized delay, not a reproduction of Bitwig internals.
- The model currently treats stage delays as independent. It does not emulate USB microframes, packet scheduling, MIDI DIN serialization, OS kernel scheduling, Bitwig's internal thread graph, USB bus contention or exact Push firmware behavior.
- Loss and reorder counters expose configured failure scenarios; default zero loss is not proof of zero loss on hardware.
- Physical Push 1 + live Bitwig testing remains necessary for final ergonomic acceptance.

## Non-regression contract

This tool is standalone Python and does not change production Java navigation, MIDI mappings, LED rendering or display code. The normal Push 1 runtime gate remains mandatory.
