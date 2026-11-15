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
1. **M2.5 Learner simulation exit check** (spec #73) [#72, #117]: seeded 90-day simulation (retention, load, prerequisite
   order, recovery after a skip).
2. **M3.2 Units 0–2 content** (spec #77, approved) [#76].
3. **M3.3 Settings** (spec #78) [#65]: settings port on SQLite and a Settings screen.
4. **M3.4 Today and session composer** (spec #79) [#72, #77, #78]: cold open, lesson and song cards; Today becomes the
    start screen.
5. **M3.5 Onboarding** (spec #80) [#67, #72, #77, #78, #79]: connect, echo C-E-G, calibrate, choose a start unit.
6. **M3.6 Update check** (spec #81) [#78, #79]: the opt-out daily check, the app's only network call.
7. **M3.7 Release pipeline** (spec #82): tag-driven signed APK on GitHub Releases; then v0.1.0 and the dogfood
    exit check.
8. **Unstable ids and kept identities** (spec #95; follow-up of #56): a missed Identity Reply clears the stored
     identity of a `sys:<id>` fallback id; stable `usb:`/`ble:` ids keep theirs.
9. **All Notes Off on the practice keyboard** (spec #121; follow-up of #101): control changes 120 and 123–127 clear
    the practice screen's keys-down and wrong-key highlights, through one engine helper shared with `PlayedNotes`.
10. **Wait-mode last note length** (spec #122; follow-up of #65): after the completing NoteOn, a wait-mode run keeps
    reading until nothing sounds (keys and pedal) or 3 s pass, so the last notes get their real length.
11. **Results analysis off the main thread** (follow-up of #66): `ResultsViewModel` runs `PerformanceAnalyser` on the
    main dispatcher when Results opens; move it to a background dispatcher (injected for tests) before passages grow
    long (mechanical).
12. **Calibration at 60 BPM** (spec #113) [#67]: clicks one second apart, so every lag up to the 400 ms cap pairs with
    its own click and larger ones are rejected, instead of a lag over 333 ms being stored as a negative offset.
13. **Calibration last-tap grace** (spec #123) [#113]: keep reading 200 ms after the last click's half beat, so a tap
    timestamped in time but delivered late still counts.
14. **Retry a failed calibration save** (spec #124; follow-up of #67): Retry saves the same accepted offset again,
    next to Try again and Cancel, as practice does for an unsaved attempt.
15. **M4.1 Tracks share the daily plan** (spec #127) [#79]: skills get a track (core, songs, ear); new skills rotate
    between tracks under the shared caps; the composer's Ear card and four-way time split (M4's exit check).
16. **M4.2 Ear training** (spec #128) [#127, #79, #80]: eight ear skills, echo on the piano and identify on screen, sets
    of 4 fresh items, speaker tones when the piano has no MIDI output.
17. **M4.3 Songs & chords** (spec #129) [#127, #77, #79]: six C-position chord skills, four pieces and the Songs screen
    with tempo-ladder progress and locked pieces.
18. **M4.4 Contribute-a-piano kit** (mechanical; write its spec with Assumptions): a guided export that bundles a
    piano's identity reply, a short note fixture and the app version into a file to attach to a GitHub issue; reads
    only, no network.
19. **M5.1 Hint fading** (spec #132) [#79]: key hints fade per passage (Full, Delayed after 1.5× your usual gap,
    Off) with a Hint button; attempts record the hint level and whether a hint was shown.
20. **M5.2 Perform mode** (spec #133) [#132, #79]: one take at a timed rung with no hints or live verdicts; Today's cold
    open performs by default.
21. **M5.3 Spot loops** (spec #134) [#132, #133]: Results offer to loop the worst two bars one rung lower until two
    clean loops; loops are never cold checks.
22. **M5.4 Unaided pass and strictness** (spec #135) [#132, #133, #78, #73, #128]: learned needs one unaided
    recalled check; aided and wait-mode runs rate at most Hard; Gentle/Standard/Strict presets (M5's exit check).

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
| M1 | MIDI input, real-time matcher, offline analyser (🔄 M1.1 rhythm in passages, M1.2 played notes and analyser harness, and M1.3 offline analyser done: exit check met on synthetic takes, real-fixture confirmation pending the hardware check; M1.4a metronome and M1.4b staff rhythm done, the click pending an owner check by ear; M1.4c tempo mode, M1.4d tiles and M1.5 latency calibration done) | Recorded fixtures with injected errors classified ≥ 95% correctly |
| M2 | Learner model (skill graph) and spaced-repetition scheduler (FSRS-6) (specced: #69–#73; 🔄 M2.1 FSRS-6 memory model, M2.3 learner storage and M2.4 learner model done) | Matches reference test vectors; simulated learner behaves sensibly |
| M3 | First playable coach (units 0–2), daily plan, first APK release (specced: #76–#82; 🔄 M3.1 content format done) | Used daily for 2 weeks |
| M4 | Songs & chords and ear-training tracks; contribute-a-piano kit (specced: #127–#129) | All tracks share the daily plan |
| M5 | Guidance fading, practice/perform modes, strictness setting (specced: #132–#135) | Unaided pass required before "learned" |
| M6 | Curriculum units 3–8, sight-reading staircase, placement | Placement reproducible |
| M7 | Heatmaps, recordings, calibration report | Predicted vs observed retention plotted |
| M8 | Optional Google Drive sync | Round-trip sync between two devices |
| M9 | Kids (ages 6–8+): research pass, age-appropriate content | — |
| Later | iOS; more piano brands | — |
