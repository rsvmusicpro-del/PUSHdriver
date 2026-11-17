# Bitwig Studio 3.4.10 compatibility assessment

## Decision

The current `custom` branch is **not a Bitwig Studio 3.4.10 build**. It is explicitly configured around Bitwig Extension API 17, Java 17 and DrivenByMoss 19.2.2. The project's own `docs/PROJECT-CONTEXT.md` names Bitwig Studio 4.3.10 as its target.

Do not install the current API-17 artifact into Bitwig 3.4.10 and do not describe it as compatible.

## Verified upstream compatibility boundary

The upstream DrivenByMoss compatibility history and POMs establish the following:

| Upstream release | Minimum Bitwig version | Extension API | Java source/target |
|---|---:|---:|---:|
| DrivenByMoss 12.3.2 | 3.2.5+ | 12 | 12 |
| DrivenByMoss 19.2.2 | 4.3.0+ | 17 | 17 |

DrivenByMoss 12.3.2 explicitly lists Push 1 support. Bitwig 3.2.5 release notes state that Extension API 12 was introduced there, so API 12 is the appropriate compatibility baseline to investigate for Bitwig 3.4.10.

Sources:
- https://www.mossgrabers.de/Software/Bitwig/Bitwig.html
- https://github.com/git-moss/DrivenByMoss/blob/12.3.2/pom.xml
- https://github.com/git-moss/DrivenByMoss/blob/19.2.2/pom.xml
- https://downloads.bitwig.com/stable/3.2.5/Release-Notes-3.2.5.html
- https://github.com/git-moss/DrivenByMoss/blob/12.3.2/Bitwig/Changes.md

## Why changing the version number is not a valid port

The current repository pins DrivenByMoss 19.2.2 at commit
`013620c1330cdcf896e96ef8fdc8a2123e4fce6a`. Its assembly script,
overrides, virtual host and tests were written against that source/API
generation.

A GitHub comparison of upstream 12.3.2
(`7749e67a7b164cdcce5c7a13d5b38811fef18bc8`) to 19.2.2 reports 198
commits and returns 300 changed paths, including at least 232 Java source
files in the returned comparison page. This is a broad upstream evolution,
not a small API dependency bump. The current PUSHdriver assembly script also
contains version-specific source patches and must not be assumed to apply to
12.3.2.

The following project-owned work must therefore be ported and verified
against the older source rather than copied blindly:

- Push 1 LCD SysEx framing, layout, glyphs and display formatting.
- Push 1 firmware inquiry parsing and defensive MIDI/SysEx validation.
- Device/Chains/Browser integration and the In/Out physical contract: CC62/In
  enters the native Device Chains selector; CC63/Out exits to the parent; both
  are inactive outside Device mode.
- Push 1 sequencer and poly-sequencer view overlays.
- Session, mixer, transport, browser and clip-launcher behavior represented
  by the virtual runtime tests.
- API capability guards, asynchronous callback assumptions and all source
  ownership/assembly checks.

## Safe migration plan

1. Keep `custom` unchanged as the API-17 / Bitwig-4.3+ reference build.
2. Create a separate legacy compatibility line pinned to DrivenByMoss 12.3.2.
3. Rebuild source assembly from the 12.3.2 tree; do not reuse the current
   19.2.2 replacement patches unless each target is verified.
4. Port the Push 1 changes in small groups, first preserving the controller
   startup and MIDI protocol, then LCD, navigation, sequencer, Session,
   mixer and transport.
5. Adapt the virtual host to API 12 and compile/test each group against the
   actual 12.3.2 sources.
6. Run Linux and macOS CI, verify the package contains only the Push 1
   extension entry point, and then qualify it in Bitwig 3.4.10 with a
   physical Push 1.

## Current verification status

- **Confirmed:** current `custom` branch is API 17 / Java 17 / DrivenByMoss
  19.2.2 and targets Bitwig 4.3.10.
- **Confirmed:** DrivenByMoss 12.3.2 is the appropriate upstream baseline
  candidate for Bitwig 3.4.10 and includes Push 1 support.
- **Not yet implemented:** API-12 source migration of PUSHdriver.
- **Not verified:** successful API-12 build, Bitwig 3.4.10 runtime, physical
  Push 1 behavior on the legacy build.

This assessment intentionally does not change the production source pin or
publish a falsely labelled legacy binary. A version-only downgrade would
produce an untrustworthy result.
