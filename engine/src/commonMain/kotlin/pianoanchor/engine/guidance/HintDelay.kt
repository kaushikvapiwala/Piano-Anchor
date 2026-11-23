package pianoanchor.engine.guidance

import pianoanchor.core.model.Attempt
import pianoanchor.core.model.StepCorrect
import pianoanchor.engine.NANOS_PER_MILLI
import pianoanchor.engine.Tunables

/** How long a Delayed hint waits (`docs/algorithms/guidance.md`). */
public object HintDelay {
    /**
     * Delay before a Delayed hint, from the step gaps of the passage's recent runs, newest first (as
     * `AttemptRepositoryPort.recent` gives them). A gap is the time between two consecutive steps' `StepCorrect`
     * events. The delay is the median gap of the newest [Tunables.HINT_DELAY_RUNS] runs times
     * [Tunables.HINT_DELAY_FACTOR], kept within [Tunables.HINT_DELAY_MIN_MS]..[Tunables.HINT_DELAY_MAX_MS];
     * [Tunables.HINT_DELAY_DEFAULT_MS] when those runs have no gap.
     */
    public fun millis(recentRuns: List<Attempt>): Long {
        val gaps =
            recentRuns
                .take(Tunables.HINT_DELAY_RUNS)
                .flatMap { run ->
                    run.events
                        .filterIsInstance<StepCorrect>()
                        .zipWithNext { a, b -> b.timestampNanos - a.timestampNanos }
                }.sorted()
        if (gaps.isEmpty()) return Tunables.HINT_DELAY_DEFAULT_MS
        val mid = gaps.size / 2
        val medianNanos = if (gaps.size % 2 == 1) gaps[mid].toDouble() else (gaps[mid - 1] + gaps[mid]) / 2.0
        val scaled = (medianNanos / NANOS_PER_MILLI * Tunables.HINT_DELAY_FACTOR).toLong()
        return scaled.coerceIn(Tunables.HINT_DELAY_MIN_MS, Tunables.HINT_DELAY_MAX_MS)
    }
}
