package pianoanchor.engine.matcher

import pianoanchor.core.model.ErrorKind
import pianoanchor.core.model.Finding
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.PlayedNote
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.engine.Tunables
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TempoRestartTest {
    private val c4 = MidiNote(60)
    private val at = ScoreNoteRef(0, c4)
    private val played = PlayedNote(c4, 80, 0, 10, 10)

    private fun finding(kind: ErrorKind): Finding =
        Finding(kind, at, if (kind == ErrorKind.MISSED_NOTE || kind == ErrorKind.INCOMPLETE_CHORD) null else played)

    @Test
    fun onlyWrongAndMissedNotesRestartTheRun() {
        val restarting = ErrorKind.entries.filter { isRestartFinding(finding(it)) }

        assertEquals(listOf(ErrorKind.WRONG_NOTE, ErrorKind.MISSED_NOTE), restarting)
    }

    @Test
    fun earlyLateAndHeldShortNeverRestart() {
        listOf(ErrorKind.EARLY, ErrorKind.LATE, ErrorKind.TOO_SHORT).forEach {
            assertFalse(isRestartFinding(finding(it)), "$it")
        }
    }

    @Test
    fun aSlowerRungIsOfferedAfterTheTunedNumberOfRestartsInARow() {
        assertFalse(offersSlowerRung(Tunables.TEMPO_RESTARTS_BEFORE_OFFER - 1))
        assertTrue(offersSlowerRung(Tunables.TEMPO_RESTARTS_BEFORE_OFFER))
    }
}
