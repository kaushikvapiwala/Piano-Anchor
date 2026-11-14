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
    fun onlyFindingsThatDropANoteHaveNoPlayedNote() {
        val played = PlayedNote(c4, 64, 0, 10, 10)
        Finding(ErrorKind.INCOMPLETE_CHORD, ScoreNoteRef(0, c4), played = null)
        Finding(ErrorKind.OCTAVE, ScoreNoteRef(0, c4), played)
        assertFailsWith<IllegalArgumentException> { Finding(ErrorKind.MISSED_NOTE, ScoreNoteRef(0, c4), played) }
        assertFailsWith<IllegalArgumentException> { Finding(ErrorKind.INCOMPLETE_CHORD, ScoreNoteRef(0, c4), played) }
        assertFailsWith<IllegalArgumentException> { Finding(ErrorKind.WRONG_NOTE, ScoreNoteRef(0, c4), played = null) }
    }

    @Test
    fun scoreNoteRefRejectsANegativeStep() {
        assertFailsWith<IllegalArgumentException> { ScoreNoteRef(-1, c4) }
    }
}
