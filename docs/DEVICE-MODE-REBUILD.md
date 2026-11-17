# Push 1 Device mode — restored Ableton workflow

Status: candidate implementation on `rebuild/device-mode-ableton`  
Target: Akai Push 1 / Bitwig Studio 4.3.10 / Extension API 17 / Java 17  
Upstream implementation: DrivenByMoss 19.2.2, pinned at `013620c1330cdcf896e96ef8fdc8a2123e4fce6a`

## Decision

The project-maintained DeviceParamsMode and DeviceChainsMode overrides have been removed. The controller now takes its Device, Chains and Browser mode implementations from the pinned DrivenByMoss Ableton Push source. This avoids maintaining a second, conflicting implementation of Ableton's Push interaction grammar.

The old overrides had reversed the two physical Push 1 button rows:
- ROW1 / CC20–27 (above the LCD) was incorrectly assigned Device actions.
- ROW2 / CC102–109 (below the LCD) was incorrectly assigned device/page selectors.

They also passed `PUSH1_COLOR2_*` RGB palette indices to ROW1's static CC palette. On Push 1, those overlapping numeric values are blink commands. This explains the otherwise nonsensical flashing and why the test bench missed it: the virtual assertions had been written to expect the same incorrect row mapping and RGB identifiers.

## Native Ableton Push 1 row contract

| Physical row | MIDI controls | Device mode responsibility |
|---|---|---|
| ROW1, above LCD | CC20–27 | Select one of eight devices, parameter pages or slot chains |
| ROW2, below LCD | CC102–109 | Device actions: enabled, parameter section, expanded, chains, banks, pin, window and up |

ROW1 must use only the Push 1 static CC palette. ROW2 uses the RGB-capable palette. The LED safety check now reads the pinned upstream DeviceParamsMode and rejects RGB/blink identifiers in its ROW1 color branch.

## Bitwig-specific integration

The complete upstream modes remain authoritative. No project-owned DeviceParamsMode, DeviceChainsMode or DeviceBrowserMode class is allowed. DeviceParamsMode is assembled from pinned upstream source without injected navigation methods. Push 1 In/Out is a narrow command adapter: In opens the existing upstream DeviceChainsMode when the cursor device exposes slot chains; the top row enters a named chain through `selectSlotChain()`. Out uses the existing upstream Device `Up` action to close the selector or select the parent device from a nested chain. In/Out no longer dispatch item/page navigation in Device, Browser, Session or Sequencer contexts. No PUSHdriver chain cursor or alternate navigation state is injected into any upstream mode.

The existing Push 1 setup routes In/Out only to their Device Chains hierarchy actions. The In command checks `HAS_SLOT_CHAINS` and the selected device's chain list before opening the selector; the Out command reuses the upstream Up action. Their LEDs advertise only those available Device actions. Optional Bitwig API capabilities must be checked before invoking parameter-section, pinning or slot-chain operations.

## Verification requirements

The virtual bench must exercise the actual registered PushControllerSetup MIDI bindings and assert:
1. Device button enters Device mode.
2. ROW1 selects a device; its LED values never overlap Push 1 blink opcodes.
3. ROW2 performs the native Ableton Device actions.
4. ROW1 selects parameter pages; Shift+Left/Right retains Ableton's device-swap gesture.
5. In opens the native Device Chains selector only when the host and device expose slot chains; Out closes the selector.
6. ROW1 selects a chain through `selectSlotChain()`; Out from the nested device invokes the upstream parent-selection action.
7. Browser, Session and Sequencer In/Out presses are no-ops; Browser row/encoder navigation and Shift+Left/Right remain unchanged.
8. Four-arrow item/page navigation remains unchanged and cross-mode tests continue to pass.

CI builds the extension and runs the virtual API-17 host tests on Linux and Intel macOS. This is not equivalent to launching Bitwig Studio 4.3.10 with a physical Push 1. The candidate is not considered fully qualified until that live integration test is completed.

## Change history

- Removed the three project Device-family overrides from `overrides/java/.../mode/device/`.
- Replaced Device-specific virtual assertions that encoded the reversed row mapping with assertions for the native Ableton mapping.
- Reworked the LED guard so it checks the pinned upstream implementation instead of expecting a project override to exist.


Physical direction is fixed by the Push 1 contract: In (CC62 / left) enters the native Device Chains selector; Out (CC63 / right) leaves it or selects the parent device from a nested chain. The command adapter owns no chain cursor or duplicate navigation state.
