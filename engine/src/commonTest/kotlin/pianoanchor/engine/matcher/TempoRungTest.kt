package pianoanchor.engine.matcher

import pianoanchor.core.model.MAX_TEMPO_BPM
import pianoanchor.core.model.MIN_TEMPO_BPM
import pianoanchor.testing.passageOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TempoRungTest {
    private val passage = passageOf(setOf(pianoanchor.core.model.MidiNote(60)))

    private fun at(bpm: Int) = passage.copy(tempoBpm = bpm)

    @Test
    fun bpmRoundsAndClamps() {
        assertEquals(
            listOf(36, 42, 51, 60),
            listOf(TempoRung.P60, TempoRung.P70, TempoRung.P85, TempoRung.P100).map {
                it.bpm(passage)
            },
        )
        assertEquals(26, TempoRung.P85.bpm(at(30))) // 25.5 rounds up
        assertEquals(25, TempoRung.P85.bpm(at(29))) // 24.65 rounds to 25
        assertEquals(MIN_TEMPO_BPM, TempoRung.P60.bpm(at(MIN_TEMPO_BPM)))
        assertEquals(MAX_TEMPO_BPM, TempoRung.P100.bpm(at(MAX_TEMPO_BPM)))
    }

    @Test
    fun waitHasNoBpm() {
        assertNull(TempoRung.WAIT.bpm(passage))
        assertNull(TempoRung.WAIT.percent)
    }

    @Test
    fun beatNanosIsAQuarterNote() {
        assertEquals(500_000_000L, beatNanos(120))
        assertFailsWith<IllegalArgumentException> { beatNanos(MIN_TEMPO_BPM - 1) }
        assertFailsWith<IllegalArgumentException> { beatNanos(MAX_TEMPO_BPM + 1) }
    }
}
