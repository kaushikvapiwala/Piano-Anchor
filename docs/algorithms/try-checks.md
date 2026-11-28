# Try checks

Each of the 36 course concepts ends its Try step with one small task the piano can check: play the glowing key, find
every C, hold a note for two clicks. `TryRun` in the engine's lesson package turns live note events into progress for
the lesson screen and, later, review quizzes. It is pure and immutable: every event returns the next run. The check
data (`TryCheck`) is in `core/model`; the table of checks is `TryChecks.kt` in the engine's content package, read
through `Curriculum.tryCheck(skillId)`.

## Progress
`TryProgress` carries `done` of `total` parts, the `expected` key (the next one to play, or null where no single key is
next: any key, chords), the `lastWrong` key for the gentle hint, `finished`, `wrongCount` and `timing` (why the last attempt reset in time: `EARLY` or `LATE` for a rhythm or a pedal, `GAP` or `OVERLAP` for a legato, else null, for the
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
- **Legato:** `Legato(notes)`, at least two notes, like Sequence: the next note is expected and a wrong key waits
  (`lastWrong`). A note after the first must go down within `TRY_LEGATO_MAX_GAP_MS` of the note before coming up, else
  the attempt fails with `timing` = `GAP` (`wrongCount` + 1, `done` 0; the press counts as note 1 of a fresh attempt
  when it is the first note). A note may go down while the one before is still held (an overlap), but the older one must
  come up within `TRY_LEGATO_MAX_OVERLAP_MS` of that press, judged at its note-off, or at the next press if it is still
  down: else `OVERLAP` and the same reset. The pedal is not read. The run finishes when the last note goes down after a
  clean join; its own release is not judged.
- **Pedal hold:** `PedalHold(chord)`, `total` 3. Part 1: every chord note goes down within `CHORD_CLUSTER_MS` while the
  pedal is down (pedal first, or pedal down within that window after the cluster's first note). Part 2: every chord key
  comes up with the pedal still down. Part 3: the pedal stays down `TRY_PEDAL_RING_MS` after the last key came up,
  judged when the pedal comes up. A pedal lifted too soon (`timing` = `EARLY`), a note outside the chord, or a cluster
  or pedal that is late each add to `wrongCount` and reset `done` to 0. A late cluster is noticed at the next event and resets with `timing` = `LATE` and `lateStart` (its own hint: press the pedal with the chord, or just before it).
  A pedal pressed and lifted with no note played is not an attempt.
- **Pedal change:** `PedalChange(chords)`, at least two chords, `total` = number of chords, `expected` null. Chord 1 is
  part 1 of a pedal hold. Each later chord: its notes within `CHORD_CLUSTER_MS`; the pedal must come up at or after its
  first note and down again within `TRY_PEDAL_CHANGE_MAX_MS` of that note. A pedal lifted before the chord's first note
  fails with `EARLY` (the sound breaks); no change in the window, or the next chord started first, fails with `LATE`
  (the chords blur). With no timer, a missed window is judged at the next event past it. Both reset `done` to 0.
- **Legato, last pair:** the last note finishes the run at its press only when the note before has already come up; if
  it is still down the run waits and finishes when that note comes up within `TRY_LEGATO_MAX_OVERLAP_MS` (else `OVERLAP`).
- **The pedal in the run:** `TryRun.onPedal(value, atMs)` takes CC 64. The pedal is down at `PEDAL_DOWN_VALUE` or more
  and up again below `PEDAL_UP_VALUE`; only those crossings count. Checks that do not read the pedal ignore it. The
  lesson screen feeds the pedal to the run in Try only. A rep that starts while the pedal is held is seeded with it down (`TryRun(check, pedalDown)`). The three kinds use no click and no count-in.
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
| legato-touch | C D E F G F E D C joined (`Legato`) |
| sustain-pedal | the C chord (C3 E3 G3) with the pedal, hands lifted, left to ring (`PedalHold`) |
| pedal-changes | C, F, G, C chords (C3 E3 G3; A2 C3 F3; B2 D3 G3) with a pedal change after each (`PedalChange`) |
| the nine piece concepts | their first passage (rhythm-song: Jingle Bells; melody-chords-in-g: Go Tell Aunt Rhody; pedal-song: Amazing Grace) |

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

### The hand
`HandDemo.of(demo, hand, position)` turns a demo into a `HandTimeline`. The thumb goes over the lowest white key of the
concept's hand position (for the left hand the highest), so a demo that stops short of the position's edge keeps the
position's fingering: the single key C3 of left-hand C position is finger 5. The position is the concept card scene's
stage keys, used only when that scene has a hand, holds at most `HAND_MAX_WHITE_SPAN` white keys and contains every
demo note; otherwise the thumb goes over the lowest white key the demo plays (left: the highest). Finger k is over the
white key k - 1 places further (left: lower). Each tone becomes
a `FingerPress` for the finger over its key, so a repeated note keeps its finger and a chord presses several at once.
There is no hand (null, and the See step looks plain) when the demo has no tones, plays a black key, or spans more
than `HAND_MAX_WHITE_SPAN` white keys: the app never invents a fingering. The lesson passes the concept's hand (its
card scene's, right when it has none). The ViewModel exposes the timeline and the time into the demo; the screen
puts a finger down while its press is active, keeps the hand `HAND_HOLD_AFTER_MS` after the last press, fades it and
shows "Your turn". A See step that moves on by itself drops the hand with the step.

A Try with a click (Repeat, Hold, StressBeatOne, Rhythm) starts the metronome with a one-bar count-in ("3, 2, 1") before notes
count. A Try passes after `tryRepsNeeded` clean checks in a row: `LESSON_TRY_REPS`, or `LESSON_TRY_REPS_LONG` when the
check has `LESSON_TRY_LONG_NOTES` or more notes, or 1 for a check the app can't verify (`AnyKey`, `Passage`). A wrong
key (or a hold released early) sets the count to 0 and the check goes on; a check finished with a wrong key doesn't
count. The screen shows "Clean 2 of 3" beside the check's own progress (not when 1 is needed). Each finished check
shows its tick for `LESSON_TRY_DONE_PAUSE_MS`, then the next check starts, or the lesson moves to Use after the last. A `Passage` check has no
Try step: it is part of Use.

### Legato and pedal demos
A legato demo plays its notes in order, each for a full beat at 60 BPM into the next. A pedal hold plays the chord for
one beat, with a pedal-down cue at the start and a pedal-up cue `TRY_PEDAL_RING_MS` after the keys come up. A pedal change plays
each chord for two beats, with an up-then-down cue just after each new chord. `Demo.pedal` lists the cues
(`PedalCue(atMs, down)`). On a piano output the demo sends CC 64 (127 or 0) at each cue and lifts a pedal still down
if it stops; on the phone speaker the notes just sound. The See step draws the cue under the key strip with the same
pedal line as the staff and tells TalkBack "Pedal down" or "Pedal up". None of these uses the click.

## Pedal in lessons
`Curriculum.pedalIsMusic(skillId)` is true when the concept's Try is a pedal hold or a pedal change, or its piece
passage has pedal marks; the lesson then ignores pedal taps as "continue" (`docs/adr/0027-hands-free-lessons.md`).
Results do the same for a passage with pedal marks. Marks are drawn on the staff and never judged.
