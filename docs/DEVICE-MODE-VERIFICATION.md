# Device mode candidate verification

Candidate branch: `feature/push1-in-out-device-hierarchy`  
Target: Push 1 / Bitwig Studio 4.3.10 / Extension API 17  
Upstream: DrivenByMoss 19.2.2 at `013620c1330cdcf896e96ef8fdc8a2123e4fce6a`

## CI evidence

No GitHub Actions run has yet been recorded for this feature branch. The successful run linked below belongs to the earlier Device-mode candidate and predates the current In/Out and nested-chain selector changes; it is historical evidence only:

https://github.com/rsvmusicpro-del/PUSHdriver/actions/runs/43619358530

The current branch adds virtual runtime assertions for:
- In entering the first slot chain and restoring the device selector when Banks mode was active.
- Out returning to the parent cursor device.
- Device Chains selection restoring the Device selector after Banks mode.
- ROW1 selecting nested-chain devices in both directions through the cursor-device API.
- Browser content-type paging retaining its established direction.

The current branch still requires the repository quality-gate workflow to run before these changes can be called CI-verified. The workflow is configured for the protected `custom` branch and pull requests targeting it; no pull request has been opened as part of this task.

## Qualification boundary

This CI run does not launch Bitwig Studio 4.3.10 or connect a physical Push 1. The final acceptance step remains a live test in Bitwig with the controller attached, confirming that the device selector row, nested-chain device selection, Device action row, parameter encoders, In/Out hierarchy navigation and Chains behave as expected on the actual host and hardware.
