# Review quizzes

Learned concepts come back as short quizzes the piano checks (`docs/course/beginner-course.md`, "Review"). The existing
FSRS planner decides which skills are due; `QuizMaker` (`lesson/QuizMaker.kt` in the engine) turns a due skill into a
`Quiz`: the `TryCheck` to pass, plus a typed `QuizPrompt` whose words live with the screen in `:feature:lesson`.
`SessionComposer` puts up to `QUIZ_MAX_PER_DAY` of them on Today as one Quizzes card; the quiz screen runs each through
`TryRun`.

## Which quiz
Each skill has one kind of quiz, varied by a random source seeded from the learner day and the skill id. The variety is
limited to what the learner has been taught (passed skills plus the quiz's own skill): without white-key names only
middle C and the C below are named, and the C chord joins the chord quiz once it was taught.

| Skill | Quiz |
|---|---|
| middle-c, black-key-groups | play C4, no light |
| high-and-low | play every C in `QUIZ_RANGE` |
| white-key-names | play every one of a random white letter (all seven, since this skill teaches them) |
| steady-beat | C4 four times with the click, 60 BPM |
| quarter-and-half | hold C4 for 2 clicks |
| bass-clef-whole-note | hold C3 for 4 clicks |
| rh-c-position | a step up from a random white key C4 to F4 (play it, then the next white key) |
| steps-and-skips | a skip up from a random white key C4 to E4 (play it, then two white keys on) |
| bars-and-counting | accent beat 1 for one bar, 60 BPM |
| staff-steps | a random white key C4 to G4, shown as a note on a mini staff |
| treble-clef | G4 shown on the staff |
| soft-and-loud | softly then loudly on a random white key C4 to G4 |
| lh-c-position | a random white key C3 to G3 |
| c-chord | the C chord |
| g-and-f-chords | a random chord of C, G, F |
| sharps-and-flats | a random black key C#4 to A#4, named with a sharp or a flat |
| eighth-notes | tap C4 in eight even eighths with the click, 60 BPM (the Try's `Rhythm` check) |
| eighth-pairs | tap C4 in quarter, quarter, eighth, eighth, quarter: 1 2 3 & 4 |
| dotted-quarter | tap C4 in dotted quarter, eighth, half: 1 (2) & 3 |
| everything else | none: passage concepts keep the cold-open passage review; hand-shape has nothing to check |

`QUIZ_RANGE` (three octaves around middle C) keeps "play every F" to 3 or 4 targets, and the click quizzes run at 60 BPM
with a one-bar count-in, so a quiz stays within about 20 s.

## Rating
Skipped gives AGAIN; finished with a wrong key (a wrong key, a failed attempt) gives HARD; finished clean gives GOOD.
The rating is recorded through `LearnerModel.recordQuiz` as a cold check (attempt id `quiz:`), at most one review per
skill and day: when the skill already has one that day, nothing is recorded. A quizzed skill is that day's review, so
the cold open never repeats it.

## Tunables
`QUIZ_MAX_PER_DAY` (3, keeps the warm-up about a minute), `QUIZ_SECONDS` (20, the budget per quiz) and `QUIZ_RANGE`
(48..84). Defined in `Tunables.kt`.
