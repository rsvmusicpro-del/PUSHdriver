# Device mode verification

Implementation branch: `feature/push1-in-out-device-hierarchy`  
Pull request: [#76](https://github.com/rsvmusicpro-del/PUSHdriver/pull/76)  
Target: Push 1 / Bitwig Studio 4.3.10 / Extension API 17  
Upstream: DrivenByMoss 19.2.2 at `013620c1330cdcf896e96ef8fdc8a2123e4fce6a`

## CI evidence

The current PR head (`1978a600aef6b3dbaa7a976ee27275138dd4b715`) passed GitHub Actions run [#1116](https://github.com/rsvmusicpro-del/PUSHdriver/actions/runs/44407224264) on Linux and Intel macOS. The aggregate `quality-gate` also passed. Linux CI ran all 37 Python unit tests, 224 view/seed scenarios (57,344 generated actions), built and verified the 2,101-class Push 1-only package, and killed the injected empty-clip Java mutant before restoring and rebuilding the clean package.

The virtual runtime exercises the production PushControllerSetup MIDI bindings and now verifies:

- In enters the first available slot chain and restores ROW1 device selectors after Banks mode.
- Out returns to the parent cursor device, including a parent that is itself nested.
- Device Chains selection restores the Device selector after Banks mode.
- Nested ROW1 selection moves the Bitwig cursor in both directions.
- The exhaustive ROW1 boundary matrix covers 288 combinations across chain sizes 1–8: 204 valid current/target pairs and 84 unavailable button targets. Missing children must not select a different device or issue cursor movement.
- Browser content-type paging retains its established direction.
- The repository's extended delayed-callback/interleaving and fuzz suite passes on both CI platforms.
- Classic and Poly Sequencer empty-slot tests verify that rendering, playhead feedback, note editing and long-press inspection do not read clip data when `doesExist()` is false.
- The deterministic fuzzer can replay a single view and seed; on failure it binary-searches and confirms the shortest failing action prefix, retaining the original trace and a reduced report.
- Linux CI proves the empty-clip regression kills an injected Java production mutant; this is one targeted mutation smoke test, not a full PIT mutation score.

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
host callback that never arrives, an unrelated nested cursor update during
chain entry, and exact host-callback pairing while the bounded queue overflows.
A new production-MIDI regression verifies that a rejected ROW1 press is still
consumed and cannot bypass serialization through DeviceParamsMode's direct
selection fallback. Device Chains selection, LED state and both LCD renderers
are also guarded against transient null slot-chain arrays.
Nested sibling moves require the exact expected adjacent index; chain entry
additionally confirms the first child.
A bounded timeout cancels stale queued intent rather than applying it to a
different hierarchy level. ENTER also requires the cursor to report the first
child, not merely any nested cursor. API 17 does not expose a parent-device
handle or expected parent ID for `selectParent()`: EXIT therefore has to use a
cursor-fingerprint change as its confirmation. A concurrent external cursor
move during that exact wait window remains indistinguishable from the parent
transition at the API boundary; this limitation is documented rather than
claimed as fully eliminated.

The simulator validates this asynchronous ordering contract, but it does not
run Bitwig Studio or physical Push 1 hardware. A live hardware check remains
necessary to qualify host-specific callback latency and LED refresh timing.


## Navigation latency optimization

Device hierarchy confirmation now uses adaptive polling: 12 ms, 16 ms, 24 ms, 36 ms, then 73 ms intervals. This reduces the extension's detection overhead immediately after a cursor command while avoiding sustained high-frequency polling during slow host transitions. The maximum confirmation budget remains 2.92 seconds. The virtual runtime verifies the 12/16/24/36 ms initial probe sequence, that none can complete before the delayed host callback, that serialized In/Out/ROW1 actions still drain, and that a missing callback is bounded by the 2,920 ms confirmation budget. The final polling interval is shortened to the remaining budget rather than allowing the nominal 73 ms backoff to overshoot it. This optimizes extension-side detection latency; it does not claim to shorten Bitwig's own internal cursor-update time. Physical latency percentiles must be measured in a live Bitwig session before quoting a hardware performance gain.


## Timestamped navigation measurements

Optional live instrumentation is disabled by default and has no per-navigation logging overhead while disabled. To enable it, launch Bitwig with the environment variable `PUSHDRIVER_NAV_TIMING=1` set, then open the Bitwig Controller Script Console. On Linux, launch Bitwig from a terminal with:

```sh
PUSHDRIVER_NAV_TIMING=1 bitwig-studio
```

On macOS, launch the Bitwig executable from a terminal with the same environment variable, for example:

```sh
PUSHDRIVER_NAV_TIMING=1 "/Applications/Bitwig Studio.app/Contents/MacOS/BitwigStudio"
```

Each completed Device hierarchy action emits one `PUSHdriver_NAV_TIMING` line. It records queue wait, time from the first Bitwig cursor command to observed cursor confirmation, total extension-side duration, and poll count using `System.nanoTime()`. Timeouts are logged separately. Export/copy those console lines to a text file and calculate P50/P95/P99 with:

```sh
python3 scripts/analyze_navigation_latency.py bitwig-controller-console.txt
```

These figures start when the PUSHdriver Java command handler enqueues the action. They do **not** include the physical button's USB/MIDI delivery before the handler, nor the Push LED/LCD refresh after cursor confirmation. Therefore they isolate extension queueing and Bitwig cursor-confirmation time; a separate external timestamp is still needed to measure button-press-to-handler and visual-feedback latency.
