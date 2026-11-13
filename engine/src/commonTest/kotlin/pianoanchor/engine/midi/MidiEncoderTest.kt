package pianoanchor.engine.midi

import pianoanchor.core.model.ControlChange
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOff
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.SysEx
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MidiEncoderTest {
    private val t = 5L
    private val c4 = MidiNote(60)

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun noteOnOnChannelZero() {
        assertContentEquals(bytes(0x90, 0x3C, 0x64), MidiEncoder.encode(NoteOn(t, 0, c4, 100)))
    }

    @Test
    fun noteOnOnChannelFifteen() {
        assertContentEquals(bytes(0x9F, 0x3C, 0x64), MidiEncoder.encode(NoteOn(t, 15, c4, 100)))
    }

    @Test
    fun noteOffKeepsReleaseVelocityOnBothChannelEnds() {
        assertContentEquals(bytes(0x80, 0x3C, 0x00), MidiEncoder.encode(NoteOff(t, 0, c4, 0)))
        assertContentEquals(bytes(0x8F, 0x3C, 0x40), MidiEncoder.encode(NoteOff(t, 15, c4, 64)))
    }

    @Test
    fun controlChangeOnBothChannelEnds() {
        assertContentEquals(bytes(0xB0, 0x40, 0x7F), MidiEncoder.encode(ControlChange(t, 0, 64, 127)))
        assertContentEquals(bytes(0xBF, 0x07, 0x00), MidiEncoder.encode(ControlChange(t, 15, 7, 0)))
    }

    @Test
    fun sysExIsFramedWithStartAndEndBytes() {
        assertContentEquals(
            bytes(0xF0, 0x7E, 0x7F, 0x06, 0x01, 0xF7),
            MidiEncoder.encode(SysEx(t, listOf(0x7E, 0x7F, 0x06, 0x01))),
        )
    }

    @Test
    fun emptySysExIsJustTheFrame() {
        assertContentEquals(bytes(0xF0, 0xF7), MidiEncoder.encode(SysEx(t, emptyList())))
    }

    @Test
    fun parsingTheEncodedBytesGivesBackEveryEventType() {
        val events: List<MidiEvent> =
            listOf(
                NoteOn(t, 0, MidiNote(0), 1),
                NoteOn(t, 15, MidiNote(127), 127),
                NoteOff(t, 3, c4, 0),
                NoteOff(t, 9, c4, 127),
                ControlChange(t, 0, 64, 127),
                ControlChange(t, 15, 0, 0),
                SysEx(t, listOf(0x41, 0x10, 0x00)),
                SysEx(t, emptyList()),
            )
        events.forEach { event ->
            assertEquals(listOf(event), MidiParser().feed(MidiEncoder.encode(event), t))
        }
    }
}
