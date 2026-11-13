# Roadmap

Living plan for Piano Anchor. Update the status column in the same PR that changes it.
Status: ✅ done · 🔄 in progress · ⏳ planned.

## Phase 0 — Foundation
| Step | Status |
|---|---|
| Build scaffolding: KMP + Compose, formatter, detekt, 100% coverage gate, `fastCheck` | ✅ |
| CI, PR conventions (Conventional Commits, DCO), templates, contributor guide | ✅ |
| Architecture rules (Konsist) | ✅ |
| Security and supply-chain checks (CodeQL, Dependency Review, actionlint) | ✅ |
| Agent guide and docs (AGENTS.md, ARCHITECTURE, ADRs, glossary, docs check) | ✅ |
| Developer hooks (git hooks, Claude Code hooks) | ✅ |
| Phase 0 exit verification (negative checks fail CI) | ✅ |

## Phase 1 — Walking skeleton
Connect a piano → play C-D-E in wait mode → see a graded result → it is saved. One thin slice through every
layer (MIDI port and adapters, parser, matcher, grading, storage, one screen), then extract agent skills
and scaffolding from the real code.

| Step | Status |
|---|---|
| MIDI event types and byte-stream parser | ✅ |
| MIDI input/output ports, `FakePiano`, contract tests (1.2) | ✅ |
| Android MIDI adapter over `MidiManager`, USB + system-opened BLE (1.4a) | ✅ |
| Bluetooth LE MIDI discovery, connect and reconnect (1.4b) | ✅ |
| Note monitor with JSONL fixture export (1.4m) | ✅ (owner hardware check pending) |
| Monitor gives up reconnecting after `MONITOR_RECONNECT_GIVE_UP_MS` | ✅ |
| Instrument identity via MIDI Identity Request, in-memory instrument store (1.5) | ✅ (hardware check pending) |
| Wait-mode matcher and the built-in C-D-E passage (1.6) | ✅ |
| Grading (accuracy, band) and the attempt storage port (1.7) | ✅ |
| SQLDelight storage adapter for instruments and attempts (1.8) | ✅ (on-device check with the hardware check) |

## Next up
The task queue for `/next-task` (`.claude/skills/next-task/`): one item per session and PR, top first.
Remove an item in the PR that finishes it; add follow-ups here. Dependencies in brackets.

1. **Hardware check** (owner): install the debug app (the `app-android-debug-<sha>` artifact of the latest CI run
   on main, or `./gradlew :app-android:assembleDebug`), connect a piano over BLE, play keys, survive a disconnect
   and export a C-D-E fixture into `fixtures/` (first hardware check of the 1.4a `MidiManager` glue, the 1.4b
   scan/connect glue and the 1.4m Android wiring); note whether the piano answers the 1.5 Identity Request
   (the monitor shows "model …") and that the piano is still known after an app restart (1.8 storage). If a piano
   known to answer shows none, the request may race the input port's open: `MidiInputPort` would then need an
   "opened" signal.
2. **Slice 1.9** — wait-mode practice screen (Living Studio mock): spec #40 [#38].
3. **Slice 1.10** — results screen (Living Studio mock): spec #41 [#40]. Then the owner's end-to-end check on a
   phone and piano (play C-D-E, see Results, the run survives an app restart) closes Phase 1.
4. **Emulator end-to-end CI job** [first device test]: when the first instrumented end-to-end test lands (ADR 0007),
   run it on Gradle Managed Devices (ATD image, KVM on `ubuntu-latest`) on pushes to main and nightly, not on every
   PR. Settle first how tests feed MIDI without a piano (an in-app `MidiDeviceService` fake).
5. **Fix: hook tests inherit `PIANO_ANCHOR_STOP_HOOK`** (mechanical): `scripts/test_hooks.py`'s Stop-hook tests pass
   the caller's environment, so in a cloud dispatcher session (`PIANO_ANCHOR_STOP_HOOK=subagent-only`) 8 of them fail
   and `fastCheck` goes red there. Clear the variable in the tests unless a test sets it.
6. **Storage failure model** (decision): with SQLite behind the repository ports, `upsert`/`save` can fail (disk
   full, corrupt file), and the monitor's identify step doesn't catch it, so one failure ends MIDI collection. Decide
   the ports' error model (e.g. a domain `StorageUnavailableException`) and how the monitor and practice screen react.
7. **Keep a known identity on a missed reply** (decision): `InstrumentRepositoryPort.upsert` replaces the identity, so
   a reconnect whose Identity Reply is lost erases a stored model. Decide whether a null identity keeps the stored one
   (contract change, then `COALESCE` in `Instrument.sq`).

Item 1 doesn't block the slices; once it lands its C-D-E fixture, add a replay test of it to `MatchWaitModeFlowTest`.

## Phase 2+ — Features
| Milestone | Scope | Exit check |
|---|---|---|
| M1 | MIDI input, real-time matcher, offline analyser | Recorded fixtures with injected errors classified ≥ 95% correctly |
| M2 | Learner model (skill graph) and spaced-repetition scheduler (FSRS-6) | Matches reference test vectors; simulated learner behaves sensibly |
| M3 | First playable coach (units 0–2), daily plan, first APK release | Used daily for 2 weeks |
| M4 | Songs & chords and ear-training tracks; contribute-a-piano kit | All tracks share the daily plan |
| M5 | Guidance fading, practice/perform modes, strictness setting | Unaided pass required before "learned" |
| M6 | Curriculum units 3–8, sight-reading staircase, placement | Placement reproducible |
| M7 | Heatmaps, recordings, calibration report | Predicted vs observed retention plotted |
| M8 | Optional Google Drive sync | Round-trip sync between two devices |
| M9 | Kids (ages 6–8+): research pass, age-appropriate content | — |
| Later | iOS; more piano brands | — |
