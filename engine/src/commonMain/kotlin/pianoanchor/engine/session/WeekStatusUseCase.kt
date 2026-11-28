package pianoanchor.engine.session

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.learner.LearnerModel

/** The week's [status] and the [playedDays] it was counted from (every day with practice in the windows read). */
public data class WeekReport(
    val status: WeekStatus,
    val playedDays: Set<LocalDate>,
)

/**
 * Counts the weekly days goal the same way for Today and Progress: practice days are the calendar days (in [zone]) with
 * an attempt, plus the learner days with an ear set or review quiz; [WeekPause] turns them into the week's status.
 */
public class WeekStatusUseCase(
    private val attempts: AttemptRepositoryPort,
    private val learner: LearnerModel,
    private val zone: TimeZone,
) {
    /**
     * The status of the week containing [today] for [goalDays] and [pausedWeeks]. The streak counts weeks back to the
     * first that missed its goal. The practice days are read in slices of [Tunables.STREAK_READ_WEEKS] weeks, newest
     * first: when every week of the slices read so far met its goal (or was paused), the slice before them is read
     * and added, until a week missed or a slice is empty. The result equals a read of the whole history.
     *
     * @throws pianoanchor.core.model.StorageUnavailableException when the store can't be read.
     */
    public suspend fun load(
        today: LocalDate,
        goalDays: Int,
        pausedWeeks: Set<LocalDate>,
    ): WeekReport {
        val thisMonday = WeekPause.mondayOf(today)
        var start = thisMonday.minus(Tunables.STREAK_READ_WEEKS, DateTimeUnit.WEEK)
        var days = daysFrom(start)
        while (weeksCounted(start, thisMonday, days, goalDays, pausedWeeks)) {
            val older = start.minus(Tunables.STREAK_READ_WEEKS, DateTimeUnit.WEEK)
            val slice = daysFrom(older).filterTo(HashSet()) { it < start }
            if (slice.isEmpty()) break
            days += slice
            start = older
        }
        return WeekReport(WeekPause.status(today, days, goalDays, pausedWeeks), days)
    }

    /** Whether every week from [from] up to (not including) [until] counts. */
    private fun weeksCounted(
        from: LocalDate,
        until: LocalDate,
        days: Set<LocalDate>,
        goalDays: Int,
        pausedWeeks: Set<LocalDate>,
    ): Boolean =
        generateSequence(from) { it.plus(1, DateTimeUnit.WEEK) }
            .takeWhile { it < until }
            .all { WeekPause.counted(it, days, goalDays, pausedWeeks) }

    private suspend fun daysFrom(from: LocalDate): Set<LocalDate> =
        attempts.practiceDays(from, zone) + learner.reviewDays(from)
}
