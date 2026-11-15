package pianoanchor.feature.onboarding

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.core.model.MidiDevice
import pianoanchor.ui.shell.PianoAnchorTheme
import kotlin.test.assertEquals

/** The onboarding screen's texts and buttons, on Robolectric. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w411dp-h891dp-xxhdpi")
class OnboardingScreenTest {
    private val piano = MidiDevice("usb:Roland/FP-30X", "FP-30X")
    private val calls = mutableListOf<String>()
    private val actions =
        OnboardingActions(
            onOpenPianoList = { calls += "list" },
            onReplay = { calls += "replay" },
            onContinue = { calls += "continue" },
            onStart = { calls += "start $it" },
        )

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: OnboardingState) =
        setContent {
            PianoAnchorTheme {
                OnboardingScreen(state, actions, calibration = { Text("calibrating ${it.name}") })
            }
        }

    @Test
    fun connectShowsTheSearchThenThePianoListAfterTheHint() =
        runComposeUiTest {
            show(OnboardingState(pianoListHint = true))

            onNodeWithText("Press any key on your piano").assertExists()
            onNodeWithText("Searching for your piano…").assertExists()
            onNodeWithText("Step 1 of 4").assertExists()
            onNodeWithText("Not found? Open the piano list").performClick()

            assertEquals(listOf("list"), calls)
        }

    @Test
    fun theConnectedPianoIsNamed() =
        runComposeUiTest {
            show(OnboardingState(OnboardingStep.ECHO, piano, echo = EchoPhase.LISTENING, keysHighlighted = true))

            onNodeWithText("Connected · FP-30X").assertExists()
            onNodeWithText("Play C, E, G").assertExists()
            onNodeWithContentDescription("C, play this key").assertExists()
            onNodeWithContentDescription("D").assertExists()
        }

    @Test
    fun aMissOffersReplayAndSkipOnlyAfterThreeMisses() =
        runComposeUiTest {
            show(OnboardingState(OnboardingStep.ECHO, piano, echo = EchoPhase.MISSED, misses = 1))
            onNodeWithText("Close! Start again from C.").assertExists()
            onNodeWithText("Skip").assertDoesNotExist()
            onNodeWithText("Replay").performClick()

            assertEquals(listOf("replay"), calls)
        }

    @Test
    fun skipAppearsAfterThreeMisses() =
        runComposeUiTest {
            show(OnboardingState(OnboardingStep.ECHO, piano, echo = EchoPhase.MISSED, misses = 3))

            onNodeWithText("Skip").performClick()

            assertEquals(listOf("continue"), calls)
        }

    @Test
    fun successContinuesToTheCalibration() =
        runComposeUiTest {
            show(OnboardingState(OnboardingStep.ECHO, piano, echo = EchoPhase.PASSED))

            onNodeWithText("You just played your first chord by ear.").assertExists()
            onNodeWithText("Continue").performClick()

            assertEquals(listOf("continue"), calls)
        }

    @Test
    fun theCalibrationStepHostsTheCalibrationScreen() =
        runComposeUiTest {
            show(OnboardingState(OnboardingStep.CALIBRATE, piano))

            onNodeWithText("calibrating FP-30X").assertExists()
        }

    @Test
    fun theStartPointOffersTheUnits() =
        runComposeUiTest {
            show(OnboardingState(OnboardingStep.START_POINT, piano))

            onNodeWithText("Start at the beginning").performClick()
            onNodeWithText("Unit 1 · Five-finger tunes").performClick()
            onNodeWithText("Unit 2 · Both hands").performClick()

            assertEquals(listOf("start BEGINNING", "start UNIT_1", "start UNIT_2"), calls)
        }

    @Test
    fun aFailedSaveIsSaid() =
        runComposeUiTest {
            show(OnboardingState(OnboardingStep.START_POINT, piano, storageFailed = true))

            onNodeWithText("Couldn't save your start point. Tap one to try again.").assertExists()
        }
}

private const val SDK = 36
