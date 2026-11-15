# PUSHdriver architecture ownership contract

Status: active architecture contract
Target: Push 1 / Bitwig Extension API 17 / Java 17
Upstream: DrivenByMoss 19.2.2, pinned by `UPSTREAM.md`

## Purpose

PUSHdriver is an adaptation of DrivenByMoss, not a second controller framework. Every behavior has one authoritative owner. Independence is achieved by isolating mutable state and lifecycle at meaningful feature boundaries, not by creating one class or engine per button, encoder, menu item or screen.

This contract is intended to prevent regressions caused by duplicated state, competing MIDI handlers, parallel DAW adapters and oversized overrides.

## Ownership map

| Responsibility | Authoritative owner | Project adaptation allowed | Must not be duplicated |
|---|---|---|---|
| Bitwig transport, tracks, devices, clips and musical event semantics | DrivenByMoss framework + Bitwig API | Thin Push-specific call-site adaptation | DAW model, clip cache, parallel MIDI/clip store |
| Physical MIDI input, output, button identities and hardware dispatch | PushControlSurface and existing DrivenByMoss hardware layer | Push 1 protocol constants, safety validation and routing corrections | Per-view MIDI listeners or competing button/encoder owners |
| Active controller mode and view selection | DrivenByMoss ModeManager / ViewManager and PushControllerSetup | Push 1 registration and focused binding changes | Project-owned duplicate modes or shadow registries |
| View-specific interaction and rendering | Active DrivenByMoss view | Narrow Push 1 view overrides | Inactive view LED writes or duplicate event dispatch |
| Sequencer-local interaction policy | ClassicSequencerEngine / PolySequencerEngine | Pure mapping, feature-local state and policy | Duplicate Bitwig clip state or a replacement sequencer framework |
| Push 1 pad address geometry | Push1PadGrid | Shared stateless coordinate validation | Different note/index arithmetic in each view |
| LCD wire protocol and physical geometry | Push1DisplayProtocol / Push1DisplayLayout | Text, glyph and formatting helpers | A second LCD writer or parallel display protocol |
| Device/browser/session navigation | Existing DrivenByMoss modes and bank APIs | PushCursorCommand / PageLeftCommand / PageRightCommand adapters | Private navigation cursors or copied bank state |
| Source selection and upstream adaptation | prepare_push1.py + explicit allowlists | Small, reviewed upstream patches | Untracked source mutation or duplicate public Java types |
| Regression validation | Existing virtual Push 1 bench and CI | Focused tests and static architecture guards | Tests that merely repeat implementation assumptions |

## Engine admission rule

A new engine is justified only when all of these are true:

1. The feature owns non-trivial mutable state or policy that can be isolated.
2. That state has a distinct lifecycle (activate, deactivate, reset or invalidate).
3. The feature has a stable boundary to the host framework and hardware adapter.
4. It can be tested without booting the complete Bitwig controller for every case.
5. It removes coupling or duplication rather than adding a forwarding wrapper.

Do not create engines for individual buttons, encoders, LCD cells, menu rows or simple commands. Those belong to the existing hardware, mode, view or display owners.

## Dependency direction

- Hardware and framework callbacks enter through the existing controller/view adapters.
- A view delegates only feature-local policy and calculations to its engine.
- Engines may depend on Java platform types and small PUSHdriver domain utilities; they must not depend on Bitwig, MIDI framework, Swing, hardware objects or concrete views.
- Engines never write LEDs, send MIDI, schedule host callbacks or mutate DAW state directly.
- The view translates engine decisions into the existing DrivenByMoss/Push APIs.
- Shared DAW state remains owned by Bitwig and accessed through the existing adapters.

## Lifecycle and concurrency rules

- An engine is active only while its owning view is active.
- Deactivation invalidates all activation tokens; a token from an earlier generation must never authorize later work.
- Feature-local transient state is reset at deactivation, not carried accidentally into the next activation.
- Any future delayed callback must capture an ActivationToken and check `accepts(token)` immediately before touching view, model or hardware state.
- Do not retain hardware button objects, note-position objects or mutable host iterators across asynchronous boundaries.
- Where API 17 has no observer-unregister operation, do not add one permanent observer per engine/view. Prefer one controller-lifetime observer routed to the active owner, or a demonstrably safe active guard that does not retain dead view instances.
- Only the active view may paint the shared pad grid or LCD.

## Push 1 coordinate contract

- Hardware pad notes: 36–99 inclusive.
- Hardware pad indices: 0–63 inclusive.
- Grid: 8 columns × 8 rows.
- Index order: left-to-right, top-to-bottom.
- Invalid notes, indices and coordinates map to `-1` or fail closed; never pass them to ButtonID.get or a hardware API.

## Safe change procedure

1. Re-read the current branch, pinned upstream revision and relevant owner files.
2. Identify the current owner and data source before introducing a class.
3. Make one bounded change with a behavior-preservation statement.
4. Add pure unit tests for boundaries and lifecycle; add virtual MIDI/runtime tests for integration.
5. Run source ownership, assembly reproducibility, Java build, package verification and virtual runtime/fuzz tests.
6. Check Linux and Intel macOS CI, then qualify the explicitly physical-only cases on Bitwig/Push 1.
7. Update this matrix and the feature-specific contract when ownership changes.

## Current known limits

- The sequencer engines are still a first isolation slice. They do not yet own all sequencer gestures or rendering decisions.
- The upstream Poly Sequencer installs a note observer without a matching unregister API in the pinned API 17 surface. This requires a separately designed lifetime fix; do not disguise it with a wrapper or duplicate observer.
- A green virtual bench does not prove live Bitwig scheduling, USB-MIDI behavior or physical Push 1 feel. See `VIRTUAL-PUSH1-COVERAGE.md`.
- No external sequencer library is approved as a production replacement for the existing Bitwig note-clip contract.
