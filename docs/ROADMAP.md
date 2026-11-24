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
1. **One hint level for the run** (follow-up of the M5 cleanup; mechanical): `PracticeViewModel` still checks perform
    mode by hand where it starts hints; give the run an effective level (OFF in perform) so `loadHints` and
    `startHintStep` read only that, and keep the Hint button's own guard.
2. **Lesson Try: the landmark hint** (follow-up of #189 and #195; mechanical): the lesson's Try step still says "That
    was X. Waiting for Y."; use the quiz screen's landmark line (`QuizHint.kt`) there too, and move that text, now copied
    from `feature/practice`, into one shared UI module instead of two copies.
3. **Keys during the link measurement** (follow-up of #172; needs an owner decision): before the first timed run,
    Practice spends about 0.3 s (0.6 s for a silent piano) measuring the link delay while showing the ready state, and
    a key pressed then is dropped. Either show a short "getting ready" state or hand those notes on to the run.
4. **Quizzes count as practice days** (spec #244; follow-up of #195; owner decided they count): Today's weekly dots and day
    line count ear reviews (`ear:` attempt ids) but not review quizzes (`quiz:` ids), so a day with only quizzes shows as
    missed. A quiz-only day counts; lesson-only days stay out (lessons store no dated record).
5. **Sight-reading generator and staircase (M6.1)** (spec #245): a seeded generator of 4-bar unseen exercises over 8
    levels opened by learned skills, and a 2-up/1-down level rule derived from saved `sight:` attempts.
6. **Daily sight-reading card (M6.2)** (spec #246) [#245]: Today's Sight-reading card (10% of the budget, after the
    song), played in wait mode from the staff only; Results say how the level moved.
7. **Played placement test (M6.3)** (spec #247): onboarding's "Test me" plays one piece per unit and starts the
    learner at the first unit not passed; the start-unit list goes; placed skills tick their lessons.
8. **Course 2: Units 4–7 (M6.4–M6.7)** (needs a grill session per unit): eighth notes and dotted quarter; G position
    and the key of G; the pedal and legato; scales with thumb-under. Built like units 0–3 (concept cards, five-step
    lessons, Try checks); each unit adds sight-reading levels.

## Owner device checks
Manual checks on a real Android phone and piano, done by the owner when both are at hand. Unattended runs never
pick these; nothing in "Next up" waits for them. Installing a build and capturing logs: `docs/debugging.md` (`scripts/capture_session.sh` records a session).

- **First release and dogfood (M3 exit):** cut v0.1.0 by following `docs/RELEASING.md` (keystore, secrets, the
  `dogfood` and `blocker` labels, then the tag; the first run also proves the untested emulator smoke job), then use
  the released APK daily as the M3 exit check says.
- **Hardware check:** install the debug app (the `app-android-debug-<sha>` artifact of the latest CI run
  on main, or `./gradlew :app-android:assembleDebug`), connect a piano over BLE, play keys, survive a disconnect
  and export a C-D-E fixture into `fixtures/` (done 2026-11-16: `fixtures/cde-frp-nuvola-ble.jsonl`, replayed by
  `MatchWaitModeFlowTest` and `RealRecordingAnalysisTest`; the 1.4b BLE scan/connect glue and the 1.4a USB
  transport on real hardware; the emulator end-to-end test, `.github/workflows/e2e.yml`, covers the 1.4a `MidiManager` glue and the
  1.4m Android wiring over a virtual MIDI device); note whether the piano answers the 1.5 Identity Request (the
  monitor shows "model …") and that the piano is still known after an app restart (1.8 storage). If a piano known
  to answer shows none, the request may race the input port's open: `MidiInputPort` would then need an
  "opened" signal. Also open Practice from the monitor, play C-D-E, and check the monitor shows the piano connected
  again afterwards (1.9: the monitor closes the input for practice and reopens it on return). End to end (closes
  Phase 1): play C-D-E, see Results (1.10) with "run N", Try again, and the run count survives an app restart.
- **Reconnect on start** (from #184): connect a Bluetooth piano in Pianos, kill the app and reopen it: Today's chip
  reads "Connecting…" and then names the piano without a scan. With the piano off, the chip settles on "No piano"
  with no error. "Forget" on Pianos disconnects it, and the next start doesn't reconnect it.
- **Brief Bluetooth drops** (from #180): on a piano whose Bluetooth link drops about 2 s after connecting, the
  onboarding echo, Pianos and a wait-mode run carry on through the drop; a timed run says "The piano disconnected for
  a moment. Starting again." and waits for the starting key.
- **Metronome by ear** (from M1.4a, #63): on the first tempo-mode run on a device, check the click's timing against
  the cursor and that the accent is audible on phone speakers.
- **Update check on a device** (from M3.6, #81): once a release exists, open Today with "update check" on and an
  older build (untagged builds are `0.0.0-dev` and never offer updates, so build one with
  `./gradlew :app-android:assembleDebug -PpianoanchorVersion=0.0.9`): the banner names the newer version, "See
  release" opens its page, "Not now" hides it; with the
  switch off, no request (the `HttpsURLConnection` glue runs only on a device).

## Phase 2+ — Features
| Milestone | Scope | Exit check |
|---|---|---|
| M1 | MIDI input, real-time matcher, offline analyser (🔄 M1.1 rhythm in passages, M1.2 played notes and analyser harness, and M1.3 offline analyser done: exit check met on synthetic takes, real-fixture confirmation pending the hardware check; M1.4a metronome and M1.4b staff rhythm done, the click pending an owner check by ear; M1.4c tempo mode, M1.4d tiles and M1.5 latency calibration done) | Recorded fixtures with injected errors classified ≥ 95% correctly |
| M2 | Learner model (skill graph) and spaced-repetition scheduler (FSRS-6) (✅ M2.1 FSRS-6 memory model, M2.2 skill graph, M2.3 learner storage, M2.4 learner model and the M2.5 90-day simulation exit check done) | Matches reference test vectors; simulated learner behaves sensibly |
| M3 | First playable coach (units 0–2), daily plan, first APK release (specced: #76–#82; 🔄 M3.1 content format, M3.2 units 0–2, M3.3 settings, M3.4 Today, M3.5 onboarding, M3.6 update check and M3.7 release pipeline done; v0.1.0 and the dogfood are the owner's) | Dogfood (`docs/RELEASING.md`): the owner uses the released APK; Today's practice-day line shows 14 practice days within 16, and no open `dogfood` issue is labelled `blocker` |
| M4 | Songs & chords and ear-training tracks; contribute-a-piano kit (✅ done: tracks in the daily plan #151, ear #152, songs and chords #153, the contribute-a-piano kit #154) | All tracks share the daily plan |
| M5 | Guidance fading, practice/perform modes, strictness setting (✅ done: M5.1 hint fading, M5.2 perform mode, M5.3 spot loops and M5.4 unaided pass and strictness) | Unaided pass required before "learned" |
| M6 | Sight-reading staircase, played placement test, course 2 (Units 4–7: eighth notes, G position, pedal, scales); units 0–3 are the first course (ADR 0026). Specced: #245–#247 | Placement reproducible |
| M7 | Heatmaps, recordings, calibration report | Predicted vs observed retention plotted |
| M8 | Optional Google Drive sync | Round-trip sync between two devices |
| M9 | Kids (ages 6–8+): research pass, age-appropriate content | — |
| Later | iOS; more piano brands | — |
