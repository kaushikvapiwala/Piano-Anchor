package pianoanchor.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
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
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land-xxhdpi")
class SettingsScreenTest {
    private val calls = mutableListOf<String>()
    private val actions =
        SettingsActions(
            onBack = { calls += "back" },
            onDailyBudget = { calls += "budget $it" },
            onWeeklyGoalStep = { calls += "step $it" },
            onClickOn = { calls += "click $it" },
            onPedalControlOn = { calls += "pedal $it" },
            onNoteNames = { calls += "names $it" },
            onStrictness = { calls += "strictness $it" },
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
            onNodeWithText("Click in timed runs").performScrollTo().performClick()
            onNodeWithText("Pedal to continue").performScrollTo().performClick()
            onNodeWithText("Off").performScrollTo().performClick()
            onNodeWithText("Strict").performScrollTo().performClick()
            onNodeWithText("Check for updates").performScrollTo().performClick()

            assertEquals(
                listOf(
                    "back",
                    "budget ${DailyBudget.MIN_30}",
                    "step -1",
                    "step 1",
                    "click false",
                    "pedal false",
                    "names OFF",
                    "strictness STRICT",
                    "update false",
                ),
                calls,
            )
        }

    @Test
    fun strictnessOffersThreePresetsWithADescriptionEach() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { SettingsScreen(SettingsState(), actions) } }

            onNodeWithText("Strictness").assertExists()
            onNodeWithText("More room for timing and slips").assertExists()
            onNodeWithText("The default").assertExists()
            onNodeWithText("Tight timing, few slips").assertExists()
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

    @Test
    fun atDoubleFontScaleTheLastRowScrollsIntoView() =
        runComposeUiTest {
            setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = LARGE_FONT_SCALE)) {
                    PianoAnchorTheme { SettingsScreen(SettingsState(storageFailed = true), actions) }
                }
            }

            onNodeWithText("Check for updates").performScrollTo().assertIsDisplayed()
        }
}

private const val LARGE_FONT_SCALE = 2f

/** Android API level the tests render with (the newest Robolectric supports). */
private const val SDK = 36
