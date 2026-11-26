package pianoanchor.core.model

import kotlinx.datetime.LocalDate

/** Minutes of practice the learner wants per day: one of three choices, as the settings screen offers them. */
@Suppress("MagicNumber") // The three minute counts are the choices themselves.
public enum class DailyBudget(
    public val minutes: Int,
) {
    MIN_15(15),
    MIN_20(20),
    MIN_30(30),
}

/**
 * The weekly practice goal a new learner starts with, in days: four days a week is a habit that fits a working week
 * and leaves rest days.
 */
public const val DEFAULT_WEEKLY_GOAL_DAYS: Int = 4

private const val MAX_WEEKLY_GOAL_DAYS = 7

/** The weekly goals the settings allow, in days: at least one day, at most every day of the week. */
public val WEEKLY_GOAL_DAYS_RANGE: IntRange = 1..MAX_WEEKLY_GOAL_DAYS

/**
 * The learner's preferences. [dailyBudget] is the practice time per day, [weeklyGoalDays] the days per week to
 * practise (in [WEEKLY_GOAL_DAYS_RANGE]), [clickOn] whether the metronome clicks in timed runs and [updateCheckOn]
 * whether the app checks once a day for a new version. [onboarded] is set once the first-launch onboarding is
 * finished, so the app opens on Today from then on. The update check keeps its own state here: [lastUpdateCheck] is the
 * learner day of the last request (at most one a day), [latestRelease] the newest release it has seen and
 * [dismissedUpdate] the version whose banner the learner dismissed with "Not now". [strictness] scales the timing
 * tolerance and the grading bands of the next run. [lastPiano] is the piano connected last through onboarding or
 * Pianos, reconnected when Today opens; null when none or forgotten. [lessonSteps] is the step each unfinished lesson
 * was left at, by skill id (a finished lesson has no entry). [pedalControlOn] lets a tap of the sustain pedal act as
 * "continue" outside a run.
 */
public data class Settings(
    val dailyBudget: DailyBudget = DailyBudget.MIN_20,
    val weeklyGoalDays: Int = DEFAULT_WEEKLY_GOAL_DAYS,
    val clickOn: Boolean = true,
    val updateCheckOn: Boolean = true,
    val onboarded: Boolean = false,
    val lastUpdateCheck: LocalDate? = null,
    val latestRelease: Release? = null,
    val dismissedUpdate: String? = null,
    val strictness: Strictness = Strictness.STANDARD,
    /** The piano connected last through onboarding or Pianos, reconnected on start; null when none or forgotten. */
    val lastPiano: MidiDevice? = null,
    /** The lesson step each unfinished course lesson stopped at, by skill id; absent = start at WHY. */
    val lessonSteps: Map<String, LessonStep> = emptyMap(),
    val pedalControlOn: Boolean = true,
) {
    init {
        require(weeklyGoalDays in WEEKLY_GOAL_DAYS_RANGE) {
            "weeklyGoalDays $weeklyGoalDays is outside $WEEKLY_GOAL_DAYS_RANGE"
        }
    }
}
