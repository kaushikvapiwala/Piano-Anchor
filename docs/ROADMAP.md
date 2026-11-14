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
| Wait-mode practice screen, Living Studio palette and fonts (1.9) | ✅ |
| Results screen: headline by band, Pitch tile, Try again (1.10) | ✅ |
| **Walking skeleton** | ✅ pending the owner's end-to-end hardware check (Next up 1) |

## Next up
The task queue for `/next-task` (`.claude/skills/next-task/`): one item per session and PR, top first.
Remove an item in the PR that finishes it; add follow-ups here. Dependencies in brackets.

1. **Hardware check** (owner): install the debug app (the `app-android-debug-<sha>` artifact of the latest CI run
   on main, or `./gradlew :app-android:assembleDebug`), connect a piano over BLE, play keys, survive a disconnect
   and export a C-D-E fixture into `fixtures/` (first hardware check of the 1.4a `MidiManager` glue, the 1.4b
   scan/connect glue and the 1.4m Android wiring); note whether the piano answers the 1.5 Identity Request
   (the monitor shows "model …") and that the piano is still known after an app restart (1.8 storage). If a piano
   known to answer shows none, the request may race the input port's open: `MidiInputPort` would then need an
   "opened" signal. Also open Practice from the monitor, play C-D-E, and check the monitor shows the piano connected
   again afterwards (1.9: the monitor closes the input for practice and reopens it on return). End to end (closes
   Phase 1): play C-D-E, see Results (1.10) with "run N", Try again, and the run count survives an app restart.
2. **Emulator end-to-end test and CI job** (spec #57): the C-D-E loop on a Gradle Managed Device (ATD image, KVM on
   `ubuntu-latest`), fed by a test-only virtual MIDI piano (`MidiDeviceService`); runs on pushes to main and nightly,
   not on every PR.
3. **Keep a known identity on a missed reply** (spec #56): a null identity in `upsert` keeps the stored one
   (contract change, `COALESCE` in `Instrument.sq`).
4. **M1.1 Rhythm in passages** (spec #59): step onsets and note lengths in ticks (960 per quarter), time signature
   and written tempo; wait mode ignores them.
5. **M1.2 Played notes and the analyser harness** (spec #60) [4]: events to played notes with the sustain pedal, seeded
   clean takes, labelled error injection and the classification score.
6. **M1.3 Offline performance analyser** (spec #61) [5]: alignment, tempo fit and every M1 error kind; lands the M1
   exit test (≥ 95 % overall, ≥ 90 % per kind, ≤ 5 % false alarms).
7. **M1.4a Metronome port and Android click adapter** (spec #63): `MetronomePort`, synthesised clicks on a
   low-latency `AudioTrack` in a new android-audio platform module.
8. **M1.4b Staff rhythm** (spec #64) [4]: time signature, bar lines, hollow half and whole notes, proportional
   spacing.
9. **M1.4c Tempo-mode runs** (spec #65) [6, 7, 8]: tempo rungs, one-bar count-in, clock-driven cursor, live judge,
    metronome, attempts store tempo and played notes (schema 1→2).
10. **M1.4d Results tiles** (spec #66) [9]: Timing, Note length and Hesitation tiles from the analyser; tempo in the
    caption.
11. **M1.5 Latency calibration** (spec #67) [7, 9, 10]: tap along to 16 clicks, a calibration port, offered before a
    piano's first timed run and from the monitor (schema 2→3).
12. **M2.1 FSRS-6 memory model** (spec #69): pure-Kotlin FSRS-6 in the engine, checked against committed py-fsrs
    golden vectors.
13. **M2.2 Skill graph** (spec #70) [4]: skills with prerequisites (validated DAG); passages declare their skills.
14. **M2.3 Learner storage** (spec #71) [12, 13]: review log plus cached skill state behind a learner repository port;
    adds kotlinx-datetime (ADR).
15. **M2.4 Learner model** (spec #72) [12, 13, 14]: cold checks rated from the band, learned rule, due list and daily
    limits; recorded after every saved run.
16. **M2.5 Learner simulation exit check** (spec #73) [15]: seeded 90-day simulation (retention, load, prerequisite
    order, recovery after a skip).
18. **M3.1 Content format** (spec #76) [5, 14]: note text, rights records, units; the rights document generated from the
    content.
19. **M3.2 Units 0–2 content** (spec #77, needs-owner: owner reviews the skill and piece list) [18].
20. **M3.3 Settings** (spec #78) [3, 10]: settings port on SQLite and a Settings screen.
21. **M3.4 Today and session composer** (spec #79) [16, 19, 20]: cold open, lesson and song cards; Today becomes the
    start screen.
22. **M3.5 Onboarding** (spec #80) [12, 16, 19, 20, 21]: connect, echo C-E-G, calibrate, choose a start unit.
23. **M3.6 Update check** (spec #81) [20, 21]: the opt-out daily check, the app's only network call.
24. **M3.7 Release pipeline** (spec #82) [2]: tag-driven signed APK on GitHub Releases; then v0.1.0 and the dogfood
    exit check.

Item 1 doesn't block the slices; once it lands its C-D-E fixture, add a replay test of it to `MatchWaitModeFlowTest`.

## Phase 2+ — Features
| Milestone | Scope | Exit check |
|---|---|---|
| M1 | MIDI input, real-time matcher, offline analyser (🔄 specced: #59–#61, #63–#67) | Recorded fixtures with injected errors classified ≥ 95% correctly |
| M2 | Learner model (skill graph) and spaced-repetition scheduler (FSRS-6) (specced: #69–#73) | Matches reference test vectors; simulated learner behaves sensibly |
| M3 | First playable coach (units 0–2), daily plan, first APK release (specced: #76–#82) | Used daily for 2 weeks |
| M4 | Songs & chords and ear-training tracks; contribute-a-piano kit | All tracks share the daily plan |
| M5 | Guidance fading, practice/perform modes, strictness setting | Unaided pass required before "learned" |
| M6 | Curriculum units 3–8, sight-reading staircase, placement | Placement reproducible |
| M7 | Heatmaps, recordings, calibration report | Predicted vs observed retention plotted |
| M8 | Optional Google Drive sync | Round-trip sync between two devices |
| M9 | Kids (ages 6–8+): research pass, age-appropriate content | — |
| Later | iOS; more piano brands | — |
