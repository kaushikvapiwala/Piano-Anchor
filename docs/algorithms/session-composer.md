# Session composer

`SessionComposer` (in `:engine`, `session/SessionComposer.kt`) turns the learner model's `DailyPlan`, the daily
budget and each passage's history into Today's `Session`: up to three cards and the minutes of the Review, Lesson and
Song segments. It is pure; the Today screen supplies the history from `AttemptRepositoryPort`.

## Cards
- **Cold open.** The first due skill of the plan (the plan lists them lowest retrievability first). Of the passages
  that exercise it, the one played least recently (never played counts as oldest; ties go to curriculum order). Its
  rung is the highest rung with a CLEAN timed run at that rung's tempo, else WAIT. A due skill the curriculum doesn't
  know, or with no passage, is skipped for the next one. Nothing due means no card.
- **Lesson.** The first new skill of the plan, on the first passage that exercises it, at WAIT. "Why now" lists the
  skill's prerequisites. No new skill means no card.
- **Song.** The latest unit with a learned skill that still has a passage not CLEAN at 100% and not already used by
  the cold open or the lesson; there, a public-domain piece is preferred over an original exercise, then curriculum
  order. The rung is the first above the passage's highest CLEAN rung, where a clean wait-mode run counts as a clean
  WAIT (so the song starts at WAIT only when nothing is clean). If no passage qualifies there is no card. The set of
  learned skills is an input (`LearnerModel.learnedSkills`): the plan lists only due and new skills, so a day with
  nothing due or new still offers a song.

## Minutes
The budget is split by `SESSION_SHARE_REVIEW`, `SESSION_SHARE_LESSON` and `SESSION_SHARE_SONG` (25/45/30 %). A
missing card's share goes to the others in proportion. Minutes are whole: each share is rounded down and the
leftover minutes go to the largest fractional parts, ties in the order review, lesson, song, so they always add up to
the budget (20 min gives 5, 9, 6; 15 min gives 4, 7, 4). With no cards all minutes are 0.

## Review segment
`Session.moreDue` holds the due skills beyond the cold open, in plan order. Once a run is saved the skill is no longer
due, so the next load of Today offers the next one as its cold open.
