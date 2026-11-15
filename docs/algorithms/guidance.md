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
stops at Full and at Off. Attempts carry no spot-loop marker yet, so every stored attempt counts as a full run; the
spot loops of a later milestone will be skipped here once they can be told apart.

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

Numbers live in `engine/src/commonMain/kotlin/pianoanchor/engine/Tunables.kt` (the `HINT_*` constants).
