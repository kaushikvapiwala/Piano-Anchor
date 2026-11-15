package pianoanchor.engine.learner

import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.Rating
import pianoanchor.core.model.RunMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class AttemptRatingTest {
    private fun attempt(
        band: Band,
        hintShown: Boolean = false,
        tempoBpm: Int? = 60,
    ) = Attempt(
        id = "a-1",
        passageId = "test:passage",
        deviceId = "usb-1",
        startedAt = Instant.parse("2026-11-01T10:00:00Z"),
        durationNanos = 1,
        stepCount = 1,
        wrongNotes = 0,
        incompleteChords = 0,
        accuracy = 1.0,
        band = band,
        events = emptyList(),
        tempoBpm = tempoBpm,
        playedNotes = emptyList(),
        hintShown = hintShown,
        mode = RunMode.PERFORM,
    )

    @Test
    fun `a clean unaided timed run is good`() = assertEquals(Rating.GOOD, attempt(Band.CLEAN).rating())

    @Test
    fun `a clean run with a hint is hard`() = assertEquals(Rating.HARD, attempt(Band.CLEAN, hintShown = true).rating())

    @Test
    fun `a clean wait mode run is hard`() = assertEquals(Rating.HARD, attempt(Band.CLEAN, tempoBpm = null).rating())

    @Test
    fun `a good run is hard, aided or not`() {
        assertEquals(Rating.HARD, attempt(Band.GOOD).rating())
        assertEquals(Rating.HARD, attempt(Band.GOOD, hintShown = true, tempoBpm = null).rating())
    }

    @Test
    fun `a keep practising run is again, aided or not`() {
        assertEquals(Rating.AGAIN, attempt(Band.KEEP_PRACTISING).rating())
        assertEquals(Rating.AGAIN, attempt(Band.KEEP_PRACTISING, hintShown = true, tempoBpm = null).rating())
    }
}
