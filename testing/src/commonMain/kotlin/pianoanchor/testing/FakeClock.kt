package pianoanchor.testing

import pianoanchor.core.ports.ClockPort

/** Deterministic [ClockPort] for tests: time moves only when [advanceNanos] is called. */
class FakeClock(
    private var nanos: Long = 0,
) : ClockPort {
    override fun nowNanos(): Long = nanos

    fun advanceNanos(delta: Long) {
        require(delta >= 0) { "FakeClock cannot go backwards (delta=$delta)" }
        nanos += delta
    }
}
