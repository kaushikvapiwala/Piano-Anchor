package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MidiEventTest {
    private val c4 = MidiNote.MIDDLE_C

    @Test
    fun noteOnRejectsVelocityZero() {
        assertFailsWith<IllegalArgumentException> { NoteOn(0, 0, c4, 0) }
    }

    @Test
    fun noteOnRejectsVelocity128() {
        assertFailsWith<IllegalArgumentException> { NoteOn(0, 0, c4, 128) }
    }

    @Test
    fun channelRejects16() {
        assertFailsWith<IllegalArgumentException> { NoteOn(0, 16, c4, 1) }
        assertFailsWith<IllegalArgumentException> { NoteOff(0, 16, c4, 0) }
        assertFailsWith<IllegalArgumentException> { ControlChange(0, 16, 0, 0) }
    }

    @Test
    fun channelRejectsNegative() {
        assertFailsWith<IllegalArgumentException> { NoteOn(0, -1, c4, 1) }
        assertFailsWith<IllegalArgumentException> { NoteOff(0, -1, c4, 0) }
        assertFailsWith<IllegalArgumentException> { ControlChange(0, -1, 0, 0) }
    }

    @Test
    fun noteOffAcceptsVelocityZero() {
        assertEquals(0, NoteOff(0, 0, c4, 0).velocity)
    }

    @Test
    fun noteOffRejectsVelocity128AndNegative() {
        assertFailsWith<IllegalArgumentException> { NoteOff(0, 0, c4, 128) }
        assertFailsWith<IllegalArgumentException> { NoteOff(0, 0, c4, -1) }
    }

    @Test
    fun controlChangeRejectsController128() {
        assertFailsWith<IllegalArgumentException> { ControlChange(0, 0, 128, 0) }
        assertFailsWith<IllegalArgumentException> { ControlChange(0, 0, -1, 0) }
    }

    @Test
    fun controlChangeRejectsValue128() {
        assertFailsWith<IllegalArgumentException> { ControlChange(0, 0, 0, 128) }
        assertFailsWith<IllegalArgumentException> { ControlChange(0, 0, 0, -1) }
    }

    @Test
    fun sysExRejectsByteAbove127() {
        assertFailsWith<IllegalArgumentException> { SysEx(0, listOf(1, 128)) }
    }

    @Test
    fun sysExRejectsNegativeByte() {
        assertFailsWith<IllegalArgumentException> { SysEx(0, listOf(-1)) }
    }

    @Test
    fun sysExAcceptsEmptyPayload() {
        assertEquals(emptyList(), SysEx(0, emptyList()).payload)
    }

    @Test
    fun boundaryValuesAreAccepted() {
        assertEquals(15, NoteOn(0, 15, c4, 127).channel)
        assertEquals(1, NoteOn(0, 0, c4, 1).velocity)
        assertEquals(127, NoteOff(0, 0, c4, 127).velocity)
        assertEquals(127, ControlChange(0, 0, 127, 127).value)
        assertEquals(listOf(0, 127), SysEx(0, listOf(0, 127)).payload)
        assertEquals(1..127, NoteOn.VELOCITY_RANGE)
    }
}
