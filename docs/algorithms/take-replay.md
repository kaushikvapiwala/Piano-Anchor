# Take replay and "Day 1 vs today"

The Progress tab lets the learner hear their own first full run of a piece next to their latest one. This note
says how a stored take turns into sound and into a timing lane. Both are pure engine code (`TakeReplay`, `TakeLane`
in the `progress` package).

## Which takes
- A *full run* is an attempt with no spot loop whose events end the passage (a `PassageComplete`).
- The *first full run* of a passage is the oldest one by start time (ties by id); the *latest* is the newest.
  Both come from `AttemptRepositoryPort`. The first full run is never pruned: any future clean-up of old attempts
  must keep it, because the comparison needs it for as long as the learner plays the piece.
- The card shows the piece played most recently whose two takes started on different local days. With no such
  piece it is hidden; there is no empty state.

## Replay
`TakeReplay.tones` turns each played note into a tone from its key-down to the end of its sound (so a held pedal is
heard), with offsets counted from the first note and the velocity kept. A note shorter than a millisecond plays for
one, since a tone needs a length. The take is cut at `Tunables.TAKE_REPLAY_MAX_MS`: later notes are dropped and one
crossing the cap ends there. Playback goes through `TonePort`; one take sounds at a time and leaving the tab stops it.

## Timing lane
`TakeLane.dots` gives one dot per played note along the take. A note of a timed run is off the beat when the
performance analyser puts it further from its score note than the early/late band of the standard strictness (the
same judgement as the Results timing tile, after the piano's latency offset is taken out). A wait-mode take has no
beat, so all its dots are on the beat; a note that matches no score note is on the beat too.

## Learned this week
The Progress tab lists skills that became learned since Monday. `LearnerModel.learnedSince` replays each learned
skill's reviews and takes the day the replayed state first reached learned; a placement does not count, since it is
a claim and not something the learner just played.
