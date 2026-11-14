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

Wait mode itself has no tempo, so it still grades by pitch alone.

## Results summaries
`ResultsSummary.of` turns an attempt and its analysis into the Results tiles. A timed attempt is analysed against its
tempo with tick 0 at time 0; a wait-mode attempt with played notes is analysed untimed; an attempt stored before played
notes were kept is not analysed and shows pitch only.

- Pitch: timed runs count score notes without a wrong, missed, octave or incomplete-chord finding; wait mode counts
  steps, all correct. The detail line agrees with the value: wrong notes (wrong and octave findings, per score note),
  missed notes, and incomplete chords (per step) for a timed run; the attempt's own counts for wait mode.
- A timed attempt is always analysed, even with no played notes (every note is then missed). Only a wait-mode attempt
  without played notes is skipped.
- Timing (timed only, and only when some note had its exact pitch; otherwise the tile is blank): the mean absolute onset deviation over exact-pitch matches in whole milliseconds; off the beat
  when any early or late finding exists. The worst note is the early or late finding with the largest deviation.
- Note length (timed only, blank like Timing without an exact-pitch note): the share of exact-pitch matches without a too-short or too-long finding. The worst note is
  the finding furthest from the written length, as held over written when long and written over held when short.
- Hesitation: the number of hesitation findings. The longest pause is the largest gap before the step of those
  findings, with the note it came before, rounded once to the nearest millisecond; it is unknown when the previous step had no matched note.
