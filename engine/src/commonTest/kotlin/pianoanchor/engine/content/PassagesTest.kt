package pianoanchor.engine.content

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Step
import kotlin.test.Test
import kotlin.test.assertEquals

class PassagesTest {
    @Test
    fun cdeIsThreeSingleNotesFromMiddleC() {
        assertEquals("builtin:cde", Passages.CDE.id)
        assertEquals(listOf(60, 62, 64).map { Step(setOf(MidiNote(it))) }, Passages.CDE.steps)
    }
}
