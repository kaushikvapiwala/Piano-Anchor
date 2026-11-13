package pianoanchor.engine.grading

import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.ChordIncomplete
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.model.Step
import pianoanchor.core.model.StepCorrect
import pianoanchor.core.model.WrongNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class GraderTest {
    private val c4 = MidiNote(60)
    private val d4 = MidiNote(62)
    private val e4 = MidiNote(64)
    private val g4 = MidiNote(67)
    private val passage = Passage("c-d-e", "C D E", listOf(Step(setOf(c4)), Step(setOf(d4)), Step(setOf(e4, g4))))
    private val wallClock = Instant.fromEpochSeconds(1_000)

    @Test
    fun accuracyIsStepsOverStepsPlusErrors() {
        assertEquals(1.0, Grader.accuracy(steps = 3, wrongNotes = 0, incompleteChords = 0))
        assertEquals(0.75, Grader.accuracy(steps = 3, wrongNotes = 1, incompleteChords = 0))
        assertEquals(0.5, Grader.accuracy(steps = 4, wrongNotes = 1, incompleteChords = 3))
    }

    @Test
    fun accuracyRejectsBadCounts() {
        assertFailsWith<IllegalArgumentException> { Grader.accuracy(steps = 0, wrongNotes = 0, incompleteChords = 0) }
        assertFailsWith<IllegalArgumentException> { Grader.accuracy(steps = 1, wrongNotes = -1, incompleteChords = 0) }
        assertFailsWith<IllegalArgumentException> { Grader.accuracy(steps = 1, wrongNotes = 0, incompleteChords = -1) }
    }

    @Test
    fun bandBoundariesBelongToTheHigherBand() {
        assertEquals(Band.CLEAN, Grader.band(1.0))
        assertEquals(Band.CLEAN, Grader.band(0.95))
        assertEquals(Band.GOOD, Grader.band(0.9499))
        assertEquals(Band.GOOD, Grader.band(0.80))
        assertEquals(Band.KEEP_PRACTISING, Grader.band(0.7999))
        assertEquals(Band.KEEP_PRACTISING, Grader.band(0.0))
    }

    @Test
    fun bandRejectsAccuracyOutsideZeroToOne() {
        assertFailsWith<IllegalArgumentException> { Grader.band(-0.1) }
        assertFailsWith<IllegalArgumentException> { Grader.band(1.1) }
    }

    @Test
    fun attemptCountsErrorsMeasuresDurationAndRebasesTimestamps() {
        val start = 5_000_000_000L
        val events =
            listOf(
                StepCorrect(start, 0),
                WrongNote(start + 100, 1, MidiNote(63)),
                StepCorrect(start + 200, 1),
                ChordIncomplete(start + 400, 2, setOf(g4)),
                StepCorrect(start + 500, 2),
                PassageComplete(start + 500, 2),
            )

        val attempt = Grader.attempt("a-1", passage, "usb-1", wallClock, startedAtNanos = start, events = events)

        assertEquals(
            Attempt(
                id = "a-1",
                passageId = "c-d-e",
                deviceId = "usb-1",
                startedAt = wallClock,
                durationNanos = 500,
                stepCount = 3,
                wrongNotes = 1,
                incompleteChords = 1,
                accuracy = 0.6,
                band = Band.KEEP_PRACTISING,
                events =
                    listOf(
                        StepCorrect(0, 0),
                        WrongNote(100, 1, MidiNote(63)),
                        StepCorrect(200, 1),
                        ChordIncomplete(400, 2, setOf(g4)),
                        StepCorrect(500, 2),
                        PassageComplete(500, 2),
                    ),
            ),
            attempt,
        )
    }

    @Test
    fun aRunWithoutErrorsIsClean() {
        val single = Passage("c", "C", listOf(Step(setOf(c4))))
        val events = listOf(StepCorrect(7, 0), PassageComplete(7, 0))

        val attempt = Grader.attempt("a-1", single, "usb-1", wallClock, startedAtNanos = 7, events = events)

        assertEquals(Band.CLEAN, attempt.band)
        assertEquals(1.0, attempt.accuracy)
        assertEquals(0, attempt.durationNanos)
    }

    @Test
    fun attemptRejectsEmptyOrUnfinishedEvents() {
        assertFailsWith<IllegalArgumentException> {
            Grader.attempt("a-1", passage, "usb-1", wallClock, startedAtNanos = 0, events = emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            Grader.attempt("a-1", passage, "usb-1", wallClock, startedAtNanos = 0, events = listOf(StepCorrect(0, 0)))
        }
    }
}
