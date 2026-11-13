# 0014. Platform adapter modules: JVM-tested logic behind a thin glue seam

- Status: accepted
- Date: 2026-11-13

## Context
Driven adapters for platform services (first: Android MIDI in `:platform:android-midi`) hold real logic: device
listing and ids, open/close rules, parsing, timestamp clamping. The platform API itself (`android.media.midi`)
cannot run in a JVM unit test, yet the project keeps 100% coverage and runs every port contract test on each
implementation.

## Decision
- Platform adapters live in `platform/<name>` modules, package `pianoanchor.platform.<name>`, built with the
  `pianoanchor.kmp-library` convention (Android target plus the desktop JVM target, which here only runs tests).
  They depend on `:core:ports` and may use `:engine` helpers such as `MidiParser`.
- The adapter logic is in `commonMain` and talks to the platform only through a small internal seam interface
  (e.g. `MidiSystem`). It runs on the JVM against a fake seam, contract tests included, at 100% coverage.
- The seam's real implementation (the glue, in `androidMain`) only translates calls and makes no decisions. It is
  `@ExcludeFromCoverage` and verified on a device: by the owner's manual test until device tests exist.
- `pianoanchor.platform` is an adapter root: domain code may not import it (architecture test).
- Alternative rejected: Robolectric or instrumented tests for all adapter logic. Slower, and they would still not
  exercise real MIDI hardware; the seam keeps the untested part small and obvious.

## Consequences
Adding a platform adapter means writing its seam, glue and a `Fake*` of the seam next to its tests. Bugs in glue
are found only on a device, so glue must stay thin; anything with a branch worth testing moves behind the seam.
