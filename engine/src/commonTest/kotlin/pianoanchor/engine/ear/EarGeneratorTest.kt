package pianoanchor.engine.ear

import pianoanchor.core.model.EarChoice
import pianoanchor.core.model.EarExercise
import pianoanchor.core.model.Tone
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.EarSkills
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val DRAWS = 200
private const val C3 = 48
private const val C4 = 60
private const val G4 = 67
private const val C5 = 72
private val WHITE = setOf(0, 2, 4, 5, 7, 9, 11)

class EarGeneratorTest {
    private fun draws(skill: String) = (0 until DRAWS).map { EarGenerator.draw(skill, Random(it)) }

    private fun EarExercise.numbers() = sounds.map { group -> group.map { it.number }.sorted() }

    private fun EarExercise.single() = numbers().map { it.single() }

    private fun identify(skill: String) = draws(skill).map { assertIs<EarExercise.Identify>(it) }

    private fun assertBothAnswersOccur(items: List<EarExercise.Identify>) {
        assertEquals(items.first().choices.toSet(), items.map { it.answer }.toSet())
        assertTrue(items.all { it.answer in it.choices && it.choices.size == 2 })
    }

    @Test
    fun `echo note is one white key from C4 to G4 and needs the exact key`() {
        val items = draws("ear-echo-note").map { assertIs<EarExercise.Echo>(it) }
        assertTrue(items.all { it.exactOctave })
        assertTrue(items.all { it.single().single() in C4..G4 && it.single().single() % 12 in WHITE })
        assertTrue(items.map { it.single().single() }.toSet().size > 1)
    }

    @Test
    fun `higher or lower leaps 2 to 12 semitones within C3 to C5 in both directions`() {
        val items = identify("ear-higher-lower")
        items.forEach {
            val (a, b) = it.single()
            assertTrue(a in C3..C5 && b in C3..C5, "$a $b")
            assertTrue(kotlin.math.abs(a - b) in 2..12)
            assertEquals(if (b > a) EarChoice.HIGHER else EarChoice.LOWER, it.answer)
        }
        assertBothAnswersOccur(items)
        assertTrue(items.map { kotlin.math.abs(it.single()[0] - it.single()[1]) }.toSet().containsAll((2..12).toList()))
    }

    @Test
    fun `step or skip uses white keys from C4 to C5 a second or third apart`() {
        val items = identify("ear-step-skip")
        val white = listOf(60, 62, 64, 65, 67, 69, 71, 72)
        items.forEach {
            val (a, b) = it.single().map { n -> white.indexOf(n) }
            assertTrue(a >= 0 && b >= 0)
            assertEquals(if (kotlin.math.abs(a - b) == 1) EarChoice.STEP else EarChoice.SKIP, it.answer)
            assertTrue(kotlin.math.abs(a - b) in 1..2)
        }
        assertBothAnswersOccur(items)
        assertTrue(items.any { it.single()[0] > it.single()[1] } && items.any { it.single()[0] < it.single()[1] })
    }

    @Test
    fun `echo three and five are melodies of steps and skips in C4 to G4 in any octave`() {
        for ((skill, length) in listOf("ear-echo-three" to 3, "ear-echo-five" to 5)) {
            val items = draws(skill).map { assertIs<EarExercise.Echo>(it) }
            val white = listOf(60, 62, 64, 65, 67)
            items.forEach {
                assertEquals(false, it.exactOctave)
                val indices = it.single().map { n -> white.indexOf(n) }
                assertEquals(length, indices.size)
                assertTrue(indices.all { i -> i >= 0 })
                indices.zipWithNext().forEach { (a, b) -> assertTrue(kotlin.math.abs(a - b) in 1..2) }
            }
            assertTrue(items.map { it.single() }.toSet().size > 1)
        }
    }

    @Test
    fun `thirds are ascending from a root in C3 to G4`() {
        val items = identify("ear-thirds")
        items.forEach {
            val (a, b) = it.single()
            assertTrue(a in C3..G4)
            assertEquals(if (b - a == 4) EarChoice.MAJOR_THIRD else EarChoice.MINOR_THIRD, it.answer)
            assertTrue(b - a in 3..4)
        }
        assertBothAnswersOccur(items)
    }

    @Test
    fun `fourths and fifths are ascending from a root in C3 to G4`() {
        val items = identify("ear-fourth-fifth")
        items.forEach {
            val (a, b) = it.single()
            assertTrue(a in C3..G4)
            assertEquals(if (b - a == 5) EarChoice.FOURTH else EarChoice.FIFTH, it.answer)
            assertTrue(b - a in 5..7)
        }
        assertBothAnswersOccur(items)
    }

    @Test
    fun `chords are root position triads sounded together`() {
        val items = identify("ear-chord-quality")
        items.forEach {
            val chord = it.numbers().single()
            assertEquals(3, chord.size)
            assertTrue(chord[0] in C3..G4)
            assertEquals(7, chord[2] - chord[0])
            assertEquals(if (chord[1] - chord[0] == 4) EarChoice.MAJOR_CHORD else EarChoice.MINOR_CHORD, it.answer)
        }
        assertBothAnswersOccur(items)
    }

    @Test
    fun `the same seed gives the same item`() =
        EarSkills.all.forEach {
            assertEquals(EarGenerator.draw(it.id, Random(7)), EarGenerator.draw(it.id, Random(7)), it.id)
        }

    @Test
    fun `a skill that is not an ear skill throws`() {
        assertFailsWith<IllegalArgumentException> { EarGenerator.draw("steps-and-skips", Random(1)) }
    }

    @Test
    fun `notes play one gap apart and a chord is held longer`() {
        val line = EarExercise.Echo(listOf(setOf(note(60)), setOf(note(64))), exactOctave = false).tones()
        assertEquals(
            listOf(
                Tone(note(60), 0, Tunables.PLAYBACK_NOTE_LENGTH_MS),
                Tone(note(64), Tunables.PLAYBACK_NOTE_GAP_MS, Tunables.PLAYBACK_NOTE_LENGTH_MS),
            ),
            line,
        )
        val chord = EarGenerator.draw("ear-chord-quality", Random(1)).tones()
        assertEquals(3, chord.size)
        assertTrue(chord.all { it.startMs == 0L && it.lengthMs == Tunables.EAR_CHORD_LENGTH_MS })
    }

    @Test
    fun `an item with no sounds has no tones`() =
        assertEquals(emptyList(), EarExercise.Echo(emptyList(), exactOctave = false).tones())

    private fun note(n: Int) = pianoanchor.core.model.MidiNote(n)
}
