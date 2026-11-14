# Wait-mode matcher

`WaitModeMatcher` (in `:engine`, package `matcher`) follows the learner through a `Passage` in wait mode: the
music waits, and the matcher moves to the next step only once the current one has been played. It turns
`MidiEvent`s into `MatchEvent`s (`StepCorrect`, `WrongNote`, `ChordIncomplete`, `PassageComplete`), which grading,
storage and the practice screen consume. `Flow<MidiEvent>.matchWaitMode(passage)` runs it over a live or replayed
stream and completes right after `PassageComplete`.

## Why a pure reducer
`reduce(state, event)` returns the next `MatchState` and the events the input caused. There is no clock and no
timer: time comes only from event timestamps, so the same input always gives the same output, and recorded
fixtures replay exactly as they were played live.

## Rules
Only `NoteOn` matters. `NoteOff`, `ControlChange` and `SysEx` change nothing, and channel and velocity are
ignored (a NoteOn on any channel counts). Rhythm is ignored too: the matcher reads only each step's pitches, never
its onset, note lengths, the time signature or the written tempo.

1. The first NoteOn of the attempt, right or wrong, sets `startedAtNanos`.
2. **Window expiry comes first.** If a chord window is open and this note's onset is more than
   `CHORD_CLUSTER_MS` after the window's first note, the step was not finished in time: emit `ChordIncomplete`
   with the notes still missing, clear the pressed notes and the window, then handle this note as a fresh one.
   The learner plays the whole step again. A note exactly at the window's edge is still inside it.
3. A note of the current step that is already pressed is ignored (a re-strike inside the window).
4. A note of the current step that isn't pressed yet is added; the first one opens the window. When every note of
   the step is pressed, emit `StepCorrect` and move to the next step. After the last step, also emit
   `PassageComplete` and stop.
5. A note that isn't in the current step emits `WrongNote`. Progress and the window are untouched, so a chord can
   still be finished in its window after a wrong note.
6. Once complete, every event is ignored.

A single-note step is a chord of one: its first note completes it, so the window never matters there.

## Expiry is noticed at the next note
A pure reducer can't fire when a window runs out, so the incomplete chord is reported at the next NoteOn, with
that note's timestamp. Wait mode loses nothing by this: nothing moves on until the learner plays, and the next
note is exactly when the learner (and the screen) needs to know.

## Worked example: a C-E-G step
The step is {C4, E4, G4}. The times assume a `CHORD_CLUSTER_MS` of 90 ms.

| Onset | Note | Result | Pressed after |
|---|---|---|---|
| 0 ms | C4 | window opens | {C4} |
| 20 ms | C#4 | `WrongNote(C#4)` | {C4} |
| 20 ms | E4 | | {C4, E4} |
| 40 ms | C4 | re-strike, ignored | {C4, E4} |
| 60 ms | G4 | `StepCorrect` at 60 ms, next step | {} |

Had G4 come at 91 ms instead, the matcher would emit `ChordIncomplete(missing = {G4})` at 91 ms, then take G4 as
the first note of a new try at the same step: pressed {G4}, window open from 91 ms.

## Not handled here
Rhythm, tempo, durations, hands, fingering and classifying wrong notes (mistouch, octave) belong to later
analysis; hints belong to the practice screen.
