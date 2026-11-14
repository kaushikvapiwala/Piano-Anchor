package pianoanchor.engine.content

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PassagesTest {
    @Test
    fun cdeIsThreeSingleNotesFromMiddleC() {
        assertEquals("builtin:cde", Passages.CDE.id)
        assertEquals(listOf(60, 62, 64).map { setOf(MidiNote(it)) }, Passages.CDE.steps.map { it.pitches })
    }

    @Test
    fun cdeIs44At80BpmWithOnsets0_960_1920AndLengths960_960_1920() {
        val cde = Passages.CDE
        assertEquals(TimeSignature(4, 4), cde.timeSignature)
        assertEquals(80, cde.tempoBpm)
        assertEquals(listOf(0, 960, 1920), cde.steps.map { it.onsetTicks })
        assertEquals(listOf(960, 960, 1920), cde.steps.map { it.notes.single().lengthTicks })
    }

    @Test
    fun byIdFindsABuiltInPassage() = assertEquals(Passages.CDE, Passages.byId("builtin:cde"))

    @Test
    fun byIdIsNullForAnUnknownId() = assertNull(Passages.byId("builtin:gone"))
}
