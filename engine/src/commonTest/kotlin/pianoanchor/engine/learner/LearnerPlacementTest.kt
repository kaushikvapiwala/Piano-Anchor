package pianoanchor.engine.learner

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Rating
import pianoanchor.engine.scheduler.Fsrs
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.passageOf
import pianoanchor.testing.skillGraphOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class LearnerPlacementTest {
    private val repository = FakeLearnerRepository()
    private val model =
        LearnerModel(skillGraphOf("a" to emptySet(), "b" to setOf("a"), "c" to setOf("b")), repository)
    private val day = LocalDate(2026, 11, 1)

    private fun cleanAttempt() =
        Attempt(
            id = "attempt-1",
            passageId = "test:passage",
            deviceId = "usb-1",
            startedAt = Instant.parse("2026-11-01T10:00:00Z"),
            durationNanos = 1,
            stepCount = 1,
            wrongNotes = 0,
            incompleteChords = 0,
            accuracy = 1.0,
            band = Band.CLEAN,
            events = emptyList(),
            tempoBpm = null,
            playedNotes = emptyList(),
        )

    @Test
    fun `place marks skills learned with a placement review`() =
        runTest {
            model.place(setOf("b", "a"), day)

            val review = repository.reviews("a").single()
            assertEquals(Rating.GOOD, review.rating)
            assertEquals(PLACEMENT_ATTEMPT_ID, review.attemptId)
            assertEquals(day, review.day)
            val state = repository.states().getValue("a")
            assertTrue(state.learned)
            assertEquals(Fsrs().initial(Rating.GOOD), state.memory)
            assertEquals(setOf("a", "b"), model.learnedSkills())
            assertEquals(listOf("c"), model.plan(day).new.map { it.id })
        }

    @Test
    fun `place leaves reviewed skills alone`() =
        runTest {
            model.record(passageOf(setOf(MidiNote(60)), skills = setOf("a")), cleanAttempt(), day)
            val before = repository.states().getValue("a")

            model.place(setOf("a", "b"), day)

            assertEquals(before, repository.states().getValue("a"))
            assertEquals(1, repository.reviews("a").size)
            assertEquals(1, repository.reviews("b").size)
        }

    @Test
    fun `place rejects a skill outside the graph`() =
        runTest {
            assertFailsWith<IllegalArgumentException> { model.place(setOf("a", "zzz"), day) }
            assertEquals(emptyMap(), repository.states())
        }

    @Test
    fun `replay keeps placed skills learned`() =
        runTest {
            model.place(setOf("a"), day)
            val placed = repository.states()

            model.replay()

            assertEquals(placed, repository.states())
            assertTrue(repository.states().getValue("a").learned)
        }

    @Test
    fun `placement does not count as a clean cold check`() =
        runTest {
            model.place(setOf("a"), day)

            val state = repository.states().getValue("a")
            assertEquals(0, state.cleanColdChecks)
            assertEquals(0, state.recalledColdChecks)

            // A later review builds on the placed memory and replays to the same state.
            val next = LocalDate(2026, 11, 5)
            model.record(passageOf(setOf(MidiNote(60)), skills = setOf("a")), cleanAttempt(), next)
            val after = repository.states().getValue("a")
            assertEquals(1, after.cleanColdChecks)
            model.replay()
            assertEquals(after, repository.states().getValue("a"))
        }
}
