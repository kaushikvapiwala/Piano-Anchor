package pianoanchor.engine.analysis

/** A straight time map: a note at `ticks` is expected at [interceptNanos] + [nanosPerTick] × `ticks`. */
internal data class TempoLine(
    val interceptNanos: Double,
    val nanosPerTick: Double,
)

/** The least-squares tempo fit (`docs/algorithms/performance-analysis.md`). */
internal object TempoFit {
    /**
     * The least-squares line of onset nanos against onset ticks through [points] (ticks to onset nanos). Null with
     * fewer than two distinct ticks, or when the line doesn't rise (no tempo).
     */
    fun fit(points: List<Pair<Int, Long>>): TempoLine? {
        if (points.distinctBy { it.first }.size < 2) return null
        val meanTicks = points.sumOf { it.first.toDouble() } / points.size
        val meanNanos = points.sumOf { it.second.toDouble() } / points.size
        val covariance = points.sumOf { (ticks, nanos) -> (ticks - meanTicks) * (nanos - meanNanos) }
        val variance = points.sumOf { (ticks, _) -> (ticks - meanTicks) * (ticks - meanTicks) }
        val slope = covariance / variance
        return if (slope > 0) TempoLine(meanNanos - slope * meanTicks, slope) else null
    }
}
