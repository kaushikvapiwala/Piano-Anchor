package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlayedNoteTest {
    private val c4 = MidiNote(60)

    @Test
    fun keepsItsTimes() {
        val note = PlayedNote(c4, velocity = 64, downNanos = 10, upNanos = 20, soundingEndNanos = 30)
        assertEquals(10, note.downNanos)
        assertEquals(20, note.upNanos)
        assertEquals(30, note.soundingEndNanos)
    }

    @Test
    fun allowsAZeroLengthNote() {
        val note = PlayedNote(c4, velocity = 1, downNanos = 10, upNanos = 10, soundingEndNanos = 10)
        assertEquals(10, note.soundingEndNanos)
    }

    @Test
    fun orderIsByKeyDownThenPitch() {
        val e4 = MidiNote(64)
        val late = PlayedNote(c4, 64, 20, 30, 30)
        val highFirst = PlayedNote(e4, 64, 10, 30, 30)
        val lowFirst = PlayedNote(c4, 64, 10, 30, 30)
        assertEquals(listOf(lowFirst, highFirst, late), listOf(late, highFirst, lowFirst).sortedWith(PLAYED_NOTE_ORDER))
    }

    @Test
    fun rejectsVelocityOutsideOneTo127() {
        assertFailsWith<IllegalArgumentException> { PlayedNote(c4, 0, 0, 1, 1) }
        assertFailsWith<IllegalArgumentException> { PlayedNote(c4, 128, 0, 1, 1) }
    }

    @Test
    fun rejectsKeyUpBeforeKeyDown() {
        assertFailsWith<IllegalArgumentException> { PlayedNote(c4, 64, 10, 9, 10) }
    }

    @Test
    fun rejectsSoundingEndBeforeKeyUp() {
        assertFailsWith<IllegalArgumentException> { PlayedNote(c4, 64, 10, 20, 19) }
    }
}
