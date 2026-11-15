package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SettingsTest {
    @Test
    fun defaultsAreTwentyMinutesFourDaysWithClickAndUpdateCheckOn() {
        assertEquals(Settings(DailyBudget.MIN_20, 4, clickOn = true, updateCheckOn = true), Settings())
    }

    @Test
    fun budgetsKnowTheirMinutes() {
        assertEquals(listOf(15, 20, 30), DailyBudget.entries.map { it.minutes })
    }

    @Test
    fun `rejects weekly goal 0 and 8`() {
        assertFailsWith<IllegalArgumentException> { Settings(weeklyGoalDays = 0) }
        assertFailsWith<IllegalArgumentException> { Settings(weeklyGoalDays = 8) }
    }

    @Test
    fun acceptsWeeklyGoalOneAndSeven() {
        assertEquals(1, Settings(weeklyGoalDays = 1).weeklyGoalDays)
        assertEquals(7, Settings(weeklyGoalDays = 7).weeklyGoalDays)
    }
}
