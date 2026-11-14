package pianoanchor.platform.androidaudio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pianoanchor.core.ports.ClockPort
import pianoanchor.core.ports.MetronomePort
import pianoanchor.engine.Tunables

private const val MS_PER_SECOND = 1_000
private const val NANOS_PER_SECOND = 1_000_000_000L

/**
 * [MetronomePort] that streams PCM to an [AudioSink] in `METRONOME_CHUNK_MS` chunks, mostly silence, from a coroutine
 * in [scope] while a pattern runs. Each click goes at the frame the latest [AudioSink.timestamp] maps its beat time to;
 * before the first timestamp, playback is assumed to start when the first chunk is written, read from [clock] (the
 * clock domain of the timestamps). Create it on Android with `AndroidMetronomeAdapter(context, clock)`.
 * Contract: `MetronomePortContractTest`; details in `docs/protocols/android-audio.md`.
 */
public class AndroidMetronomeAdapter internal constructor(
    private val sink: AudioSink,
    private val clock: ClockPort,
    private val scope: CoroutineScope,
) : MetronomePort {
    private class Pattern(
        val firstBeatNanos: Long,
        val beatIntervalNanos: Long,
        val beatsPerBar: Int,
    )

    private class ScheduledClick(
        val startFrame: Long,
        val samples: ShortArray,
    ) {
        val endFrame: Long get() = startFrame + samples.size
    }

    /** The running writer coroutine and the pattern it plays. */
    private class Writer(
        val pattern: MutableStateFlow<Pattern>,
        val job: Job,
    )

    private val renderer = ClickRenderer(sink.sampleRate)
    private val chunkFrames = (sink.sampleRate * Tunables.METRONOME_CHUNK_MS / MS_PER_SECOND).toInt()
    private val lock = Mutex()
    private var writer: Writer? = null // guarded by lock

    override suspend fun start(
        firstBeatNanos: Long,
        beatIntervalNanos: Long,
        beatsPerBar: Int,
    ) {
        require(beatIntervalNanos > 0) { "beatIntervalNanos must be positive, was $beatIntervalNanos" }
        require(beatsPerBar >= 1) { "beatsPerBar must be at least 1, was $beatsPerBar" }
        val next = Pattern(firstBeatNanos, beatIntervalNanos, beatsPerBar)
        lock.withLock {
            val running = writer
            if (running != null && running.job.isActive) {
                running.pattern.value = next
                return
            }
            // A writer that failed (its exception went to the scope's handler) left the sink mid-stream: start afresh.
            if (running != null) sink.stop()
            val pattern = MutableStateFlow(next)
            writer = Writer(pattern, scope.launch { stream(pattern) })
        }
    }

    override suspend fun stop() {
        lock.withLock {
            val running = writer ?: return
            running.job.cancelAndJoin()
            writer = null
            sink.stop()
        }
    }

    /** Writes chunks until cancelled, placing the clicks of the latest [patterns] value. */
    private suspend fun stream(patterns: StateFlow<Pattern>) {
        var written = 0L
        var latest: FrameTime? = null
        var scheduledFor: Pattern? = null
        var nextBeat = 0L
        val clicks = ArrayDeque<ScheduledClick>()
        val chunk = ShortArray(chunkFrames)
        while (true) {
            val time = sink.timestamp() ?: latest ?: FrameTime(0, clock.nowNanos())
            latest = time
            val end = written + chunkFrames
            val current = patterns.value
            if (current !== scheduledFor) {
                scheduledFor = current
                nextBeat = firstBeatFrom(current, timeOf(written, time))
            }
            while (true) {
                val frame = frameOf(current.firstBeatNanos + nextBeat * current.beatIntervalNanos, time)
                if (frame >= end) break
                val accent = nextBeat % current.beatsPerBar == 0L
                if (frame >= written) clicks += ScheduledClick(frame, renderer.click(accent))
                nextBeat++
            }
            mix(clicks, written, chunk)
            sink.write(chunk)
            clicks.removeAll { it.endFrame <= end }
            written = end
        }
    }

    /** Fills [chunk], which starts at frame [from], with silence and the parts of [clicks] that fall in it. */
    private fun mix(
        clicks: List<ScheduledClick>,
        from: Long,
        chunk: ShortArray,
    ) {
        chunk.fill(0)
        for (click in clicks) {
            for (frame in maxOf(click.startFrame, from) until minOf(click.endFrame, from + chunk.size)) {
                val i = (frame - from).toInt()
                val sum = chunk[i] + click.samples[(frame - click.startFrame).toInt()]
                chunk[i] = sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
    }

    /** Index of the first beat of [pattern] at or after [nanos]. */
    private fun firstBeatFrom(
        pattern: Pattern,
        nanos: Long,
    ): Long {
        val sinceFirst = nanos - pattern.firstBeatNanos
        return if (sinceFirst <= 0) 0 else (sinceFirst + pattern.beatIntervalNanos - 1) / pattern.beatIntervalNanos
    }

    private fun timeOf(
        frame: Long,
        time: FrameTime,
    ): Long = time.nanoTime + (frame - time.framePosition) * NANOS_PER_SECOND / sink.sampleRate

    /** The frame [nanos] maps to through [time], rounded to the nearest frame. */
    private fun frameOf(
        nanos: Long,
        time: FrameTime,
    ): Long =
        time.framePosition +
            Math.floorDiv((nanos - time.nanoTime) * sink.sampleRate + NANOS_PER_SECOND / 2, NANOS_PER_SECOND)
}
