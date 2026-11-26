# 0027. Hands-free lessons

- Status: accepted
- Date: 2026-11-26

## Context
The learner has both hands on the piano. Tapping "Next" on the tablet breaks the flow, and ADR 0026's lesson card
shows four text fields at once, which is a lot to read before playing.

## Decision
1. A tap of the sustain pedal (CC 64 down and up within `PEDAL_TAP_MAX_MS`, with hysteresis) means "continue": it
   presses the primary button of the lesson, quiz summary, Results and Today. It is ignored during a run, a Try, a quiz
   and is meant to be ignored for concepts that use the pedal as music: `LessonViewModel` has a `pedalIsMusic` flag for that, which nothing sets yet and which gets wired when course 2's pedal unit adds pedal concepts. `Settings.pedalControlOn` (default on) turns it off. Each screen's
   ViewModel reads the piano events it already reads (Results and Today open the piano and close it before another
   screen does, so there is still one reader) and runs the pure `PedalTapDetector`; there is no new port.
2. Lesson steps move on by themselves: Why after one animation loop (`LESSON_WHY_AUTO_MS`), See when the demo ends,
   Use opens Practice after `LESSON_AUTO_OPEN_MS` (not when the lesson was reopened at Use). Recap waits.
3. This amends ADR 0026's lesson screen: the card pane shows the animation and the What line, with a "More" button for
   Why, How and When. Recap shows Why and How. The four fields remain required in the content.

## Consequences
Screens that lead away close their piano reader first (a `releasePiano` or `close`). The timings and the tap length are
starting values to tune on a real piano (ROADMAP, owner device checks).
