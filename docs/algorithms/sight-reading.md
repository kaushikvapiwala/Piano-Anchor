# Sight-reading staircase

Sight-reading means playing music never seen before. Reading a tune that is already known tests memory, so the
exercises are generated from a seed and never repeat by design. The seed is stored in the exercise id
(`sight:<level>:<seed>`), so the same id always rebuilds the same exercise. The code is in
`engine/src/commonMain/kotlin/pianoanchor/engine/sight/`.

## Levels

Twelve levels, four bars each (`SIGHT_BARS`). A level opens when the learner has the skill that introduces its
material, and levels open in order: if level 3 is closed, level 4 stays closed even if bass clef is learned.

| Level | Opens with | Material |
|---|---|---|
| 1 | staff steps | right hand, C4 to G4, steps (or a repeat), quarters and halves, 4/4 |
| 2 | treble clef | adds skips of a third |
| 3 | grand staff, turns, rests | adds whole notes and quarter rests |
| 4 | bass clef, whole note | left hand alone, C3 to G3, the material of levels 1 to 3 |
| 5 | grand staff, turns, rests | hands take turns, one bar each, right hand first |
| 6 | 3/4 time | the turns of level 5 in 3/4, with dotted halves |
| 7 | hands together, held | right-hand tune over one held left-hand note per bar (C3 or G3) |
| 8 | melody and chords | right-hand tune over a held C, F or G triad per bar, last bar C |
| 9 | eighth notes | the right-hand material of level 3 plus beamed eighth pairs |
| 10 | dotted quarter | level 9 plus a dotted quarter followed by an eighth |
| 11 | right-hand G position | level 10's cells in right-hand G position (G4 to D5), key of G, from and back to G4 |
| 12 | G and D7 chords | the level 11 tune over one held left-hand chord per bar: G (G2 B2 D3), then G or D7, then D7, then G |

Levels 9 and 10 stay in the right hand, C4 to G4, 4/4, with no left hand. Level 9 needs melody and chords as well as
eighth notes, because the levels open in order. Their new rhythm cells (ticks; a quarter is 960):

| Level | Cells added |
|---|---|
| 9 | 480 480 960 960 960; 960 960 480 480 960; 960 480 480 1920; 480 480 480 480 1920 |
| 10 | 1440 480 960 960; 1440 480 1920; 960 1440 480 960 |

Levels 11 and 12 add no cells: they draw from level 10's.

## Generating an exercise

Each bar is filled from a short fixed list of rhythm cells that each add up to one bar. The pitches are a walk over the
five white keys from the level's tonic, moving by the level's allowed jump (a step, plus a skip from level 2) or staying put, and never
leaving the range. The walk starts and ends on the tonic, the first key of the position (C4 for levels 1 to 3 and 7 to 10, C3 for 4, the turns levels keep C4 and C3, G4 for 11 and 12); the last bar uses only a cell with no rest whose last note is its
longest, so the phrase closes on a long tonic (rule unchanged at levels 9 and 10: a cell ending on an eighth or a dotted quarter never closes it). At level 8 the chords are C, then F or G, then F or G, then C, and the
right-hand note on beat 1 of each bar is a tone of that bar's chord. At level 12 the chords are G, then G or D7, then D7, then G, and the beat-1 note is a tone of G (G B D) or of D7 (A C D). Each exercise carries its key: C for levels 1 to 10 and G for 11 and 12, so the staff draws the sharp. Because those constraints can leave a dead end, the
walk backtracks and tries another note; a valid walk always exists. The tempo is only a placeholder, since sight
reading runs in wait mode. The passage lists no skills, so saving a sight attempt records no skill review.

## The staircase

The level is derived from saved attempts; nothing else is stored. Only the first play of each exercise counts, in
`startedAt` order, so replaying a piece cannot be used to climb. Starting at level 1: a CLEAN first play adds to a
streak and `SIGHT_UP_STREAK` of them in a row move up one level; GOOD keeps the level and ends the streak; KEEP_PRACTISING
moves down one level. The level is clamped to 1 and the highest open level after every attempt. Every counted attempt
moves the walk by its grade, whichever level it was played at.

A staircase that goes up after two successes and down after one failure settles at the level where about 70% of plays
are clean (Levitt, 1971), hard enough to stretch and not enough to discourage.

## The daily card

Today offers one exercise a day (`docs/algorithms/session-composer.md`): the staircase level is computed from the
saved attempts whose passage id starts with `sight:` and the level the learner's skills have opened, and the exercise
is generated from that level and the learner day's epoch day, so it is new each day and stable within it. Once a
sight-reading attempt has started on the current learner day (the day that begins at `LEARNER_DAY_START_HOUR`), the card is gone until tomorrow. Practice plays it in wait mode
from the staff alone: no key hints, no hear-first, no tempo rungs, no perform mode, no spot loop. Results name the
level ("Sight-reading · level 3") with no run number and one line on how the level moved, found by comparing the
staircase over the attempts up to this one with the staircase over those before it; a replay of the same exercise
says only the first play moves the level.
