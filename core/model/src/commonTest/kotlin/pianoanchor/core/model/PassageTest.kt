package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PassageTest {
    private val c4 = Step(setOf(MidiNote(60)))

    @Test
    fun stepKeepsItsNotes() {
        val chord = setOf(MidiNote(60), MidiNote(64), MidiNote(67))
        assertEquals(chord, Step(chord).notes)
    }

    @Test
    fun stepRejectsNoNotes() {
        assertFailsWith<IllegalArgumentException> { Step(emptySet()) }
    }

    @Test
    fun passageKeepsIdTitleAndSteps() {
        val passage = Passage("builtin:c", "C", listOf(c4))
        assertEquals("builtin:c", passage.id)
        assertEquals("C", passage.title)
        assertEquals(listOf(c4), passage.steps)
    }

    @Test
    fun passageRejectsBlankId() {
        assertFailsWith<IllegalArgumentException> { Passage(" ", "C", listOf(c4)) }
    }

    @Test
    fun passageRejectsNoSteps() {
        assertFailsWith<IllegalArgumentException> { Passage("builtin:c", "C", emptyList()) }
    }
}
