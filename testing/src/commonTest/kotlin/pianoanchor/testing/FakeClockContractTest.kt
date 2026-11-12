package pianoanchor.testing

import pianoanchor.core.ports.ClockPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FakeClockContractTest : ClockPortContractTest() {
    override fun createSubject(): ClockPort = FakeClock()

    @Test
    fun advancesOnlyWhenTold() {
        val clock = FakeClock(nanos = 5)
        clock.advanceNanos(10)
        assertEquals(15, clock.nowNanos())
    }

    @Test
    fun rejectsNegativeAdvance() {
        assertFailsWith<IllegalArgumentException> { FakeClock().advanceNanos(-1) }
    }
}
