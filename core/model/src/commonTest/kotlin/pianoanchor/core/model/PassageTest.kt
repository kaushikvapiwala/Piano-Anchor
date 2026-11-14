package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PassageTest {
    private val c4 = MidiNote(60)
    private val e4 = MidiNote(64)
    private val g4 = MidiNote(67)
    private val fourFour = TimeSignature(4, 4)

    private fun quarter(pitch: MidiNote) = ScoreNote(pitch, TICKS_PER_QUARTER)

    private fun step(
        onset: Int,
        vararg pitches: MidiNote,
    ) = Step(onset, pitches.map(::quarter).toSet())

    private fun passage(
        steps: List<Step> = listOf(step(0, c4)),
        id: String = "builtin:c",
        timeSignature: TimeSignature = fourFour,
        tempoBpm: Int = 80,
    ) = Passage(id, "C", steps, timeSignature, tempoBpm)

    @Test
    fun pitchesListsTheStepsPitches() {
        assertEquals(setOf(c4, e4, g4), step(0, c4, e4, g4).pitches)
    }

    @Test
    fun stepKeepsOnsetAndNotes() {
        val half = ScoreNote(c4, 2 * TICKS_PER_QUARTER)
        val step = Step(TICKS_PER_QUARTER, setOf(half))
        assertEquals(TICKS_PER_QUARTER, step.onsetTicks)
        assertEquals(setOf(half), step.notes)
    }

    @Test
    fun rejectsNonPositiveLength() {
        listOf(0, -1).forEach { length ->
            val e = assertFailsWith<IllegalArgumentException> { ScoreNote(c4, length) }
            assertTrue("C4" in e.message.orEmpty())
        }
    }

    @Test
    fun rejectsNegativeOnset() {
        assertFailsWith<IllegalArgumentException> { step(-1, c4) }
    }

    @Test
    fun rejectsAnEmptyStep() {
        assertFailsWith<IllegalArgumentException> { Step(0, emptySet()) }
    }

    @Test
    fun rejectsDuplicatePitchInAStep() {
        val e =
            assertFailsWith<IllegalArgumentException> {
                Step(0, setOf(ScoreNote(c4, TICKS_PER_QUARTER), ScoreNote(c4, 2 * TICKS_PER_QUARTER)))
            }
        assertTrue("same pitch" in e.message.orEmpty())
    }

    @Test
    fun rejectsOnsetsThatDoNotIncrease() {
        listOf(TICKS_PER_QUARTER, 0).forEach { second ->
            val e =
                assertFailsWith<IllegalArgumentException> {
                    passage(listOf(step(TICKS_PER_QUARTER, c4), step(second, e4)))
                }
            assertTrue("builtin:c" in e.message.orEmpty())
        }
    }

    @Test
    fun acceptsIncreasingOnsets() {
        val steps = listOf(step(0, c4), step(TICKS_PER_QUARTER, e4), step(3 * TICKS_PER_QUARTER, g4))
        assertEquals(steps, passage(steps).steps)
    }

    @Test
    fun rejectsTimeSignatureBeatsOutOf1To32() {
        listOf(0, 33).forEach { beats -> assertFailsWith<IllegalArgumentException> { TimeSignature(beats, 4) } }
        listOf(1, 32).forEach { beats -> assertEquals(beats, TimeSignature(beats, 4).beats) }
    }

    @Test
    fun rejectsBeatUnitNotAPowerOfTwoUpTo16() {
        listOf(0, 3, 6, 32).forEach { unit -> assertFailsWith<IllegalArgumentException> { TimeSignature(4, unit) } }
        listOf(1, 2, 4, 8, 16).forEach { unit -> assertEquals(unit, TimeSignature(4, unit).beatUnit) }
    }

    @Test
    fun rejectsTempoOutside20To300() {
        listOf(MIN_TEMPO_BPM - 1, MAX_TEMPO_BPM + 1).forEach { tempo ->
            val e = assertFailsWith<IllegalArgumentException> { passage(tempoBpm = tempo) }
            assertTrue("builtin:c" in e.message.orEmpty())
        }
    }

    @Test
    fun acceptsTheTempoBounds() {
        assertEquals(20, passage(tempoBpm = MIN_TEMPO_BPM).tempoBpm)
        assertEquals(300, passage(tempoBpm = MAX_TEMPO_BPM).tempoBpm)
    }

    @Test
    fun passageKeepsItsFields() {
        val steps = listOf(step(0, c4))
        val passage = Passage("builtin:c", "C", steps, TimeSignature(3, 4), 90)
        assertEquals("builtin:c", passage.id)
        assertEquals("C", passage.title)
        assertEquals(steps, passage.steps)
        assertEquals(TimeSignature(3, 4), passage.timeSignature)
        assertEquals(90, passage.tempoBpm)
    }

    @Test
    fun passageRejectsBlankId() {
        assertFailsWith<IllegalArgumentException> { passage(id = " ") }
    }

    @Test
    fun passageRejectsNoSteps() {
        assertFailsWith<IllegalArgumentException> { passage(steps = emptyList()) }
    }
}
