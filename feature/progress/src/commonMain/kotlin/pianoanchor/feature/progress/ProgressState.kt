package pianoanchor.feature.progress

import kotlinx.datetime.LocalDate
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Passage
import pianoanchor.core.model.Skill
import pianoanchor.engine.progress.LaneDot
import pianoanchor.engine.session.WeekStatus

/**
 * What the Progress tab shows. [week] is null until it is read (or when the store can't be); [minutesByDay] has seven
 * entries, Monday to Sunday of [week]'s week, and [todayIndex] is today's place in it (0 is Monday). [learnedThisWeek]
 * are the skills that became learned since Monday. [compare] is null when no piece has two full runs on different days
 * (the card is then hidden, with no empty state). [playing] is the take that sounds now.
 */
public data class ProgressState(
    val week: WeekStatus? = null,
    val minutesByDay: List<Int> = emptyList(),
    val learnedThisWeek: List<Skill> = emptyList(),
    val compare: Compare? = null,
    val playing: Take? = null,
    val storageFailed: Boolean = false,
    val todayIndex: Int = 0,
)

/**
 * "Day 1 vs today" for [passage]: its [day1] (first full run) and [today] (latest full run) takes, started on
 * [day1Date] and [todayDate] (local calendar days, which differ), each with the dots of its note-timing lane.
 */
public data class Compare(
    val passage: Passage,
    val day1: Attempt,
    val today: Attempt,
    val day1Date: LocalDate,
    val todayDate: LocalDate,
    val day1Lane: List<LaneDot> = emptyList(),
    val todayLane: List<LaneDot> = emptyList(),
)

/** Which of the two takes of [Compare]. */
public enum class Take { DAY_1, TODAY }
