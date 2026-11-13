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

The workflow itself is configured correctly and runs on every push and pull request to `custom`. However, the repository's branch metadata has reported `protected: false`. The connected GitHub integration cannot read or change the branch-protection endpoint (GitHub returns HTTP 403, and the repository has no active rulesets). Therefore the server-side merge lock is **not yet confirmed and must be enabled by a repository administrator** using the steps above.

Until that setting is enabled, CI reports failures but GitHub may still accept direct pushes or merges that bypass the check. Do not describe the bench as server-enforced mandatory until branch protection is verified.
