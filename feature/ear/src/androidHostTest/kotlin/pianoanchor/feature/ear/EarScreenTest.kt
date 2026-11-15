package pianoanchor.feature.ear

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.core.model.EarChoice
import pianoanchor.ui.shell.PianoAnchorTheme
import kotlin.test.assertEquals

/** The ear screen's texts and buttons, on Robolectric. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w411dp-h891dp-xxhdpi")
class EarScreenTest {
    private val calls = mutableListOf<String>()
    private val actions =
        EarActions(
            onBack = { calls += "back" },
            onReplay = { calls += "replay" },
            onAnswer = { calls += "answer $it" },
            onShowMe = { calls += "show" },
            onNext = { calls += "next" },
            onDone = { calls += "done" },
        )

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: EarState) =
        setContent { PianoAnchorTheme { EarScreen(state, actions) } }

    @Test
    fun anIdentifyItemShowsItsCounterAndTwoAnswerButtons() =
        runComposeUiTest {
            show(IDENTIFY_STATE)

            onNodeWithText("Item 2 of 4").assertExists()
            onNodeWithText("Skill 1 of 3").assertExists()
            onNodeWithText("Higher or lower").assertExists()
            onNodeWithText("Listen, then choose what you heard.").assertExists()
            onNodeWithText("Higher").assertHeightIsAtLeast(48.dp).performClick()
            onNodeWithText("Lower").assertHeightIsAtLeast(48.dp).performClick()

            assertEquals(listOf("answer ${EarChoice.HIGHER}", "answer ${EarChoice.LOWER}"), calls)
        }

    @Test
    fun anItemThatSoundsSaysSo() =
        runComposeUiTest {
            show(IDENTIFY_STATE.copy(playing = true))

            onNodeWithText("Listening…").assertExists()
        }

    @Test
    fun replayShowsHowManyAreLeftAndIsOffAtZero() =
        runComposeUiTest {
            show(IDENTIFY_STATE)
            onNodeWithText("Replay (2 left)").assertHeightIsAtLeast(48.dp).performClick()
            assertEquals(listOf("replay"), calls)
        }

    @Test
    fun replayIsDisabledWhenNoneAreLeft() =
        runComposeUiTest {
            show(IDENTIFY_STATE.copy(replaysLeft = 0))

            onNodeWithText("No replays left").assertIsNotEnabled()
        }

    @Test
    fun aWrongAnswerSaysListenAgainAndOffersShowMe() =
        runComposeUiTest {
            show(IDENTIFY_STATE.copy(feedback = EarFeedback.TRY_AGAIN))

            onNodeWithText("Not quite. Listen again.").assertExists()
            onNodeWithText("Replay (2 left)").assertIsEnabled()
            onNodeWithText("Show me").performClick()

            assertEquals(listOf("show"), calls)
        }

    @Test
    fun theRevealedAnswerIsMarkedAndNextMovesOn() =
        runComposeUiTest {
            show(IDENTIFY_STATE.copy(feedback = EarFeedback.REVEALED))

            onNodeWithText("Here is the answer. Listen once more.").assertExists()
            onNodeWithContentDescription("Higher, the answer").assertExists()
            onNodeWithText("Show me").assertDoesNotExist()
            onNodeWithText("Next").performClick()

            assertEquals(listOf("next"), calls)
        }

    @Test
    fun anEchoItemShowsTheKeysAndMarksTheAnswerWhenShown() =
        runComposeUiTest {
            show(ECHO_STATE)
            onNodeWithText("Listen, then play it back on your piano.").assertExists()
            onNodeWithContentDescription("C").assertExists()
            onNodeWithContentDescription("C, part of the answer").assertDoesNotExist()
        }

    @Test
    fun theShownEchoAnswerMarksItsKeys() =
        runComposeUiTest {
            show(ECHO_STATE.copy(feedback = EarFeedback.REVEALED))

            onNodeWithContentDescription("C, part of the answer").assertExists()
            onNodeWithContentDescription("E, part of the answer").assertExists()
            onNodeWithContentDescription("D, part of the answer").assertExists()
            onNodeWithContentDescription("F").assertExists()
        }

    @Test
    fun backIsOffered() =
        runComposeUiTest {
            show(IDENTIFY_STATE)
            onNodeWithContentDescription("Back").performClick()

            assertEquals(listOf("back"), calls)
        }

    @Test
    fun theSummaryNamesTheSkillsAndFirstTries() =
        runComposeUiTest {
            show(EarState(skillCount = 3, summary = EarSummary(3, 10, 12)))

            onNodeWithText("Ear practice done").assertExists()
            onNodeWithText("3 skills · 10 of 12 first try").assertExists()
            onNodeWithText("Done").assertHeightIsAtLeast(48.dp).performClick()

            assertEquals(listOf("done"), calls)
        }

    @Test
    fun aSingleSkillIsNotPlural() =
        runComposeUiTest {
            show(EarState(skillCount = 1, summary = EarSummary(1, 4, 4), storageFailed = true))

            onNodeWithText("1 skill · 4 of 4 first try").assertExists()
            onNodeWithText("Couldn't save some results.").assertExists()
        }

    @Test
    fun everyChoiceHasAName() =
        runComposeUiTest {
            val names =
                mapOf(
                    EarChoice.STEP to "Step",
                    EarChoice.SKIP to "Skip",
                    EarChoice.MAJOR_THIRD to "Major third",
                    EarChoice.MINOR_THIRD to "Minor third",
                    EarChoice.FOURTH to "Fourth",
                    EarChoice.FIFTH to "Fifth",
                    EarChoice.MAJOR_CHORD to "Major chord",
                    EarChoice.MINOR_CHORD to "Minor chord",
                )
            var state by mutableStateOf(IDENTIFY_STATE)
            setContent { PianoAnchorTheme { EarScreen(state, actions) } }
            for ((choice, name) in names) {
                val other = if (choice == EarChoice.STEP) EarChoice.SKIP else EarChoice.STEP
                state = IDENTIFY_STATE.copy(exercise = IDENTIFY.copy(choices = listOf(choice, other), answer = choice))
                onNodeWithText(name).assertExists()
            }
        }
}

private const val SDK = 36
