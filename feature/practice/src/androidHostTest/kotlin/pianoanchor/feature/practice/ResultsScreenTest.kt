package pianoanchor.feature.practice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    fun aTimedRunShowsFourTilesAndTheTempo() =
        runComposeUiTest {
            val timed =
                ready.copy(
                    tempoBpm = 72,
                    timing = TimingTile(31, TimingWorst(late = true, noteNumber = 6)),
                    length = LengthTile(86, LengthWorst(tooLong = false, noteNumber = 3, value = NoteValue.HALF)),
                    hesitation = HesitationTile(1, Pause(14, "D4")),
                )
            setContent { PianoAnchorTheme { ResultsScreen(timed, onBack = {}, onTryAgain = {}, onRetry = {}) } }

            onNodeWithText("C-D-E · run 1 · ♩ = 72").assertExists()
            onNodeWithContentDescription("Timing: within 31 milliseconds on average, late at note 6").assertExists()
            onNodeWithContentDescription("Note length: 86 percent of notes held right, half note held short")
                .assertExists()
            onNodeWithContentDescription("Hesitation: 1 pause, 1.4 s before D4").assertExists()
        }

    @Test
    fun tileDetailsNameTheWorstNote() =
        runComposeUiTest {
            var state by mutableStateOf<ResultsState>(ready)
            setContent { PianoAnchorTheme { ResultsScreen(state, onBack = {}, onTryAgain = {}, onRetry = {}) } }

            fun check(
                tile: ResultsState.Ready,
                description: String,
            ) {
                state = tile
                waitForIdle()
                onNodeWithContentDescription(description).assertExists()
            }
            val base = ready.copy(tempoBpm = 80)
            check(
                base.copy(timing = TimingTile(1, TimingWorst(late = false, noteNumber = 3))),
                "Timing: within 1 millisecond on average, early at note 3",
            )
            check(base.copy(timing = TimingTile(0, null)), "Timing: within 0 milliseconds on average, in time")
            check(
                base.copy(length = LengthTile(100, null)),
                "Note length: 100 percent of notes held right, every note held right",
            )
            check(
                base.copy(length = LengthTile(50, LengthWorst(true, 4, null))),
                "Note length: 50 percent of notes held right, note 4 held long",
            )
            check(
                base.copy(length = LengthTile(50, LengthWorst(true, 1, NoteValue.WHOLE))),
                "Note length: 50 percent of notes held right, whole note held long",
            )
            check(
                base.copy(length = LengthTile(50, LengthWorst(false, 1, NoteValue.QUARTER))),
                "Note length: 50 percent of notes held right, quarter note held short",
            )
            check(base.copy(hesitation = HesitationTile(0, null)), "Hesitation: no pauses")
            check(base.copy(hesitation = HesitationTile(2, null)), "Hesitation: 2 pauses")
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
