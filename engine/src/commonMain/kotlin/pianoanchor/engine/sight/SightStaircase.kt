package pianoanchor.engine.sight

import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.engine.Tunables

/** The 2-up/1-down staircase over saved sight-reading attempts (`docs/algorithms/sight-reading.md`). */
public object SightStaircase {
    /**
     * The current level from [attempts] (any order; non-sight ids ignored; only the earliest attempt of each passage
     * id counts, in startedAt order), starting at 1, clamped to 1..[open]. 0 when [open] is 0.
     */
    public fun level(
        attempts: List<Attempt>,
        open: Int,
    ): Int {
        if (open == 0) return 0
        var level = 1
        var streak = 0
        attempts
            .filter { SightReadingGenerator.parse(it.passageId) != null }
            .sortedBy { it.startedAt }
            .distinctBy { it.passageId }
            .forEach { attempt ->
                when (attempt.band) {
                    Band.CLEAN -> {
                        streak++
                        if (streak == Tunables.SIGHT_UP_STREAK) {
                            level++
                            streak = 0
                        }
                    }

                    Band.GOOD -> {
                        streak = 0
                    }

                    Band.KEEP_PRACTISING -> {
                        level--
                        streak = 0
                    }
                }
                level = level.coerceIn(1, open)
            }
        return level
    }
}
