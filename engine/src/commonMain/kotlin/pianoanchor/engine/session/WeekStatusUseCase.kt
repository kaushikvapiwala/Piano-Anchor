package pianoanchor.engine.session

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.engine.learner.LearnerModel

private val FAR_PAST = LocalDate(2000, 1, 1)

/** The week's [status] and the [playedDays] it was counted from (every day with practice, back to the first). */
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
     * first that missed its goal, so it reads the whole history.
     *
     * @throws pianoanchor.core.model.StorageUnavailableException when the store can't be read.
     */
    public suspend fun load(
        today: LocalDate,
        goalDays: Int,
        pausedWeeks: Set<LocalDate>,
    ): WeekReport {
        val days = attempts.practiceDays(FAR_PAST, zone) + learner.reviewDays(FAR_PAST)
        return WeekReport(WeekPause.status(today, days, goalDays, pausedWeeks), days)
    }
}
