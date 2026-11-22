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

The runtime now also timestamps host-scheduled callbacks with absolute virtual deadlines and runs a nine-gesture DEVICE → IN → ROW1 → Chains → ROW2-UP → TRACK → VOLUME burst without draining callbacks between gestures. It verifies immediate MIDI delivery, pending callback interleavings, and that the native 300 ms parent completion cannot steal focus from a newer mode selection. The trace records each gesture timestamp, active mode, cursor index and pending callback count. This is deterministic software-path evidence, not a measurement of physical MIDI/Bitwig latency. A separate 322-case IN/Chains→ROW1 sweep varies simulated Bitwig cursor-publication delay (0–300 ms) and physical gesture spacing around each exact callback deadline, recording stale-cursor selections and the first safe gap. It also verifies that a pending chain callback cannot block or steal focus from an independent TRACK mode change. The runtime also measures alternating DEVICE/TRACK/VOLUME/PAN-SEND mode transitions through production MIDI bindings and controller flush; these wall-clock numbers are diagnostic only and have no CI threshold.

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

Device hierarchy commands follow the pinned DrivenByMoss flow directly: In and Device Chains call `selectSlotChain`; Out reuses the native `DeviceParamsMode.moveUp()` path; nested ROW1 moves dispatch sibling cursor calls immediately. There is no navigation queue, cursor polling loop, deferred ROW1 replay, or input interception between gestures. The native 300 ms parent-return completion is retained, but it is canceled if the user has already switched away from Device Params, so it cannot steal focus from Track, Volume, Pan/Send, or another mode. The virtual runtime verifies direct In/Chains dispatch, native Out/ROW2 hierarchy behavior, nested traversal direction, physical MIDI bindings, and ROW1 bounds. This deterministic host model does not prove Bitwig hardware latency; live Push 1 testing remains the final UX check.


## Post-audit extension hardening

- Poly observer registration is shared per track bank, fans note events out to every live active Push Poly view, and retains views only through weak references. This preserves multiple Push surfaces sharing one model while respecting the API-17 limitation that observers cannot be removed.
- Poly note aggregation and long-press/modifier scans use the clip's highest occupied row rather than blindly retrieving 128 step-info objects per step. The virtual runtime guards the sparse four-note case at no more than 40 getStep calls across the 32-cell grid; this is an API-call count, not a hardware latency claim.
- Push 1 MIDI/button indices are bottom-up while framework coordinates are top-down. The adapter and virtual hardware tests cover all coordinate corners and verify that physical bottom-left maps to Poly step zero.
- The source assembler is split into seven ordered patch batches. Its shared Java patch helpers are independently tested, the source-ownership inventory covers replaceJavaMethod targets, and CI compiles every build/validation Python script.
- The display glyph pixel query reads immutable glyph data directly instead of allocating a defensive copy for each pixel; the public bitmap accessor remains defensive.
