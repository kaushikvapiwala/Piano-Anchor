package pianoanchor.engine.ear

import pianoanchor.core.model.EarExercise
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOn
import kotlin.test.Test
import kotlin.test.assertEquals

class EarEchoTest {
    private fun played(vararg numbers: Int) = numbers.map { NoteOn(0, 0, MidiNote(it), 64) }

    private fun echo(
        vararg numbers: Int,
        exactOctave: Boolean,
    ) = EarExercise.Echo(numbers.map { setOf(MidiNote(it)) }, exactOctave)

    @Test
    fun `the exact key passes`() = assertEquals(true, EarEcho.matches(echo(64, exactOctave = true), played(64)))

    @Test
    fun `the wrong octave fails when the exact key is needed`() =
        assertEquals(false, EarEcho.matches(echo(64, exactOctave = true), played(76)))

    @Test
    fun `any octave passes for a melody`() =
        assertEquals(true, EarEcho.matches(echo(60, 64, 62, exactOctave = false), played(72, 52, 50)))

    @Test
    fun `the wrong order fails`() =
        assertEquals(false, EarEcho.matches(echo(60, 64, 62, exactOctave = false), played(60, 62, 64)))

    @Test
    fun `a missing note fails`() =
        assertEquals(false, EarEcho.matches(echo(60, 64, 62, exactOctave = false), played(60, 64)))

    @Test
    fun `extra notes after the target are ignored`() =
        assertEquals(true, EarEcho.matches(echo(60, 64, exactOctave = false), played(60, 64, 71)))

    @Test
    fun `a chord group counts from its lowest note up`() {
        val chord = EarExercise.Echo(listOf(setOf(MidiNote(67), MidiNote(60))), exactOctave = true)
        assertEquals(true, EarEcho.matches(chord, played(60, 67)))
        assertEquals(false, EarEcho.matches(chord, played(67, 60)))
    }
}
