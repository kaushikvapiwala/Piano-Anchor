package pianoanchor.platform.androidaudio

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Tone
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals

private const val RATE = 48_000

class ToneRendererTest {
    private val renderer = ToneRenderer(RATE)

    private fun tone(
        note: Int,
        startMs: Long = 0,
        lengthMs: Long = 1_000,
    ) = Tone(MidiNote(note), startMs, lengthMs)

    /** Rising zero crossings in one second: the frequency of a sine. */
    private fun hertz(samples: ShortArray) = (1 until samples.size).count { samples[it - 1] < 0 && samples[it] >= 0 }

    @Test
    fun `A4 is 440 hertz`() = assertEquals(440, hertz(renderer.render(listOf(tone(69)))))

    @Test
    fun `middle C is 262 hertz`() = assertEquals(262, hertz(renderer.render(listOf(tone(60)))))

    @Test
    fun `a triad is summed without clipping`() {
        val samples = renderer.render(listOf(tone(60), tone(64), tone(67)))
        val peak = samples.maxOf { abs(it.toInt()) }
        assertEquals(true, peak <= (0.8 * Short.MAX_VALUE).toInt() + 1)
        assertEquals(true, peak > Short.MAX_VALUE / 2)
    }

    @Test
    fun `a single tone is not scaled down`() {
        val peak = renderer.render(listOf(tone(69))).maxOf { abs(it.toInt()) }
        assertEquals(true, peak > (0.79 * Short.MAX_VALUE).toInt())
    }

    @Test
    fun `the envelope starts and ends at zero and is full between`() {
        val samples = renderer.render(listOf(tone(69, lengthMs = 500)))
        assertEquals(0, samples.first().toInt())
        assertEquals(0, samples.last().toInt())
        assertEquals(true, samples.slice(RATE / 10 until RATE / 5).maxOf { abs(it.toInt()) } > Short.MAX_VALUE / 2)
        assertEquals(true, abs(samples[RATE / 1000].toInt()) < abs(samples[RATE / 10].toInt()) + Short.MAX_VALUE)
    }

    @Test
    fun `the audio is silent until a tone starts and ends with the last tone`() {
        val samples = renderer.render(listOf(tone(69, startMs = 500, lengthMs = 250)))
        assertEquals(RATE * 750 / 1000, samples.size)
        assertEquals(true, samples.take(RATE / 2).all { it.toInt() == 0 })
    }

    @Test
    fun `no tones render no audio`() = assertEquals(0, renderer.render(emptyList()).size)
}
