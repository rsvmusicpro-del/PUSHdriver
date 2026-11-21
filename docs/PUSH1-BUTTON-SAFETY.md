# Push 1 universal button-event safety

## Scope and integration point

PUSHdriver uses the pinned DrivenByMoss AbstractHwButton implementation as the shared state and dispatch point for physical hardware buttons. The Push 1 build now applies a narrowly scoped source patch there, so the protection is inherited by every Push 1 IHwButton instance rather than being duplicated in individual commands or views.

This covers the Push's momentary hardware buttons, the 64 pad button objects, and footswitch button objects represented through the same hardware-button abstraction. It does not intercept encoder deltas, encoder-touch controls, ribbon/pitch-bend, pad aftertouch, or arbitrary MIDI traffic; those are different continuous or expressive event classes and must not be throttled as buttons.

## Guarantees

- A DOWN edge is accepted only when that button is not already down.
- An UP edge is accepted only when that button is currently down and bound.
- A complete DOWN → UP cycle is always accepted, even if the next tap follows immediately. There is intentionally no global time-based cooldown.
- State is independent per button, preserving simultaneous modifiers and chords.
- Each press owns a generation token. A delayed LONG callback is ignored after release, a newer press, or clearState().
- A LONG callback is accepted only while its own press remains in DOWN state, so duplicate scheduler delivery cannot emit LONG twice.
- Accepted DOWN/UP/LONG event order, velocity, command dispatch and handler dispatch remain unchanged.

## Why there is no fixed debounce delay

A fixed debounce interval applied to all buttons would suppress legitimate rapid taps and could break Push gestures. The API 17 callback layer does not expose raw electrical switch-contact samples or hardware timestamps that would let the extension distinguish physical contact bounce from a genuine very fast tap with certainty. This implementation therefore performs lossless state-transition de-duplication and stale-callback protection; it does not claim to electrically debounce the Push's switches.

## Verification

AbstractHwButtonSafetyTest exercises the actual assembled DrivenByMoss button class: repeated edges, 10,000 complete rapid taps, delayed and duplicated long-press callbacks, state clearing, and simultaneous button chords. The complete virtual Push 1 runtime/fuzz suite and both CI platform jobs remain mandatory. Physical switch-bounce characterization still requires timestamped observation of a real Push 1 and must not be inferred from the virtual bench.
