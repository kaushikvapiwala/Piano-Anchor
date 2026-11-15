package pianoanchor.engine.onboarding

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val C = 60
private const val D = 62
private const val E = 64
private const val F = 65
private const val G = 67
private const val OCTAVE = 12

private fun played(vararg notes: Int) = notes.mapIndexed { i, n -> NoteOn(i.toLong(), 0, MidiNote(n), 80) }

class EchoMatchesTest {
    @Test
    fun `C E G in any octave passes`() {
        assertTrue(echoMatches(played(C, E, G)))
        assertTrue(echoMatches(played(C - OCTAVE, E + OCTAVE, G)))
        assertTrue(echoMatches(played(C + OCTAVE, E - OCTAVE, G - OCTAVE)))
    }

    @Test
    fun `wrong order fails`() {
        assertFalse(echoMatches(played(C, G, E)))
        assertFalse(echoMatches(played(E, C, G)))
        assertFalse(echoMatches(played(C, E)))
        assertFalse(echoMatches(played()))
    }

    @Test
    fun `one stray note before C passes`() {
        assertTrue(echoMatches(played(D, C, E, G)))
        assertTrue(echoMatches(played(C, C, E, G)))
    }

    @Test
    fun `two stray notes fail`() {
        assertFalse(echoMatches(played(D, F, C, E, G)))
    }

    @Test
    fun `notes after the G do not matter`() {
        assertTrue(echoMatches(played(C, E, G, D)))
    }

    @Test
    fun `an attempt can still match while it starts C E G after at most one stray note`() {
        assertTrue(echoCanStillMatch(played()))
        assertTrue(echoCanStillMatch(played(C)))
        assertTrue(echoCanStillMatch(played(D)))
        assertTrue(echoCanStillMatch(played(D, C, E)))
        assertTrue(echoCanStillMatch(played(C, E, G)))
    }

    @Test
    fun `an attempt that cannot become C E G is over`() {
        assertFalse(echoCanStillMatch(played(C, E, F)))
        assertFalse(echoCanStillMatch(played(D, F)))
        assertFalse(echoCanStillMatch(played(D, D, C, E, G)))
        assertEquals(false, echoCanStillMatch(played(C, E, G, D)))
    }
}
