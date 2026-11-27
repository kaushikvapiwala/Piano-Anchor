# Try checks

Each of the 32 course concepts ends its Try step with one small task the piano can check: play the glowing key, find
every C, hold a note for two clicks. `TryRun` in the engine's lesson package turns live note events into progress for
the lesson screen and, later, review quizzes. It is pure and immutable: every event returns the next run. The check
data (`TryCheck`) is in `core/model`; the table of checks is `TryChecks.kt` in the engine's content package, read
through `Curriculum.tryCheck(skillId)`.

## Progress
`TryProgress` carries `done` of `total` parts, the `expected` key (the next one to play, or null where no single key is
next: any key, chords), the `lastWrong` key for the gentle hint, `finished`, `wrongCount` and `timing` (why the last rhythm reset happened: `EARLY` or `LATE`, else null, for the
hint). Once finished, further
events change nothing. A note-off is ignored except by a hold. Timing is not judged, except where it is the concept
itself (hold length, the beat-1 accent, the spacing of a rhythm's taps).

## Kinds
- **Any key:** the first press finishes; the app cannot see fingers and trusts the learner.
- **Play key:** the right note finishes; another key is wrong and the run waits. `lit` only says whether the strip glows it.
- **Find all:** every key of a pitch class in a range, once each, in any order. A repeat counts nothing. A key of
  another pitch class is wrong and `expected` is the unfound target nearest to it (the lower one on a tie); a key of the
  right pitch class outside the range counts nothing either way (a quiz's "Play every F" names no range). The range is the 88-key piano (A0 to C8), so "every C" is 8 keys; using the connected piano's range is a
  later follow-up.
- **Sequence:** like wait mode: the next note is expected, a wrong note waits.
- **Repeat:** each press of the note counts, however it is timed; other keys are wrong.
- **Hold:** the note-off must come at least `clicks x 60000 / bpm x (1 - TRY_HOLD_TOLERANCE)` ms after the note-on.
  An earlier release resets to 0 and adds to `wrongCount`, with no wrong key.
- **Soft then loud:** a press at or under `TRY_SOFT_MAX_VELOCITY`, then one at or over `TRY_LOUD_MIN_VELOCITY`. A press
  at the wrong level does not count and adds to `wrongCount`.
- **Stress beat one:** `bars x 4` presses, then the mean velocity of presses 1, 5, 9, ... must beat the mean of the rest
  by `TRY_STRESS_MIN_DELTA`; otherwise the count resets and `wrongCount` grows. Other keys are wrong and count no press.
- **Rhythm:** `Rhythm(note, lengths, bpm)` is a pattern of at least two lengths in ticks (`TICKS_PER_QUARTER` = a
  quarter), played on one note with the click. Only presses of `note` count; another key is wrong (`lastWrong`,
  `wrongCount` + 1, the count stays). Press 1 starts the pattern at any moment. Press k (k of 2 or more) must come
  `lengths[k-2] x 60000 / (bpm x 960)` ms after press k-1, give or take `TRY_RHYTHM_TOLERANCE x 30000 / bpm` ms (a
  fraction of an eighth note, each side). A press outside that window adds to `wrongCount`, sets `timing` to `EARLY` or
  `LATE`, and itself becomes press 1 of a fresh attempt (`done` = 1), so a learner who tapped late can carry on from
  that tap. `total` is `lengths.size` and the run finishes when `done` reaches it; the last note's length is not
  judged and note-offs are ignored. Presses before the count-in ends count nothing (the lesson screen starts the
  run after it, as for Repeat and Hold). The See step also says the count, such as "1 & 2 &" (`rhythmCount`: a number
  for a tap on a beat, "&" for a tap halfway, brackets for a beat held through).
- **Chord:** all notes within `CHORD_CLUSTER_MS` of the cluster's first. A note later than that, or outside the chord,
  fails the cluster (`wrongCount` + 1, `done` back to 0); a late chord note starts a new cluster. A repeat inside the
  cluster is ignored.
- **Passage:** piece concepts play the concept's first passage in wait mode with right notes. The practice screen
  checks it, so `TryRun` rejects it.

## The table
| Skill | Check |
|---|---|
| hand-shape | any key |
| middle-c | play C4, lit |
| black-key-groups | play C4, not lit |
| high-and-low | find every C |
| steady-beat | C4 eight times at 60 BPM |
| quarter-and-half | hold C4 for 2 clicks |
| white-key-names | find every F |
| rh-c-position | C D E F G F E D C |
| steps-and-skips | E4 then G4 |
| bars-and-counting | stress beat one, 2 bars |
| staff-steps | C D E |
| treble-clef | play G4, not lit |
| soft-and-loud | C4 soft then loud |
| lh-c-position | play C3, lit |
| bass-clef-whole-note | hold C3 for 4 clicks |
| c-chord | C E G together |
| sharps-and-flats | play C#4, not lit |
| eighth-notes | C4 in eight eighths at 60 BPM (eight taps 500 ms apart) |
| eighth-pairs | C4 in quarter, quarter, eighth, eighth, quarter at 60 BPM: 1 2 3 & 4 |
| dotted-quarter | C4 in dotted quarter, eighth, half at 60 BPM: 1 (2) & 3 |
| rh-g-position | G A B C D C B A G (G4 up to D5 and back) |
| lh-g-position | D3 C3 B2 A2 G2 (thumb to little finger) |
| key-of-g | find every F sharp (7 keys) |
| g-and-d7-chords | F#2 C3 D3 together |
| the eight piece concepts | their first passage (rhythm-song: Jingle Bells; melody-chords-in-g: Go Tell Aunt Rhody) |

The thresholds are `TRY_*` tunables in `Tunables.kt`, with the reasons beside each.

## See demos
The lesson's See step plays what the Try asks for (`LessonDemo`), on the piano when it lists as an output, else on the
phone speaker, lighting the keys as they sound:

| Check | Demo |
|---|---|
| PlayKey | the note |
| FindAll | every target, low to high |
| Sequence | the notes in order |
| Repeat, Hold, StressBeatOne | on the click, one beat after it starts; StressBeatOne accents beat 1 by velocity |
| Rhythm | the pattern on the click, one beat after it starts, each note sounding half its written length |
| SoftThenLoud | the note at velocity `LESSON_DEMO_SOFT_VELOCITY`, then at `LESSON_DEMO_LOUD_VELOCITY` |
| Chord | the notes together |
| Passage | the passage at its written tempo |
| AnyKey | nothing to hear: the screen says "Watch the hand" |

A Try with a click (Repeat, Hold, StressBeatOne, Rhythm) starts the metronome with a one-bar count-in ("3, 2, 1") before notes
count. A Try passes after `tryRepsNeeded` clean checks in a row: `LESSON_TRY_REPS`, or `LESSON_TRY_REPS_LONG` when the
check has `LESSON_TRY_LONG_NOTES` or more notes, or 1 for a check the app can't verify (`AnyKey`, `Passage`). A wrong
key (or a hold released early) sets the count to 0 and the check goes on; a check finished with a wrong key doesn't
count. The screen shows "Clean 2 of 3" beside the check's own progress (not when 1 is needed). Each finished check
shows its tick for `LESSON_TRY_DONE_PAUSE_MS`, then the next check starts, or the lesson moves to Use after the last. A `Passage` check has no
Try step: it is part of Use.
