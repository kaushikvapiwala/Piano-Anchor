package pianoanchor.engine.session

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
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
     * first that missed its goal. The practice days are read in windows of [Tunables.STREAK_READ_WEEKS] weeks, newest
     * first: when every week of a window met its goal (or was paused) the next window back is read too, until a week
     * missed or no older day turns up. The result equals a read of the whole history.
     *
     * @throws pianoanchor.core.model.StorageUnavailableException when the store can't be read.
     */
    public suspend fun load(
        today: LocalDate,
        goalDays: Int,
        pausedWeeks: Set<LocalDate>,
    ): WeekReport {
        val thisMonday = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
        var weeks = Tunables.STREAK_READ_WEEKS
        var days = daysFrom(thisMonday.minus(weeks, DateTimeUnit.WEEK))
        while (true) {
            val status = WeekPause.status(today, days, goalDays, pausedWeeks)
            val currentCounted = status.paused || status.daysPlayed >= goalDays
            val streakBefore = status.streakWeeks - if (currentCounted) 1 else 0
            if (streakBefore < weeks) return WeekReport(status, days)
            weeks += Tunables.STREAK_READ_WEEKS
            val older = daysFrom(thisMonday.minus(weeks, DateTimeUnit.WEEK))
            if (older.size == days.size) return WeekReport(status, days)
            days = older
        }
    }

    private suspend fun daysFrom(from: LocalDate): Set<LocalDate> =
        attempts.practiceDays(from, zone) + learner.reviewDays(from)
}
