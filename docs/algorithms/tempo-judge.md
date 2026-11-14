# Tempo judge

`TempoJudge` (in `:engine`, package `matcher`) tells the learner, note by note and while they play, whether a key was
right, wrong or off time in a tempo run. It is deliberately approximate: it needs an answer within a frame, so it
looks at one note at a time and never revisits. The performance analyser grades the whole take afterwards and has the
last word; the live marks are only feedback.

## The clock
A run at a rung's tempo puts tick 0 at `startNanos`, the beat after the count-in. The judge turns every step's written
onset into a time from that. `cursorStep(now)` is the last step whose onset has passed, clamped to the first and last
step. The run ends one beat after the last note's written end (`endNanos`).

## The rule
A NoteOn is matched to the nearest score note that is still unjudged, has the same pitch, and whose written onset is
within half the gap to its neighbouring steps (the first and last steps use their one neighbour, a lone step one
beat). Halving the gap makes the windows of neighbouring steps meet without overlapping, so a note can belong to only
one step.

- A match within the early/late band (`TIMING_TOLERANCE_MS` or `TIMING_TOLERANCE_BEAT_FRACTION` of a beat, whichever
  is larger, the analyser's own band) is `Correct`; further off it is `TimingOff` with the deviation in milliseconds
  (plus late, minus early).
- No match is `Wrong`: a pitch the score doesn't ask for here, a repeat of a judged note, or a note too far from any
  step.

A judged score note is never matched again, so a repeated key is `Wrong` even if it would fit.

## Why it can differ from the analyser
The analyser aligns the whole take, tolerates a drifting tempo and hesitations, treats brushed keys as mistouches and
judges note length. The judge sees none of that. A learner who is a beat late throughout is `TimingOff` on every note
live, and the analyser may read the same take differently. Both use the same tolerance so they usually agree.
