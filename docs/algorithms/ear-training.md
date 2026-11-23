# Ear training

The ear track teaches the learner to hear pitch relations and to find them on the piano. Eight skills, each needing the
one before it (the echo skills also need a playing skill, so the ear never runs far ahead of the hands).

| Skill | Format | What is heard |
|---|---|---|
| `ear-echo-note` | echo, exact key | one white key, C4 to G4 |
| `ear-higher-lower` | identify | two notes C3 to C5, 2 to 12 semitones apart, either direction |
| `ear-step-skip` | identify | two white keys C4 to C5, a second or a third apart, either direction |
| `ear-echo-three` | echo, any octave | 3 notes C4 to G4, each move a step or a skip |
| `ear-thirds` | identify | ascending major or minor third, root C3 to G4 |
| `ear-fourth-fifth` | identify | ascending fourth or fifth, root C3 to G4 |
| `ear-chord-quality` | identify | a root-position major or minor triad, sounded together, root C3 to G4 |
| `ear-echo-five` | echo, any octave | 5 notes C4 to G4, each move a step or a skip |

## Why sets of four
An identify item has two choices, so one item is right half the time by guessing. A review is therefore a set of
`EAR_ITEMS_PER_REVIEW` freshly drawn items (never the same twice in a set, unless the skill has too few to avoid it).
`EarGenerator.drawNew` repeats a draw up to `EAR_MAX_REDRAWS` times to avoid an item already in the set, then accepts one.
Only the set's band is stored, as one review of the skill: the ear track shares the daily plan and the learner model
with everything else (`docs/algorithms/learner-model.md`).

## Items
`EarGenerator.draw` is pure over an injected `Random`; the app seeds it from the clock. An item says what to play
(`EarExercise.sounds`: groups in order, the notes of one group sound together) and how to answer it. `tones()` turns it
into the playback schedule shared by MIDI and speaker output: one group every `PLAYBACK_NOTE_GAP_MS`, a single note held
`PLAYBACK_NOTE_LENGTH_MS`, a chord `EAR_CHORD_LENGTH_MS`. Those three playback values are the onboarding's too.

## Answering
- Identify: tap one of the two names. Echo: play the target notes; `EarEcho.matches` compares the next notes played
  with the target in order, by exact key (one-note echo, it trains register) or by pitch class (melodies).
- Replay plays the item again, at most `EAR_MAX_REPLAYS` times; it is not an answer. A wrong answer allows one more
  (`EAR_MAX_ANSWERS` in all). "Show me" after a first wrong answer plays the item and shows the answer.

## Outcome and band
An item ends FIRST_TRY (right at the first answer without a replay), RECOVERED (right, otherwise) or MISSED (two wrong
answers or Show me). `EarGrading.outcome` decides an item's outcome from the answer count and the replays. `EarGrading.band`: every item FIRST_TRY is CLEAN; at least `EAR_GOOD_MIN_RIGHT` right in the end is
GOOD; else KEEP_PRACTISING. `LearnerModel.recordEarSet` then records it like a played cold check: the first review of
the learner day counts and the band maps to the rating as for passages (never EASY).

## Sound
The piano plays the item through MIDI output (NoteOn and NoteOff only, never SysEx) when it lists an output. Without
one, or after a send fails, the phone plays simple tones (`docs/protocols/android-audio.md`) for the rest of the
session. The screen reads the piano only while an echo item waits for its answer, and closes it before leaving.
