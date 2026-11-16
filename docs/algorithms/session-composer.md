# Session composer

`SessionComposer` (in `:engine`, `session/SessionComposer.kt`) turns the learner model's `DailyPlan`, the daily
budget and each passage's history into Today's `Session`: up to four cards and the minutes of the Review, Lesson,
Song and Ear segments. It is pure; the Today screen supplies the history from `AttemptRepositoryPort`
through `passageHistories` (beside `PassageHistory`), the loader Songs shares.

## Cards
- **Cold open.** The first due skill of the plan that is not an ear skill (the plan lists them lowest retrievability first). Of the passages
  that exercise it, the one played least recently (never played counts as oldest; ties go to curriculum order). Its
  rung is the highest rung with a CLEAN timed run at that rung's tempo, else WAIT. A due skill the curriculum doesn't
  know, or with no passage, is skipped for the next one. Nothing due means no card.
- **Lesson.** The first new skill of the plan that is not an ear skill, on the first passage that exercises it, at WAIT. "Why now" lists the
  skill's prerequisites. No new skill means no card.
- **Song.** Only a piece Songs shows as open: a passage marked as a piece whose skills' prerequisites are all learned
  (`Pieces.waitingFor` is null, the same lock rule as Songs), not already used by the cold open or the lesson and not
  yet CLEAN at 100%. Chord exercises and locked pieces are never offered. Among the candidates of every unit: pieces
  already played before new ones, then public domain before original, then curriculum order (unit, then content
  order). The rung is the first above the passage's highest CLEAN rung, where a clean wait-mode run counts as a clean
  WAIT (so the song starts at WAIT only when nothing is clean). No candidate, or no learned skill at all, means no
  card. The set of
  learned skills is an input (`LearnerModel.learnedSkills`): the plan lists only due and new skills, so a day with
  nothing due or new still offers a song.

- **Ear.** The due ear skills in plan order, then the new ear skills, at most
  `max(1, earMinutes / EAR_MINUTES_PER_SKILL)` of them. It carries the skills (the ear screen opens them); it has no
  passage. No ear skills means no card. Ear skills never become the cold open, the lesson or `moreDue`.

## Minutes
The budget is split by `SESSION_SHARE_REVIEW`, `SESSION_SHARE_LESSON`, `SESSION_SHARE_SONG` and `SESSION_SHARE_EAR`
(20/35/30/15 %). A missing card's share goes to the others in proportion. Minutes are whole: each share is rounded
down and the leftover minutes go to the largest fractional parts, ties in the order review, lesson, song, ear, so they
always add up to the budget (20 min with all four cards gives 4, 7, 6, 3; with no ear card, 5, 8, 7). With no cards
all minutes are 0.

## Review segment
`Session.moreDue` holds the due skills beyond the cold open, in plan order. Once a run is saved the skill is no longer
due, so the next load of Today offers the next one as its cold open.
