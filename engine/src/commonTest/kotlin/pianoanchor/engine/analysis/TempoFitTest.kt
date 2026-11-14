package pianoanchor.engine.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TempoFitTest {
    @Test
    fun aStraightTakeGivesItsLine() {
        assertEquals(TempoLine(100.0, 2.0), TempoFit.fit(listOf(0 to 100L, 960 to 2020L, 1920 to 3940L)))
    }

    @Test
    fun noLineThroughOneOnset() {
        assertNull(TempoFit.fit(listOf(0 to 100L, 0 to 140L)))
    }

    @Test
    fun noLineWhenOnsetsDoNotRise() {
        assertNull(TempoFit.fit(listOf(0 to 100L, 960 to 100L)))
        assertNull(TempoFit.fit(listOf(0 to 200L, 960 to 100L)))
    }

    @Test
    fun onePauseDoesNotMoveTheLine() {
        val ms = 1_000_000L
        val points = listOf(0 to 0L, 960 to 1000 * ms, 1920 to 2000 * ms, 2880 to 5000 * ms, 3840 to 6000 * ms)
        val line = TempoFit.fit(points)
        assertEquals(TempoLine(0.0, 1_000_000_000.0 / 960), line)
    }

    @Test
    fun aChordCountsItsEarliestNote() {
        val ms = 1_000_000L
        val line = TempoFit.fit(listOf(0 to 0L, 960 to 1030 * ms, 960 to 1000 * ms))
        assertEquals(TempoLine(0.0, 1000 * ms / 960.0), line)
    }

    @Test
    fun anEvenNumberOfGapsUsesTheMeanOfTheMiddleTwo() {
        // Gaps of 1, 2, 3 and 10 nanos per tick: the median is 2.5.
        val line = TempoFit.fit(listOf(0 to 0L, 1 to 1L, 2 to 3L, 3 to 6L, 4 to 16L))
        assertEquals(2.5, assertNotNull(line).nanosPerTick, 1e-9)
    }
}
