# Mandatory virtual Push 1 regression gate

Every evolution intended for `custom` must pass the complete virtual Push 1 bench before it can be merged.

## What CI already enforces

`.github/workflows/build-pushdriver.yml` is triggered by every push to `custom`, every pull request targeting `custom`, and manual dispatch. It has no path filters, so a documentation-only or source-only change still triggers the workflow.

The required aggregate check is the `quality-gate` job. It depends on both platform jobs and fails unless both are successful:

- `build`: assembles the pinned Push 1 source, checks deterministic assembly, builds and verifies the extension, runs the unified virtual simulation suite, validates the local runner and publishes the `.bwextension` artifact.
- `macos-intel-validation`: builds, package-verifies and runs the same virtual simulation suite on Intel macOS.
- `quality-gate`: succeeds only when both jobs succeed.

The single local command for the same main test suite is:

```bash
python3 scripts/run_virtual_push1.py
```

The virtual suite also runs `scripts/check_push1_cross_regressions.py` against every overridden mode. It compares first-row LED values with the Push 1 blink-command IDs, catching cross-row palette aliases in shared states such as Device and parameter-page selection before the runtime simulation starts.

Do not weaken, skip or replace `python3 scripts/test_push1_extension.py` with a compile-only check. A change to the virtual model or its tests must itself pass the complete suite.

## Required GitHub repository setting

A workflow running on pushes and pull requests is not, by itself, a merge lock. GitHub must be configured to require the aggregate check for branch `custom`.

In repository **Settings → Branches → Add branch protection rule** (or an equivalent repository ruleset), configure:

1. Branch name pattern: `custom`.
2. Require a pull request before merging.
3. Require status checks to pass before merging.
4. Select the exact check named `quality-gate` from **Build PUSHdriver**.
5. Do not allow administrators or other roles to bypass the rule if the intention is that every evolution must pass the bench.
6. Disable force pushes and branch deletion for this rule.

After saving, verify that GitHub reports the `custom` branch as protected and that a test pull request cannot merge before `quality-gate` succeeds.

## Current enforcement status

The workflow itself is configured correctly and runs on every push and pull request to `custom`. The repository's branch metadata now reports `protected: true`. However, the connected GitHub integration cannot read the detailed branch-protection endpoint (GitHub returns HTTP 403), so it cannot independently verify that `quality-gate` is required or that administrators and other roles are prevented from bypassing the rule.

A repository administrator should confirm those two settings in GitHub. Until then, describe the workflow as an active CI gate and the branch as protected, but do not claim that every possible merge path is guaranteed to require `quality-gate`.
