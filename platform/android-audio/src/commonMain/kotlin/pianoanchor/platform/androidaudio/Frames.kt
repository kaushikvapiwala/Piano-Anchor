package pianoanchor.platform.androidaudio

private const val MS_PER_SECOND = 1_000L

/** The number of audio frames [ms] milliseconds span at [sampleRate] Hz, truncated. */
internal fun framesOf(
    sampleRate: Int,
    ms: Long,
): Int = (ms * sampleRate / MS_PER_SECOND).toInt()
