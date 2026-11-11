package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MidiNoteTest {
    @Test
    fun middleCIsNamedC4() {
        assertEquals("C4", MidiNote.MIDDLE_C.name)
    }

    @Test
    fun namesCoverPianoRangeEdgesAndSharps() {
        assertEquals("A0", MidiNote(21).name)
        assertEquals("C8", MidiNote(108).name)
        assertEquals("C#4", MidiNote(61).name)
        assertEquals("C-1", MidiNote(0).name)
    }

    @Test
    fun rangeIsTheFullMidiNoteSpace() {
        assertEquals(0..127, MidiNote.RANGE)
    }

    @Test
    fun rejectsNumbersOutsideMidiRange() {
        assertFailsWith<IllegalArgumentException> { MidiNote(-1) }
        assertFailsWith<IllegalArgumentException> { MidiNote(128) }
    }
}
