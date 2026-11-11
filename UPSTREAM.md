# Upstream source provenance

PUSHdriver assembles its Java source tree during the build from the upstream
DrivenByMoss 19.0.0 source revision:

- Repository: https://github.com/git-moss/DrivenByMoss
- Release: 19.0.0
- Immutable commit: `40057d6e7784974f523139dc0cebe62f4bde1f26`
- License: LGPL-3.0 (see upstream source headers and repository license)

The build workflow checks out this exact commit into `upstream/`. The
`scripts/prepare_push1.py` step then gathers the shared framework, Bitwig
adapter, Push controller implementation and required resources into this
project's Maven source tree, applies the Push 1-only adaptations, and removes
the Push 2 and Raindrops implementations.

The generated source tree is deliberately assembled in the build workspace;
it is not a checked-in mirror of the full upstream repository. The package
verifier checks that every assembled Java source has a corresponding compiled
class in `PUSHdriver.bwextension`, and that only the Push 1 Bitwig extension
entry point is registered.
