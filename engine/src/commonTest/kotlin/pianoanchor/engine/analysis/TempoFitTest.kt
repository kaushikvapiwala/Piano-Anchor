package pianoanchor.engine.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TempoFitTest {
    @Test
    fun fitsTheLeastSquaresLine() {
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
}
