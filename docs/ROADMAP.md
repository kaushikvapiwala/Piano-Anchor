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
1. **Note-name letters in Compose resources** (follow-up of #266): the key strips' letters ("C", "C♯", "D♭")
    come from engine strings; return a pitch class and accidental from the note-name rule and map it to
    Compose string resources in the UI, so the names can be translated.
2. **Note names: screenshots and first frame** (follow-up of #266): goldens for the lesson strip and the
    Unit 0 wide strip and at font scale 2.0; a "not loaded yet" state so a beginner's strip doesn't open blank and
    letter itself a moment later; Practice takes the note-labels use case from Koin like Lesson and Quiz.
3. **Staff notation: flat keys and compound metres** (follow-up of #272): draw flat key signatures and spell black
    keys as flats in flat keys (today a flat key reads "in F major" with no flats drawn); beam 6/8 eighths in threes
    and use dotted-quarter rests on its beats.
4. **Rhythm Try: drift from the first tap** (spec #296): judge each tap also against where it should fall counting
    from the first tap, so a steady tempo off the click can't pass.
5. **Session steps: Results and stored progress** (spec #297): Practise and Sight-read steps of the one-tap session
    end on Results with "Continue"; the day's finished steps are stored so a restart resumes; the streak read is
    bounded.
6. **Left-hand C position card keys** (found reviewing #274): the left-hand C position card scene lights
    48..52 chromatically (C, C♯, D, D♯, E) instead of the white keys C D E F G; use an explicit key list like the
    left-hand G scene and re-record its screenshots.
7. **See hand: wait for "Your turn"** (spec #298): when See shows a hand, it moves on to Try after the hold, the
    fade and a short "Your turn" moment.
8. **Course 2: Unit 7, scales with thumb-under (M6.7)** (spec #300) [#295, #296]: an even-scale Try, the C and G
    major scales (right hand) and the left-hand C scale, Joy to the World, sight-reading level 14 and an eighth
    placement piece; closes course 2.

## Owner device checks
Manual checks on a real Android phone and piano, done by the owner when both are at hand. Unattended runs never
pick these; nothing in "Next up" waits for them. Installing a build and capturing logs: `docs/debugging.md` (`scripts/capture_session.sh` records a session).

- **Pedal and legato (#294, #295):** Unit 6's lessons on a real piano: the legato Try passes with joined keys, the
  sustain-pedal and pedal-change Trys read the pedal (CC 64), a pedal tap doesn't skip those lessons, the See demo's
  pedal cue sounds on the piano, and Amazing Grace's staff shows the pedal line with a notch each bar.
- **Progress tab (#271):** the rail's Progress tab shows this week's days, streak and minutes by day; after a piece
  has full runs on two days, "Day 1 vs today" plays each take through the speaker, one at a time, and leaving the tab
  stops it.
- **Play along (#270):** in a session with today's song, the Play along step counts in one bar and the soft chords
  land on bar 1 with the click, under the piano; the chords restart with each restart of the run and stop with it;
  "Chords + click" turns them off.
- **See hand (#269):** in a lesson's See step the drawn fingers (no palm) press the key strip in time with the
  sound; the left-hand C position shows finger 5 on C3; reduce motion keeps the hand still.
- **One-tap session (#268):** Start on Today runs the day's steps in a row with the 10 s card between them (Pause,
  Skip step, a pedal tap goes now), back mid-step asks "Leave today's session?", the recap names minutes, steps and
  a tip, and "Pause this week" keeps the streak.
- **Note names on keys (#266):** a new learner sees C to B on every white key; after the "C D E, then F G A B"
  concept passes only the key to play keeps its letter, and after its quiz none; Settings → "Note names on keys" →
  Off clears them; letters stay readable in light and dark.
- **Lesson mastery (#265):** a Try needs 3 clean reps (2 for long ones), the lesson's Use piece runs with no hints and
  passes the concept, and the next day's quiz turns the tick into a ★ on Songs.
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
- **Pedal as a control** (from #264): the piano's sustain pedal sends CC 64 over Bluetooth and a quick tap reads as
  "continue" in a lesson, while holding it during a piece does not (the recorded C-D-E fixture has no pedal events).
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
| M6 | Sight-reading staircase, played placement test, course 2 (Units 4–7: eighth notes, G position, pedal, scales); units 0–3 are the first course (ADR 0026). M6.1 (generator and staircase, #245), M6.2 (daily sight-reading card, #246) and M6.3 (played placement test, #247) done; staff notation done (#272); M6.4 (Unit 4, eighth notes and the dotted quarter, #273) done; M6.5 (Unit 5, G position and the key of G, #274) done; M6.6 (Unit 6, legato and the pedal, #295) done | Placement reproducible |
| M7 | Heatmaps, recordings, calibration report | Predicted vs observed retention plotted |
| M8 | Optional Google Drive sync | Round-trip sync between two devices |
| M9 | Kids (ages 6–8+): research pass, age-appropriate content | — |
| Later | iOS; more piano brands | — |
