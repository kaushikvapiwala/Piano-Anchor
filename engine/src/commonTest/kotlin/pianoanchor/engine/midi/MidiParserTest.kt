package pianoanchor.engine.midi

import pianoanchor.core.model.ControlChange
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOff
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.SysEx
import pianoanchor.engine.Tunables
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MidiParserTest {
    private val parser = MidiParser()
    private val t = 7L
    private val c4 = MidiNote(60)
    private val d4 = MidiNote(62)

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun parse(vararg v: Int): List<MidiEvent> = parser.feed(bytes(*v), t)

    @Test
    fun noteOnEmitsChannelNoteAndVelocity() {
        assertEquals(listOf(NoteOn(t, 0, c4, 100)), parse(0x90, 0x3C, 0x64))
    }

    @Test
    fun channelComesFromLowNibble() {
        assertEquals(listOf(NoteOn(t, 15, c4, 100)), parse(0x9F, 0x3C, 0x64))
    }

    @Test
    fun noteOnWithVelocityZeroIsNoteOff() {
        assertEquals(listOf(NoteOff(t, 0, c4, 64)), parse(0x90, 0x3C, 0x00))
    }

    @Test
    fun noteOffKeepsReleaseVelocity() {
        assertEquals(listOf(NoteOff(t, 3, MidiNote(64), 30)), parse(0x83, 0x40, 0x1E))
    }

    @Test
    fun controlChangeEmitsSustainPedal() {
        assertEquals(listOf(ControlChange(t, 0, 64, 127)), parse(0xB0, 0x40, 0x7F))
    }

    @Test
    fun runningStatusRepeatsLastStatus() {
        assertEquals(listOf(NoteOn(t, 0, c4, 100), NoteOn(t, 0, d4, 100)), parse(0x90, 0x3C, 0x64, 0x3E, 0x64))
    }

    @Test
    fun runningStatusTurnsVelocityZeroIntoNoteOff() {
        assertEquals(listOf(NoteOn(t, 0, c4, 100), NoteOff(t, 0, c4, 64)), parse(0x90, 0x3C, 0x64, 0x3C, 0x00))
    }

    @Test
    fun messageSplitAcrossFeedsUsesLastFeedTimestamp() {
        assertEquals(emptyList(), parser.feed(bytes(0x90, 0x3C), 1))
        assertEquals(listOf(NoteOn(2, 0, c4, 100)), parser.feed(bytes(0x64), 2))
    }

    @Test
    fun realTimeBetweenDataBytesIsIgnored() {
        assertEquals(listOf(NoteOn(t, 0, c4, 100)), parse(0x90, 0xF8, 0x3C, 0xFE, 0x64))
    }

    @Test
    fun realTimeAloneEmitsNothing() {
        assertEquals(emptyList(), parse(0xFE))
    }

    @Test
    fun realTimeKeepsRunningStatus() {
        assertEquals(listOf(NoteOn(t, 0, c4, 100), NoteOn(t, 0, d4, 100)), parse(0x90, 0x3C, 0x64, 0xF8, 0x3E, 0x64))
    }

    @Test
    fun sysExEmitsPayloadWithoutFraming() {
        assertEquals(listOf(SysEx(t, listOf(0x41, 0x10, 0x42))), parse(0xF0, 0x41, 0x10, 0x42, 0xF7))
    }

    @Test
    fun emptySysExEmitsEmptyPayload() {
        assertEquals(listOf(SysEx(t, emptyList())), parse(0xF0, 0xF7))
    }

    @Test
    fun sysExSplitAcrossFeeds() {
        assertEquals(emptyList(), parser.feed(bytes(0xF0, 0x41), 1))
        assertEquals(listOf(SysEx(2, listOf(0x41, 0x10))), parser.feed(bytes(0x10, 0xF7), 2))
    }

    @Test
    fun realTimeInsideSysExIsIgnored() {
        assertEquals(listOf(SysEx(t, listOf(0x41, 0x10))), parse(0xF0, 0x41, 0xF8, 0x10, 0xF7))
    }

    @Test
    fun statusByteInsideSysExDiscardsIt() {
        assertEquals(listOf(NoteOn(t, 0, c4, 100)), parse(0xF0, 0x41, 0x90, 0x3C, 0x64))
    }

    @Test
    fun sysExClearsRunningStatus() {
        assertEquals(
            listOf(NoteOn(t, 0, c4, 100), SysEx(t, emptyList())),
            parse(0x90, 0x3C, 0x64, 0xF0, 0xF7, 0x3E, 0x64),
        )
    }

    @Test
    fun systemCommonClearsRunningStatus() {
        assertEquals(listOf(NoteOn(t, 0, c4, 100)), parse(0x90, 0x3C, 0x64, 0xF6, 0x3E, 0x64))
    }

    @Test
    fun songPositionConsumesTwoDataBytes() {
        assertEquals(listOf(NoteOn(t, 0, c4, 100)), parse(0xF2, 0x01, 0x02, 0x90, 0x3C, 0x64))
    }

    @Test
    fun songSelectConsumesOneDataByte() {
        assertEquals(listOf(NoteOn(t, 0, c4, 100)), parse(0xF3, 0x05, 0x90, 0x3C, 0x64))
    }

    @Test
    fun timeCodeConsumesOneDataByteAndDoesNotRepeat() {
        assertEquals(listOf(NoteOn(t, 0, c4, 100)), parse(0xF1, 0x05, 0x06, 0x90, 0x3C, 0x64))
    }

    @Test
    fun tuneRequestAndUndefinedCommonAreDropped() {
        assertEquals(listOf(NoteOn(t, 0, c4, 100)), parse(0xF4, 0xF5, 0x3C, 0x64, 0x90, 0x3C, 0x64))
    }

    @Test
    fun strayEndOfExclusiveIsIgnored() {
        assertEquals(listOf(NoteOn(t, 0, c4, 100)), parse(0xF7, 0x90, 0x3C, 0x64))
    }

    @Test
    fun strayEndOfExclusiveCancelsPartialMessage() {
        assertEquals(emptyList(), parse(0x90, 0x3C, 0xF7, 0x64))
    }

    @Test
    fun strayEndOfExclusiveClearsRunningStatus() {
        assertEquals(listOf(NoteOn(t, 0, c4, 100)), parse(0x90, 0x3C, 0x64, 0xF7, 0x3E, 0x64))
    }

    @Test
    fun systemResetDropsPartialMessageAndRunningStatus() {
        assertEquals(emptyList(), parse(0x90, 0x3C, 0xFF, 0x64, 0x3E, 0x64))
    }

    @Test
    fun systemResetDropsPartialSysEx() {
        assertEquals(emptyList(), parse(0xF0, 0x41, 0xFF, 0x10, 0xF7))
    }

    @Test
    fun dataBytesBeforeAnyStatusAreDropped() {
        assertEquals(listOf(NoteOn(t, 0, c4, 100)), parse(0x3C, 0x64, 0x90, 0x3C, 0x64))
    }

    @Test
    fun programChangeIsConsumedAndDropped() {
        assertEquals(listOf(NoteOn(t, 0, c4, 100)), parse(0xC0, 0x05, 0x06, 0x90, 0x3C, 0x64))
    }

    @Test
    fun pitchBendConsumesTwoDataBytes() {
        assertEquals(listOf(NoteOn(t, 0, c4, 100)), parse(0xE0, 0x00, 0x40, 0x90, 0x3C, 0x64))
    }

    @Test
    fun polyAndChannelPressureAreDropped() {
        assertEquals(listOf(NoteOn(t, 0, c4, 100)), parse(0xA0, 0x3C, 0x10, 0xD0, 0x20, 0x90, 0x3C, 0x64))
    }

    @Test
    fun oversizedSysExIsDiscarded() {
        val body = IntArray(Tunables.SYSEX_MAX_BYTES + 5) { 0x01 }
        val input = intArrayOf(0xF0) + body + intArrayOf(0xF7, 0x90, 0x3C, 0x64)
        assertEquals(listOf(NoteOn(t, 0, c4, 100)), parse(*input))
    }

    @Test
    fun sysExAtSizeCapIsKept() {
        val body = IntArray(Tunables.SYSEX_MAX_BYTES) { 0x01 }
        val input = intArrayOf(0xF0) + body + intArrayOf(0xF7)
        assertEquals(listOf(SysEx(t, body.toList())), parse(*input))
    }

    @Test
    fun resetDropsPartialMessageAndRunningStatus() {
        assertEquals(emptyList(), parse(0x90, 0x3C))
        parser.reset()
        assertEquals(emptyList(), parse(0x64, 0x3E, 0x64))
    }

    @Test
    fun resetDropsPartialSysEx() {
        parse(0xF0, 0x41)
        parser.reset()
        assertEquals(emptyList(), parse(0x10, 0xF7))
    }

    @Test
    fun feedParsesOnlyTheGivenRange() {
        assertEquals(
            listOf(NoteOn(t, 0, c4, 100)),
            parser.feed(bytes(0xFF, 0x90, 0x3C, 0x64, 0xFF), t, offset = 1, count = 3),
        )
    }

    @Test
    fun feedRejectsRangeOutsideArray() {
        val data = bytes(0x90, 0x3C, 0x64)
        assertFailsWith<IllegalArgumentException> { parser.feed(data, t, offset = -1, count = 1) }
        assertFailsWith<IllegalArgumentException> { parser.feed(data, t, offset = 0, count = -1) }
        assertFailsWith<IllegalArgumentException> { parser.feed(data, t, offset = 2, count = 2) }
        assertFailsWith<IllegalArgumentException> { parser.feed(data, t, offset = 4) }
    }
}
