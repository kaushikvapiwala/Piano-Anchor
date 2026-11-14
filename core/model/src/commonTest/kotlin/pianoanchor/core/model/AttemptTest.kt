package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class AttemptTest {
    private val events = listOf(StepCorrect(0, 0), WrongNote(5, 1, MidiNote(61)), PassageComplete(9, 1))

    private fun attempt(
        id: String = "a-1",
        passageId: String = "c-d-e",
        deviceId: String = "usb-1",
        durationNanos: Long = 9,
        stepCount: Int = 2,
        wrongNotes: Int = 1,
        incompleteChords: Int = 0,
        accuracy: Double = 2.0 / 3,
        events: List<MatchEvent> = this.events,
        tempoBpm: Int? = null,
    ) = Attempt(
        id,
        passageId,
        deviceId,
        Instant.fromEpochSeconds(1_000),
        durationNanos,
        stepCount,
        wrongNotes,
        incompleteChords,
        accuracy,
        Band.KEEP_PRACTISING,
        events,
        tempoBpm,
        emptyList(),
    )

    @Test
    fun keepsAValidAttempt() {
        assertEquals(events, attempt().events)
        assertEquals(emptyList(), attempt(events = emptyList()).events)
    }

    @Test
    fun acceptsTheRangeLimits() {
        val attempt = attempt(durationNanos = 0, stepCount = 1, wrongNotes = 0, incompleteChords = 0, accuracy = 1.0)
        assertEquals(1.0, attempt.accuracy)
        assertEquals(0.0, attempt(accuracy = 0.0).accuracy)
    }

    @Test
    fun rejectsBlankIds() {
        assertFailsWith<IllegalArgumentException> { attempt(id = " ") }
        assertFailsWith<IllegalArgumentException> { attempt(passageId = "") }
        assertFailsWith<IllegalArgumentException> { attempt(deviceId = " ") }
    }

    @Test
    fun rejectsCountsOutOfRange() {
        assertFailsWith<IllegalArgumentException> { attempt(durationNanos = -1) }
        assertFailsWith<IllegalArgumentException> { attempt(stepCount = 0) }
        assertFailsWith<IllegalArgumentException> { attempt(wrongNotes = -1) }
        assertFailsWith<IllegalArgumentException> { attempt(incompleteChords = -1) }
    }

    @Test
    fun rejectsAccuracyOutsideZeroToOne() {
        assertFailsWith<IllegalArgumentException> { attempt(accuracy = -0.01) }
        assertFailsWith<IllegalArgumentException> { attempt(accuracy = 1.01) }
        assertFailsWith<IllegalArgumentException> { attempt(accuracy = Double.NaN) }
    }

    @Test
    fun rejectsEventsBeforeTheFirstNoteOrOutOfOrder() {
        assertFailsWith<IllegalArgumentException> { attempt(events = listOf(StepCorrect(-1, 0))) }
        assertFailsWith<IllegalArgumentException> {
            attempt(events = listOf(StepCorrect(5, 0), PassageComplete(4, 1)))
        }
    }

    @Test
    fun acceptsNoTempoOrATempoInRangeAndRejectsOthers() {
        assertEquals(null, attempt().tempoBpm)
        assertEquals(MIN_TEMPO_BPM, attempt(tempoBpm = MIN_TEMPO_BPM).tempoBpm)
        assertEquals(MAX_TEMPO_BPM, attempt(tempoBpm = MAX_TEMPO_BPM).tempoBpm)
        assertFailsWith<IllegalArgumentException> { attempt(tempoBpm = MIN_TEMPO_BPM - 1) }
        assertFailsWith<IllegalArgumentException> { attempt(tempoBpm = MAX_TEMPO_BPM + 1) }
    }
}
