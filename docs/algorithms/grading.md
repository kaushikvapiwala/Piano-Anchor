# Grading

`Grader` (in `:engine`, package `grading`) turns a completed wait-mode run into an `Attempt`: its counts, an
accuracy, a band and the run's match events. `AttemptRepositoryPort` stores attempts; the Results screen reads them.

## Accuracy
accuracy = steps / (steps + wrong notes + incomplete chords)

`steps` is the passage's step count. In wait mode the matcher moves on only after a step was played, so a
completed run played every step correctly exactly once; the errors are everything else the learner pressed on the
way. A run without errors scores 1.0, and every slip lowers the score a little less than the one before, so one
wrong note costs more on a short passage than on a long one. The score never reaches 0.

Wrong notes and incomplete chords weigh the same: both are a moment where the hands did something other than the
music asked.

## Bands
The band names the result for the learner: `CLEAN` from `BAND_CLEAN_MIN_ACCURACY`, `GOOD` from
`BAND_GOOD_MIN_ACCURACY`, `KEEP_PRACTISING` below. An accuracy exactly at a threshold gets the higher band.

## Building an attempt
`Grader.attempt` counts `WrongNote` and `ChordIncomplete` in the run's events, takes the duration from the first
NoteOn (`startedAtNanos`) to `PassageComplete`, and rebases every event's timestamp so the first NoteOn is 0. The
events must end with `PassageComplete`: only completed runs are graded and stored, abandoned ones are dropped.

Events are kept with their relative timestamps so later analysis (a timing ribbon, hesitations) can use them
without a new recording.

## Timed accuracy
A tempo run is graded from the played notes, not from matcher events: `Grader.timedAttempt` runs the performance
analyser against the rung's tempo, with tick 0 at time 0 (the take is rebased to the written time of tick 0).

accuracy = score notes without a finding / (score notes + extra notes + mistouches)

Every finding on a score note (wrong, missed, octave, incomplete chord, early, late, hesitation, too short or long)
spoils that note. An extra or brushed key is not a fault of any score note, so it only enlarges the denominator
instead of also spoiling the note it sits next to. The bands use the same thresholds as wait mode. The attempt's wrong
notes count wrong, octave, extra and mistouch findings; incomplete chords count chord findings; it keeps no match
events, but stores its tempo and the played notes. Wait mode also stores its played notes, rebased to the first
NoteOn, with no tempo.

Wait mode itself has no tempo, so it still grades by pitch alone; the Results tiles for timing come later.
