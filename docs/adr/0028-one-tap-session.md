# 0028. One-tap session

- Status: accepted
- Date: 2026-11-27

## Context
Today showed every card of the day and the learner had to open each one, then come back. A day streak also pressed on
learners who missed a day. The redesign boards (Session, Session recap, DecideNext, DecidePause, DecidePicker) replace
that with one button and a weekly goal.

## Decision
1. Today shows one hero with **Start session**. The pure `SessionRunner` turns the composed `Session` into ordered
   `SessionStep`s (warm-up, learn, practise, ear, sight-read). The existing step screens open one after another; a
   10-second card with a countdown ring sits between them (`SESSION_NEXT_COUNTDOWN_S`). A tap on it or a pedal tap
   (ADR 0027) goes now; Pause and Skip step are on it. After the last step a recap shows time, steps done, skills
   learned and a tip.
2. `SessionFlowViewModel` drives the flow. Its one piano reader (the pedal) runs only while the between card shows,
   and is closed and joined before a step's screen opens the piano (one reader per input).
3. The weekly goal replaces the day streak. `WeekPause` counts days, streak weeks and pauses; `Settings.pausedWeeks`
   stores the Mondays of paused weeks. At most two pauses are unused, one returns four weeks after use. A paused week
   counts as met.
4. Finished steps are kept in memory (`SessionProgress`, one per app run), not stored: a restart of the app starts the
   day's session again. No new port, no new library.
5. The recap's tip is a typed value (passage title and bars), and the sentence is built from string resources, so
   no UI text lives in the engine.
6. `rememberReduceMotion` moved from the lesson feature to `ui/shell` so both features share it.

## Consequences
Today's per-card layout, its strings and goldens are removed. The practise step ends when its run is saved, without the
Results screen. The play-along step and persisted session progress are left for later specs. The new thresholds are in
`Tunables`.
