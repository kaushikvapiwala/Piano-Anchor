package pianoanchor.engine.session

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import pianoanchor.engine.Tunables

private const val DAYS_PER_WEEK = 7

/**
 * How the current week stands: [daysPlayed] of [goalDays] since [weekStart] (a Monday), whether the week is [paused],
 * the [pausesLeft], whether the learner [canPause] this week and the [streakWeeks].
 */
public data class WeekStatus(
    val weekStart: LocalDate,
    val daysPlayed: Int,
    val goalDays: Int,
    val paused: Boolean,
    val pausesLeft: Int,
    val canPause: Boolean,
    val streakWeeks: Int,
)

/** The weekly days goal and its pauses (`docs/algorithms/session-composer.md`, weekly goal). Pure. */
public object WeekPause {
    /**
     * The status of the week containing [today] (weeks start on Monday): [playedDays] are the days with practice,
     * [pausedWeeks] the Mondays of the paused weeks.
     *
     * A pause counts as used from its week until [Tunables.WEEK_PAUSE_REFILL_WEEKS] weeks later, and at most
     * [Tunables.WEEK_PAUSES_MAX] can be unused. The streak counts the weeks before this one that met the goal or were
     * paused, back to the first that did neither, plus this week when it meets the goal or is paused.
     */
    public fun status(
        today: LocalDate,
        playedDays: Set<LocalDate>,
        goalDays: Int,
        pausedWeeks: Set<LocalDate>,
    ): WeekStatus {
        require(goalDays > 0) { "goalDays ($goalDays) must be positive" }
        val weekStart = mondayOf(today)

        fun counted(start: LocalDate) =
            start in pausedWeeks ||
                playedDays.count { it >= start && it < start.plus(1, DateTimeUnit.WEEK) } >= goalDays

        val refillFrom = weekStart.minus(Tunables.WEEK_PAUSE_REFILL_WEEKS * DAYS_PER_WEEK - 1, DateTimeUnit.DAY)
        val used = pausedWeeks.count { it >= refillFrom }
        val pausesLeft = (Tunables.WEEK_PAUSES_MAX - used).coerceAtLeast(0)
        val paused = weekStart in pausedWeeks
        var streak = if (counted(weekStart)) 1 else 0
        var week = weekStart.minus(1, DateTimeUnit.WEEK)
        while (counted(week)) {
            streak++
            week = week.minus(1, DateTimeUnit.WEEK)
        }
        return WeekStatus(
            weekStart = weekStart,
            daysPlayed = playedDays.count { it >= weekStart && it <= today },
            goalDays = goalDays,
            paused = paused,
            pausesLeft = pausesLeft,
            canPause = !paused && pausesLeft > 0,
            streakWeeks = streak,
        )
    }

    /**
     * [pausedWeeks] with the week of [status] added.
     *
     * @throws IllegalStateException when `!status.canPause`.
     */
    public fun pause(
        today: LocalDate,
        status: WeekStatus,
        pausedWeeks: Set<LocalDate>,
    ): Set<LocalDate> {
        check(status.canPause) { "the week of ${status.weekStart} can't be paused" }
        return pausedWeeks + mondayOf(today)
    }

    private fun mondayOf(day: LocalDate): LocalDate = day.minus(day.dayOfWeek.ordinal, DateTimeUnit.DAY)
}
