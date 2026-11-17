# Device mode verification

Implementation branch: `feature/push1-in-out-device-hierarchy`  
Pull request: [#76](https://github.com/rsvmusicpro-del/PUSHdriver/pull/76)  
Target: Push 1 / Bitwig Studio 4.3.10 / Extension API 17  
Upstream: DrivenByMoss 19.2.2 at `013620c1330cdcf896e96ef8fdc8a2123e4fce6a`

## CI evidence

GitHub Actions run [#1073](https://github.com/rsvmusicpro-del/PUSHdriver/actions/runs/44181267631) completed successfully on Linux and Intel macOS. The quality-gate job also passed.

The virtual runtime exercises the production PushControllerSetup MIDI bindings and now verifies:

- In enters the first available slot chain and restores ROW1 device selectors after Banks mode.
- Out returns to the parent cursor device, including a parent that is itself nested.
- Device Chains selection restores the Device selector after Banks mode.
- Nested ROW1 selection moves the Bitwig cursor in both directions.
- The exhaustive ROW1 boundary matrix covers 288 combinations across chain sizes 1–8: 204 valid current/target pairs and 84 unavailable button targets. Missing children must not select a different device or issue cursor movement.
- Browser content-type paging retains its established direction.
- The repository's extended delayed-callback/interleaving and fuzz suite passes on both CI platforms.

## Qualification boundary

CI validates compilation, packaging, deterministic API-17 host models, production MIDI bindings, and the virtual runtime. It does not launch Bitwig Studio 4.3.10 or connect a physical Push 1.

The remaining acceptance step is a live test in Bitwig with the controller attached. In particular, verify ROW1 presses immediately after entering or leaving a chain, rapid repeated ROW1 presses, nested chains with fewer than eight Devices, and the In/Out LEDs against the actual host cursor state.


## Asynchronous hierarchy regression coverage

In, Out, Device Chains selection and ROW1 share a serialized navigation
queue. Each hierarchy transition or nested sibling movement is considered
complete only after Bitwig's cursor reports the expected state change.
Consecutive ROW1 requests that are still waiting in the queue are coalesced
to the latest target; a ROW1 selection already sent to Bitwig is immutable,
and a newer target runs only after its confirmation. If a queued target is
already the active device when reached, the original upstream reselect gesture
(Banks toggle or Layer Mix entry) is preserved. Queue overflow rejects only
the newest command and never cancels an operation already sent to Bitwig.
The virtual host deliberately delays and interleaves cursor callbacks to cover
immediate ROW1-after-In, ROW1-after-Out, two ROW1 presses during a root-device
selection, same-device reselect, queue overflow, null slot-chain banks and a
host callback that never arrives. Nested sibling moves require the exact
expected adjacent index; chain entry additionally confirms the first child.
A bounded timeout cancels stale queued intent rather than applying it to a
different hierarchy level.

The simulator validates this asynchronous ordering contract, but it does not
run Bitwig Studio or physical Push 1 hardware. A live hardware check remains
necessary to qualify host-specific callback latency and LED refresh timing.
