package pianoanchor.engine.session

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.model.Rating
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.testing.FakeAttemptRepository
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.skillGraphOf
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/** Records the `from` of every practice-day read. */
private class RecordingAttempts(
    private val inner: FakeAttemptRepository,
) : AttemptRepositoryPort by inner {
    val reads = mutableListOf<LocalDate>()

    override suspend fun practiceDays(
        from: LocalDate,
        zone: TimeZone,
    ): Set<LocalDate> {
        reads += from
        return inner.practiceDays(from, zone)
    }
}

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

    private val monday = LocalDate(2026, 11, 16)

    private fun weekDay(weeksBack: Int) = monday.minus(weeksBack, DateTimeUnit.WEEK).plus(1, DateTimeUnit.DAY)

    private suspend fun play(weeksBack: Int) {
        val day = weekDay(weeksBack)
        attempts.save(attempt("w-$weeksBack", "${day}T10:00:00Z"))
    }

    @Test
    fun aFortyWeekStreakReadsTwoWindows() =
        runTest {
            (1..40).forEach { play(it) }
            val recording = RecordingAttempts(attempts)

            val report = WeekStatusUseCase(recording, learner, TimeZone.UTC).load(today, 1, emptySet())

            assertEquals(40, report.status.streakWeeks)
            assertEquals(2, recording.reads.size)
            assertEquals(monday.minus(Tunables.STREAK_READ_WEEKS, DateTimeUnit.WEEK), recording.reads[0])
            assertEquals(monday.minus(2 * Tunables.STREAK_READ_WEEKS, DateTimeUnit.WEEK), recording.reads[1])
        }

    @Test
    fun aStreakBrokenThreeWeeksAgoReadsOneWindow() =
        runTest {
            play(1)
            play(2)
            play(4)
            val recording = RecordingAttempts(attempts)

            val report = WeekStatusUseCase(recording, learner, TimeZone.UTC).load(today, 1, emptySet())

            assertEquals(2, report.status.streakWeeks)
            assertEquals(1, recording.reads.size)
        }

    @Test
    fun aStreakAsOldAsTheDataStopsReadingWhenNothingOlderComes() =
        runTest {
            (1..Tunables.STREAK_READ_WEEKS).forEach { play(it) }
            val recording = RecordingAttempts(attempts)

            val report = WeekStatusUseCase(recording, learner, TimeZone.UTC).load(today, 1, emptySet())

            assertEquals(Tunables.STREAK_READ_WEEKS, report.status.streakWeeks)
            assertEquals(2, recording.reads.size)
        }

    @Test
    fun pausedWeeksKeepAStreakGoingThroughTheWindows() =
        runTest {
            (1..30).filter { it != 10 }.forEach { play(it) }
            val paused = setOf(monday.minus(10, DateTimeUnit.WEEK))

            val report = useCase.load(today, 1, paused)

            assertEquals(30, report.status.streakWeeks)
        }

    @Test
    fun theWindowedReadEqualsTheUnboundedReadOnRandomHistories() =
        runTest {
            for (seed in 1..SEEDS) {
                val random = Random(seed)
                val repository = FakeAttemptRepository()
                val weeks = random.nextInt(1, HISTORY_WEEKS)
                for (back in 0..weeks) {
                    if (random.nextInt(MISS_ONE_IN) != 0) {
                        val day = monday.minus(back, DateTimeUnit.WEEK).plus(random.nextInt(3), DateTimeUnit.DAY)
                        repository.save(attempt("s$seed-$back", "${day}T10:00:00Z"))
                    }
                }
                val paused = setOf(monday.minus(random.nextInt(HISTORY_WEEKS), DateTimeUnit.WEEK))
                val fresh = WeekStatusUseCase(repository, learner, TimeZone.UTC)

                val windowed = fresh.load(today, 1, paused).status
                val unbounded =
                    WeekPause.status(today, repository.practiceDays(LocalDate(2000, 1, 1), TimeZone.UTC), 1, paused)

                assertEquals(unbounded, windowed, "seed $seed")
            }
        }
}

private const val SEEDS = 40
private const val HISTORY_WEEKS = 120
private const val MISS_ONE_IN = 30
