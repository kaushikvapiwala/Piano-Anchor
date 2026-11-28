package pianoanchor.engine.session

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import pianoanchor.engine.Tunables
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeekPauseTest {
    // Wednesday 2026-11-18; the week began on Monday the 16th.
    private val today = LocalDate(2026, 11, 18)
    private val monday = LocalDate(2026, 11, 16)

    private fun weeksBefore(
        weeks: Int,
        day: LocalDate = monday,
    ) = day.minus(weeks, DateTimeUnit.WEEK)

    /** [days] practice days in the week that began [weeksAgo] weeks before this one. */
    private fun played(
        weeksAgo: Int,
        days: Int,
    ) = (0 until days).map { weeksBefore(weeksAgo).plus(it, DateTimeUnit.DAY) }

    private fun status(
        played: Set<LocalDate> = emptySet(),
        paused: Set<LocalDate> = emptySet(),
        goal: Int = 3,
        on: LocalDate = today,
    ) = WeekPause.status(on, played, goal, paused)

    @Test
    fun `meeting the goal adds this week to the streak`() {
        val last = played(1, 3) + played(2, 3)
        assertEquals(2, status(last.toSet()).streakWeeks)
        assertEquals(3, status((last + played(0, 3)).toSet()).streakWeeks)
    }

    @Test
    fun `a paused week counts as met whatever was played`() {
        val result = status(played(2, 3).toSet(), paused = setOf(weeksBefore(1)))

        assertEquals(2, result.streakWeeks)
        assertEquals(1, status(paused = setOf(monday)).streakWeeks)
    }

    @Test
    fun `an unmet unpaused week resets the streak to zero`() {
        assertEquals(0, status((played(1, 2) + played(2, 3)).toSet()).streakWeeks)
    }

    @Test
    fun `an unmet week this week keeps the streak of the weeks before`() {
        assertEquals(1, status(played(1, 3).toSet()).streakWeeks)
    }

    @Test
    fun `two pauses used leave none`() {
        val result = status(paused = setOf(weeksBefore(1), weeksBefore(3)))

        assertEquals(0, result.pausesLeft)
        assertFalse(result.canPause)
    }

    @Test
    fun `a pause used four weeks ago is back`() {
        val result = status(paused = setOf(weeksBefore(1), weeksBefore(Tunables.WEEK_PAUSE_REFILL_WEEKS)))

        assertEquals(1, result.pausesLeft)
        assertTrue(result.canPause)
    }

    @Test
    fun `a pause used three weeks ago is still used`() {
        assertEquals(1, status(paused = setOf(weeksBefore(3))).pausesLeft)
    }

    @Test
    fun `an already paused week can't be paused again`() {
        val result = status(paused = setOf(monday))

        assertTrue(result.paused)
        assertEquals(1, result.pausesLeft)
        assertFalse(result.canPause)
    }

    @Test
    fun `pause adds the Monday of this week`() {
        val result = status()

        assertEquals(setOf(monday), WeekPause.pause(today, result, emptySet()))
    }

    @Test
    fun `pause throws when the week can't be paused`() {
        val paused = setOf(monday)

        assertFailsWith<IllegalStateException> { WeekPause.pause(today, status(paused = paused), paused) }
    }

    @Test
    fun `the week starts on Monday and Sunday still pauses it`() {
        val sunday = LocalDate(2026, 11, 22)
        val onSunday = status(on = sunday)
        val onMonday = status(on = monday)

        assertEquals(monday, onSunday.weekStart)
        assertEquals(monday, onMonday.weekStart)
        assertTrue(onSunday.canPause)
        assertEquals(LocalDate(2026, 11, 23), status(on = LocalDate(2026, 11, 23)).weekStart)
    }

    @Test
    fun `days played count from Monday up to today`() {
        val result = status(setOf(monday.minus(1, DateTimeUnit.DAY), monday, today, today.plus(1, DateTimeUnit.DAY)))

        assertEquals(2, result.daysPlayed)
        assertEquals(3, result.goalDays)
    }

    @Test
    fun `a goal below one day is rejected`() {
        assertFailsWith<IllegalArgumentException> { status(goal = 0) }
    }
}
