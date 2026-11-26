package pianoanchor.engine.lesson

import pianoanchor.core.model.TryCheck
import pianoanchor.engine.Tunables

/**
 * How many clean checks in a row [check] needs to pass (`docs/course/beginner-course.md`, "Mastery"): 1 for a check the
 * app can't verify ([TryCheck.AnyKey], or a [TryCheck.Passage], which the Use step plays), `LESSON_TRY_REPS_LONG` for
 * a check of `LESSON_TRY_LONG_NOTES` or more notes, else `LESSON_TRY_REPS`.
 */
public fun tryRepsNeeded(check: TryCheck): Int =
    when {
        check is TryCheck.AnyKey || check is TryCheck.Passage -> 1
        TryRun(check).progress.total >= Tunables.LESSON_TRY_LONG_NOTES -> Tunables.LESSON_TRY_REPS_LONG
        else -> Tunables.LESSON_TRY_REPS
    }
