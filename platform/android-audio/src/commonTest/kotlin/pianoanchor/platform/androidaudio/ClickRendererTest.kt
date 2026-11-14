package pianoanchor.platform.androidaudio

import pianoanchor.engine.Tunables
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SAMPLE_RATE = 48_000

class ClickRendererTest {
    private val renderer = ClickRenderer(SAMPLE_RATE)

    @Test
    fun clickIsClickMsLongAndDecays() {
        for (accent in listOf(true, false)) {
            val click = renderer.click(accent)
            assertEquals((SAMPLE_RATE * Tunables.METRONOME_CLICK_MS / 1_000).toInt(), click.size)
            val half = click.size / 2
            val firstPeak = click.take(half).maxOf { abs(it.toInt()) }
            val lastPeak = click.drop(half).maxOf { abs(it.toInt()) }
            assertTrue(lastPeak * 5 < firstPeak, "peak $firstPeak should decay, was $lastPeak in the second half")
        }
    }

    @Test
    fun accentUsesTheAccentFrequency() {
        assertEquals(cyclesExpected(Tunables.METRONOME_ACCENT_HZ), cycles(renderer.click(accent = true)))
        assertEquals(cyclesExpected(Tunables.METRONOME_BEAT_HZ), cycles(renderer.click(accent = false)))
    }

    private fun cyclesExpected(hertz: Int) = (hertz * Tunables.METRONOME_CLICK_MS / 1_000).toInt()

    /** Whole sine cycles in [samples]: sign changes between non-zero samples, two per cycle. */
    private fun cycles(samples: ShortArray): Int {
        val signs = samples.filter { it.toInt() != 0 }.map { it > 0 }
        return (signs.zipWithNext().count { (a, b) -> a != b } + 1) / 2
    }
}
