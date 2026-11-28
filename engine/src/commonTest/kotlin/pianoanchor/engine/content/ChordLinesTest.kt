package pianoanchor.engine.content

import pianoanchor.core.model.ChordMark
import pianoanchor.core.model.ChordQuality
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.core.model.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChordLinesTest {
    private val songUnits = setOf("unit-1", "unit-more-songs")

    private val pieces get() =
        Curriculum.units
            .filter { it.id in songUnits }
            .flatMap { it.passages }
            .filter { it.piece }

    @Test
    fun `every piece of unit 1 and the more songs unit has a chord line`() {
        assertEquals(7, pieces.size)
        pieces.forEach { assertTrue(it.chords.isNotEmpty(), "${it.id} has no chord line") }
    }

    @Test
    fun `the chord lines of the left-hand pieces of the course are set too`() {
        val ids = listOf("jingle-bells", "go-tell-aunt-rhody", "ode-to-joy-with-chords", "lightly-row-with-chords")
        ids.forEach { id -> assertTrue(Curriculum.passage("builtin:$id")!!.chords.isNotEmpty(), id) }
    }

    @Test
    fun `every chord sits on a beat before the end of its passage and the line starts at the first beat`() {
        Curriculum.passages.filter { it.chords.isNotEmpty() }.forEach { passage ->
            val end = passage.steps.maxOf { step -> step.onsetTicks + step.notes.maxOf { it.lengthTicks } }
            assertEquals(0, passage.chords.first().onsetTicks, passage.id)
            passage.chords.forEach { chord ->
                assertEquals(0, chord.onsetTicks % TICKS_PER_QUARTER, "${passage.id}: chord off the beat")
                assertTrue(chord.onsetTicks < end, "${passage.id}: chord at ${chord.onsetTicks} is past the end")
            }
        }
    }

    @Test
    fun `a chord line gives each bar its chords evenly`() {
        val bar = TimeSignature(4, 4).barTicks

        assertEquals(
            listOf(
                ChordMark(0, 0, ChordQuality.MAJOR),
                ChordMark(bar, 9, ChordQuality.MINOR),
                ChordMark(2 * bar, 7, ChordQuality.DOMINANT_7),
                ChordMark(2 * bar + bar / 2, 5, ChordQuality.MAJOR),
            ),
            chordLine("C | Am | G7 F", TimeSignature(4, 4)),
        )
    }

    @Test
    fun `blank text is no chord line`() {
        assertTrue(chordLine(" ", TimeSignature(4, 4)).isEmpty())
    }

    @Test
    fun `a chord line rejects an unknown chord`() {
        assertFailsWith<IllegalArgumentException> { chordLine("H", TimeSignature(4, 4)) }
        assertFailsWith<IllegalArgumentException> { chordLine("Cx", TimeSignature(4, 4)) }
    }

    @Test
    fun `a chord line rejects a bar that its chords do not divide`() {
        assertFailsWith<IllegalArgumentException> { chordLine("C G F", TimeSignature(4, 4)) }
        assertFailsWith<IllegalArgumentException> { chordLine("C D E F G A B", TimeSignature(4, 4)) }
    }
}
