# Upstream source provenance

PUSHdriver assembles its Java source tree during the build from the upstream
DrivenByMoss 19.0.0 source revision:

- Repository: https://github.com/git-moss/DrivenByMoss
- Release: 19.0.0
- Immutable commit: `40057d6e7784974f523139dc0cebe62f4bde1f26`
- License: LGPL-3.0 (see upstream source headers and repository license)

The complete upstream repository is now attached to this project as the `upstream/`
Git submodule, pinned to the immutable commit above. Clone with
`git clone --recurse-submodules` (or initialize submodules after cloning) to
retrieve all external source files and upstream build resources alongside this
project. GitHub Actions checks out submodules recursively, so builds no longer
need a separate checkout of a moving external reference.

The `scripts/prepare_push1.py` step gathers the shared framework, Bitwig adapter,
Push controller implementation, Maven metadata, local Maven repository and
required resources from `upstream/` into this project's build tree. It then
applies the Push 1-only adaptations and removes Push 2 and Raindrops
implementations. The package verifier checks that every assembled Java source
has a corresponding compiled class in `PUSHdriver.bwextension`, and that only
the Push 1 Bitwig extension entry point is registered.
