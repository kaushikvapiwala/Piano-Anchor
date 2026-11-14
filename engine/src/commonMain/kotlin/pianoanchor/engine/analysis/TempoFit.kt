package pianoanchor.engine.analysis

/** A straight time map: a note at `ticks` is expected at [interceptNanos] + [nanosPerTick] × `ticks`. */
internal data class TempoLine(
    val interceptNanos: Double,
    val nanosPerTick: Double,
)

/** The median-gap tempo fit (`docs/algorithms/performance-analysis.md`). */
internal object TempoFit {
    /**
     * The line through [points] (ticks to onset nanos): nanos per tick is the median of the gaps between consecutive
     * distinct ticks (a chord counts by its earliest note), the intercept the median offset from that slope, so one
     * long pause doesn't move it. Null with fewer than two distinct ticks, or when the tempo doesn't rise. With only
     * two gaps a pause still moves the median, as it is their mean.
     */
    fun fit(points: List<Pair<Int, Long>>): TempoLine? {
        val onsets =
            points
                .groupBy({ it.first }, { it.second })
                .map { (ticks, nanos) -> ticks to nanos.min() }
                .sortedBy { it.first }
        if (onsets.size < 2) return null
        val nanosPerTick =
            median(
                onsets.zipWithNext { (fromTicks, fromNanos), (toTicks, toNanos) ->
                    (toNanos - fromNanos).toDouble() / (toTicks - fromTicks)
                },
            )
        return if (nanosPerTick > 0) {
            TempoLine(median(onsets.map { (ticks, nanos) -> nanos - nanosPerTick * ticks }), nanosPerTick)
        } else {
            null
        }
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }
}
