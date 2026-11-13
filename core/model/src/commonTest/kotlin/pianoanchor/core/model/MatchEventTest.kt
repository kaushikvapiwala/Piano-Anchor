package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class MatchEventTest {
    @Test
    fun everyEventExposesTimestampAndStep() {
        val events: List<MatchEvent> =
            listOf(
                StepCorrect(1, 0),
                WrongNote(1, 0, MidiNote(61)),
                ChordIncomplete(1, 0, setOf(MidiNote(67))),
                PassageComplete(1, 0),
            )
        events.forEach {
            assertEquals(1L, it.timestampNanos)
            assertEquals(0, it.stepIndex)
        }
    }

    @Test
    fun wrongNoteAndChordIncompleteCarryTheirNotes() {
        assertEquals(MidiNote(61), WrongNote(1, 0, MidiNote(61)).note)
        assertEquals(setOf(MidiNote(67)), ChordIncomplete(1, 0, setOf(MidiNote(67))).missing)
    }
}
