package pianoanchor.platform.androidaudio

import kotlinx.coroutines.delay
import pianoanchor.core.ports.ClockPort

private const val NANOS_PER_SECOND = 1_000_000_000L
private const val MS_PER_SECOND = 1_000L

/**
 * [AudioSink] for tests: records every frame written, per run between [stop]s, and paces writes in virtual time like
 * a device that plays each chunk as it arrives. [timestamp] is whatever the test set.
 */
internal class FakeAudioSink(
    private val clock: ClockPort,
    override val sampleRate: Int = 48_000,
) : AudioSink {
    /** Frames written since the run started at [startNanos] (read from the clock at its first write). */
    class Run(
        val startNanos: Long,
    ) {
        val frames = mutableListOf<Short>()
    }

    val runs = mutableListOf<Run>()
    var stops = 0
        private set
    var nextTimestamp: FrameTime? = null

    /** The next write throws, like a track that failed. */
    var failNextWrite = false
    private var current: Run? = null

    override suspend fun write(frames: ShortArray) {
        if (failNextWrite) {
            failNextWrite = false
            throw IllegalStateException("audio track failed")
        }
        val run = current ?: Run(clock.nowNanos()).also { runs += it }
        current = run
        run.frames += frames.toList()
        delay(frames.size * MS_PER_SECOND / sampleRate)
    }

    override fun timestamp(): FrameTime? = nextTimestamp

    override fun stop() {
        stops++
        current = null
    }

    /** Clicks of [run] that [renderer] made, as (frame, accent), in order. */
    fun onsets(
        run: Run,
        renderer: ClickRenderer,
    ): List<Pair<Int, Boolean>> {
        val samples = run.frames.toShortArray()
        val accent = renderer.click(accent = true)
        val beat = renderer.click(accent = false)
        val found = mutableListOf<Pair<Int, Boolean>>()
        var i = 0
        while (i < samples.size) {
            val match = listOf(accent, beat).firstOrNull { samples.matchesAt(i, it) }
            if (match == null) {
                i++
            } else {
                found += i to (match === accent)
                i += match.size
            }
        }
        return found
    }

    /** The time a frame of [run] maps to when playback started at its first write. */
    fun timeOf(
        run: Run,
        frame: Int,
    ): Long = run.startNanos + frame * NANOS_PER_SECOND / sampleRate

    private fun ShortArray.matchesAt(
        index: Int,
        click: ShortArray,
    ) = index + click.size <= size && click.indices.all { this[index + it] == click[it] }
}
