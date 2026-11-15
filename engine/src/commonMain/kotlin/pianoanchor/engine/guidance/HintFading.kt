package pianoanchor.engine.guidance

import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.HintLevel
import pianoanchor.engine.Tunables

/** Which key-hint level a passage's next run starts at (`docs/algorithms/guidance.md`). */
public object HintFading {
    /**
     * The level for the next run of a passage, from its previous full runs, newest last. Starting at
     * [HintLevel.FULL], [Tunables.HINT_FADE_RUNS] CLEAN runs in a row move it one level down, as many
     * KEEP_PRACTISING runs in a row one level up. Each move resets the count and a GOOD run breaks either streak.
     */
    public fun level(history: List<Attempt>): HintLevel {
        var level = HintLevel.FULL
        var cleanRun = 0
        var weakRun = 0
        for (attempt in history) {
            cleanRun = if (attempt.band == Band.CLEAN) cleanRun + 1 else 0
            weakRun = if (attempt.band == Band.KEEP_PRACTISING) weakRun + 1 else 0
            val move =
                when (Tunables.HINT_FADE_RUNS) {
                    cleanRun -> 1
                    weakRun -> -1
                    else -> 0
                }
            if (move != 0) {
                level = HintLevel.entries[(level.ordinal + move).coerceIn(0, HintLevel.entries.lastIndex)]
                cleanRun = 0
                weakRun = 0
            }
        }
        return level
    }
}
