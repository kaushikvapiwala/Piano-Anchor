package pianoanchor.platform.androidaudio

/**
 * Seam between [AndroidMetronomeAdapter] and the platform audio output, so the scheduling logic runs on the JVM.
 * Frame positions count the frames written since the sink was created or last [stop]ped.
 */
internal interface AudioSink {
    /** Frames per second of the output. */
    val sampleRate: Int

    /**
     * Writes mono 16-bit PCM, starting playback if it is stopped; suspends until all of it is queued (the caller may
     * then reuse [frames]). Cancellable while it waits.
     */
    suspend fun write(frames: ShortArray)

    /** The frame played at a monotonic time (`AudioTrack.getTimestamp`), or null before playback has a timestamp. */
    fun timestamp(): FrameTime?

    /** Stops playback and drops queued audio; the next [write] starts again from frame 0. */
    fun stop()
}

/** [framePosition] was played at [nanoTime], a monotonic time in the `ClockPort` domain. */
internal data class FrameTime(
    val framePosition: Long,
    val nanoTime: Long,
)
