package pianoanchor.feature.practice

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.core.model.Band
import pianoanchor.ui.shell.PianoAnchorTheme
import kotlin.test.assertEquals

/** The results screen's actions, on Robolectric. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land")
class ResultsScreenTest {
    private val ready =
        ResultsState.Ready(
            passageTitle = "C-D-E",
            runNumber = 1,
            band = Band.CLEAN,
            correctSteps = 3,
            stepCount = 3,
            wrongNotes = 0,
            incompleteChords = 0,
        )

    @Test
    fun tryAgainAndBackCallTheirActions() =
        runComposeUiTest {
            val calls = mutableListOf<String>()
            setContent {
                PianoAnchorTheme {
                    ResultsScreen(
                        ready,
                        onBack = { calls += "back" },
                        onTryAgain = { calls += "try again" },
                        onRetry = { calls += "retry" },
                    )
                }
            }

            onNodeWithText("Try again").performClick()
            onNodeWithContentDescription("Back").performClick()

            assertEquals(listOf("try again", "back"), calls)
        }

    @Test
    fun thePitchTileReadsAsOneSentence() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { ResultsScreen(ready, onBack = {}, onTryAgain = {}, onRetry = {}) } }

            onNodeWithContentDescription("Pitch: 3 of 3 correct, every note right").assertExists()
        }

    @Test
    fun unavailableOffersRetryAndBackToPianos() =
        runComposeUiTest {
            val calls = mutableListOf<String>()
            setContent {
                PianoAnchorTheme {
                    ResultsScreen(
                        ResultsState.Unavailable,
                        onBack = { calls += "back" },
                        onTryAgain = { calls += "try again" },
                        onRetry = { calls += "retry" },
                    )
                }
            }

            onNodeWithText("Couldn't load this run.").assertExists()
            onNodeWithText("Retry").performClick()
            onNodeWithText("Back to pianos").performClick()

            assertEquals(listOf("retry", "back"), calls)
        }
}
