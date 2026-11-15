package pianoanchor.engine.content

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.core.model.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val Q = TICKS_PER_QUARTER

/** A sixteenth-note bar: any whole number of sixteenths fits, so tests of single events need no padding. */
private val ANY_LENGTH = TimeSignature(1, 16)

private val FOUR_FOUR = TimeSignature(4, 4)

class NoteTextTest {
    private fun pitches(text: String) = NoteText.parse(text, ANY_LENGTH).map { step -> step.pitches.map { it.number } }

    private fun message(
        text: String,
        time: TimeSignature = FOUR_FOUR,
    ) = assertFailsWith<IllegalArgumentException> { NoteText.parse(text, time) }.message.orEmpty()

    @Test
    fun `parses names, accidentals and octaves`() {
        assertEquals(
            listOf(listOf(60), listOf(66), listOf(58), listOf(0), listOf(127)),
            pitches("C4q F#4q Bb3q C-1q G9q"),
        )
    }

    @Test
    fun `lengths in ticks including dotted`() {
        val steps = NoteText.parse("C4w C4h C4q C4e C4s C4h. C4q.", ANY_LENGTH)
        assertEquals(
            listOf(4 * Q, 2 * Q, Q, Q / 2, Q / 4, 3 * Q, Q * 3 / 2),
            steps.map { it.notes.single().lengthTicks },
        )
        assertEquals(
            listOf(0, 4 * Q, 6 * Q, 7 * Q, 7 * Q + Q / 2, 7 * Q + Q * 3 / 4, 10 * Q + Q * 3 / 4),
            steps.map { it.onsetTicks },
        )
    }

    @Test
    fun `chord shares onset and length`() {
        val step = NoteText.parse("[C4 E4 G4]h rh", ANY_LENGTH).single()
        assertEquals(0, step.onsetTicks)
        assertEquals(setOf(MidiNote(60), MidiNote(64), MidiNote(67)), step.pitches)
        assertEquals(setOf(2 * Q), step.notes.map { it.lengthTicks }.toSet())
    }

    @Test
    fun `rest moves later onsets`() {
        assertEquals(listOf(0, 2 * Q), NoteText.parse("C4q rq D4h", FOUR_FOUR).map { it.onsetTicks })
        assertEquals(listOf(2 * Q), NoteText.parse("rh C4h", FOUR_FOUR).map { it.onsetTicks })
    }

    @Test
    fun `bar lines must fill the time signature`() {
        assertEquals(3, NoteText.parse("C4h D4h | E4w", FOUR_FOUR).size)
        assertEquals(6, NoteText.parse("C4q D4q E4q | F4q G4q A4q |", TimeSignature(3, 4)).size)
        assertEquals(
            "token 3 '|': bar has 2880 ticks, expected 3840 for TimeSignature(beats=4, beatUnit=4)",
            message("C4h D4q |"),
        )
        assertTrue(message("C4w | | C4w").startsWith("token 3 '|': bar has 0 ticks"))
        assertTrue(message("C4h D4h E4q").startsWith("the last bar has 960 ticks"))
    }

    @Test
    fun `after a bar line the last bar must be exactly one bar`() {
        assertEquals(2, NoteText.parse("C4w | D4w", FOUR_FOUR).size)
        assertTrue(message("C4w | D4w E4w").startsWith("the last bar has 7680 ticks"))
    }

    @Test
    fun `rejects text with only rests`() = assertEquals("note text has no notes, only rests", message("rw"))

    @Test
    fun `any whitespace separates tokens`() = assertEquals(listOf(listOf(60), listOf(62)), pitches("C4q\n\tD4q"))

    @Test
    fun `rejects unknown name`() {
        assertEquals("token 7 'H4q': unknown note name", message("C4h C4h | C4w | C4h H4q"))
        assertTrue(message("C4q Cq").contains("octave number"))
    }

    @Test
    fun `rejects octave out of range`() {
        assertTrue(message("C10q").contains("octave 10 out of range"))
        assertTrue(message("C99999999999q").startsWith("token 1 'C99999999999q': octave 99999999999 out of range"))
        assertTrue(message("G#9q").contains("outside MIDI"))
        assertTrue(message("C-2q").contains("octave -2 out of range"))
        assertTrue(message("Cb-1q").contains("pitch -1 is outside MIDI"))
    }

    @Test
    fun `rejects empty text`() {
        assertEquals("note text is empty", message("  "))
    }

    @Test
    fun `error names the token`() {
        assertTrue(message("C4q C4z").startsWith("token 2 'C4z': unknown length 'z'"))
        assertTrue(message("[C4 E4q]q").startsWith("token 1 '[C4 E4q]q'"))
        assertTrue(message("[]q").contains("empty chord"))
        assertTrue(message("[ C4]q").contains("unknown note name"))
        assertTrue(message("[C4 C4]q").contains("same pitch twice"))
        assertTrue(message("C4w [C4 E4").startsWith("token 2 '[C4 E4': chord is not closed"))
    }
}
