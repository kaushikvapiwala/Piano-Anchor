package pianoanchor.testing

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** Deterministic wall clock for tests: [now] stays put until [advance] moves it forward. */
class FakeWallClock(
    private var instant: Instant = Instant.fromEpochSeconds(0),
) : Clock {
    override fun now(): Instant = instant

    fun advance(duration: Duration) {
        require(!duration.isNegative()) { "FakeWallClock cannot go backwards (duration=$duration)" }
        instant += duration
    }
}
