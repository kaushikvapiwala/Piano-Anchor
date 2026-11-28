package pianoanchor.engine.guidance

import pianoanchor.core.model.BarRange
import pianoanchor.core.model.ChordMark
import pianoanchor.core.model.ChordQuality
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.core.model.TimeSignature
import pianoanchor.testing.passageOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PassageSliceTest {
    // 12 quarter notes in 4/4 = 3 bars; the pitch of each step is 60 + its index.
    private val passage = passageOf(*Array(12) { setOf(MidiNote(60 + it)) }, id = "test:p")

    @Test
    fun `slice keeps only the steps inside the range, rebased to tick 0`() {
        val slice = passage.slice(BarRange(2, 3))
        assertEquals((64..71).map { setOf(MidiNote(it)) }, slice.steps.map { it.pitches })
        assertEquals(List(8) { it * TICKS_PER_QUARTER }, slice.steps.map { it.onsetTicks })
    }

    @Test
    fun `slice of the first bar starts where it did`() {
        val slice = passage.slice(BarRange(1, 1))
        assertEquals(listOf(0, 960, 1920, 2880), slice.steps.map { it.onsetTicks })
    }

    @Test
    fun `slice keeps the time signature, tempo, rights and skills and suffixes the id`() {
        val slice = passage.slice(BarRange(2, 3))
        assertEquals(TimeSignature(4, 4), slice.timeSignature)
        assertEquals(passage.tempoBpm, slice.tempoBpm)
        assertEquals(passage.rights, slice.rights)
        assertEquals(passage.skills, slice.skills)
        assertEquals("test:p#bars2-3", slice.id)
    }

    @Test
    fun `slice drops the chord line`() {
        val chorded = passage.copy(chords = listOf(ChordMark(0, 0, ChordQuality.MAJOR)))
        assertEquals(emptyList(), chorded.slice(BarRange(2, 3)).chords)
    }

    @Test
    fun `slice outside the passage is rejected`() {
        assertFailsWith<IllegalArgumentException> { passage.slice(BarRange(4, 5)) }
    }
}
