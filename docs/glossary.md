# Glossary

Domain terms used in code, tests and docs. Add a term when you introduce it.

| Term | Meaning |
|---|---|
| Accent | The click on the first beat of each bar, higher than the others (`METRONOME_ACCENT_HZ`), so the bar can be heard. |
| Accuracy | How cleanly a completed attempt was played by pitch: steps / (steps + wrong notes + incomplete chords), from 0 to 1 (`docs/algorithms/grading.md`). |
| Adapter | Implementation of a port for a real platform or technology (e.g. Android MIDI). Named `*Adapter`. |
| All Notes Off | MIDI control change 123 (also implied by 124–127): every key still down comes up; the sustain pedal still holds them. A piano sends it to clear stuck notes. |
| All Sound Off | MIDI control change 120: every key comes up and every sounding note stops at once, pedal-held ones included. |
| Attempt | One completed run of a passage, from the first note to `PassageComplete` (`Attempt` in `:core:model`): its counts, accuracy, band and match events, stored by `AttemptRepositoryPort`. Abandoned runs are not saved. |
| Band | The result of an attempt named for the learner: `CLEAN`, `GOOD` or `KEEP_PRACTISING`, from its accuracy and the `BAND_*_MIN_ACCURACY` tunables. |
| BLE MIDI | MIDI over Bluetooth Low Energy. A BLE MIDI piano is found by a scan and must be connected from the app (`MidiDiscoveryPort`); pairing in the system settings pairs only Bluetooth audio. |
| Card scene | A concept card's short looping animation, written as data (`CardScene` in `:core:model`: a stage, timed cues in one fixed vocabulary and a description for TalkBack) and drawn by one renderer so every card looks and moves the same (`docs/ui/living-studio.md`, "Card animations"). |
| Chord window | The `CHORD_CLUSTER_MS` span from a step's first pressed note in which its other notes must follow; the matcher reports an incomplete chord when it runs out. |
| Cold check | The first saved attempt on a learner day that exercises a skill, in any mode and at any tempo; it becomes the skill's review for that day, rated from its band (`docs/algorithms/learner-model.md`). Later attempts that day change nothing. |
| Concept card | A concept's four required lines in the beginner course: what it is, why it exists, how it's used and when you meet it (`docs/course/beginner-course.md`); the type is `ConceptCard`. |
| Contract test | Abstract test in `testing/` that every implementation of a port must pass. |
| Daily budget | The minutes of practice the learner wants per day: 15, 20 (default) or 30 (`DailyBudget` in `Settings`). |
| Difficulty | FSRS: how hard an item is for this learner, 1 (easy) to 10 (hard); part of `MemoryState` (`docs/algorithms/scheduler.md`). |
| Due | A skill is due on a learner day when its `dueDay` (last review day plus the FSRS interval) is on or before that day; the plan lists due skills lowest retrievability first, at most `MAX_DUE_PER_DAY`. |
| Ear item | One thing to hear and answer in the ear-training track: an *echo* or an *identify* item (`EarExercise` in `:core:model`). Four of them make one review of an ear skill (`EAR_ITEMS_PER_REVIEW`). |
| Echo | An ear item answered by playing back what was heard on the piano; the next notes played are judged by `EarEcho.matches`. One note must be the exact key, melodies may be in any octave. |
| Error kind | What the performance analyser can report about a score note (`ErrorKind`): wrong note, missed note, extra note, incomplete chord, mistouch, octave, early, late, hesitation, too short, too long (`docs/algorithms/performance-analysis.md`). |
| FakePiano | In-memory test double in `testing/` that implements both MIDI ports for one wired device, MIDI discovery for the devices it advertises, and decodes incoming bytes with the real parser. |
| Finding | One error the analyser found (`Finding` in `:core:model`): its error kind, the score note it concerns (`ScoreNoteRef`: step and pitch) and the played note involved, if any. |
| Fixture | A recorded MIDI session as a JSONL file (`fixtures/SCHEMA.md`): a header, then each message's canonical bytes and its time since the first message. Feeds the matcher and replay tests. |
| Contribution kit | One `.json` file saved from the note monitor (`docs/contribute-a-piano.md`): the app version, the piano's Identity Reply and a short fixture. Attached to a "Support for my piano" issue. |
| Fake | Deterministic test implementation of a port, in `testing/`. |
| Hint level | How much help a run of a passage starts with: Full (amber key band at once), Delayed (after the learner's usual pause) or Off (only on the Hint button). Faded by clean runs, restored by keep-practising runs (`HintFading`, `docs/algorithms/guidance.md`). |
| Landmark hint | The one plain line shown after a wrong key in wait mode: how to find the key to play from the groups of two and three black keys (`wrongKeyHint`, `docs/algorithms/wrong-key-hint.md`). |
| Lesson step | One of a beginner lesson's five steps: Why (the concept card), See (the app plays it), Try (one action the piano checks), Use (a tiny piece), Recap. |
| Perform run | An attempt played in perform mode: one take at a timed rung with no hints and no live verdicts (`mode` PERFORM). |
| Try check | The small piano-checked task of a course concept, such as "play every C" or "hold C for 2 clicks"; the type is `TryCheck`, evaluated by `TryRun` (`docs/algorithms/try-checks.md`). |
| Unaided run | A perform run with no hint shown (`Attempt.unaided`); always the case for a perform run. `Review.unaided` also holds for ear sets and placement. A skill needs one unaided recalled check before it counts as learned (`docs/algorithms/learner-model.md`). |
| Aided run | An attempt with `hintShown` true: the key band showed at some point (Full level, a Delayed hint that fired, or the Hint button). |
| Hesitation | A pause before a note that is much longer than the music asks for, without a wrong note. |
| Identify | An ear item answered by tapping the name of what was heard on screen (higher or lower, step or skip, an interval, a chord quality). Two choices in M4. |
| Incomplete chord | `ChordIncomplete`: the chord window of a step ran out before all its notes were pressed; the step must be played again. |
| Learned | A skill that has `LEARNED_MIN_RECALLED_COLD_CHECKS` recalled (Hard or Good) cold checks on distinct days, at least `LEARNED_MIN_CLEAN_COLD_CHECKS` of them clean, and stability of at least `LEARNED_MIN_STABILITY_DAYS`. Sticky: it stays learned after a lapse, and it unlocks the skills that depend on it. |
| Learner day | A calendar date (`kotlinx.datetime.LocalDate`) in the learner's own time zone; a skill gets at most one review per learner day. Stored as ISO `yyyy-mm-dd` text (ADR 0022). |
| Note text | The compact text line a passage's notes are written in, e.g. `C4q D4q E4h`; parsed by `NoteText`. See `docs/algorithms/note-text.md`. |
| Review | One FSRS review (`Review` in `:core:model`): a cold check of a skill on a learner day that produced a rating, from one attempt. |
| Review log | The append-only list of reviews kept by `LearnerRepositoryPort`: the source of truth. The cached state per skill (`SkillState`) is derived from it and can be rebuilt by replaying the log. |
| Link resumed | The `LinkResumed` event: a Bluetooth piano's link dropped and was reopened within the drop grace (`BLE_DROP_GRACE_MS`), and messages sent in the gap were lost. Wait-mode runs, the monitor and the onboarding ignore it; a timed practice run starts over from the count-in and says why (`docs/protocols/ble-midi.md`). |
| Latency offset | How much later (positive) or earlier (negative) a piano's notes arrive than the click they answer, in nanoseconds: the phone's click output, Bluetooth and the piano together. Stored per piano (`CalibrationRepositoryPort`), subtracted from played-note times in timed runs, and kept on each attempt (`Attempt.latencyOffsetNanos`, null = played uncalibrated). |
| Calibration | The 16-click tapping run that measures a piano's latency offset (`LatencyCalibration`, `docs/algorithms/latency-calibration.md`). |
| Instrument | A piano the app has seen, stored by its stable device id (`InstrumentRepositoryPort`): display name, instrument identity if it answered, and when it was first and last seen. Per-piano data (latency, velocity curve) hangs off it. |
| Instrument identity | What a piano reports in a MIDI Identity Reply: manufacturer id, model family, model number and four version bytes (`docs/protocols/midi-identity.md`). Null when the piano did not answer. |
| Labelled take | A generated take of a passage at a tempo with the errors injected into it as findings (`LabelledTake` in `testing/`); a clean take has none. The analyser's test harness scores findings against the labels. |
| Metronome | The audible beat behind `MetronomePort`: clicks at given monotonic times, with an accent every bar, synthesised on the device (`docs/protocols/android-audio.md`). |
| Middle C | MIDI note 60, named C4. |
| MIDI device | A MIDI instrument the platform can see: a stable `id` plus a display `name`. Listed by the MIDI input and output ports. |
| MIDI event | A typed message from the piano (`NoteOn`, `NoteOff`, `ControlChange`, `SysEx`) with a monotonic timestamp, produced by the MIDI parser from raw bytes. |
| Mistouch | An extra note one or two semitones from a correct note, played at the same moment (brushed key). |
| MIDI discovery | Finding and connecting MIDI devices that the platform does not list until connected, such as BLE MIDI pianos: `MidiDiscoveryPort.scan`, `connect` and `disconnect`. |
| Note match | One score note as the performance analyser aligned it (`NoteMatch`): the played note paired with it (none when missed), how far its onset was from the expected one and the gap from the previous step. |
| Note monitor | The Pianos screen (`:feature:monitor`), reached from Today's rail: pick a piano, watch its live notes, record a session and export it as a fixture. |
| Onset | When a step starts, in ticks from the start of the passage (`Step.onsetTicks`); onsets strictly increase. |
| Passage | A piece of music to practise (`Passage` in `:core:model`): a stable id, a title, its steps, a time signature and a written tempo. |
| Piece | A passage marked `piece` (`Passage.piece`): a tune or song, listed on the Songs screen with its tempo-ladder progress, as opposed to an exercise. `Pieces.list` gives each a status: new, in progress or mastered. Nothing is locked. |
| Placement | Starting a learner past the early units (`LearnerModel.place`): each skill of the units before the chosen start gets a GOOD review with attempt id `placement` and counts as learned, without counting as a cold check. Chosen in the onboarding's last step. |
| Performance analyser | `PerformanceAnalyser` in `:engine`: aligns a take's played notes to the passage, fits its tempo and reports findings of every error kind (`docs/algorithms/performance-analysis.md`). |
| Played note | One key as played (`PlayedNote` in `:core:model`): pitch, onset velocity, key down, key up and when it stopped sounding, paired from MIDI events by `PlayedNotes.from`. |
| Port | Interface through which the domain uses the outside world. Named `*Port`, lives in `core/ports`. |
| Practice day | A calendar day with at least one attempt, or a learner day with an ear-set review (`LearnerModel.earDays`); Today's weekly goal and its 16-day line count them. Placement reviews do not count. |
| Practice screen | The wait-mode screen (`:feature:practice`): the passage on a treble staff with a cursor on the waiting note, and a live key strip. A completed run is graded and saved; a run that ends early is discarded. |
| Prerequisite | A skill that must be learned before another one is introduced (`Skill.prerequisites`, by id). |
| Rating | How a review went for the memory model: `AGAIN`, `HARD`, `GOOD` or `EASY` (`Rating` in `:core:model`). |
| Reconnect backoff | How the adapter reopens a dropped BLE MIDI device: waits that double from `BLE_RECONNECT_INITIAL_MS` up to `BLE_RECONNECT_MAX_MS`, giving up after `BLE_RECONNECT_GIVE_UP_MS`. |
| Release tail | The part of a wait-mode run after the NoteOn that completes the passage: keys up and pedal changes still join the take until nothing sounds, a new key is pressed, the piano drops or `WAIT_MODE_RELEASE_TIMEOUT_MS` passes, so the last notes get their real length. |
| Results screen | The screen after a completed run (`:feature:practice`): "run N" of the passage, a headline from the band, the Pitch tile (correct steps of all, wrong notes, incomplete chords) and Try again. |
| Retrievability | FSRS: the chance of recalling an item now, 1 right after a review and falling with the days since; `Fsrs.retrievability`. |
| Rights record | Why a passage may ship (`Rights` in `:core:model`): original or public domain, its origin, the basis and the check date; every passage has one and `docs/generated/rights.md` lists them. |
| Running status | MIDI shorthand: after one channel status byte, further data-byte groups reuse that status without repeating it. |
| Skill node | One learnable skill (`Skill` in `:core:model`, e.g. "Right hand, C position"): a stable kebab-case id, a title and its prerequisites. Passages list the skills they exercise. |
| Stability | FSRS: the days after which recall has fallen to 90%; grows with each successful review. Part of `MemoryState`. |
| Stable device id | The `MidiDevice.id` an adapter derives from what the platform reports (e.g. `usb:<manufacturer>/<product>/<serial>`, `ble:<address>`), so the same instrument keeps its id across reconnects when the platform reports enough to tell it apart. |
| Step | One moment in a passage: the keys to press together (one note, or a chord) at its onset, each with a written length in ticks (`ScoreNote`). Wait mode reads only its pitches. |
| Strictness | The learner's Settings choice of Gentle, Standard (default) or Strict: it scales the timing tolerance and the CLEAN and GOOD accuracy bands of the next run (`StrictnessProfile`, `docs/algorithms/grading.md`). |
| Skill graph | The skills and their prerequisites as a validated acyclic graph (`SkillGraph`); `unlocked` lists the skills a learner may start next. See `docs/algorithms/learner-model.md`. |
| Sustain pedal | The right (damper) pedal, sent as control change 64; values 64–127 hold every released note sounding until it drops below 64. |
| SysEx | System Exclusive: a variable-length, vendor-specific MIDI message framed by 0xF0 and 0xF7. |
| Track | A strand of the curriculum a skill belongs to (`Track`: core, songs, ear). All tracks share one skill graph, one due list and one daily budget; new skills rotate between tracks (`docs/algorithms/learner-model.md`). |
| Tempo ladder | The practice screen's tempo rungs: Wait, then 60/70/85/100 % of the written tempo (`TempoRung`). Each timed rung starts a tempo run. |
| Tempo rung | One step of the tempo ladder. A timed rung shows done (blue) once a clean timed attempt exists at its tempo. |
| Tempo run | Timed practice at a rung: one key starts a count-in, then the cursor moves with the clock, each note is judged live and the take is graded afterwards by the performance analyser. |
| Count-in | The one bar of clicks (accented first beat) before a tempo run; tick 0 falls on the beat after it. |
| Tempo reference | What the performance analyser times a take against (`TempoReference`): a target tempo (optionally with when beat 1 fell), the tempo fitted to the take, or none (wait mode). |
| Tick | The unit of musical time in a passage: `TICKS_PER_QUARTER` (960) ticks per quarter note, so onsets and note lengths are whole numbers. |
| Time signature | Beats per bar (1–32) and the note value of a beat (1, 2, 4, 8 or 16), e.g. 3/4 (`TimeSignature`). |
| Tunable | A threshold or prior in `engine/src/commonMain/kotlin/pianoanchor/engine/Tunables.kt`, documented with why and source. |
| Update check | The opt-out daily request for the latest release (`UpdateCheckPort`, `docs/protocols/update-check.md`): the app's only network call. Today shows a banner when the release is newer than the installed version. |
| Unit | One step of the curriculum (`CurriculumUnit` in `:core:model`): a title, the skills it teaches and the passages that teach them; the units are listed in `Curriculum`. |
| Weekly goal | The days per week the learner aims to practise, 1–7, default 4 (`Settings.weeklyGoalDays`). |
| Wait mode | Practice mode where the music waits until the learner plays the right note(s). |
| Written tempo | The tempo a passage states, in quarter notes per minute (`Passage.tempoBpm`, 20–300); tempo rungs are percentages of it. |
| Wrong note | `WrongNote`: a key pressed that isn't in the current step; progress is unchanged. |
| Session | Today's plan as cards plus a bar of Review, Lesson and Song minutes, composed by `SessionComposer` from the daily plan and the daily budget. |
| Spot loop | A run of the 2 worst bars of a passage (`Attempt.spot`, `SpotFinder`), one tempo rung below the run it came from, repeated until two clean loops in a row. Saved as practice, never a cold check or hint-fading input. |
| Cold open | The first card of a session: the most overdue due skill's passage, played with no hints as a review. |
| Card | One item of Today: a cold open, a new lesson (with a "why now" line) or a song, each opening Practice on a passage and tempo rung. |
| Ticked | A course concept the learner has passed: any of its passages has a clean wait-mode run (`CourseProgress.ticked`). A timed-only clean run doesn't tick. Songs shows a tick on its pieces; nothing is locked by it. |
| Next lesson | The first course concept in map order that isn't ticked (`CourseProgress.next`). Today's Lesson card suggests it, Songs marks its pieces with a "Next" chip and the lesson's Recap offers it. |
| Lesson step | One of the five steps of a lesson, Why, See, Try, Use, Recap (`LessonStep`); the step reached is saved per concept in `Settings.lessonSteps` until the lesson is finished. |
