package pianoanchor.engine.calibration

import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.PlayedNote
import pianoanchor.engine.Tunables
import pianoanchor.engine.matcher.beatNanos
import kotlin.math.abs

private const val NANOS_PER_MILLI = 1_000_000L
private const val HALF = 2

/**
 * What a calibration run found (`docs/algorithms/latency-calibration.md`). Offsets are positive when notes are late.
 */
public sealed interface CalibrationResult {
    /** The median offset of the counted taps and their median absolute deviation. */
    public data class Accepted(
        val offsetNanos: Long,
        val spreadNanos: Long,
    ) : CalibrationResult

    /** The taps were not locked to the beat: their median absolute deviation is above the limit. */
    public data class TooUneven(
        val spreadNanos: Long,
    ) : CalibrationResult

    /** The median offset is beyond `Tunables.CALIBRATION_MAX_OFFSET_MS`: the link or the setup is wrong. */
    public data class OutOfRange(
        val offsetNanos: Long,
    ) : CalibrationResult

    /** Fewer than `Tunables.CALIBRATION_MIN_TAPS` counted clicks were answered. */
    public data class TooFewTaps(
        val taps: Int,
    ) : CalibrationResult
}

/** Measures one piano's latency offset from taps on metronome clicks (`docs/algorithms/latency-calibration.md`). */
public object LatencyCalibration {
    /** Click times of the calibration pattern starting at [firstClickNanos]. */
    public fun clickTimes(firstClickNanos: Long): List<Long> {
        val beat = beatNanos(Tunables.CALIBRATION_BPM)
        return List(Tunables.CALIBRATION_CLICKS) { firstClickNanos + it * beat }
    }

    /**
     * Pairs each NoteOn in [taps] with the nearest click within half a beat (one tap per click: the nearest to it),
     * drops the warm-up clicks, and returns the median offset and median absolute deviation. A beat is the spacing of
     * [clicks], which must have at least two. Checked in order: too few counted taps, too uneven, out of range.
     */
    public fun evaluate(
        clicks: List<Long>,
        taps: List<NoteOn>,
    ): CalibrationResult {
        val offsets = countedOffsets(clicks, taps).values.toList()
        if (offsets.size < Tunables.CALIBRATION_MIN_TAPS) return CalibrationResult.TooFewTaps(offsets.size)
        val offset = median(offsets)
        val spread = median(offsets.map { abs(it - offset) })
        return when {
            spread > Tunables.CALIBRATION_MAX_SPREAD_MS * NANOS_PER_MILLI -> CalibrationResult.TooUneven(spread)
            abs(offset) > Tunables.CALIBRATION_MAX_OFFSET_MS * NANOS_PER_MILLI -> CalibrationResult.OutOfRange(offset)
            else -> CalibrationResult.Accepted(offset, spread)
        }
    }

    /** Half the spacing of [clicks] (which must have at least two): how far a tap may be from the click it answers. */
    public fun halfBeatNanos(clicks: List<Long>): Long {
        require(clicks.size >= 2) { "need at least two clicks, got ${clicks.size}" }
        return (clicks[1] - clicks[0]) / HALF
    }

    /**
     * The offset (tap minus click) of the tap answering each counted click, by click index: the pairing [evaluate]
     * uses (nearest click within half a beat, the tap nearest the click when several pair with one, warm-up clicks
     * dropped). Its size is the number of counted clicks that have a tap.
     */
    public fun countedOffsets(
        clicks: List<Long>,
        taps: List<NoteOn>,
    ): Map<Int, Long> {
        val halfBeat = halfBeatNanos(clicks)
        return taps
            .mapNotNull { tap ->
                val index = clicks.indices.minBy { abs(tap.timestampNanos - clicks[it]) }
                val offset = tap.timestampNanos - clicks[index]
                if (abs(offset) <= halfBeat) index to offset else null
            }.filter { (index, _) -> index >= Tunables.CALIBRATION_WARMUP_CLICKS }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, offsets) -> offsets.minBy { abs(it) } }
    }

    /**
     * [notes] as the piano would have played them with no latency: every time moved earlier by [offsetNanos] (null,
     * an uncalibrated piano, leaves them alone). Timed analysis and grading take their input through this.
     */
    public fun corrected(
        notes: List<PlayedNote>,
        offsetNanos: Long?,
    ): List<PlayedNote> = if (offsetNanos == null) notes else notes.map { it.shiftedBy(-offsetNanos) }

    private fun median(values: List<Long>): Long {
        val sorted = values.sorted()
        val middle = sorted.size / HALF
        return if (sorted.size % HALF == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / HALF
    }
}
