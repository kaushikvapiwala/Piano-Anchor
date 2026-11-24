# Session composer

`SessionComposer` (in `:engine`, `session/SessionComposer.kt`) turns the learner model's `DailyPlan`, the daily
budget and each passage's history into Today's `Session`: up to four cards and the minutes of the Review, Lesson,
Song and Ear segments. It is pure; the Today screen supplies the history from `AttemptRepositoryPort`
through `passageHistories` (beside `PassageHistory`), the loader Songs shares.

## Cards
- **Quizzes.** Before the cold open: the due core-track skills (plan order, most overdue first) that `QuizMaker` has a
  quiz for, at most `QUIZ_MAX_PER_DAY`, as one card the quiz screen runs (`docs/algorithms/quizzes.md`). Each quiz is
  seeded by the learner day and its skill id, so a day's quiz is stable. A quizzed skill counts as reviewed today: the
  cold open and `moreDue` skip it. Skills without a quiz (passage concepts, hand-shape), ear skills and unknown skills
  never get one. Its minutes are `ceil(count * QUIZ_SECONDS / 60)` and come off the budget before the shares are split;
  they are added to the Review segment. With no quizzable due skill there is no card.
- **Cold open.** The first due skill of the plan that is not an ear skill (the plan lists them lowest retrievability first). Of the passages
  that exercise it and whose skills are all learned (the due skill itself always counts, so its own passage
  qualifies; a piece needing later skills is not used to review an early one), the one played least recently (never played counts as oldest; ties go to curriculum order). Its
  rung is the highest rung with a CLEAN timed run at that rung's tempo, else WAIT. A due skill the curriculum doesn't
  know, or with no passage, is skipped for the next one. Nothing due means no card.
- **Lesson.** The first course concept in map order that isn't ticked (`CourseProgress.next`): a course skill (core
  track, the 23 skills of Units 0 to 3) is ticked when any passage that exercises it has a clean wait-mode run in the
  passage history (`PassageHistory.cleanWait`). The plan's new skills play no part, so a concept with a later concept
  ticked is still suggested first. The card carries the concept and its first passage (the lesson's Use step), at
  WAIT; "why now" lists the concept's prerequisites. It opens the five-step lesson, not Practice. With every concept
  ticked there is no Lesson card and its share goes to the others, as for any missing card.
- **Song.** A passage marked as a piece, not already used by the cold open or the lesson and not yet CLEAN at 100%.
  There is no lock filter: pieces are open whatever is learned, as on Songs. Exercises are never offered. Among the candidates of every unit: pieces
  already played before new ones, then public domain before original, then curriculum order (unit, then content
  order). The rung is the first above the passage's highest CLEAN rung, where a clean wait-mode run counts as a clean
  WAIT (so the song starts at WAIT only when nothing is clean). No candidate, or no learned skill at all, means no
  card. The set of
  learned skills is an input (`LearnerModel.learnedSkills`): the plan lists only due and new skills, so a day with
  nothing due or new still offers a song once anything is learned.

- **Ear.** The due ear skills in plan order, then the new ear skills, at most
  `max(1, earMinutes / EAR_MINUTES_PER_SKILL)` of them. It carries the skills (the ear screen opens them); it has no
  passage. No ear skills means no card. Ear skills never become the cold open, the lesson or `moreDue`.

## Minutes
The Quizzes card takes its minutes first; the rest of the budget is split as below.
The budget is split by `SESSION_SHARE_REVIEW`, `SESSION_SHARE_LESSON`, `SESSION_SHARE_SONG`, `SESSION_SHARE_SIGHT`
and `SESSION_SHARE_EAR` (18/32/27/10/13 %). A missing card's share goes to the others in proportion. Minutes are
whole: each share is rounded down and the leftover minutes go to the largest fractional parts, ties in the order
review, lesson, song, sight-reading, ear, so they always add up to the budget (20 min with all five cards gives 4, 6,
5, 2, 3; with no sight-reading card, 4, 7, 6, 3). With no cards all minutes are 0.

## Sight-reading card
`compose` takes `sightLevel` (0 when the staircase is closed, see `docs/algorithms/sight-reading.md`) and
`sightDoneToday`. The card exists when the level is at least 1 and no sight-reading attempt started today. It offers
the exercise of that level seeded by the learner day's epoch day, so a day's exercise is the same every time Today
loads, opens Practice in wait mode, and sits after the Song card and before the Ear card.

## Review segment
`Session.moreDue` holds the due skills beyond the cold open, in plan order. Once a run is saved the skill is no longer
due, so the next load of Today offers the next one as its cold open.
