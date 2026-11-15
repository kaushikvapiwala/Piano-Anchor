package pianoanchor.feature.practice

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.engine.calibration.CalibrationResult
import pianoanchor.ui.shell.PianoAnchorTheme
import kotlin.test.assertEquals

/** The calibration screen's text, buttons and accessibility descriptions, on Robolectric. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land")
class CalibrationScreenTest {
    private val ms = 1_000_000L

    private fun done(
        result: CalibrationResult?,
        saveFailed: Boolean = false,
    ) = CalibrationState(phase = CalibrationPhase.DONE, result = result, saveFailed = saveFailed)

    @Test
    fun startShowsTheTitleAndInstructionAndStarts() =
        runComposeUiTest {
            var starts = 0
            setContent { PianoAnchorTheme { CalibrationScreen(CalibrationState(), { starts++ }, {}, {}) } }

            onNodeWithText("Calibrate timing").assertExists()
            onNodeWithText("Play any key on each click. 16 clicks, about 16 seconds.").assertExists()
            onNodeWithText("Start").performClick()

            assertEquals(1, starts)
        }

    @Test
    fun runningShowsGetReadyThenTheCounterWithADescription() =
        runComposeUiTest {
            val state = mutableStateOf(CalibrationState(phase = CalibrationPhase.RUNNING, click = 2))
            setContent { PianoAnchorTheme { CalibrationScreen(state.value, {}, {}, {}) } }
            onNodeWithText("Get ready").assertExists()

            state.value = CalibrationState(phase = CalibrationPhase.RUNNING, click = 11, pulse = true, countedTaps = 7)
            onNodeWithText("Play a key on each click").assertExists()
            onNodeWithText("7 / 12").assertExists()
            onNodeWithContentDescription("7 of 12 taps").assertExists()
        }

    @Test
    fun anAcceptedResultSaysHowMuchAndOffersDone() =
        runComposeUiTest {
            var dones = 0
            val state = mutableStateOf(done(CalibrationResult.Accepted(38 * ms, 5 * ms)))
            setContent { PianoAnchorTheme { CalibrationScreen(state.value, {}, { dones++ }, {}) } }

            onNodeWithText("Your piano answers 38 ms after the click. Saved.").assertExists()
            onNodeWithText("Done").performClick()
            assertEquals(1, dones)

            state.value = done(CalibrationResult.Accepted(-25 * ms, 5 * ms))
            onNodeWithText("Your piano answers 25 ms before the click. Saved.").assertExists()
        }

    @Test
    fun otherResultsGiveASpecificLineWithTryAgainAndCancel() =
        runComposeUiTest {
            var tries = 0
            var cancels = 0
            val state = mutableStateOf(done(CalibrationResult.TooUneven(60 * ms)))
            setContent { PianoAnchorTheme { CalibrationScreen(state.value, { tries++ }, {}, { cancels++ }) } }

            onNodeWithText("Taps were uneven").assertExists()
            state.value = done(CalibrationResult.TooFewTaps(3))
            onNodeWithText("Too few taps").assertExists()
            state.value = done(CalibrationResult.OutOfRange(450 * ms))
            onNodeWithText("That's more than 0.4 s: check the connection").assertExists()
            state.value = done(CalibrationResult.Accepted(38 * ms, 0), saveFailed = true)
            onNodeWithText("Couldn't save the result.").assertExists()

            onNodeWithText("Try again").performClick()
            onNodeWithText("Cancel").performClick()
            assertEquals(1, tries)
            assertEquals(1, cancels)
        }

    @Test
    fun aDroppedPianoShowsTheDisconnectedCard() =
        runComposeUiTest {
            var cancels = 0
            setContent {
                PianoAnchorTheme { CalibrationScreen(CalibrationState(disconnected = true), {}, {}, { cancels++ }) }
            }

            onNodeWithText("Piano disconnected").assertExists()
            onNodeWithText("Cancel").performClick()
            assertEquals(1, cancels)
        }
}
