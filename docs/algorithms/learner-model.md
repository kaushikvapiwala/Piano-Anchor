# Learner model

The learner model (`LearnerModel` in `:engine`) decides what to practise next. It turns each saved run into
spaced-repetition reviews, keeps a state per skill and plans each day's due and new skills. It reads and writes
only through `LearnerRepositoryPort`; the memory maths is in `docs/algorithms/scheduler.md`.

## Skills and passages
A `Skill` (in `:core:model`) has a stable id, a title and a set of prerequisite ids. Ids are lowercase kebab-case
so they are safe as storage keys and readable in logs; reviews are stored against them, so they never change.
A `Passage` lists the ids of the skills it exercises. A passage with no skills is never scheduled. Skill titles
live with the built-in content in `:engine`, like passage titles. There is no weighting between skills and no
attribution of a finding to one skill within a passage: a run counts for every skill the passage lists.

## The skill graph
`SkillGraph` refuses a graph that cannot be learned in some order: a bad id, a duplicate id, an unknown
prerequisite, a skill that requires itself, or a cycle. Each error names the skill, and a cycle error lists the
cycle (`a -> c -> b -> a`, each skill needing the next).

`SkillGraph.skills` is a topological order (Kahn's algorithm, smallest id first among the ready skills), so it is
the same on every run and platform. `unlocked(learned)` returns, in that order, the skills not yet learned whose
prerequisites are all learned. It ignores due dates and daily limits, which belong to the plan.

## Learner day
A review belongs to a learner day: the local date in the learner's time zone, where the day starts at
`LEARNER_DAY_START_HOUR` (a run at 01:30 still belongs to the evening before). `learnerDay` reads the local clock
time and subtracts a calendar day when it is before that hour, so a daylight-saving change cannot shift the date
by a day. A skill has at most one review per learner day.

## Cold checks
The first saved attempt on a learner day that exercises a skill is that skill's review for the day, whatever the
mode and tempo. It is a cold check: no warm-up has been counted first. Later attempts that day change nothing, so
playing a passage over and over cannot inflate the memory. `record` writes one review for each of the passage's
skills that has none on that day, and returns them. A day earlier than the skill's last review (the clock went
backwards) also records nothing, because FSRS needs the gap to be at least one day.

## Ratings
The band of the attempt gives the rating: `KEEP_PRACTISING` is Again, `GOOD` is Hard and `CLEAN` is Good. Easy is
never produced: it inflates intervals, and motor skills fade less predictably than facts. Tempo-aware ratings wait
for guidance fading (M5).

## State after a review
- The first review of a skill uses `Fsrs.initial(rating)` and sets `introducedDay`; later ones use
  `Fsrs.next(memory, days since the last review, rating)`.
- `dueDay` is the review day plus `Fsrs.intervalDays(memory)`.
- `cleanColdChecks` counts Good ratings (clean runs) on distinct days; a lapse does not reset it.
- `recalledColdChecks` counts Hard and Good ratings (recalled runs, clean or with small slips) the same way; Again
  adds nothing, a lapse does not reset it, and it is never below `cleanColdChecks`.
- `learned` becomes true once there are `LEARNED_MIN_RECALLED_COLD_CHECKS` recalled cold checks, at least
  `LEARNED_MIN_CLEAN_COLD_CHECKS` of them clean, and the stability is at least `LEARNED_MIN_STABILITY_DAYS`. A
  skill that is recalled every time with small slips therefore no longer stalls its dependants. It is sticky: a learned skill stays learned (it keeps unlocking its
  dependants), and a lapse only makes it due sooner, through FSRS.

## The daily plan
`plan(day)` returns a `DailyPlan`:
1. **Due:** skills whose `dueDay` is on or before the day, lowest current retrievability first (ties by id), at most
   `MAX_DUE_PER_DAY`. The overflow is not dropped, just not offered; it is still due on later days.
2. **New:** only while fewer than `MAX_DUE_PER_DAY` are due. Skills unlocked by the learned set that were never
   introduced (a skill is introduced by its first review), in graph order, at most `MAX_NEW_PER_DAY` minus the
   skills already introduced on that day.

## Replay
The review log is the source of truth and the stored states are a cache. `replay` rebuilds every state by folding
each skill's reviews, in day order, through the same rules as `record`, then calls `replaceStates`. It covers the
graph's skills and any skill that has a cached state. The result equals the states `record` built up.

## When storage fails
`PracticeViewModel` calls `record` after the attempt is saved. If learner storage is unavailable the run is
already safe, so the failure is skipped without an error card; a replay or the next cold check recovers.

## Worked example
One skill, default parameters, five review days. Stability is in days.

| Day | Run | Rating | Stability | Due | Clean checks | Recalled | Learned |
|---|---|---|---|---|---|---|---|
| 1 | clean | Good | 2.31 | day 3 | 1 | 1 | no |
| 3 | clean | Good | 10.96 | day 14 | 2 | 2 | yes (2 recalled, 2 clean, stability over 3) |
| 8 | good band | Hard | 22.49 | day 30 | 2 | 3 | yes |
| 20 | keep practising | Again | 1.93 | day 22 | 2 | 3 | yes (sticky) |
| 22 | clean | Good | 4.55 | day 27 | 3 | 4 | yes |

Day 1 is the first review, so it starts from the initial stability of a Good rating. On day 3 the second recalled cold
check (one clean is enough) pushes stability past the bar and the skill is learned. Day 8 is an early review (not yet due), rated Hard.
On day 20 a lapse cuts stability to under two days and the skill is due again in two, but it stays learned.
