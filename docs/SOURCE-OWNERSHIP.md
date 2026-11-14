# Source ownership and single-implementation policy

## Authority

The pinned DrivenByMoss 19.2.2 submodule is the sole owner of Push controller modes. PUSHdriver must never add or overlay project-owned `DeviceParamsMode`, `DeviceChainsMode`, or `DeviceBrowserMode` classes. Device, Chains and Browser state transitions and row semantics remain in the upstream classes.

PUSHdriver may adapt upstream behavior only through narrowly scoped, reviewed patches where Bitwig/Push 1 integration requires them. Every such patch must be listed in `PATCH_TARGET_ALLOWLIST` in `scripts/check_source_ownership.py`. A second mode implementation is prohibited, not merely allowlisted. The DeviceParamsMode-specific In/Out adapter was removed; upstream already provides the corresponding item navigation and boundary methods.

## Required review for ownership changes

Before adding or changing an override or assembly patch, review the upstream implementation and callers, the physical Push 1 MIDI/LED contract, other modes/views sharing those controls or pads, settings registration, and the production-path virtual test. Update the exact inventory in the same change and explain the reason in the pull request.

## What CI rejects

- New or removed project overrides not reflected in the reviewed exact allowlist.
- Any project-owned mode class, including Device/Chains/Browser.
- Any new upstream patch target not reflected in the exact patch allowlist.
- Shadow files whose package-qualified type identity differs from the upstream class they replace.
- Duplicate public fully-qualified Java types in the assembled source tree.
- An assembled Device class inventory that differs from pinned upstream.
- Reintroduction of the PUSHdriver-specific DeviceParamsMode navigation methods.

This is a source-ownership gate, not a claim that static analysis can prove every runtime MIDI mapping conflict. The production virtual bench remains mandatory; live Bitwig and physical Push 1 qualification remain separate.

## Root cause

The old guard only searched for three named files under `overrides/java/.../mode/device`. The assembly script can also mutate upstream classes directly with `replace_once`, and it overlays all files in `overrides/java`. Neither path had a complete, reviewed ownership inventory. The virtual tests were behavior-oriented: if their expected row roles mirrored the implementation, the simulator could confirm the same incorrect contract. The new gate independently checks ownership and patch-surface drift before runtime tests.
