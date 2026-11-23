package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ToneTest {
    @Test
    fun `a tone keeps its note and times`() = assertEquals(500, Tone(MidiNote.MIDDLE_C, 100, 500).lengthMs)

    @Test
    fun `a tone cannot start before the call`() {
        assertFailsWith<IllegalArgumentException> { Tone(MidiNote.MIDDLE_C, -1, 500) }
    }

    @Test
    fun `a tone cannot be empty`() {
        assertFailsWith<IllegalArgumentException> { Tone(MidiNote.MIDDLE_C, 0, 0) }
    }

    @Test
    fun `a tone may carry a velocity from 1 to 127`() {
        assertEquals(null, Tone(MidiNote.MIDDLE_C, 0, 500).velocity)
        assertEquals(40, Tone(MidiNote.MIDDLE_C, 0, 500, velocity = 40).velocity)
        assertFailsWith<IllegalArgumentException> { Tone(MidiNote.MIDDLE_C, 0, 500, velocity = 0) }
        assertFailsWith<IllegalArgumentException> { Tone(MidiNote.MIDDLE_C, 0, 500, velocity = 128) }
    }
}
