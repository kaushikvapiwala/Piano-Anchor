package pianoanchor.feature.progress

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.core.model.Skill
import pianoanchor.ui.shell.PianoAnchorTheme
import kotlin.test.assertEquals

/** The Progress tab in landscape: the rail, the week, the bars, what was learned and "Day 1 vs today". */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land")
class ProgressScreenTest {
    @Test
    fun theRailShowsProgressSelected() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { ProgressScreen(STATE, ProgressActions()) } }

            // The rail label and the heading.
            onAllNodesWithText("Progress").assertCountEquals(2)
            onNodeWithText("Songs").assertIsDisplayed()
        }

    @Test
    fun theWeekCardAndTheBarsAreReadAloud() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { ProgressScreen(STATE, ProgressActions()) } }

            onNodeWithText("This week · 3 of 4 days").assertIsDisplayed()
            onNodeWithText("2-week streak").assertIsDisplayed()
            onNodeWithContentDescription("Monday, 12 minutes").assertExists()
            onNodeWithContentDescription("Tuesday, no practice").assertExists()
            onNodeWithContentDescription("Wednesday (today), 7 minutes").assertExists()
        }

    @Test
    fun aDayWithoutPracticeShowsADashAndTodayWithoutOneSaysSo() =
        runComposeUiTest {
            val state = STATE.copy(minutesByDay = listOf(0, 0, 0, 0, 0, 0, 0))
            setContent { PianoAnchorTheme { ProgressScreen(state, ProgressActions()) } }

            onAllNodesWithText("–").assertCountEquals(0) // merged into the column descriptions
            onNodeWithContentDescription("Wednesday (today), no practice yet").assertExists()
        }

    @Test
    fun theLearnedCardCapsAtFiveAndCountsTheRest() =
        runComposeUiTest {
            val skills = (1..7).map { Skill("s$it", "Skill $it", emptySet()) }
            setContent { PianoAnchorTheme { ProgressScreen(STATE.copy(learnedThisWeek = skills), ProgressActions()) } }

            onNodeWithText("Skill 5").assertExists()
            onAllNodesWithText("Skill 6").assertCountEquals(0)
            onNodeWithText("+2 more").assertExists()
        }

    @Test
    fun theCompareCardIsHiddenWithoutTwoTakes() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { ProgressScreen(STATE.copy(compare = null), ProgressActions()) } }

            onAllNodesWithText("Day 1 vs today").assertCountEquals(0)
        }

    @Test
    fun thePlayButtonsToggleBetweenPlayAndStop() =
        runComposeUiTest {
            var state by mutableStateOf(STATE)
            val actions =
                ProgressActions(
                    onPlay = { state = state.copy(playing = it) },
                    onStop = { state = state.copy(playing = null) },
                )
            setContent { PianoAnchorTheme { ProgressScreen(state, actions) } }
            val title = COMPARE.passage.title

            onNodeWithText("Day 1 vs today").assertIsDisplayed()
            onNodeWithText(title).assertIsDisplayed()
            onNodeWithContentDescription("Play Day 1, 3 Nov").performClick()
            assertEquals(Take.DAY_1, state.playing)
            onNodeWithContentDescription("Stop Day 1, 3 Nov").assertExists()
            onNodeWithContentDescription("Play Today, 18 Nov").performClick()
            assertEquals(Take.TODAY, state.playing)
            onNodeWithContentDescription("Play Day 1, 3 Nov").assertExists()
            onNodeWithContentDescription("Stop Today, 18 Nov").performClick()
            assertEquals(null, state.playing)
        }

    @Test
    fun theLaneSaysHowManyNotesWereOffTheBeat() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { ProgressScreen(STATE, ProgressActions()) } }

            onNodeWithContentDescription("Day 1: 4 notes, 2 off the beat").assertExists()
            onNodeWithContentDescription("Today: 4 notes, 0 off the beat").assertExists()
        }

    @Test
    fun aStorageErrorShowsTheBannerWithRetry() =
        runComposeUiTest {
            var retried = false
            val state = ProgressState(storageFailed = true)
            setContent { PianoAnchorTheme { ProgressScreen(state, ProgressActions(onRetry = { retried = true })) } }

            onNodeWithText("Couldn't load your progress.").assertIsDisplayed()
            onNodeWithText("Retry").performClick()
            assertEquals(true, retried)
        }

    @Test
    fun theRailLeadsToTheOtherTabs() =
        runComposeUiTest {
            val visited = mutableListOf<String>()
            val actions =
                ProgressActions(
                    onToday = { visited += "today" },
                    onSongs = { visited += "songs" },
                    onPianos = { visited += "pianos" },
                    onSettings = { visited += "settings" },
                )
            setContent { PianoAnchorTheme { ProgressScreen(STATE, actions) } }

            onAllNodesWithText("Today").onFirst().performClick()
            onNodeWithText("Songs").performClick()
            onNodeWithText("Pianos").performClick()
            onNodeWithText("Settings").performClick()
            assertEquals(listOf("today", "songs", "pianos", "settings"), visited)
        }
}
