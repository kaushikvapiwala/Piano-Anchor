package pianoanchor.platform.androidaudio

import pianoanchor.engine.Tunables
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MS_PER_SECOND = 1_000

/** Peak level of a click as a fraction of full scale: loud, with headroom so two overlapping clicks barely clip. */
private const val PEAK_LEVEL = 0.8

/** How many time constants the click's exponential decay spans: it ends at e^-5, under 1% of its peak. */
private const val DECAY_TIME_CONSTANTS = 5.0

/**
 * Synthesised metronome clicks at [sampleRate]: sine bursts of `METRONOME_CLICK_MS` with an exponential decay, at
 * `METRONOME_ACCENT_HZ` for a bar's first beat and `METRONOME_BEAT_HZ` otherwise. No samples, so no assets or rights.
 */
internal class ClickRenderer(
    private val sampleRate: Int,
) {
    private val accent = render(Tunables.METRONOME_ACCENT_HZ)
    private val beat = render(Tunables.METRONOME_BEAT_HZ)

    /** Mono 16-bit PCM of one click; shared, so callers must not change it. */
    fun click(accent: Boolean): ShortArray = if (accent) this.accent else beat

    private fun render(hertz: Int): ShortArray {
        val frames = (sampleRate * Tunables.METRONOME_CLICK_MS / MS_PER_SECOND).toInt()
        return ShortArray(frames) { i ->
            val envelope = exp(-DECAY_TIME_CONSTANTS * i / frames)
            val wave = sin(2 * PI * hertz * i / sampleRate)
            (PEAK_LEVEL * Short.MAX_VALUE * envelope * wave).roundToInt().toShort()
        }
    }
}
