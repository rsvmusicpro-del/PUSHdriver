# Source ownership and single-implementation policy

## Authority

The pinned DrivenByMoss 19.2.2 submodule is the sole owner of Push controller modes. PUSHdriver must never add or overlay project-owned `DeviceParamsMode`, `DeviceChainsMode`, or `DeviceBrowserMode` classes. Device, Chains and Browser state transitions and row semantics remain in the upstream classes.

PUSHdriver may adapt upstream behavior only through narrowly scoped, reviewed patches where Bitwig/Push 1 integration requires them. Every such patch must be listed in `PATCH_TARGET_ALLOWLIST` in `scripts/check_source_ownership.py`. A second mode implementation is prohibited, not merely allowlisted. The DeviceParamsMode-specific In/Out methods and DeviceBrowserMode filter-column navigation methods were removed. The Push 1 In/Out command adapter now calls the upstream mode's existing item/page methods; it owns no Device or Browser state machine.

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


## In/Out adapter boundary

The Push 1 In/Out command classes may choose which existing upstream item/page method to call, based on the upstream mode's own state. They must not own filter-column cursors, duplicate navigation state, or add new methods to Device/Browser mode classes. The virtual runtime test asserts device selection, parameter-bank scrolling and Browser content-type paging through those upstream methods.


## Current intentional project-owned classes

These are explicit, reviewed overlays or additions, not parallel classes loaded alongside an upstream class:

| File | Ownership role |
|---|---|
| `PageLeftCommand`, `PageRightCommand` | Push 1 In/Out command adapter. They call existing upstream mode item/page methods; they do not maintain Device or Browser state. |
| `PushCursorCommand` | Shift-aware arrow routing through the active mode's existing navigation API and the scene bank. |
| `SequencerView`, `PolySequencerView` | Push 1-specific sequencer presentation and note/step editing built on DrivenByMoss abstract sequencer views. They replace the upstream concrete view classes at the same FQCN. |
| `PushNewCommand` | New Push 1 clip-launcher command extending the upstream New command; not a second mode. |
| `Push1DisplayGlyphs`, `Push1DisplayLayout`, `Push1DisplayProtocol`, `Push1DisplayText`, `Push1DisplayValueFormatter`, `Push1FirmwareVersion` | New Push 1 LCD/protocol support types; no upstream class is shadowed. |

The two sequencer views are the largest remaining full-class overlays. They are not simultaneously registered beside another class with the same FQCN, but they can drift from future upstream changes. Their exact allowlist entry and the production runtime bench are mandatory until they can be reduced to smaller extension points.


## Physical In/Out direction contract

In is CC62 on the left/Page Left button and advances to the next device, parameter page or Browser content type. Out is CC63 on the right/Page Right button and moves to the previous item/page. The command adapters must delegate to the upstream methods in those directions. The static ownership gate and virtual runtime bench both enforce this mapping so a swapped label, command or test constant cannot pass unnoticed.
