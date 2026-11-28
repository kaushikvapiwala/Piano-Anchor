package pianoanchor.engine.session

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.model.Rating
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.testing.FakeAttemptRepository
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.skillGraphOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class WeekStatusUseCaseTest {
    private val attempts = FakeAttemptRepository()
    private val learner = LearnerModel(skillGraphOf("a" to emptySet()), FakeLearnerRepository())
    private val useCase = WeekStatusUseCase(attempts, learner, TimeZone.UTC)

    // Wednesday; the week started on Monday the 16th.
    private val today = LocalDate(2026, 11, 18)

    private fun attempt(
        id: String,
        at: String,
    ) = Attempt(
        id = id,
        passageId = "p",
        deviceId = "d",
        startedAt = Instant.parse(at),
        durationNanos = 1,
        stepCount = 1,
        wrongNotes = 0,
        incompleteChords = 0,
        accuracy = 1.0,
        band = Band.CLEAN,
        events = listOf(PassageComplete(0, 0)),
        tempoBpm = null,
        playedNotes = emptyList(),
    )

    @Test
    fun countsAttemptDaysAndReviewDaysTogether() =
        runTest {
            attempts.save(attempt("a-1", "2026-11-16T10:00:00Z"))
            attempts.save(attempt("a-2", "2026-11-16T11:00:00Z"))
            learner.recordEarSet("a", Band.CLEAN, LocalDate(2026, 11, 17), "1")

            val report = useCase.load(today, goalDays = 2, pausedWeeks = emptySet())

            assertEquals(setOf(LocalDate(2026, 11, 16), LocalDate(2026, 11, 17)), report.playedDays)
            assertEquals(2, report.status.daysPlayed)
            assertEquals(1, report.status.streakWeeks)
        }

    @Test
    fun appliesThePausedWeeks() =
        runTest {
            val monday = LocalDate(2026, 11, 16)

            val report = useCase.load(today, goalDays = 3, pausedWeeks = setOf(monday))

            assertEquals(true, report.status.paused)
            assertEquals(1, report.status.streakWeeks)
        }

    @Test
    fun anUnreadableStoreThrows() =
        runTest {
            attempts.failing = true
            assertFailsWith<StorageUnavailableException> { useCase.load(today, 3, emptySet()) }
        }
}
