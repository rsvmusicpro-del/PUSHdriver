# Device mode candidate verification

Candidate PR: #38  
Target: Push 1 / Bitwig Studio 4.3.10 / Extension API 17  
Upstream: DrivenByMoss 19.2.2 at `013620c1330cdcf896e96ef8fdc8a2123e4fce6a`

## CI evidence

GitHub Actions run: https://github.com/rsvmusicpro-del/PUSHdriver/actions/runs/43619358530

The run completed successfully on both supported CI jobs:

- Linux: source assembly reproducibility, Java 17 build, Push 1 package verification, full virtual protocol/runtime suite and extended fuzzing.
- Intel macOS: Java 17 build, Push 1 package verification, full virtual protocol/runtime suite and extended fuzzing.

The virtual runtime suite boots the production `PushControllerSetup` and dispatches virtual MIDI through registered production bindings. The Device assertions verify the restored Ableton Push 1 row roles, selector/action behavior, In opening Device Chains, top-row chain selection, Out returning to the parent device, unsupported/empty-chain guards and no-op behavior outside Device mode.

The generated artifact is named `PUSHdriver-bwextension` and is attached to the successful workflow run above.

## Qualification boundary

This baseline CI run does not launch Bitwig Studio 4.3.10 or connect a physical Push 1. The revised In/Out chain-entry/exit scenarios are added on the dedicated work branch and still require its own CI run, followed by a live Bitwig test with the controller attached.
