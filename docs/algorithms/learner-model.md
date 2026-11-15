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
  `LEARNED_MIN_CLEAN_COLD_CHECKS` of them clean, and the stability is at least `LEARNED_MIN_STABILITY_DAYS`; or
  once there are that many recalled cold checks, clean or not, and the stability is at least
  `LEARNED_MATURE_STABILITY_DAYS` (Anki's "mature" bar). A skill that is recalled every time with small slips
  therefore can't stall its dependants for good: the 90-day simulation found a skill recalled seven times, never
  clean, that locked a third of the graph. It is sticky: a learned skill stays learned (it keeps unlocking its
  dependants), and a lapse only makes it due sooner, through FSRS.

## The daily plan
`plan(day)` returns a `DailyPlan`:
1. **Due:** skills whose `dueDay` is on or before the day, lowest current retrievability first (ties by id), at most
   `MAX_DUE_PER_DAY`. The overflow is not dropped, just not offered; it is still due on later days.
2. **New:** only while fewer than `MAX_DUE_PER_DAY` are due. Skills unlocked by the learned set that were never
   introduced (a skill is introduced by its first review), in graph order, at most `MAX_NEW_PER_DAY` minus the
   skills already introduced on that day. The slots are filled one skill at a time by rotating over tracks (below).

### Tracks
Every skill belongs to a track (`CORE`, `SONGS` or `EAR`; `CORE` unless stated). All tracks share one skill graph, so
a prerequisite may sit in another track (an ear skill can need a core skill), one due list and one budget.
- **Due** ignores tracks: one list, lowest retrievability first, capped in total.
- **New** rotates: among the tracks that have an unlocked, never-introduced skill, take the one whose most recent
  `introducedDay` is oldest (a track never introduced counts as oldest; ties in track order CORE, SONGS, EAR), give
  it its first candidate in graph order, count that track as introduced today and repeat until the slots are full.
  Rotation follows the latest introduction, not counts, so a track that starts late is not pushed ahead to catch up.
  A track with nothing unlocked is skipped. Placed skills count as introduced for the rotation too.

## Replay
The review log is the source of truth and the stored states are a cache. `replay` rebuilds every state by folding
each skill's reviews, in day order, through the same rules as `record`, then calls `replaceStates`. It covers the
graph's skills and any skill that has a cached state. The result equals the states `record` built up.

## Placement
`place(skills, day)` starts a learner past the early units (the onboarding's "I can already play"). Each skill with no
state yet gets a review on `day` rated GOOD with attempt id `placement`, so its memory starts as after a first Good, and its
state is `learned = true`. A placement review is a claim, not a check: it adds nothing to `cleanColdChecks` or
`recalledColdChecks`, and `replay` treats it the same way, so the rebuilt states equal the stored ones. Skills that
already have a state are left alone, and a skill outside the graph is rejected. Placed skills come back for review at
their first interval and confirm themselves there. A placed state (learned, no recalled check) does not use up the day's
`MAX_NEW_PER_DAY` slots, so the first day still offers new lessons.

## When storage fails
`PracticeViewModel` calls `record` after the attempt is saved. If learner storage is unavailable the run is
already safe, so the failure is skipped without an error card; a replay or the next cold check recovers.

## Simulation (M2 exit check)
`LearnerSimulationExitTest` drives the real `LearnerModel`, on `FakeLearnerRepository`, with seeded simulated
learners (`SimulatedLearner` in `testing`). The setup:
- **The learners:** each learner's true memory of a skill follows the FSRS forgetting curve. It starts from the model's
  own initial state times a per-skill ability factor (log-normal, σ 0.3), so the model is not handed the answer. A cold
  check is recalled with the true retrievability. A recalled check is clean 70% of the time, and three practice
  runs on a new skill each raise its true stability by 15%.
- **The graph:** an original 60-skill graph in 6 layers of 10, each later skill with 1–2 prerequisites from the
  layer above.
- **The runs:** 20 seeds, each run twice: 90 days of daily practice, and the same with days 40–49 skipped.

**What it proves:**
- **Retention:** reviews of skills introduced at least 14 days earlier are recalled 0.90 ± 0.05 of the time,
  pooled; each seed stays within 0.78–1.00.
- **Load:** after day 14, due reviews never exceed `MAX_DUE_PER_DAY` and average at most three quarters of it.
- **Order and progress:** no skill comes before its prerequisites are learned; every skill is introduced by
  day 90, and at least 80% are learned.
- **The skip:** after the 10-day skip, the most overdue skills come first, the load stays capped and the backlog
  clears within 10 days.
- **Scenario guards:** two checks fail the test as "scenario too easy" if the load and skip checks would pass
  without being tested.

**What it doesn't prove:** that real learners behave like this one. The forgetting curve is FSRS's own, so the
retention check confirms the plan follows the model's predictions, not that the predictions fit people; that is
M7's calibration.

**Why pooled retention and 20 seeds:** one seed has only about 100–200 qualifying reviews, so its rate swings by
several points from luck alone (an earlier 5-seed run missed ±0.05 on a 39-review seed). Pooled over 20 seeds
(about 3 000 reviews), the luck error is about ±0.008. Twenty seeds also show a failure that hits 1 learner in 10 at
least once about 88% of the time (5 seeds: 41%; 10: 65%), and more seeds add little.

**What it found:** a skill recalled seven times with slips but never clean stayed unlearned and locked a third of the
graph. That led to the mature-stability path to learned (see "State after a review").

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
