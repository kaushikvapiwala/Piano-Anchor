package pianoanchor.feature.onboarding

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
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
import pianoanchor.core.model.MidiDevice
import pianoanchor.ui.shell.PianoAnchorTheme
import kotlin.test.assertEquals

/** The onboarding screen's texts and buttons, on Robolectric. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land-xxhdpi")
class OnboardingScreenTest {
    private val piano = MidiDevice("usb:Roland/FP-30X", "FP-30X")
    private val calls = mutableListOf<String>()
    private val actions =
        OnboardingActions(
            onOpenPianoList = { calls += "list" },
            onReplay = { calls += "replay" },
            onContinue = { calls += "continue" },
            onTestMe = { calls += "test" },
            onStartAtBeginning = { calls += "beginning" },
            onStopHere = { calls += "stop" },
            onNextPiece = { calls += "next" },
        )

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: OnboardingState) =
        setContent {
            PianoAnchorTheme {
                OnboardingScreen(state, actions) { Text("run of ${it.passageId}") }
            }
        }

    private val piece = PlacementPiece(3, 6, "builtin:held-bass", "Hold and play")

    @Test
    fun connectShowsTheSearchThenThePianoListAfterTheHint() =
        runComposeUiTest {
            show(OnboardingState(pianoListHint = true))

            onNodeWithText("Press any key on your piano").assertExists()
            onNodeWithText("Searching for your piano…").assertExists()
            onNodeWithText("Step 1 of 3").assertExists()
            onNodeWithText("Press any key to continue.").assertDoesNotExist()
            onNodeWithText(SLEEP_LINE).assertExists()
            onNodeWithText("Not found? Open the piano list").performClick()

            assertEquals(listOf("list"), calls)
        }

    @Test
    fun aListedPianoIsNamedWhileWaitingForAKey() =
        runComposeUiTest {
            show(OnboardingState(listed = piano))

            onNodeWithText("Press any key on your piano").assertExists()
            onNodeWithText("Connected · FP-30X").assertExists()
            onNodeWithText("Searching for your piano…").assertDoesNotExist()
            onNodeWithText("Press any key to continue.").assertExists()
            onNodeWithText(SLEEP_LINE).assertDoesNotExist()
        }

    @Test
    fun aPickedPianoSaysAKeyPressContinues() =
        runComposeUiTest {
            show(OnboardingState(device = piano))

            onNodeWithText("Press any key to continue.").assertExists()
        }

    @Test
    fun searchingDoesNotSayToPressAKeyToContinue() =
        runComposeUiTest {
            show(OnboardingState())

            onNodeWithText("Press any key to continue.").assertDoesNotExist()
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
    fun successContinuesToTheStartPoint() =
        runComposeUiTest {
            show(OnboardingState(OnboardingStep.ECHO, piano, echo = EchoPhase.PASSED))

            onNodeWithText("You just played your first chord by ear.").assertExists()
            onNodeWithText("Continue").performClick()

            assertEquals(listOf("continue"), calls)
        }

    @Test
    fun theStartPointIsStepThreeOfThree() =
        runComposeUiTest {
            show(OnboardingState(OnboardingStep.START_POINT, piano))

            onNodeWithText("Step 3 of 3").assertExists()
        }

    @Test
    fun theStartPointOffersTheTestAndTheBeginning() =
        runComposeUiTest {
            show(OnboardingState(OnboardingStep.START_POINT, piano))

            onNodeWithText("Test me").performScrollTo().performClick()
            onNodeWithText("Start from the beginning").performScrollTo().performClick()
            onNodeWithText("Unit 1 · Five-finger tunes").assertDoesNotExist()

            assertEquals(listOf("test", "beginning"), calls)
        }

    @Test
    fun aTestPieceShowsItsHeaderAndHostsTheRun() =
        runComposeUiTest {
            show(OnboardingState(OnboardingStep.START_POINT, piano, piece = piece))

            onNodeWithText("Piece 3 of 6 · Hold and play").assertExists()
            onNodeWithText("run of builtin:held-bass").assertExists()
            onNodeWithText("Stop here").performClick()

            assertEquals(listOf("stop"), calls)
        }

    @Test
    fun aPassedPieceSaysSoAndOffersTheNext() =
        runComposeUiTest {
            show(OnboardingState(OnboardingStep.START_POINT, piano, piece = piece, verdict = PlacementVerdict.PASSED))

            onNodeWithText("Passed, next piece").assertExists()
            onNodeWithText("run of builtin:held-bass").assertDoesNotExist()
            onNodeWithText("Next piece").performClick()

            assertEquals(listOf("next"), calls)
        }

    @Test
    fun theLastVerdictSaysWhereWeStart() =
        runComposeUiTest {
            show(
                OnboardingState(
                    OnboardingStep.START_POINT,
                    piano,
                    piece = piece,
                    verdict = PlacementVerdict.START_HERE,
                ),
            )

            onNodeWithText("That's where we'll start").assertExists()
            onNodeWithText("Continue").performClick()

            assertEquals(listOf("next"), calls)
        }

    @Test
    fun aFailedSaveIsSaid() =
        runComposeUiTest {
            show(OnboardingState(OnboardingStep.START_POINT, piano, storageFailed = true))

            onNodeWithText("Couldn't save your start point. Tap again to retry.").assertExists()
        }

    @Test
    fun atDoubleFontScaleTheLastStartPointScrollsIntoView() =
        runComposeUiTest {
            setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = LARGE_FONT_SCALE)) {
                    PianoAnchorTheme {
                        OnboardingScreen(
                            OnboardingState(OnboardingStep.START_POINT, piano, storageFailed = true),
                            actions,
                        )
                    }
                }
            }

            onNodeWithText("Start from the beginning").performScrollTo().assertIsDisplayed()
        }
}

private const val LARGE_FONT_SCALE = 2f

private const val SDK = 36

private const val SLEEP_LINE =
    "Pianos turn Bluetooth off when they sit idle. Turn yours off and on again, then wait a few seconds."
