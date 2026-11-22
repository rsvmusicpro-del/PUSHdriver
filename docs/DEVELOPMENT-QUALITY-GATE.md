# Mandatory virtual Push 1 regression gate

Every evolution intended for `custom` must pass the complete virtual Push 1 bench before it can be merged.

## What CI already enforces

`.github/workflows/build-pushdriver.yml` is triggered by every push to `custom`, every pull request targeting `custom`, and manual dispatch. It has no path filters, so a documentation-only or source-only change still triggers the workflow.

The required aggregate check is the `quality-gate` job. It depends on both platform jobs and fails unless both are successful:

- Both platform jobs run `scripts/check_push1_led_safety.py`, which rejects project-maintained Device/Chains/Browser mode overrides and verifies that the pinned upstream Device selector row does not use RGB/blink palette identifiers.
- After source assembly, both platform jobs run `scripts/check_source_ownership.py`. It rejects project-owned mode classes, requires exact allowlists for source overrides and upstream patch targets, checks shadow type identity, verifies the assembled Device class inventory against pinned upstream, and rejects duplicate public fully-qualified Java types.
- The inventory covers setup/configuration, modes, views, sequencer/drum/pad handlers, commands and controller surfaces. New source overlays or patch targets fail CI until explicitly reviewed.
- `build`: assembles the pinned Push 1 source, checks deterministic assembly, builds and verifies the extension, runs the unified virtual simulation suite, validates the local runner and publishes the `.bwextension` artifact.
- `macos-intel-validation`: builds, package-verifies and runs the same virtual simulation suite on Intel macOS.
- `quality-gate`: succeeds only when both jobs succeed.

The single local command for the same main test suite is:

```bash
python3 scripts/run_virtual_push1.py
```

Do not weaken, skip or replace `python3 scripts/test_push1_extension.py` with a compile-only check. A change to the virtual model or its tests must itself pass the complete suite.

The Device/Browser row contract is checked at two levels: the Java runtime suite sends real virtual MIDI taps through the production bindings to verify the native Ableton row roles (top-row selectors, bottom-row Device actions, parameter-page selection, Chains selection and Browser navigation); the Python guard prevents a second Device-family implementation from silently reintroducing the reversed row mapping or RGB/blink identifiers on the static selector row. The runtime also explicitly rejects Push 1 blink opcodes from all eight Device selector LEDs.

The runtime timestamps host callbacks and exercises rapid DEVICE → IN → ROW1 → Chains → ROW2-UP → TRACK → VOLUME gestures without draining callbacks between inputs. Nested ROW1 selects the exact existing sibling through one DeviceBank item command instead of a sequence of relative cursor steps. When Bitwig is still publishing a newly selected chain cursor, the latest dependent hierarchy gesture is retained: ROW1 selects the latest requested child, while OUT/ROW2-UP unwinds the newly entered hierarchy. One weakly routed cursor-name observer per IModel is shared across Push surfaces and completes the intent as soon as Bitwig publishes a changed cursor name. A bounded 8 ms confirmation probe remains only as a fallback for unchanged-name transitions or missing notifications (maximum 600 ms); a missing callback drops the intent instead of acting on the stale parent. The 322-case IN/Chains→ROW1 matrix varies callback delay (0–300 ms) and gesture spacing and requires zero stale selections. Additional regressions cover repeated ROW1 taps, OUT/ROW2-UP during pending entry, timeout, late callbacks and independent mode changes. Native parent-return decisions remain DrivenByMoss-compatible, but the fixed 300 ms sleep is replaced by cursor-publication completion: the shared name observer finishes immediately when the parent name changes, and an 8 ms / 600 ms bounded fallback handles missed notifications. The cursor fingerprint (name, nesting/position, and visible sibling-bank contents) detects same-name nested-to-nested changes when the parent and child banks differ. If a parent return remains completely indistinguishable, it retains DrivenByMoss' original 300 ms completion as a bounded fallback; a pending chain-entry gesture remains fail-closed when every API-17-visible field is identical. An IN arriving during an in-flight parent return is deferred until that parent is published, then selects the first chain on the new cursor. A selection made in the Chains chooser is likewise retained by chain name and applied only after the parent cursor and channel finalization are complete. Repeated OUT/ROW2-UP presses are coalesced behind the in-flight parent and replayed through the dedicated OUT command after each cursor publication, preventing duplicate parent requests against a stale cursor. The virtual runtime asserts OUT completes on a 73 ms host publication, before the former 300 ms deadline. This is deterministic software-path evidence, not a measurement of physical MIDI/Bitwig latency. The runtime also measures alternating DEVICE/TRACK/VOLUME/PAN-SEND transitions; wall-clock figures are diagnostic only and have no CI timing gate.

The runtime suite also checks the pinned DrivenByMoss 300 ms button long-press boundary using virtual time, then executes 16 reproducible generated seeds plus every unique seed in `tests/scenarios/fuzz-seeds.txt` across every packaged view, with 256 actions per view/seed scenario. The CI matrix therefore executes at least 224 view/seed scenarios and 57,344 mixed actions. The generator now includes rapid mode/command bursts and drains pending host callbacks at varied virtual-time boundaries (1, 8, 73 and 100 ms) to exercise stale and delayed callback interleavings. A separate eight-producer MIDI ingress stress sends 32,768 unique short messages through the virtual callback boundary and asserts no loss, duplicates or callback overlap. The first extended run also corrected a virtual-host contract mismatch: the test double had returned the global 64-scene bank from ITrackBank.getSceneBank() instead of the eight-slot clip-launcher page, causing impossible LCD row indices. The global scene bank and per-track slot page are now modeled separately. The local runner accepts `--fuzz-actions` and `--fuzz-seeds` to tune the duration. Failures report the seed, view and complete action trace, write a local report under `target/push1-failures/`, and are automatically uploaded as a CI artifact on either platform. The runner then replays the failing seed on the single affected view and binary-searches the shortest failing action prefix, confirming that prefix fails and its immediate predecessor passes; it does not claim arbitrary-subsequence minimality. Fixed failures are promoted with `python3 scripts/promote_push1_regression.py SEED --reason '...'` and then run on every subsequent local and CI execution. The promotion helper, prefix reducer and In/Out source-contract mutation checks have Python unit tests in CI. Linux CI also removes the shared empty-clip guard from the assembled Java source, rebuilds the extension, and requires the focused runtime regression to fail for the expected reason; it then restores the source and rebuilds the clean package. This is one deterministic production-code mutation smoke test, not a full PIT mutation score. The runtime also tests empty classic/Poly sequencer slots so absent clips cannot trigger playhead or note reads. See `docs/VIRTUAL-PUSH1-TEST-STRATEGY.md` for the evidence layers, external references and staged roadmap.

## Required GitHub repository setting

A workflow running on pushes and pull requests is not, by itself, a merge lock. As verified on 2026-11-19, the active `protection` ruleset (ID `27227660`) on `custom` requires pull requests and the strict `quality-gate` status check, blocks deletion and non-fast-forward updates, and has no bypass actor. Required human approvals are zero. If this ruleset is ever recreated, preserve the settings below.

In repository **Settings → Branches → Add branch protection rule** (or an equivalent repository ruleset), configure:

1. Branch name pattern: `custom`.
2. Require a pull request before merging.
3. Require status checks to pass before merging.
4. Select the exact check named `quality-gate` from **Build PUSHdriver**.
5. Do not allow administrators or other roles to bypass the rule if the intention is that every evolution must pass the bench.
6. Disable force pushes and branch deletion for this rule.

After saving, verify that GitHub reports the `custom` branch as protected and that a test pull request cannot merge before `quality-gate` succeeds.

## Current enforcement status

The workflow itself is configured correctly and runs on every push and pull request to `custom`. The active `protection` ruleset requires PRs and the strict `quality-gate` check, blocks branch deletion and non-fast-forward updates, and has no bypass actor. Required human approvals are zero. See https://github.com/rsvmusicpro-del/PUSHdriver/rules/27227660. These statements describe the ruleset actually read from GitHub, not an inference from branch metadata.


## Why this gate is necessary

The former guard checked only three named Device-family files in one override directory. It did not inventory every project override or the upstream files modified directly by the assembly script. The virtual bench exercised production MIDI bindings but did not independently enforce implementation ownership; tests could therefore confirm a self-consistent but incorrect mapping.

The source-ownership gate makes the upstream/project boundary explicit. DeviceParamsMode-specific In/Out methods and DeviceBrowserMode filter-column navigation methods remain absent. Push 1 In enters the first available slot chain and Out selects the parent cursor device; both In and Device Chains entry restore ROW1 device selectors after Banks mode. The existing DeviceParamsMode ROW1 handler is narrowly adapted for nested-device selection. Browser content-type paging remains upstream-owned.


The quality gate also verifies the physical In/Out contract end-to-end: CC62 enters the first available slot chain, CC63 returns to its parent, and nested ROW1 taps select the corresponding chain devices. Setup labels, LED availability, virtual-device constants, hardware documentation and Browser paging are checked.


## Device navigation latency

Device hierarchy commands retain the pinned DrivenByMoss ownership: In and Device Chains call `selectSlotChain`; Out reuses the native `DeviceParamsMode.moveUp()` path; nested ROW1 selects the exact sibling through one DeviceBank item command rather than a sequence of relative cursor steps. One weakly routed cursor-name observer per IModel is shared across Push surfaces; the pending intent is also model-scoped and latest-wins, so surfaces sharing a cursor cannot replay conflicting ROW1 targets. The observer completes dependent ROW1/OUT intents immediately when Bitwig publishes the changed cursor. The bounded 8 ms fallback covers delayed or missed notifications when the published cursor is distinguishable by the available fields. Entering the first nested level is also recognized from the root-to-nested state change, even if names match. The pinned API-17 wrapper exposes an empty cursor ID; if both parent and child are already nested and have identical names, the host API provides no stable identity to distinguish them, so the intent times out safely instead of selecting on the stale parent. The probe is not a general navigation queue. The native fixed 300 ms parent-return sleep is removed. OUT now starts the parent selection and completes the existing mode/channel finalization on cursor-name publication; an 8 ms probe is only a bounded fallback (600 ms ceiling). If the user switches away from Device Params, the pending completion is canceled. The virtual runtime verifies completion at the simulated 73 ms publication point, not at 300 ms. The virtual runtime verifies direct In/Chains dispatch, native Out/ROW2 hierarchy behavior, nested traversal direction, physical MIDI bindings, and ROW1 bounds. This deterministic host model does not prove Bitwig hardware latency; live Push 1 testing remains the final UX check.


## Temporal and hardware-feedback simulation

The standalone `scripts/push1_temporal_simulator.py` complements the API-17 runtime without changing production navigation. It runs seeded Monte-Carlo scenarios for IN → rapid ROW1 → ROW2-UP, repeated chain traversal and mode bursts. Configurable distributions model electrical event detection, USB/MIDI input, Bitwig dispatch, extension navigation, cursor publication, and separate MIDI/USB/LED and MIDI/USB/LCD feedback paths. Reports include P50/P95/P99, jitter, losses and feedback reordering. CI runs 5,000 samples per scenario on Linux and Intel macOS and retains JSON reports as artifacts.

The checked-in profile is explicitly theoretical and configurable; it is not a hardware timing claim. See `docs/PUSH1-TEMPORAL-SIMULATOR.md` for calibration using timestamped USB/MIDI captures, optical LED/LCD sensors and force/displacement instrumentation. The simulator cannot reproduce tactile force, key travel, click feel, actual LED brightness or LCD optical response. A physical Push 1 with live Bitwig remains required to qualify those boundaries.

## Post-audit extension hardening

- Poly observer registration is shared per track bank, fans note events out to every live active Push Poly view, and retains views only through weak references. This preserves multiple Push surfaces sharing one model while respecting the API-17 limitation that observers cannot be removed.
- Poly note aggregation and long-press/modifier scans use the clip's highest occupied row rather than blindly retrieving 128 step-info objects per step. The virtual runtime guards the sparse four-note case at no more than 40 getStep calls across the 32-cell grid; this is an API-call count, not a hardware latency claim.
- Push 1 MIDI/button indices are bottom-up while framework coordinates are top-down. The adapter and virtual hardware tests cover all coordinate corners and verify that physical bottom-left maps to Poly step zero.
- The source assembler is split into seven ordered patch batches. Its shared Java patch helpers are independently tested, the source-ownership inventory covers replaceJavaMethod targets, and CI compiles every build/validation Python script.
- The display glyph pixel query reads immutable glyph data directly instead of allocating a defensive copy for each pixel; the public bitmap accessor remains defensive.
