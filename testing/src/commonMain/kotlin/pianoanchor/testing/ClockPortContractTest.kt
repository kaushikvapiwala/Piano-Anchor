package pianoanchor.testing

import pianoanchor.core.ports.ClockPort
import kotlin.test.Test
import kotlin.test.assertEquals

/** Behaviour every [ClockPort] adapter must satisfy. Subclass it once per adapter. */
abstract class ClockPortContractTest {
    protected abstract fun createSubject(): ClockPort

    @Test
    fun neverGoesBackwards() {
        val clock = createSubject()
        val readings =
            List(SAMPLES) { clock.nowNanos() }
        assertEquals(readings.sorted(), readings, "clock readings must be non-decreasing")
    }

    private companion object {
        const val SAMPLES = 1_000
    }
}
