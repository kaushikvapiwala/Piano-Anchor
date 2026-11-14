package pianoanchor.engine.grading

import pianoanchor.core.model.Band
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.PlayedNote
import pianoanchor.testing.passageOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class GraderTimedTest {
    private val c4 = MidiNote(60)
    private val d4 = MidiNote(62)
    private val e4 = MidiNote(64)
    private val ms = 1_000_000L
    private val passage = passageOf(setOf(c4), setOf(d4), setOf(e4), id = "c-d-e", title = "C D E")
    private val chord = passageOf(setOf(c4, e4), id = "chord", title = "Chord")
    private val startedAt = Instant.fromEpochSeconds(1_000)

    /** A key pressed at [downMs] and held 900 ms, so a quarter note at 60 BPM is neither short nor long. */
    private fun note(
        pitch: MidiNote,
        downMs: Long,
        velocity: Int = 80,
        heldMs: Long = 900,
    ) = PlayedNote(pitch, velocity, downMs * ms, (downMs + heldMs) * ms, (downMs + heldMs) * ms)

    private val cleanTake = listOf(note(c4, 0), note(d4, 1_000), note(e4, 2_000))

    private fun grade(
        notes: List<PlayedNote>,
        passage: pianoanchor.core.model.Passage = this.passage,
    ) = Grader.timedAttempt("a-1", passage, "usb-1", startedAt, bpm = 60, notes = notes)

    @Test
    fun cleanTakeScoresOneAndIsClean() {
        val attempt = grade(cleanTake)

        assertEquals(1.0, attempt.accuracy)
        assertEquals(Band.CLEAN, attempt.band)
        assertEquals(0, attempt.wrongNotes)
        assertEquals(0, attempt.incompleteChords)
        assertEquals(2_900 * ms, attempt.durationNanos)
    }

    @Test
    fun extraAndMistouchNotesEnlargeTheDenominator() {
        val extra = grade(cleanTake + note(MidiNote(90), 1_500))
        assertEquals(3.0 / 4, extra.accuracy)
        assertEquals(1, extra.wrongNotes)

        val brushed =
            grade(listOf(note(c4, 0), note(MidiNote(61), 10, velocity = 40), note(d4, 1_000), note(e4, 2_000)))
        assertEquals(3.0 / 4, brushed.accuracy)
        assertEquals(1, brushed.wrongNotes)

        val both =
            grade(
                listOf(
                    note(c4, 0),
                    note(MidiNote(61), 10, velocity = 40),
                    note(d4, 1_000),
                    note(e4, 2_000),
                    note(MidiNote(90), 1_500),
                ),
            )
        assertEquals(3.0 / 5, both.accuracy)
        assertEquals(2, both.wrongNotes)
        assertEquals(Band.KEEP_PRACTISING, both.band)
    }

    @Test
    fun countsMapFromFindings() {
        val wrongAndOctave = grade(listOf(note(c4, 0), note(MidiNote(66), 1_000), note(MidiNote(76), 2_000)))
        assertEquals(2, wrongAndOctave.wrongNotes)
        assertEquals(1.0 / 3, wrongAndOctave.accuracy)

        val incomplete = grade(listOf(note(c4, 0)), chord)
        assertEquals(1, incomplete.incompleteChords)
        assertEquals(0, incomplete.wrongNotes)
        assertEquals(1.0 / 2, incomplete.accuracy)

        val missed = grade(cleanTake.take(2))
        assertEquals(0, missed.wrongNotes)
        assertEquals(0, missed.incompleteChords)
        assertEquals(2.0 / 3, missed.accuracy)
    }

    @Test
    fun lateNotesAreFindingsToo() {
        val late = grade(listOf(note(c4, 0), note(d4, 1_400), note(e4, 2_000)))
        assertEquals(2.0 / 3, late.accuracy)
    }

    @Test
    fun tempoBpmAndPlayedNotesAreKept() {
        val attempt = grade(cleanTake)

        assertEquals(60, attempt.tempoBpm)
        assertEquals(cleanTake, attempt.playedNotes)
        assertEquals(emptyList(), attempt.events)
        assertEquals("c-d-e", attempt.passageId)
        assertEquals(3, attempt.stepCount)
        assertEquals(startedAt, attempt.startedAt)
    }

    @Test
    fun anEmptyTakeScoresZeroWithNoDuration() {
        val attempt = grade(emptyList())

        assertEquals(0.0, attempt.accuracy)
        assertEquals(Band.KEEP_PRACTISING, attempt.band)
        assertEquals(0, attempt.durationNanos)
    }

    @Test
    fun aTakeEndingBeforeTickZeroHasNoNegativeDuration() {
        val early = PlayedNote(c4, 80, -300 * ms, -200 * ms, -200 * ms)

        assertEquals(0, grade(listOf(early)).durationNanos)
    }
}
