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

## Why no timing yet
Wait mode has no tempo: the music waits for the learner, so there is nothing to be early or late against. Timing,
note length and hesitation need rhythm in the passage and come with later analysis.
