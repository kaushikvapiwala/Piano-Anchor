package pianoanchor.engine.calibration

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.PlayedNote
import pianoanchor.engine.Tunables
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LatencyCalibrationTest {
    private val ms = 1_000_000L
    private val first = 10_000_000_000L
    private val clicks = LatencyCalibration.clickTimes(first)
    private val warmup = Tunables.CALIBRATION_WARMUP_CLICKS

    private fun tap(at: Long) = NoteOn(at, channel = 0, note = MidiNote(60), velocity = 64)

    /** One tap per click, each [lateMs] after it. */
    private fun steady(lateMs: Long) = clicks.map { tap(it + lateMs * ms) }

    @Test
    fun clickTimesAtTheTempo() {
        val beat = 60_000_000_000L / Tunables.CALIBRATION_BPM
        assertEquals(Tunables.CALIBRATION_CLICKS, clicks.size)
        assertEquals(first, clicks.first())
        assertEquals(List(Tunables.CALIBRATION_CLICKS) { first + it * beat }, clicks)
    }

    @Test
    fun steadyTaps38MsLateGiveAccepted38Ms() {
        assertEquals(CalibrationResult.Accepted(38 * ms, 0), LatencyCalibration.evaluate(clicks, steady(38)))
    }

    @Test
    fun warmUpTapsAreIgnored() {
        val taps = clicks.mapIndexed { i, click -> tap(click + (if (i < warmup) 200 else 38) * ms) }
        assertEquals(CalibrationResult.Accepted(38 * ms, 0), LatencyCalibration.evaluate(clicks, taps))
    }

    @Test
    fun tapsFarFromAnyClickAreIgnored() {
        val stray = listOf(tap(first - 5_000 * ms), tap(clicks.last() + 20_000 * ms))
        assertEquals(
            CalibrationResult.Accepted(38 * ms, 0),
            LatencyCalibration.evaluate(clicks, stray + steady(38)),
        )
    }

    @Test
    fun oneTapPerClick() {
        val counted = clicks.drop(warmup)
        // Seven counted clicks answered twice, the rest not at all: seven taps, not fourteen.
        val taps = counted.take(7).flatMap { listOf(tap(it + 150 * ms), tap(it + 38 * ms)) }
        assertEquals(CalibrationResult.TooFewTaps(7), LatencyCalibration.evaluate(clicks, taps))
        // With enough clicks answered, the tap nearest the click counts.
        val enough = counted.flatMap { listOf(tap(it + 150 * ms), tap(it + 38 * ms)) }
        assertEquals(CalibrationResult.Accepted(38 * ms, 0), LatencyCalibration.evaluate(clicks, enough))
    }

    @Test
    fun unevenTapsGiveTooUneven() {
        val taps = clicks.mapIndexed { i, click -> tap(click + (if (i % 2 == 0) 0 else 120) * ms) }
        assertEquals(CalibrationResult.TooUneven(60 * ms), LatencyCalibration.evaluate(clicks, taps))
    }

    @Test
    fun aSpreadOf40MsIsAcceptedAndOf50MsIsTooUneven() {
        fun alternating(gap: Int) = clicks.mapIndexed { i, click -> tap(click + (if (i % 2 == 0) 0 else gap) * ms) }
        assertEquals(
            CalibrationResult.Accepted(40 * ms, 40 * ms),
            LatencyCalibration.evaluate(clicks, alternating(80)),
        )
        assertEquals(CalibrationResult.TooUneven(50 * ms), LatencyCalibration.evaluate(clicks, alternating(100)))
    }

    @Test
    fun tooFewTapsGiveTooFewTaps() {
        val taps = clicks.drop(warmup).take(Tunables.CALIBRATION_MIN_TAPS - 1).map { tap(it + 38 * ms) }
        assertEquals(CalibrationResult.TooFewTaps(7), LatencyCalibration.evaluate(clicks, taps))
        assertEquals(CalibrationResult.TooFewTaps(0), LatencyCalibration.evaluate(clicks, emptyList()))
    }

    @Test
    fun aLagOf380MsIsMeasuredAsPositive() {
        assertEquals(CalibrationResult.Accepted(380 * ms, 0), LatencyCalibration.evaluate(clicks, steady(380)))
    }

    @Test
    fun aLagOf450MsIsRejectedAsOutOfRange() {
        assertEquals(CalibrationResult.OutOfRange(450 * ms), LatencyCalibration.evaluate(clicks, steady(450)))
    }

    @Test
    fun aLagJustUnderTheCapIsAccepted() {
        val lag = Tunables.CALIBRATION_MAX_OFFSET_MS - 5
        assertEquals(CalibrationResult.Accepted(lag * ms, 0), LatencyCalibration.evaluate(clicks, steady(lag)))
    }

    @Test
    fun negativeOffsetAccepted() {
        assertEquals(CalibrationResult.Accepted(-25 * ms, 0), LatencyCalibration.evaluate(clicks, steady(-25)))
    }

    @Test
    fun medianOfAnEvenCountAveragesTheMiddlePair() {
        val taps = clicks.mapIndexed { i, click -> tap(click + (30 + i % 2 * 10) * ms) }
        assertEquals(CalibrationResult.Accepted(35 * ms, 5 * ms), LatencyCalibration.evaluate(clicks, taps))
    }

    @Test
    fun medianOfAnOddCountIsTheMiddleValue() {
        // Nine counted taps 30..38 ms late: median 34 ms, deviations 4 3 2 1 0 1 2 3 4, median deviation 2 ms.
        val taps = clicks.drop(warmup + 3).mapIndexed { i, click -> tap(click + (30 + i) * ms) }
        assertEquals(CalibrationResult.Accepted(34 * ms, 2 * ms), LatencyCalibration.evaluate(clicks, taps))
    }

    @Test
    fun countedOffsetsHaveOneEntryPerAnsweredCountedClick() {
        val counted = clicks.drop(warmup)
        val taps =
            listOf(tap(clicks[0] + 10 * ms)) + // warm-up
                listOf(tap(counted[0] + 150 * ms), tap(counted[0] + 38 * ms)) + // double tap on one click
                tap(counted[1] + 20 * ms) +
                tap(clicks.last() + 20_000 * ms) // after the last click
        assertEquals(mapOf(warmup to 38 * ms, warmup + 1 to 20 * ms), LatencyCalibration.countedOffsets(clicks, taps))
        assertEquals(clicks[1] - clicks[0], 2 * LatencyCalibration.halfBeatNanos(clicks))
    }

    @Test
    fun correctedMovesNotesEarlierByThePositiveOffsetAndLeavesUncalibratedOnesAlone() {
        val note = PlayedNote(MidiNote(60), 64, downNanos = 500 * ms, upNanos = 600 * ms, soundingEndNanos = 600 * ms)
        assertEquals(listOf(note), LatencyCalibration.corrected(listOf(note), null))
        assertEquals(listOf(note.shiftedBy(-40 * ms)), LatencyCalibration.corrected(listOf(note), 40 * ms))
    }

    @Test
    fun fewerThanTwoClicksAreRejected() {
        assertFailsWith<IllegalArgumentException> { LatencyCalibration.evaluate(listOf(first), emptyList()) }
        assertFailsWith<IllegalArgumentException> { LatencyCalibration.halfBeatNanos(listOf(first)) }
    }
}
