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
| **Walking skeleton** | ✅ pending the owner's end-to-end hardware check (Owner device checks) |

## Next up
The task queue for `/next-task` (`.claude/skills/next-task/`): one item per session and PR, top first.
Remove an item in the PR that finishes it; add follow-ups here. Dependencies in brackets, as spec issue numbers
(renumbering items doesn't break them); `docsCheck` fails when an item comes before an item whose spec it depends on.
Checks that need a phone and a piano are under "Owner device checks", not here.

1. **Robust fitted tempo** (spec #96) [#61]: fit the analyser tempo from the median gap, so one long pause no longer
   hides a hesitation or makes the notes around it early or late.
2. **Note length at key up** (spec #97) [#61]: judge `TOO_SHORT`/`TOO_LONG` on key up, so pedalling isn't reported
   as held too long.
3. **M1.4c Tempo-mode runs** (spec #65) [#61, #63, #64]: tempo rungs, one-bar count-in, clock-driven cursor, live judge,
   metronome, attempts store tempo and played notes (schema 1→2). Hearing the M1.4a click on a device is an owner
   device check.
4. **M1.4d Results tiles** (spec #66) [#65]: Timing, Note length and Hesitation tiles from the analyser; tempo in the
   caption.
5. **M1.5 Latency calibration** (spec #67) [#63, #65, #66]: tap along to 16 clicks, a calibration port, offered before a
   piano's first timed run and from the monitor (schema 2→3).
6. **M2.1 FSRS-6 memory model** (spec #69): pure-Kotlin FSRS-6 in the engine, checked against committed py-fsrs
   golden vectors.
7. **M2.2 Skill graph** (spec #70) [#59]: skills with prerequisites (validated DAG); passages declare their skills.
8. **M2.3 Learner storage** (spec #71) [#69, #70]: review log plus cached skill state behind a learner repository port;
   adds kotlinx-datetime (ADR).
9. **M2.4 Learner model** (spec #72) [#69, #70, #71]: cold checks rated from the band, learned rule, due list and daily
   limits; recorded after every saved run.
10. **M2.5 Learner simulation exit check** (spec #73) [#72]: seeded 90-day simulation (retention, load, prerequisite
    order, recovery after a skip).
11. **M3.1 Content format** (spec #76) [#59, #70]: note text, rights records, units; the rights document generated from the
    content.
12. **M3.2 Units 0–2 content** (spec #77, approved) [#76].
13. **M3.3 Settings** (spec #78) [#65]: settings port on SQLite and a Settings screen.
14. **M3.4 Today and session composer** (spec #79) [#72, #77, #78]: cold open, lesson and song cards; Today becomes the
    start screen.
15. **M3.5 Onboarding** (spec #80) [#67, #72, #77, #78, #79]: connect, echo C-E-G, calibrate, choose a start unit.
16. **M3.6 Update check** (spec #81) [#78, #79]: the opt-out daily check, the app's only network call.
17. **M3.7 Release pipeline** (spec #82): tag-driven signed APK on GitHub Releases; then v0.1.0 and the dogfood
    exit check.
18. **Unstable ids and kept identities** (spec #95; follow-up of #56): a missed Identity Reply clears the stored
    identity of a `sys:<id>` fallback id; stable `usb:`/`ble:` ids keep theirs.
19. **All Notes Off on the practice keyboard** (follow-up of #101): the practice screen's keys-down and wrong-key
    highlights still ignore control changes 120 and 123–127, so a key whose NoteOff was lost stays lit; clear them
    as `PlayedNotes` does (mechanical).

## Owner device checks
Manual checks on a real Android phone and piano, done by the owner when both are at hand. Unattended runs never
pick these; nothing in "Next up" waits for them.

- **Hardware check:** install the debug app (the `app-android-debug-<sha>` artifact of the latest CI run
  on main, or `./gradlew :app-android:assembleDebug`), connect a piano over BLE, play keys, survive a disconnect
  and export a C-D-E fixture into `fixtures/` (the 1.4b BLE scan/connect glue and the 1.4a USB transport on real
  hardware; the emulator end-to-end test, `.github/workflows/e2e.yml`, covers the 1.4a `MidiManager` glue and the
  1.4m Android wiring over a virtual MIDI device); note whether the piano answers the 1.5 Identity Request (the
  monitor shows "model …") and that the piano is still known after an app restart (1.8 storage). If a piano known
  to answer shows none, the request may race the input port's open: `MidiInputPort` would then need an
  "opened" signal. Also open Practice from the monitor, play C-D-E, and check the monitor shows the piano connected
  again afterwards (1.9: the monitor closes the input for practice and reopens it on return). End to end (closes
  Phase 1): play C-D-E, see Results (1.10) with "run N", Try again, and the run count survives an app restart.
- **Metronome by ear** (from M1.4a, #63): on the first tempo-mode run on a device, check the click's timing against
  the cursor and that the accent is audible on phone speakers.
- **Real recording:** once the hardware check lands its C-D-E fixture, add a replay test of it to
  `MatchWaitModeFlowTest` and run the performance analyser on it (the M1 exit check on a real recording).

## Phase 2+ — Features
| Milestone | Scope | Exit check |
|---|---|---|
| M1 | MIDI input, real-time matcher, offline analyser (🔄 M1.1 rhythm in passages, M1.2 played notes and analyser harness, and M1.3 offline analyser done: exit check met on synthetic takes, real-fixture confirmation pending the hardware check; M1.4a metronome and M1.4b staff rhythm done, the click pending an owner check by ear; specced: #65–#67) | Recorded fixtures with injected errors classified ≥ 95% correctly |
| M2 | Learner model (skill graph) and spaced-repetition scheduler (FSRS-6) (specced: #69–#73) | Matches reference test vectors; simulated learner behaves sensibly |
| M3 | First playable coach (units 0–2), daily plan, first APK release (specced: #76–#82) | Used daily for 2 weeks |
| M4 | Songs & chords and ear-training tracks; contribute-a-piano kit | All tracks share the daily plan |
| M5 | Guidance fading, practice/perform modes, strictness setting | Unaided pass required before "learned" |
| M6 | Curriculum units 3–8, sight-reading staircase, placement | Placement reproducible |
| M7 | Heatmaps, recordings, calibration report | Predicted vs observed retention plotted |
| M8 | Optional Google Drive sync | Round-trip sync between two devices |
| M9 | Kids (ages 6–8+): research pass, age-appropriate content | — |
| Later | iOS; more piano brands | — |
