package pianoanchor.engine.content

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Step
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PassagesTest {
    @Test
    fun cdeIsThreeSingleNotesFromMiddleC() {
        assertEquals("builtin:cde", Passages.CDE.id)
        assertEquals(listOf(60, 62, 64).map { Step(setOf(MidiNote(it))) }, Passages.CDE.steps)
    }

    @Test
    fun byIdFindsABuiltInPassage() = assertEquals(Passages.CDE, Passages.byId("builtin:cde"))

    @Test
    fun byIdIsNullForAnUnknownId() = assertNull(Passages.byId("builtin:gone"))
}
