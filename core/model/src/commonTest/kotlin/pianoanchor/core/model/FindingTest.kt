package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FindingTest {
    private val c4 = MidiNote(60)

    @Test
    fun aMissedNoteHasNoPlayedNote() {
        val finding = Finding(ErrorKind.MISSED_NOTE, ScoreNoteRef(2, c4), played = null)
        assertEquals(ScoreNoteRef(2, c4), finding.at)
        assertNull(finding.played)
    }

    @Test
    fun scoreNoteRefRejectsANegativeStep() {
        assertFailsWith<IllegalArgumentException> { ScoreNoteRef(-1, c4) }
    }
}
