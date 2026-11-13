package pianoanchor.testing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class FakeWallClockTest {
    @Test
    fun movesOnlyWhenAdvanced() {
        val clock = FakeWallClock(Instant.fromEpochSeconds(10))
        assertEquals(Instant.fromEpochSeconds(10), clock.now())
        clock.advance(5.seconds)
        assertEquals(Instant.fromEpochSeconds(15), clock.now())
    }

    @Test
    fun startsAtTheEpochByDefault() {
        assertEquals(Instant.fromEpochSeconds(0), FakeWallClock().now())
    }

    @Test
    fun rejectsGoingBackwards() {
        assertFailsWith<IllegalArgumentException> { FakeWallClock().advance((-1).seconds) }
    }
}
