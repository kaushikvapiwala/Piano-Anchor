package pianoanchor.testing

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.core.model.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals

class TestPassagesTest {
    private val c4 = MidiNote(60)
    private val e4 = MidiNote(64)
    private val g4 = MidiNote(67)

    @Test
    fun passageOfPlacesQuarterNotesOneBeatApartIn44() {
        val passage = passageOf(setOf(c4, e4), setOf(g4), id = "test:x", title = "X")
        assertEquals("test:x", passage.id)
        assertEquals("X", passage.title)
        assertEquals(TimeSignature(4, 4), passage.timeSignature)
        assertEquals(TEST_TEMPO_BPM, passage.tempoBpm)
        assertEquals(listOf(setOf(c4, e4), setOf(g4)), passage.steps.map { it.pitches })
        assertEquals(listOf(0, TICKS_PER_QUARTER), passage.steps.map { it.onsetTicks })
        assertEquals(
            setOf(TICKS_PER_QUARTER),
            passage.steps.flatMap { step -> step.notes.map { it.lengthTicks } }.toSet(),
        )
    }

    @Test
    fun passageOfHasADefaultIdAndTitle() {
        val passage = passageOf(setOf(c4))
        assertEquals("test:passage", passage.id)
        assertEquals("Test passage", passage.title)
    }
}
