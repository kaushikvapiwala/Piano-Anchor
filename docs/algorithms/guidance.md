# Key-hint fading

The practice screen's amber band, which marks the keys of the waiting step, is a crutch. A passage that holds
without it is known; one that doesn't needs it back. `HintFading` and `HintDelay` (in `:engine`, package `guidance`)
decide how much help a run starts with. They are pure functions over the passage's stored attempts, so the level can
never drift from the history: there is no separate store.

## Levels
`HintLevel` has three values. At Full the band shows on the waiting step at once. At Delayed it appears only when the
learner has not pressed the step's keys within a delay. At Off it never appears unless the learner taps the Hint
button, which shows it for that step only. One level belongs to the passage and covers every rung, wait mode and timed
runs alike: hints are about knowing the notes, not the tempo.

## Level rule
`HintFading.level` walks the passage's attempts oldest first, starting at Full. A run of consecutive CLEAN attempts as
long as the fade count moves the level one step down; a run of KEEP_PRACTISING attempts as long as that count moves it
one step up. Every move resets both counts, and a GOOD attempt breaks either streak without adding to one. The level
stops at Full and at Off. Spot-loop attempts (those with a bar range) are skipped: they neither count nor break a
streak. The practice screen reads the level when its first run starts and keeps the band hidden until it has it, so a slow
store never flashes the band before a Delayed or Off run. Later runs on the same screen reuse that read (the history
can't change there); a failed read is tried again at the next start, and a failed save clears it.

## Delay rule
`HintDelay.millis` pools the gaps between the `StepCorrect` events of the newest few runs (the events already carry
each step's timestamp, in wait mode and timed runs alike, so no extra data is stored), takes their median and scales
it by a factor, then clamps the result so a fluent player is never flashed and a beginner never left stuck. With no
gap to learn from it returns a default.

The screen times the delay. In wait mode it counts from the previous step's completion, or from the start of the run
for the first step. In a timed run it counts from the step's written onset (the moment the cursor reaches it), and a
correct key press cancels it. A new step starts without the band again.

## Recording
Each attempt stores the level it started at and `hintShown`: true at Full, or once a delayed hint fired or the Hint
button was tapped during the run. Attempts stored before this existed read back as Full and shown, which is what they
were. Later milestones use `hintShown` to tell aided runs from unaided ones.

## Run modes
A run is played in one of two modes, stored as `Attempt.mode` (`RunMode`). Practice is the behaviour above: live
verdicts plus the faded hints. Perform is one take at a timed rung, like playing from a music stand: no key band at any
level, no Hint button, no live verdicts (note colours, timing tags and the key strip's verdict text stay hidden, and
TalkBack announces none); the staff, cursor, count-in, click and the keys lighting neutrally when pressed remain.
Results show everything afterwards. A perform run always stores `hintShown` false, so an attempt that is `PERFORM` and
not `hintShown` is unaided (`Attempt.unaided`), which later milestones read without a new port. It still stores the hint
level the passage would have started at and counts in the fading history like any run. Wait mode has no perform mode: it
always practises. The mode isn't remembered per passage; Practice opens by default, except that the cold open performs
when its rung is timed.

Numbers live in `engine/src/commonMain/kotlin/pianoanchor/engine/Tunables.kt` (the `HINT_*` and `SPOT_LOOP_*` constants).

## Spot loops

After a run, Results can offer to loop the stretch that went worst.

- **Findings per bar.** A bar of a step is its onset tick divided by the bar length (from the time signature), plus
  one. A wait-mode run's findings are its `WrongNote` and `ChordIncomplete` events, counted at the step they name. A
  timed run's findings are the performance analyser's, counted at their score note's step.
- **Window.** `SpotFinder.worst` slides a window of `SPOT_LOOP_BARS` bars over the passage and picks the one with the
  most findings; the earliest wins a tie. A passage shorter than the window gives its whole range. No findings, no
  window, and Results offer nothing. A perform take offers none either: loops are practice. A CLEAN run offers none:
  only GOOD and KEEP_PRACTISING runs do. Timed findings use the learner's strictness (the timing band the run was
  graded with), so the window agrees with the Results screen.
- **Slice.** `Passage.slice` keeps the steps inside the bars, moves them so the first bar starts at tick 0 and keeps
  the time signature, tempo, rights and skills. Its in-memory id gets a `#bars5-6` suffix; it never reaches storage.
- **Loop run.** Practice opens on the slice in practice mode, one tempo rung below the run's (wait mode stays wait),
  at the key-hint level the piece has reached (read from the whole piece's attempts, not the loop's).
  Each finished loop is graded and saved as usual under the piece's own id with `spot` set, then the next one starts at once; a short line
  says "Clean" or what went wrong. After `SPOT_LOOP_CLEAN_TARGET` CLEAN loops in a row the screen asks whether to play
  the whole piece ("Play it" opens the full passage at the original rung) or keep looping (the streak restarts). Any
  loop that is not clean resets the streak.
- **Not a review.** An attempt with `spot` set is never a cold check (`LearnerModel.record` skips it), does not count
  for hint fading, and is left out of the run-number, recent-runs, clean-tempo and clean-wait-run queries of the attempt
  store (Results' "run N" counts full runs only). It does count as playing the piece: the last-played date and the
  practice days include it. The done rungs a loop shows are the piece's.
