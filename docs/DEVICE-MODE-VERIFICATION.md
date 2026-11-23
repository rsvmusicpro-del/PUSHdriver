# Device mode candidate verification

Candidate PR: #38  
Target: Push 1 / Bitwig Studio 4.3.10 / Extension API 17  
Upstream: DrivenByMoss 19.2.2 at `013620c1330cdcf896e96ef8fdc8a2123e4fce6a`

## CI evidence

GitHub Actions run: https://github.com/rsvmusicpro-del/PUSHdriver/actions/runs/43619358530

The run completed successfully on both supported CI jobs:

- Linux: source assembly reproducibility, Java 17 build, Push 1 package verification, full virtual protocol/runtime suite and extended fuzzing.
- Intel macOS: Java 17 build, Push 1 package verification, full virtual protocol/runtime suite and extended fuzzing.

The virtual runtime suite boots the production `PushControllerSetup` and dispatches virtual MIDI through registered production bindings. The Device assertions verify the restored Ableton Push 1 row roles, selector/action behavior, parameter-page selection, In/Out navigation and Device Chains selection.

The generated artifact is named `PUSHdriver-bwextension` and is attached to the successful workflow run above.

## Qualification boundary

This CI run does not launch Bitwig Studio 4.3.10 or connect a physical Push 1. The final acceptance step remains a live test in Bitwig with the controller attached, confirming that the device selector row, Device action row, parameter encoders, In/Out and Chains behave as expected on the actual host and hardware.
