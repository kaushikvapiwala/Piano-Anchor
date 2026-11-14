# AGENTS.md — instructions for AI coding agents (and humans)

Piano Anchor: an open-source, offline Android piano coach. It reads a digital piano over MIDI, diagnoses
weak spots and plans each day's practice. Kotlin Multiplatform + Compose Multiplatform, hexagonal design.
Read `docs/ARCHITECTURE.md` before changing structure; domain terms are in `docs/glossary.md`.

## Commands
- `./gradlew fastCheck` — the gate: formatting, detekt, unit tests, 100% coverage, architecture rules, docs and hook tests
- `./gradlew installGitHooks` — once per clone: git hooks format, run `fastCheck` and check sign-off (`.githooks/`)
- Claude Code cloud sessions: the `SessionStart` hook runs `scripts/cloud_setup.sh` (Android SDK into `~/android-sdk`; installs the git hooks, whose pre-push skips `ciBuild` there because CI runs it; no-op unless `CLAUDE_CODE_REMOTE=true`; also a Gradle init script that mirrors Maven Central because it answers 429 there), so `fastCheck` works there; Gradle fetches JDK 25 itself
- `./gradlew generateDocs` — regenerate `docs/generated/` from the code. Generated docs are regenerated, never hand-edited (`GeneratedDocsTest` fails when they are stale)
- `./gradlew spotlessApply` — auto-format (ktlint official style). Never hand-format.
- `./gradlew ciBuild` — everything CI's build job runs (`fastCheck` and `check` in every module, both apps); `./gradlew :app-android:assembleDebug`, `./gradlew :app-desktop:run`
- One module: `./gradlew :engine:desktopTest`, `./gradlew :architecture-tests:test`

## Before writing code (in order; stop at the first "yes")
1. Does this need to exist at all? Can the requirement be met by changing or deleting code?
2. Does the codebase already do it? Search first; reuse the existing port, helper or pattern.
3. Does the Kotlin stdlib, Compose or the Android platform already provide it?
4. Does a dependency we already have provide it?
5. Does a maintained, licence-compatible library provide it? Apache-2.0/MIT/BSD/EPL only, never GPL/AGPL/LGPL;
   Kotlin Multiplatform for shared modules. Propose it with a new `docs/adr/` record. No dependency for a few
   lines of code. `core/*` and `engine` stay free of non-kotlinx libraries: libraries live in adapters behind
   ports, and every spec fills "Hexagon placement" to say where each new type goes.
6. Only then write new code: the smallest version that is tested. Never cut validation, error handling,
   accessibility or tests to make code shorter.

## Architecture rules (most enforced by `architecture-tests/`; magic numbers by detekt)
- Domain = `core/model`, `core/ports`, `engine`: pure Kotlin, no Android/AndroidX/Compose/Koin/`java.*`.
- Ports are interfaces in `core/ports` named `*Port`, with KDoc and an abstract `<Port>ContractTest` in `testing/`.
- Adapters implement ports outside the domain, are named `*Adapter`, and have a test extending the contract test.
  Test doubles live in `testing/` and are named `Fake*`.
- Thresholds live in `engine/src/commonMain/kotlin/pianoanchor/engine/Tunables.kt`, each with `Why:` and `Source:` in its KDoc. No magic numbers.
- Every `*ViewModel` and `*UseCase` has a `*Test`. Use cases only for logic shared by several callers.
- An input has one reader at a time (`AndroidMidiAdapter.events` throws on a second open): when a screen hands a
  piano to another, the old reader closes it (`cancelAndJoin`) before the next one opens it.
- UI text goes in Compose resources (`composeResources/values/strings.xml`), never hard-coded (review-enforced until a lint rule lands).

## Testing
- Pure-Kotlin modules: **100% line and branch coverage** (Kover fails the build). If code truly can't run on the
  JVM, annotate `@ExcludeFromCoverage("reason + where it is tested")`.
- Kover counts `companion object` `val` getters as uncovered lines: put private constants at file top level instead.
- Robolectric breaks on a checkout path with spaces: keep `robolectric.dependency.dir` under the Gradle user home,
  as `feature/monitor/build.gradle.kts` does (copy that setup into new modules with Robolectric tests).
- Write the failing test first for behaviour changes. Use fakes, not mocks. Name tests after behaviour.
- A fake that calls back on another thread must change shared state (e.g. a `StateFlow`) on the caller's thread:
  while `runTest`'s body waits on another thread, virtual time can skip through background `delay`s.
- Pyramid: unit (JVM) → integration (contract tests on real adapters) → UI (screenshots, Compose UI tests) →
  few end-to-end tests on an emulator. A real piano is a manual release checklist.

## Definition of done (all must be true before you say "done")
1. Tests added or updated, and `./gradlew fastCheck` passes. Show the command output.
2. Docs updated: `docs/ARCHITECTURE.md` for structure, `docs/glossary.md` for new terms,
   `docs/algorithms/` for engine behaviour, `docs/ROADMAP.md` when scope or milestone status changes,
   a new `docs/adr/` record for any new dependency or architectural decision.
3. Self-review: correctness review, then a cleanup pass (reuse, simplification, efficiency).

## Workflow
- One feature per PR, on a branch (`feat/…`, `fix/…`, `docs/…`, `ci/…`, `chore/…`). Never commit to `main`.
- PR title = Conventional Commit (`feat(engine): …`); it becomes the squash commit. Sign off: `git commit -s`.
- Never force-push or rewrite pushed history; add commits. Never commit secrets, `local.properties`,
  build output or `.claude/worktrees/`.
- AI loop: an Opus session orchestrates and reviews; the `implementer` subagent (`.claude/agents/`, Sonnet)
  writes code from one spec issue and can't finish while `fastCheck` fails (`SubagentStop` hook; `.claude/hooks/stop_fastcheck.py` also skips when another Gradle build runs, and the cloud dispatcher sets `PIANO_ANCHOR_STOP_HOOK=subagent-only` so only subagent stops run it); Haiku
  subagents only locate and quote. Specs are GitHub issues (label `spec`), never committed.
- **Ratchet rule:** when review catches a new kind of mistake, add the cheapest permanent guard: an
  architecture or detekt rule, a test, or one line in this file.

## Never
- Copy code from GPL projects, piano-vendor apps or commercial learning apps (clean-room; Apache-2.0 repo).
- Write a piano setting (SysEx address) that isn't documented and verified; probe unknown ones with reads only.
- Bundle copyrighted music. Pieces must be public domain (with a rights record) or original.
- Add network calls, analytics or accounts. Learner data stays on the device (sync is opt-in, later).
