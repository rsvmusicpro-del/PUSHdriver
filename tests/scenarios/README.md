# Permanent generated Push 1 regressions

`fuzz-seeds.txt` is the checked-in corpus for generated failures that have been fixed. The default Java runtime suite always executes the four rotating exploration seeds **and every unique seed in this file** across every packaged Push 1 view (64 mixed actions per view/seed pair).

When a generated scenario fails, the runtime reports its exact seed, active view, and complete 64-action trace, and writes a report under `target/push1-failures/`. GitHub Actions automatically uploads that directory as a platform-specific artifact when either runtime suite fails. Reproduce it with:

```bash
python3 scripts/run_virtual_push1.py --fuzz-seed=5788624465584084314
```

After fixing the defect, promote the seed before merging the fix:

```bash
python3 scripts/promote_push1_regression.py 5788624465584084314 --reason "DeviceLayerMode null layer on DRUM64 mute/solo"
```

The promotion command is idempotent: a seed is stored once, with a human-readable defect note. The full suite then replays it on every local run and both CI platforms. Keep the defect description factual; do not promote a flaky or environment-only failure as a product regression until it has a deterministic reproduction.

The corpus stores seeds rather than implementation-derived expected values. This preserves the original generated interaction sequence and makes failures reproducible without copying production constants into the oracle.
