package pianoanchor.feature.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.core.model.DailyBudget
import pianoanchor.core.model.Settings
import pianoanchor.ui.shell.PianoAnchorTheme
import kotlin.test.assertEquals

/** The settings screen's controls and their accessibility labels, on Robolectric. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreenTest {
    private val calls = mutableListOf<String>()
    private val actions =
        SettingsActions(
            onBack = { calls += "back" },
            onDailyBudget = { calls += "budget $it" },
            onWeeklyGoalStep = { calls += "step $it" },
            onClickOn = { calls += "click $it" },
            onUpdateCheckOn = { calls += "update $it" },
            onDismissError = { calls += "dismiss" },
        )

    @Test
    fun everyControlReportsItsChange() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { SettingsScreen(SettingsState(Settings(weeklyGoalDays = 4)), actions) } }

            onNodeWithContentDescription("Back").performClick()
            onNodeWithContentDescription("30 minutes").performClick()
            onNodeWithContentDescription("Fewer days").performClick()
            onNodeWithContentDescription("More days").performClick()
            onNodeWithText("Click in timed runs").performClick()
            onNodeWithText("Check for updates").performClick()

            assertEquals(
                listOf("back", "budget ${DailyBudget.MIN_30}", "step -1", "step 1", "click false", "update false"),
                calls,
            )
        }

    @Test
    fun theUpdateCheckSaysWhatItSends() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { SettingsScreen(SettingsState(), actions) } }

            onNodeWithText("Checks GitHub once a day for a new version. Sends nothing about you.").assertExists()
            onNodeWithText("4 days a week").assertExists()
        }

    @Test
    fun theStepperStopsAtOneAndSeven() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { SettingsScreen(SettingsState(Settings(weeklyGoalDays = 1)), actions) } }
            onNodeWithText("1 day a week").assertExists()
            onNodeWithContentDescription("Fewer days").assertIsNotEnabled()
            onNodeWithContentDescription("More days").assertIsEnabled()
        }

    @Test
    fun theStepperStopsAtSeven() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { SettingsScreen(SettingsState(Settings(weeklyGoalDays = 7)), actions) } }
            onNodeWithContentDescription("More days").assertIsNotEnabled()
        }

    @Test
    fun theErrorCardCanBeDismissed() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { SettingsScreen(SettingsState(storageFailed = true), actions) } }

            onNodeWithText("Couldn't save the settings. Try again.").assertExists()
            onNodeWithText("Dismiss").performClick()

            assertEquals(listOf("dismiss"), calls)
        }
}

/** Android API level the tests render with (the newest Robolectric supports). */
private const val SDK = 36
