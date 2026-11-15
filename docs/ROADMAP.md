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
1. **Practice-day query index** (follow-up of #79; mechanical): `selectStartedSince` in
    `platform/storage/src/commonMain/sqldelight/pianoanchor/platform/storage/db/Attempt.sq` filters on `started_at_ms`
    alone; add an index on it with a SQLDelight migration so Today's practice-day counts don't scan every attempt.
2. **Ear days count as practice days** (follow-up of #128; mechanical): ear sets store only reviews, so a day with
    only ear training leaves Today's "n of 7 days" and the weekly dots empty; count days with an ear review (from
    `LearnerRepositoryPort` reviews) alongside `practiceDays` from attempts.
3. **One MIDI playback helper** (follow-up of #128; mechanical): onboarding's arpeggio and `EarViewModel` each send
    NoteOn/NoteOff for the shared `PLAYBACK_*` tunables; move both onto one helper that plays `tones()` over
    `MidiOutputPort` and releases held keys on cancel, and move the Ear card's amber accent into the shared theme
    in `ui/shell`.
4. **Song card picks unlocked pieces** (follow-up of #129; needs an owner decision): Today's song card still picks any
    passage of the newest unit with a learned skill, so it can offer a chord exercise, or a piece that Songs shows
    as locked; decide whether it should pick only unlocked pieces (as `Pieces.list` judges them).
5. **Back returns to the opening screen** (follow-up of #129; mechanical): Settings and Pianos opened from the Songs
    rail go back to Today; remember where they were opened from, as Practice does with `from`.
6. **One passage-history loader** (follow-up of #129; mechanical): `TodayViewModel` and `SongsViewModel` both build
    each passage's `PassageHistory` from three `AttemptRepositoryPort` reads; share one loader.
7. **M4 cleanup** (end of M4; mechanical): run `/simplify` on the whole M4 diff (#151–#153 and the contribute-a-piano kit) as
    its own small cleanup PR, as `.claude/skills/next-task/SKILL.md` step 3 asks at a milestone's end.
8. **No hint flash before the level loads** (follow-up of #132; mechanical): `PracticeViewModel` shows the band
    (FULL) until the passage's attempts are read, so a DELAYED or OFF run can flash it for a moment on a slow store;
    hide the keys' band until `loadHints` finishes.
9. **Perform cursor colour** (follow-up of #133; needs an owner decision): a perform take still draws the staff's
    waiting note in the amber "waiting" colour, which marks the step the matcher waits for; decide whether perform
    should draw it neutrally like the keys.
10. **Spot-loop hints and clean offers** (follow-up of #134; needs an owner decision): a spot loop always runs at the
    FULL hint level (its attempts sit under the slice's id, and `HintFading` skips them), and Results offer a loop
    after a CLEAN timed run whenever the analyser found any timing finding; decide whether loops should use the
    passage's level and whether CLEAN runs should offer a loop at all.
11. **Results know the run's rung** (follow-up of #134; mechanical): `ResultsViewModel` infers the rung from the
    attempt's tempo, so rungs that clamp to the same BPM resolve to the lowest; store or pass the rung the run was
    played on, so "Loop bars" and "Play it" use it exactly.
12. **Results grade timing with the run's strictness** (follow-up of #135; mechanical): `ResultsViewModel` re-analyses
    the take with the Standard timing tolerance, so a run played on Gentle or Strict can show timing findings that
    disagree with its stored band; store the strictness on the attempt (or read the setting) and analyse with it.
13. **M5 cleanup** (end of M5; mechanical): run `/simplify` on the whole M5 diff (#156–#158 and M5.4) as its own small
    cleanup PR, as `.claude/skills/next-task/SKILL.md` step 3 asks at a milestone's end.

## Owner device checks
Manual checks on a real Android phone and piano, done by the owner when both are at hand. Unattended runs never
pick these; nothing in "Next up" waits for them.

- **First release and dogfood (M3 exit):** cut v0.1.0 by following `docs/RELEASING.md` (keystore, secrets, the
  `dogfood` and `blocker` labels, then the tag; the first run also proves the untested emulator smoke job), then use
  the released APK daily as the M3 exit check says.
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
- **Update check on a device** (from M3.6, #81): once a release exists, open Today with "update check" on and an
  older build (untagged builds are `0.0.0-dev` and never offer updates, so build one with
  `./gradlew :app-android:assembleDebug -PpianoanchorVersion=0.0.9`): the banner names the newer version, "See
  release" opens its page, "Not now" hides it; with the
  switch off, no request (the `HttpsURLConnection` glue runs only on a device).
- **Real recording:** once the hardware check lands its C-D-E fixture, add a replay test of it to
  `MatchWaitModeFlowTest` and run the performance analyser on it (the M1 exit check on a real recording).

## Phase 2+ — Features
| Milestone | Scope | Exit check |
|---|---|---|
| M1 | MIDI input, real-time matcher, offline analyser (🔄 M1.1 rhythm in passages, M1.2 played notes and analyser harness, and M1.3 offline analyser done: exit check met on synthetic takes, real-fixture confirmation pending the hardware check; M1.4a metronome and M1.4b staff rhythm done, the click pending an owner check by ear; M1.4c tempo mode, M1.4d tiles and M1.5 latency calibration done) | Recorded fixtures with injected errors classified ≥ 95% correctly |
| M2 | Learner model (skill graph) and spaced-repetition scheduler (FSRS-6) (✅ M2.1 FSRS-6 memory model, M2.2 skill graph, M2.3 learner storage, M2.4 learner model and the M2.5 90-day simulation exit check done) | Matches reference test vectors; simulated learner behaves sensibly |
| M3 | First playable coach (units 0–2), daily plan, first APK release (specced: #76–#82; 🔄 M3.1 content format, M3.2 units 0–2, M3.3 settings, M3.4 Today, M3.5 onboarding, M3.6 update check and M3.7 release pipeline done; v0.1.0 and the dogfood are the owner's) | Dogfood (`docs/RELEASING.md`): the owner uses the released APK; Today's practice-day line shows 14 practice days within 16, and no open `dogfood` issue is labelled `blocker` |
| M4 | Songs & chords and ear-training tracks; contribute-a-piano kit (✅ done: tracks in the daily plan #151, ear #152, songs and chords #153, the contribute-a-piano kit #154) | All tracks share the daily plan |
| M5 | Guidance fading, practice/perform modes, strictness setting (✅ done: M5.1 hint fading, M5.2 perform mode, M5.3 spot loops and M5.4 unaided pass and strictness) | Unaided pass required before "learned" |
| M6 | Curriculum units 3–8, sight-reading staircase, placement | Placement reproducible |
| M7 | Heatmaps, recordings, calibration report | Predicted vs observed retention plotted |
| M8 | Optional Google Drive sync | Round-trip sync between two devices |
| M9 | Kids (ages 6–8+): research pass, age-appropriate content | — |
| Later | iOS; more piano brands | — |
