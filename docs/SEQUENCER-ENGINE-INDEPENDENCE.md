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

The concrete classic view currently owns playhead-follow state and its note-grid gestures. The Push Poly view now deliberately delegates note-area and step-area event handling to the pinned DrivenByMoss `AbstractPolySequencerView` implementation; its Push override is limited to the upstream long-press / Select / Shift gestures and the fundamental-color rule. The former custom persistent-selection and inline step-editor overrides were removed because they replaced upstream event semantics and made physical note/step gestures diverge from DrivenByMoss. The upstream poly base registers track-selection and note observers in its constructor. The track-selection observer can be removed through the inherited `IBank.removeSelectionObserver`, but the pinned API-17 `ITrackBank` exposes `addNoteObserver` without a corresponding `removeNoteObserver`. The observer callback therefore has no public unregister path in this API version and captures the view's key manager for the lifetime of the track bank. This is a concrete lifecycle hazard, not just a theoretical concern. The replacement must avoid installing one permanent note observer per engine: use one shared, long-lived observer at the existing controller/model boundary and route events only to the active engine, or use a verified API-17-safe active guard without retaining dead view instances. The selection observer should be stored as a stable field and explicitly removed when its owner can be deactivated.

A particularly important shared boundary is `AbstractSequencerView.getClip()`: it obtains the model's note-clip adapter for the view's configured clip dimensions. This is the correct shared DAW data boundary and must not be replaced with duplicate clip adapters or parallel MIDI stores.

The shared note editor is another intentional integration boundary: `editNote()` switches to the Note mode / Note Edit view and hands it the selected clip and note position. The two sequencers must serialize this handoff and clear their own local edit state when it takes control.

## Failure mechanisms to address

1. **Observer lifetime (confirmed API limitation):** Poly Sequencer installs a track-selection observer and a note observer from its constructor. API 17 allows removing the selection observer, but has no `removeNoteObserver`; the note callback can retain the view indefinitely and continue calling its key manager after mode changes.
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

## Implemented isolation and color safeguards

The concrete Push 1 views remain separate instances on their distinct upstream bases: SequencerView extends AbstractNoteSequencerView; PolySequencerView extends AbstractPolySequencerView. Their mutable note selection, edit-step, duration, page-follow and gesture fields are instance-owned. They share the Bitwig note clip only as the single DAW document, not as a private engine state cache.

API 17 has no removeNoteObserver on the track bank. The assembly step therefore routes the upstream Poly key-manager observer only when that exact Poly view is the active view (ViewManager.getActive() == this). This prevents the inactive Poly instance from consuming note feedback and contaminating its pressed-key state while the classic sequencer or another view is active. The observer registration itself remains host-lifetime because API 17 exposes no removal method; this is an explicit compatibility constraint, not a claim that the listener is unregistered.

The Poly keyboard fundamental is evaluated before transient selection/playback highlighting. Its pad color is always resolved through the selected track's DAW color path, so selecting or physically holding the fundamental cannot replace the track identity color. A Python regression test protects both this priority and the active-view observer routing.

## Poly Sequencer interaction restoration

The Push-specific Poly implementation had overridden both `handleNoteArea` and `handleSequencerArea`. That duplicated the upstream event state machine and changed the established timing/meaning of note-pad presses, step release toggles, occupied-step clearing and long-press note editing. It also introduced a second inline step editor that was not part of the pinned DrivenByMoss interaction model. These overrides have been removed. The pinned upstream `AbstractPolySequencerView` now owns the actual note and step event flow again; the Push-specific class retains the upstream long-press step inspection and Select/Shift combinations, plus only the required fundamental track-color override.

The runtime regression now drives the physical virtual MIDI pads: it selects notes in the lower keyboard, programs the upper step grid, verifies the upstream occupied-step toggle behavior, checks the long-note continuation feedback and checks that the fundamental remains in the track color both while held and released. This deliberately tests the same event route used by the physical Push instead of calling private/custom editor helpers directly.


## Engine kernel implementation — 2026-11-15

The first production slice introduces a Java 17 engine kernel under
`de.mossgrabers.controller.ableton.push.engine.sequencer`:

- `SequencerEngine` defines an explicit activation contract and immutable
  `ActivationToken` record.
- `AbstractSequencerEngine` serializes lifecycle transitions and advances a
  monotonic generation on activation/deactivation. Deferred work can capture the
  token and call `accepts(token)` before applying its result; stale work from
  an earlier activation is rejected. The new sequencer adapters use this
  lifecycle contract for ownership; wiring every existing delayed callback to
  tokens remains part of the later per-domain migration.
- `ClassicSequencerEngine` owns Push-specific page-follow/page-lock state.
  The view acts as the Bitwig/Push adapter for page and playhead inputs.
- `PolySequencerEngine` owns Push grid bounds, bottom-up step coordinate
  conversion and scale-fundamental classification. The view delegates these
  decisions to its engine.

The implementation uses Java 17 records and synchronized lifecycle transitions
without a third-party runtime dependency. Bitwig's note clip remains the single
source of truth for musical data; DrivenByMoss remains the owner of the Poly
note-memory and note/step event state machine. No parallel MIDI scheduler,
clip cache, LED writer or second controller event router has been introduced.

**Migration boundary:** this commit establishes the reusable engine contract
and extracts the first two Push-specific sequencer engines. It does not claim
that every DrivenByMoss feature (Drum, Session, Note, Mixer, Device, Browser,
Transport, etc.) has already been migrated. Those feature engines must be
extracted one domain at a time behind the same lifecycle contract, preserving
upstream behavior and the source-ownership gate. Claiming all modes are
migrated before their callbacks and state ownership are moved would be a false
architectural guarantee.

The deterministic `SequencerEngineTest` is part of the mandatory virtual
bench and covers token invalidation, classic page-follow policy, Push pad
bounds, Poly step mapping and scale-fundamental classification.


## Shared physical pad-grid contract — 2026-11-15

The physical Push 1 8x8 pad matrix now has one project-owned definition in
`Push1PadGrid`. It owns the first MIDI note, the 64-pad count, MIDI-note
membership, note-to-index conversion, and index validation (including a
bounded-size overload for logical grids).

Both Classic and Poly sequencer engines delegate their pad-index checks to
this contract. Their long-press adapters validate the translated index before
resolving a hardware button. The source assembler also uses the same contract
for upstream Drum4, Drum8, DrumView and AbstractDrum64View long-press bounds.
The dynamic Drum64 grid continues to use its own columns × rows dimensions.

This consolidation changes only invalid-index handling. It does not change
valid pad mapping, note routing, gesture ownership, clip mutation or LED
rendering. Java boundary tests and Python source-contract tests cover both
sequencer adapters and the generated Drum guards.
