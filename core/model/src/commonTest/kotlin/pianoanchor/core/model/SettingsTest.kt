package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SettingsTest {
    @Test
    fun defaultsAreTwentyMinutesFourDaysWithClickAndUpdateCheckOnAndNotOnboarded() {
        assertEquals(
            Settings(DailyBudget.MIN_20, 4, clickOn = true, updateCheckOn = true, onboarded = false),
            Settings(),
        )
    }

    @Test
    fun theUpdateCheckStartsWithNothingSeen() {
        val settings = Settings()
        assertEquals(null, settings.lastUpdateCheck)
        assertEquals(null, settings.latestRelease)
        assertEquals(null, settings.dismissedUpdate)
        assertEquals(Release("0.2.0", "https://example.org"), Release("0.2.0", "https://example.org"))
    }

    @Test
    fun strictnessDefaultsToStandard() {
        assertEquals(Strictness.STANDARD, Settings().strictness)
        assertEquals(Strictness.STRICT, Settings(strictness = Strictness.STRICT).strictness)
    }

    @Test
    fun noPianoIsRememberedByDefault() {
        assertNull(Settings().lastPiano)
        val piano = MidiDevice("ble:AA:BB", "FP-30X")
        assertEquals(piano, Settings(lastPiano = piano).lastPiano)
    }

    @Test
    fun noLessonHasASavedStepByDefault() {
        assertEquals(emptyMap(), Settings().lessonSteps)
        assertEquals(
            LessonStep.TRY,
            Settings(lessonSteps = mapOf("middle-c" to LessonStep.TRY)).lessonSteps["middle-c"],
        )
        assertEquals(listOf("WHY", "SEE", "TRY", "USE", "RECAP"), LessonStep.entries.map { it.name })
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
