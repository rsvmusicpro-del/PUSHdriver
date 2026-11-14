# Sequencer engine isolation — architecture and implementation contract

Status: diagnostic baseline and implementation plan
Target branch: `fix/independent-sequencer-engines`
Base: `custom` at `18e9ac3e18bfbfbb10c0618b30e3a3c1d57c580c`
Target runtime: Push 1 / Bitwig 4.3.10 / Extension API 17 / Java 17

## Objective

Make the classic Note Sequencer and Poly Sequencer independent at the controller-engine level without changing their musical contract or introducing a second implementation of existing DrivenByMoss behavior.

Independence does not mean duplicating Bitwig's selected clip adapter or MIDI data. Both modes intentionally edit the same selected Bitwig clip. It means that mode-local interaction state, event handling, rendering decisions and cleanup cannot leak from one mode into the other.

## Current source findings

The project already has two distinct concrete views:

- `SequencerView` extends `AbstractNoteSequencerView`.
- `PolySequencerView` extends `AbstractPolySequencerView`.

They are not two names for one concrete class. Both inherit the shared `AbstractSequencerView`, which owns common clip access, note-edit handoff, sequencer-active state, resolution handling and page helpers.

The concrete classic view currently owns playhead-follow state and its note-grid gestures. The concrete poly view owns its persistent pitch selection, selected-note duration editing and step-editor lifecycle. The upstream poly base also registers track-selection and note observers in its constructor. The observer callbacks capture the view's key manager; that registration and its lifecycle need explicit verification when both views are retained by the view manager.

A particularly important shared boundary is `AbstractSequencerView.getClip()`: it obtains the model's note-clip adapter for the view's configured clip dimensions. This is the correct shared DAW data boundary and must not be replaced with duplicate clip adapters or parallel MIDI stores.

The shared note editor is another intentional integration boundary: `editNote()` switches to the Note mode / Note Edit view and hands it the selected clip and note position. The two sequencers must serialize this handoff and clear their own local edit state when it takes control.

## Failure mechanisms to address

1. **Observer lifetime:** Poly Sequencer installs track observers from its constructor. If the view is not disposed or observers are not scoped, inactive instances may continue receiving note/selection events.
2. **Mode-local state surviving deactivation:** Poly selection (`noteMemory`), `editingStep`, `durationEditNote` and pending long-press release are local fields but need a deterministic reset policy on mode exit, track change, clip change and note-editor handoff.
3. **Shared hardware event stream:** Both view objects receive events through one Push surface. The dispatcher must guarantee that only the active view mutates its engine state. Long-press callbacks must obey the same active-view gate.
4. **Shared DAW clip state:** Edit page, loop, step length, note content and playhead are properties of the selected clip. They should remain shared and must not be shadow-copied into two private engines.
5. **Rendering ownership:** Each engine may calculate its own pad state, but only the active engine may paint the physical 8×8 grid. An inactive engine must not turn off or overwrite the active engine's LEDs.
6. **Index domains:** Push hardware pads are always 0–63; clip steps may span multiple pages. Every conversion between hardware pad, local page step and global clip step must be explicit and bounds-checked.
7. **Continuation notes:** A note's start and continuation are different states. Selection, deletion, duration edits and playhead feedback must never mistake a continuation for a note start or clear a sustained note's tail accidentally.

## Proposed engine design

Keep the existing public view names, mode registration, MIDI mappings, note-clip adapter and upstream API-17 compatibility. Replace only the fragile interaction internals in the two existing view classes, in small test-backed slices.

- `ClassicSequencerEngine`: owns classic scale-row mapping, 8×7 note-step editing, note long-press duration gestures, Select note editing, Shift note-repeat, Duplicate/Mute gestures, clip page/loop gestures, playhead-follow lock and classic pad feedback.
- `PolySequencerEngine`: owns the 4×8 step matrix, 4×8 playable note area, persistent note-selection set with velocity, step-local note editor, individual-note duration selection, poly step aggregation, repeat/duplicate/mute gestures and poly pad feedback.
- `SequencerClipPort` (thin adapter only if the tests demonstrate a need): a narrow facade over the existing `INoteClip` methods used by both engines. It must not cache note data, invent another clip identity, or mirror Bitwig state.
- `SequencerPadFrame`: immutable per-frame pad output (64 pad coordinates/colors) produced by the active engine and committed once by the Push surface. No inactive engine writes LEDs.
- Shared pure utilities may cover pad/step coordinate conversion, note continuation scans and range validation. They must be stateless; do not create a shared mutable "super sequencer" engine.

The view classes remain the integration adapters required by DrivenByMoss: they receive Bitwig callbacks, delegate to their own engine, and translate engine output to the existing Push 1 hardware API. No new menu, controller mode, settings IDs, MIDI bindings or parallel note editor should be introduced.

## Behavior-preservation contract

The classic Sequencer retains:
- 8 columns × 7 scale-mapped note rows and the top page/loop row;
- page navigation, clip loop gestures and manual page lock versus playhead auto-follow;
- note toggle, velocity/accent, note selection/edit, note duration long-press;
- Shift note-repeat, Select editing, Duplicate and Mute combinations;
- octave/scale mapping and chromatic/scale-root feedback;
- moving playhead column and existing Push 1 color semantics.

The Poly Sequencer retains:
- upper 4×8 step matrix and lower 4×8 scale-aware playable keyboard;
- persistent note selection and per-note velocity;
- step tap adds selected notes without overwriting existing notes;
- long-press step opens its note editor; pad taps remove notes; Select+note selects a single note for duration editing;
- Shift repeat, Select edit, Duplicate, Mute, accent and duration gestures;
- note-start/continuation rendering and selected/root-note feedback.

The two engines must not share mutable selection, editing, gesture, page-follow, copy-buffer or cached-render state. The active clip remains shared because it is the DAW document being edited.

## Hardening and safety rules

- Gate every input callback and delayed callback against the owning view's active state.
- Cancel or invalidate delayed work on deactivation; use generation tokens if the host scheduler cannot cancel a task.
- Clear pressed-note state on track change, clip identity change and mode deactivation; do not silently erase persistent Poly selection on every key release.
- Validate pad indices (0–63), note numbers (0–127), MIDI channel (0–15), page/step bounds and positive durations before calling the DAW API.
- Never trust a stale `IHwButton` or note-position object across an asynchronous callback.
- Avoid iteration that mutates a note clip while relying on a live iterator; use bounded note-number scans as the existing code does.
- Ensure exceptions or invalid Bitwig model state fail closed for LED rendering and do not send invalid MIDI/LED values.
- Keep the upstream submodule pinned and all source-assembly modifications explicitly allowlisted.
- Do not add third-party production dependencies unless they compile and run on Java 17 and Bitwig API 17, and have a license compatible with this LGPL-derived project.

## External engine / library assessment

No external step-sequencer library is a safe drop-in replacement for this task. A generic MIDI sequencer owns its own event clock, note store or transport and therefore does not naturally operate on Bitwig's live `INoteClip` adapter, Push 1 gestures and Extension API 17.

The most compatible production reference is the project's pinned DrivenByMoss 19.2.2 implementation itself: `AbstractNoteSequencerView`, `AbstractPolySequencerView` and their shared `AbstractSequencerView` contract. Keep these as behavioral/API references and adapt the two Push-specific engines around the existing host API rather than importing a second sequencer framework.

External projects are useful for validation, not as production engines:
- [bitwig-harness](https://github.com/unthingable/bitwig-harness) demonstrates a virtual-MIDI proxy plus Bitwig state observation. Its published Java 21 / Bitwig 5.3+ / API 21 requirements do not match this Java 17 / Bitwig 4.3.10 / API 17 target, so it must not be added as a build dependency.
- [Mido](https://mido.github.io/) and [python-rtmidi](https://github.com/SpotlightKid/python-rtmidi) are suitable optional external MIDI test-driver references. They do not replace the Java production engine and must remain outside the extension artifact.

## Required regression matrix before refactor

1. Enter classic -> Poly -> classic repeatedly; assert no selection, edit mode, page lock or pad LED leakage.
2. Enter Poly -> classic -> Poly repeatedly; assert selected pitches and velocities follow the documented Poly persistence policy only.
3. Switch track and clip while either mode is active; assert stale note selections and delayed gestures cannot edit the new clip.
4. Test all 64 hardware pad indices, all 8 classic columns, all 7 classic note rows, all 4×8 Poly step cells and all 4×8 Poly keyboard cells.
5. Test note start, continuation, muted, repeated and empty states independently in both modes.
6. Test short tap, long press, release ordering, Select/Shift/Duplicate/Mute chords and delayed callbacks.
7. Test first/last clip pages, loop boundaries, playhead crossing a page and manual page lock/unlock.
8. Verify that inactive engine callbacks do not call clip mutation methods and do not write pad LEDs.
9. Run the full local bench, source-ownership guards, Linux/macOS CI builds and package verification.

## Implementation sequence

1. Add regression cases for cross-mode lifecycle and inactive-engine writes before changing behavior.
2. Extract Poly engine first because it owns the largest amount of mutable gesture/selection state and registers model observers.
3. Extract Classic engine, preserving its current page-follow and playhead behavior.
4. Introduce a single active-engine render/dispatch boundary at the existing view-manager seam; avoid a second hardware event router.
5. Add lifecycle cleanup and stale-callback guards; verify observer registrations are removable or use the existing framework lifecycle if available.
6. Run all regression suites and compare the public behavior matrix above before considering the refactor complete.

No production behavior should be removed or intentionally redesigned as part of the isolation refactor. Any UX improvement is a separate opt-in change after parity is proven.
