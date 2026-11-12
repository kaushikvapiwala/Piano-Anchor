# Architecture

Piano Anchor uses **hexagonal architecture** (ports and adapters) inside a Kotlin Multiplatform build.
The domain knows nothing about Android, MIDI hardware, databases or UI; those plug in through ports.
Rules in this document are enforced by `architecture-tests/` (Konsist) and the coverage gate.

```
   driving adapters                    domain (pure Kotlin)                  driven adapters
 ┌──────────────────┐        ┌───────────────────────────────────┐        ┌───────────────────┐
 │ ui/shell, feature│ calls  │ engine: matcher, analysis, learner │ calls  │ platform MIDI,    │
 │ app-android      │ ─────▶ │ model, scheduler, session          │ ─────▶ │ storage, clock,   │
 │ app-desktop      │        │ core/model · core/ports (*Port)    │  ports │ instrument drivers│
 └──────────────────┘        └───────────────────────────────────┘        └───────────────────┘
```

## Modules

| Module | Role | Notes |
|---|---|---|
| `:core:model` | Domain types (e.g. `MidiNote`, `MidiEvent`) | Pure Kotlin, 100% coverage |
| `:core:ports` | Driven ports (e.g. `ClockPort`) | Interfaces only, each with KDoc |
| `:engine` | Coaching logic, including the `midi` byte-stream parser; `Tunables` holds every threshold | Pure Kotlin, 100% coverage |
| `:testing` | Fakes and abstract port contract tests | Used by all test source sets |
| `:ui:shell` | Shared Compose root and string resources | Driving adapter; screenshot tests planned (Phase 1) |
| `:app-android` | Android app: composition root | No business logic |
| `:app-desktop` | Desktop development harness | No business logic |
| `:architecture-tests` | Konsist rules enforcing this document | Runs in `fastCheck` |

Build configuration lives in `build-logic/` convention plugins: `pianoanchor.kmp-library` (pure modules,
100% coverage gate), `pianoanchor.kmp-compose` (UI), `pianoanchor.android-app`, `pianoanchor.jvm`, and
`pianoanchor.quality` (formatting, detekt, Kover, per-module `fastCheck`). Versions: `gradle/libs.versions.toml`.
Developer hooks (`.githooks/`, `.claude/hooks/`) run the same checks locally; see `docs/adr/0011-developer-hooks.md`.
`docs/generated/` is produced by `./gradlew generateDocs` and checked by `GeneratedDocsTest`; never edit it by hand.

## Dependency rules
- Domain code (`pianoanchor.core.*`, `pianoanchor.engine`) imports only Kotlin and other domain code: no
  Android, AndroidX, Compose, Koin or `java.*`/`javax.*`.
- Domain code never imports driving adapters (`pianoanchor.ui`, `pianoanchor.app`, `pianoanchor.desktop`)
  or test support (`pianoanchor.testing`).
- Apps are composition roots: they wire adapters to ports and contain no business logic.

## Ports
- A port is a top-level interface in `core/ports`, named `*Port`, documenting its contract in KDoc.
- Ports describe what the domain needs (time, MIDI input/output, storage), not how a platform does it.

## Adapters
- An adapter implements a port outside the domain and is named `*Adapter` (class or object).
- Test doubles live in `testing/`, are named `Fake*`, and obey the same contract.

## Testing
- Each port has an abstract `<Port>ContractTest` in `testing/`; every implementation has a test class
  (`<Impl>Test` or `<Impl>ContractTest`) that extends it, so fakes and real adapters behave the same.
- Every `*ViewModel` and `*UseCase` has a `*Test`; test classes are named `*Test`.
- Pure modules need 100% line and branch coverage; `@ExcludeFromCoverage("reason")` marks the rare exception.
