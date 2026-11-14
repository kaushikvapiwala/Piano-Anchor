package pianoanchor.testing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pianoanchor.core.ports.ClockPort
import pianoanchor.core.ports.MetronomePort

/** One click a metronome played: its time in the [ClockPort] domain and whether it was a bar's accent. */
data class MetronomeClick(
    val nanos: Long,
    val accent: Boolean,
)

/**
 * In-memory [MetronomePort] that records patterns. [running] is the current pattern or null; [clicksUntil] replays what
 * a real metronome would have played, reading [clock] when each pattern starts or stops.
 */
class FakeMetronome(
    private val clock: ClockPort = FakeClock(),
) : MetronomePort {
    /** A pattern as passed to [start]. */
    data class Pattern(
        val firstBeatNanos: Long,
        val beatIntervalNanos: Long,
        val beatsPerBar: Int,
    )

    private class Segment(
        val pattern: Pattern,
        val fromNanos: Long,
        var untilNanos: Long? = null,
    )

    private val current = MutableStateFlow<Pattern?>(null)
    private val segments = mutableListOf<Segment>()

    /** The pattern clicking now, or null when stopped. */
    val running: StateFlow<Pattern?> = current.asStateFlow()

    /** When set, [start] suspends until it completes, like a real adapter queuing audio. */
    var startGate: CompletableDeferred<Unit>? = null

    override suspend fun start(
        firstBeatNanos: Long,
        beatIntervalNanos: Long,
        beatsPerBar: Int,
    ) {
        require(beatIntervalNanos > 0) { "beatIntervalNanos must be positive, was $beatIntervalNanos" }
        require(beatsPerBar >= 1) { "beatsPerBar must be at least 1, was $beatsPerBar" }
        startGate?.await()
        val now = clock.nowNanos()
        endSegment(now)
        val pattern = Pattern(firstBeatNanos, beatIntervalNanos, beatsPerBar)
        segments += Segment(pattern, now)
        current.value = pattern
    }

    override suspend fun stop() {
        endSegment(clock.nowNanos())
        current.value = null
    }

    /**
     * The clicks played up to and including [untilNanos], oldest first: each pattern's beats from when it started
     * (earlier beats skipped) until it was replaced or stopped.
     */
    fun clicksUntil(untilNanos: Long): List<MetronomeClick> =
        segments.flatMap { segment ->
            val pattern = segment.pattern
            val end = minOf(untilNanos + 1, segment.untilNanos ?: Long.MAX_VALUE)
            val skipped = segment.fromNanos - pattern.firstBeatNanos
            val first = if (skipped <= 0) 0 else (skipped + pattern.beatIntervalNanos - 1) / pattern.beatIntervalNanos
            generateSequence(first) { it + 1 }
                .map { it to pattern.firstBeatNanos + it * pattern.beatIntervalNanos }
                .takeWhile { (_, nanos) -> nanos < end }
                .map { (index, nanos) -> MetronomeClick(nanos, index % pattern.beatsPerBar == 0L) }
                .toList()
        }

    private fun endSegment(nanos: Long) {
        segments.lastOrNull()?.takeIf { it.untilNanos == null }?.untilNanos = nanos
    }
}
