package pianoanchor.platform.androidaudio

import pianoanchor.core.model.Tone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Equal temperament: A4 (MIDI note 69) is 440 Hz and an octave doubles the frequency. */
private const val A4_NOTE = 69
private const val A4_HERTZ = 440.0
private const val SEMITONES_PER_OCTAVE = 12.0

/** The fade-in and fade-out of each tone: long enough to avoid a click, short against a note. */
private const val ATTACK_MS = 10L
private const val RELEASE_MS = 60L

/** Peak level as a fraction of full scale; a chord is scaled down to it so the sum never clips. */
private const val PEAK_LEVEL = 0.8

/**
 * Synthesised tones at [sampleRate]: sines at equal temperament with a short attack and release. No samples, so no
 * assets or rights. Overlapping tones (a chord) are summed and, if the sum would pass PEAK_LEVEL, scaled down to it.
 */
internal class ToneRenderer(
    private val sampleRate: Int,
) {
    /** Mono 16-bit PCM from the call's start until the last tone has ended; silent between tones. */
    fun render(tones: List<Tone>): ShortArray {
        val mix = DoubleArray(tones.fold(0) { end, tone -> maxOf(end, frames(tone.startMs + tone.lengthMs)) })
        for (tone in tones) addTo(mix, tone)
        val peak = mix.fold(0.0) { top, sample -> maxOf(top, abs(sample)) }
        val scale = if (peak > PEAK_LEVEL) PEAK_LEVEL / peak else 1.0
        return ShortArray(mix.size) { (mix[it] * scale * Short.MAX_VALUE).roundToInt().toShort() }
    }

    private fun addTo(
        mix: DoubleArray,
        tone: Tone,
    ) {
        val start = frames(tone.startMs)
        val length = frames(tone.lengthMs)
        val attack = frames(ATTACK_MS)
        val release = frames(RELEASE_MS)
        val hertz = A4_HERTZ * 2.0.pow((tone.note.number - A4_NOTE) / SEMITONES_PER_OCTAVE)
        for (i in 0 until length) {
            // 0 at the first and the last frame, 1 between the fades.
            val envelope = minOf(1.0, i.toDouble() / attack, (length - 1 - i).toDouble() / release)
            mix[start + i] += PEAK_LEVEL * envelope * sin(2 * PI * hertz * i / sampleRate)
        }
    }

    private fun frames(ms: Long): Int = framesOf(sampleRate, ms)
}
