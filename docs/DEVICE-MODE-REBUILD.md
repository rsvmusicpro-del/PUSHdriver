# Device Mode Rebuild — Hybrid Workflow Specification

Status: design baseline for `feature/device-mode-rebuild`  
Target: Push 1 / Bitwig Studio 4.3.10 / Controller API 17 / DrivenByMoss 19.2.2  
Base commit: `280e20ed7e5c192ea95321d1ab9974de3799df03`

## 1. Objective and non-negotiable rules

Replace the current Device workflow as a coherent subsystem, not by layering more special cases onto the existing three-mode interaction. Preserve the pinned upstream source and all non-Device modes. Keep the old implementation available on `custom` until the replacement has passed the virtual regression suite.

The hybrid combines:
- Push 1 / Live's direct hardware grammar: select a device from the selector row, use the state row for device state, edit eight parameters with the encoders, use In/Out to move through hierarchy/banks, and retain tactile Shift operations.
- DrivenByMoss / Bitwig concepts: cursor device, remote-control parameter pages, slot chains, expanded device UI, pinning and browser operations only when the API 17 host advertises the corresponding capability.
- A PUSHdriver-specific improvement: a visible, explicit navigation context. Device selection, parameter-page selection and chain selection must never be hidden behind a boolean that silently changes what the same button means.

## 2. Interaction model

Device Mode has one controller mode and three explicit selector contexts:

1. **DEVICES** — the selector strip shows the eight visible devices in the selected track's device bank.
2. **PAGES** — the selector strip shows the selected device's named remote-control parameter pages.
3. **CHAINS** — the selector strip shows available slot chains for the selected container device.

The active context is always printed in the LCD header and represented by the Banks action LED. The selected item is marked with the Push selection glyph and a bright selector LED. Empty slots are dark and inert.

### Entry and exit

- Device button enters Device Mode and focuses the current cursor device.
- Track button exits to Track Mode without changing the selected device.
- Device button while already in Device Mode does not cycle into an unrelated mode.
- If no device exists, the display says `NO DEVICE`; device-only actions are disabled. The user can still enter the browser through the documented browser command.
- On entering, default selector context is DEVICES. The selected device and parameter page are restored only if the host still reports them as valid.

### Device selection

- Selector buttons in DEVICES select the corresponding existing device in the current device bank.
- Selection is immediate; encoders always bind to the newly selected device's current parameter page.
- Selecting the already-selected device changes context to PAGES (a deliberate, repeatable "open parameters" gesture).
- A held modifier may be used for non-destructive alternate selection only where the command is already supported and regression-tested. Destructive actions must never be inferred from a plain tap.
- Left/Right navigation moves to the previous/next device and stops at bank boundaries; it must not wrap silently.
- LCD shows selected device name, enabled/disabled state, position and total count when available.

### Parameter editing

- The eight main encoders always control the eight parameters of the active remote-control page.
- Encoder touch starts/stops parameter touch exactly once per gesture. Delete + encoder touch resets the parameter and consumes Delete so no second action fires.
- Shift + encoder retains the framework's fine-adjust behavior.
- PAGES selector buttons select a non-empty named page. The page name and selected state are shown on LCD/LED.
- In/Out navigate previous/next parameter pages only when the current device exposes more than one page and no nested chain is being entered. No modulo wrap.
- Missing parameter, missing page or stale device is a no-op with cleared display/LED state, never a null dereference.

### Chains and nested devices

- CHAINS context is offered only when `HAS_SLOT_CHAINS` is supported and the current device actually exposes chains.
- Selecting a chain selects it in the host and returns to DEVICES/PAGES context for the resulting cursor device; the display must wait for host state to reflect the selection rather than assuming synchronous navigation.
- In enters a selected chain/container only when the API supports that operation. Out returns to its parent. If unsupported, show a short unavailable indication and leave selection unchanged.
- The Up action unwinds exactly one hierarchy level per press: CHAINS → DEVICES → parent device/track. It must not schedule an unguarded delayed mode switch.
- No speculative calls to slot-chain APIs when the host capability is absent.

### Device actions

The action row is a stable command row. A command is disabled (grey) when its API capability or target is unavailable.

| Position | Label | Color family | Behavior |
|---|---|---|---|
| 1 | On | Green | Toggle selected device enabled state |
| 2 | Params | Cyan | Toggle Bitwig parameter-page section visibility, if supported |
| 3 | Expand | Blue | Toggle device expanded state |
| 4 | Chains | Violet | Enter/leave CHAINS context, only if supported |
| 5 | Banks | Yellow | Cycle DEVICES → PAGES → CHAINS (skip unavailable contexts) |
| 6 | Pin | Magenta | Toggle device pin only if host supports pinning |
| 7 | Window | White | Toggle plug-in/device window |
| 8 | Up | Red | Move up one hierarchy level; red remains the navigation affordance |

For stateful actions, dim intensity means off and bright intensity means on. Stateless navigation actions use a consistent fixed intensity. Unavailable actions are grey/off and cannot dispatch.

## 3. Color and feedback contract

- Action row (RGB-capable row): one semantic hue per function as shown above; on/off uses low/high intensity of the same hue.
- Selector row (static-CC row): dim green = available, bright green = selected, off = empty/unavailable. Never send RGB-only palette indices to this row because the Push 1 static CC palette overlaps blink command values.
- Browser feedback remains a separate palette/state machine and must not leak into Device Mode.
- A mode/context transition must clear stale LEDs before rendering the new state. LED output must be idempotent: repeated refreshes of unchanged state produce no blinking or alternating commands.
- LCD row 1 labels the selector row; rows 2–3 pair each encoder's parameter name/value; row 4 labels the action row. All text is ASCII-safe and fixed-width for the four 17-character LCD modules.

## 4. Browser workflow

Keep browser lifecycle separate from Device Mode:
- Open browser to replace the selected device, or insert before/after only when the API 17 browser supports that operation.
- Confirm loads once; cancel closes without changing the current device.
- Browser deactivation must stop browsing exactly once and must not be triggered by ordinary Device context changes.
- Null/empty browser info, missing result lists and delayed result updates are valid states.
- Browser preview is shown/toggled only when the host advertises preview support.

## 5. Architecture direction

Introduce a small Device Mode state/controller layer with explicit enum state (selector context, selected device identity, selected page, pending hierarchy transition). Keep host/API operations behind narrow methods that check capability and existence before dispatch.

Rendering must be a pure projection of current host state plus explicit UI context:
- one renderer for action-row labels/colors;
- one renderer for selector entries/colors;
- one renderer for the four LCD rows;
- no state mutation in render methods;
- no LED writes that encode blink on the static-CC row.

Keep the DrivenByMoss framework's parameter provider and knob-touch lifecycle. Avoid polling or re-binding parameters every controller tick. Observe only identity/name/page changes and rebind once after a confirmed state transition.

Do not change the pinned upstream submodule, public controller API target, MIDI mappings, or unrelated modes as part of the Device rewrite.

## 6. Required virtual acceptance matrix

The implementation is not ready to replace the old mode until all of these pass in the production PushControllerSetup runtime bench:

1. Enter/exit Device Mode; no-device state; stale cursor device.
2. Select each of eight devices; empty entries are inert; first/last navigation boundaries do not wrap.
3. Select each available parameter page; empty pages are inert; first/last page navigation boundaries do not wrap.
4. Change device while a knob is touched; old parameter receives exactly one release and new parameter receives no phantom touch.
5. Encoder values, Shift fine control, Delete+touch reset and automation touch lifecycle.
6. Device enable/disable, expanded state, window state, parameter section visibility and pinning.
7. Chains supported/unsupported; zero, one and eight chains; chain selection; enter/leave nested hierarchy; delayed host state; parent/child transitions.
8. Browser replace/insert/cancel/confirm, preview supported/unsupported, delayed/empty results, null info text.
9. Capability matrix with each relevant API capability independently enabled/disabled.
10. LED assertions for every action state and selector entry; repeated refresh stability; no blink opcodes on static row.
11. LCD assertions for all 4 × 68 characters, empty names, long names, glyphs, selected markers and value refresh.
12. Cross-mode regressions: Track, Mixer, Note, Session, Sequencer, Drum, Browser and Layer modes remain unchanged.

Run the same unified bench on Linux and Intel macOS with Java 17. Package verification must pass. The artifact must be built from the candidate branch's exact commit SHA.

## 7. Rollout and safety

- Implement on this feature branch only.
- Add failing tests before replacing existing behavior.
- Keep the current implementation intact until the candidate passes the full acceptance matrix.
- Build and upload a candidate `.bwextension` as a GitHub Actions artifact; do not merge automatically.
- A green virtual bench proves the tested API-17 controller contract only. It does not prove behavior inside a live Bitwig Studio 4.3.10 process or on physical Push 1 hardware.

## 8. Audit follow-up — 2026-11-14

The follow-up audit adds regression coverage and guards for three race/capability edges:

- Device-bank navigation, parameter-page navigation and page selection release the exact parameter instance currently under encoder touch before changing the target.
- Parent navigation no longer assumes that Bitwig's FOLLOW_SELECTION cursor has updated after a fixed 300 ms. It polls the cursor identity/state with a bounded timeout and invalidates the callback when a later controller action or mode transition supersedes it.
- Slot-chain selection keeps the Chains selector active until the cursor reports the resulting device. The Chains context is not offered for null/blank-only chain names, and all Chains-mode entry/render paths guard HAS_SLOT_CHAINS independently.

The virtual host now models a delayed parent cursor update and a delayed slot-chain selection. It also exercises stale parent callbacks, touch release during In/Out and page selection, empty chain names, and unsupported slot-chain mode rendering.

### Targeted audit completion — 2026-11-14

The three requested Device-mode audit areas are implemented and covered by the production virtual Push 1 runtime bench:

1. **Asynchronous chain cursor return:** the pending transition compares a structural cursor snapshot (device name/position, nesting/layer/slot traits, available slot names, sibling-device bank and parameter-page names). It polls for up to 40 × 73 ms. A confirmed cursor update returns to DEVICES; a missing/failed host update also exits the pending CHAINS context at timeout, so the controller cannot remain stranded there. A later Bitwig cursor update remains authoritative and refreshes the Device view.
2. **Nested hierarchy and pages:** parent navigation uses the same structural snapshot and bounded asynchronous wait; stale callbacks are invalidated by later controller actions or mode deactivation. Parameter touch is released on the original parameter before device/page/hierarchy target changes. Tests cover multiple parent levels, delayed cursor changes, stale transitions, page selection, and all eight chain selector positions.
3. **Bitwig API 17 capability contract:** the pinned DrivenByMoss 19.2.2 HostImpl advertises the three optional Device capabilities below. The Device implementation guards each independently, and the virtual test exercises all eight combinations of their enabled/disabled states.

| Capability | API-backed operations guarded by Device mode |
|---|---|
| HAS_SLOT_CHAINS | getSlotChains, chain selection, Chains context, chain LEDs/LCD, cursor snapshots |
| HAS_PARAMETER_PAGE_SECTION | parameter-section state read and toggle |
| HAS_PINNING | pin state read and toggle |

The virtual acceptance for these three requested areas is complete on the pinned API-17 contract. The CI bench cannot emulate the actual Bitwig 4.3.10 process or physical Push 1 hardware; those are integration checks, not additional code-audit objectives.
