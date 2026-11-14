package pianoanchor.engine.analysis

import pianoanchor.core.model.ControlChange
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOff
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.PlayedNote
import pianoanchor.core.model.SysEx
import pianoanchor.engine.midi.MidiParser
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayedNotesTest {
    private val c4 = MidiNote(60)
    private val e4 = MidiNote(64)
    private val g4 = MidiNote(67)

    private fun on(
        t: Long,
        pitch: MidiNote,
        velocity: Int = 64,
    ) = NoteOn(t, 0, pitch, velocity)

    private fun off(
        t: Long,
        pitch: MidiNote,
    ) = NoteOff(t, 0, pitch, 0)

    private fun pedal(
        t: Long,
        value: Int,
    ) = ControlChange(t, 0, 64, value)

    private fun allNotesOff(t: Long) = ControlChange(t, 3, 123, 0)

    private fun allSoundOff(t: Long) = ControlChange(t, 3, 120, 0)

    private fun notes(vararg events: MidiEvent) = PlayedNotes.from(events.toList())

    @Test
    fun pairsNoteOnAndOff() {
        assertEquals(listOf(PlayedNote(c4, 70, 100, 200, 200)), notes(on(100, c4, 70), off(200, c4)))
    }

    @Test
    fun noteOnVelocityZeroEndsTheNote() {
        // On the wire a NoteOn with velocity 0 is a NoteOff: the parser turns it into one.
        val events =
            MidiParser().feed(byteArrayOf(0x90.toByte(), 60, 70), 100) +
                MidiParser().feed(byteArrayOf(0x90.toByte(), 60, 0), 200)
        assertEquals(listOf(PlayedNote(c4, 70, 100, 200, 200)), PlayedNotes.from(events))
    }

    @Test
    fun pedalExtendsSoundingEndUntilReleased() {
        val played =
            notes(on(100, c4), pedal(150, 127), off(200, c4), pedal(250, 80), pedal(400, 63), on(500, e4), off(600, e4))
        assertEquals(listOf(PlayedNote(c4, 64, 100, 200, 400), PlayedNote(e4, 64, 500, 600, 600)), played)
    }

    @Test
    fun pedalDownAfterReleaseDoesNotExtend() {
        val played = notes(on(100, c4), off(200, c4), pedal(300, 127), pedal(400, 0))
        assertEquals(listOf(PlayedNote(c4, 64, 100, 200, 200)), played)
    }

    @Test
    fun pedalHeldToTheEndEndsAtTheLastEvent() {
        val played = notes(pedal(50, 127), on(100, c4), off(200, c4), on(300, e4), off(350, e4))
        assertEquals(listOf(PlayedNote(c4, 64, 100, 200, 350), PlayedNote(e4, 64, 300, 350, 350)), played)
    }

    @Test
    fun repeatedPitchWhileSustainedStartsANewNote() {
        val played = notes(pedal(50, 127), on(100, c4), off(200, c4), on(300, c4, 90), off(400, c4), pedal(500, 0))
        assertEquals(listOf(PlayedNote(c4, 64, 100, 200, 300), PlayedNote(c4, 90, 300, 400, 500)), played)
    }

    @Test
    fun repeatedPitchWhileStillDownEndsTheEarlierNote() {
        val played = notes(on(100, c4), on(300, c4, 90), off(400, c4))
        assertEquals(listOf(PlayedNote(c4, 64, 100, 300, 300), PlayedNote(c4, 90, 300, 400, 400)), played)
    }

    @Test
    fun unfinishedNoteEndsAtTheLastEvent() {
        val played = notes(on(100, c4), on(150, e4), off(250, e4))
        assertEquals(listOf(PlayedNote(c4, 64, 100, 250, 250), PlayedNote(e4, 64, 150, 250, 250)), played)
    }

    @Test
    fun allNotesOffEndsEveryDownKey() {
        val played = notes(on(100, c4), on(110, e4), allNotesOff(300), on(400, g4), off(500, g4))
        val expected =
            listOf(
                PlayedNote(c4, 64, 100, 300, 300),
                PlayedNote(e4, 64, 110, 300, 300),
                PlayedNote(g4, 64, 400, 500, 500),
            )
        assertEquals(expected, played)
    }

    @Test
    fun modeChangesActAsAllNotesOff() {
        (124..127).forEach { controller ->
            val played = notes(on(100, c4), ControlChange(300, 0, controller, 0), off(400, c4))
            assertEquals(listOf(PlayedNote(c4, 64, 100, 300, 300)), played, "CC$controller")
        }
    }

    @Test
    fun allNotesOffUnderPedalKeepsNotesSounding() {
        val played = notes(pedal(50, 127), on(100, c4), allNotesOff(300), pedal(600, 0))
        assertEquals(listOf(PlayedNote(c4, 64, 100, 300, 600)), played)
    }

    @Test
    fun allSoundOffEndsDownAndHeldNotes() {
        val played = notes(pedal(50, 127), on(100, c4), off(200, c4), on(250, e4), allSoundOff(300), pedal(600, 0))
        assertEquals(listOf(PlayedNote(c4, 64, 100, 200, 300), PlayedNote(e4, 64, 250, 300, 300)), played)
    }

    @Test
    fun allSoundOffKeepsPedalState() {
        val played = notes(pedal(50, 127), allSoundOff(100), on(200, c4), off(300, c4), pedal(500, 0))
        assertEquals(listOf(PlayedNote(c4, 64, 200, 300, 500)), played)
    }

    @Test
    fun ignoresOtherEvents() {
        val played =
            notes(
                SysEx(50, listOf(0x7E)),
                on(100, c4),
                ControlChange(120, 0, 7, 127),
                off(200, e4),
                off(250, c4),
            )
        assertEquals(listOf(PlayedNote(c4, 64, 100, 250, 250)), played)
    }

    @Test
    fun onsetOrderAcrossPitches() {
        val played = notes(on(100, g4), on(100, c4), on(150, e4), off(200, e4), off(300, g4), off(300, c4))
        assertEquals(listOf(c4, g4, e4), played.map { it.pitch })
    }

    @Test
    fun eventsOutOfTimestampOrderArePairedInTimeOrder() {
        val played = notes(off(200, c4), on(300, e4), on(100, c4), off(400, e4))
        assertEquals(listOf(PlayedNote(c4, 64, 100, 200, 200), PlayedNote(e4, 64, 300, 400, 400)), played)
    }

    @Test
    fun noEventsGiveNoNotes() {
        assertEquals(emptyList(), notes())
    }
}
