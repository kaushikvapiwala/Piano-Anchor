package pianoanchor.feature.monitor

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.core.model.MidiDevice
import pianoanchor.ui.shell.PianoAnchorTheme
import kotlin.test.assertEquals

/** The monitor's Practice, Calibrate and back buttons: shown unless the composition root passes null. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land-xxhdpi")
class MonitorScreenTest {
    /** The left pane is the first scrollable: a lazy list, so scroll to the item rather than to its node. */
    private fun androidx.compose.ui.test.ComposeUiTest.scrollLeftPaneToTheKitButton() {
        onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("Save contribution kit"))
    }

    @Test
    fun practiceAndCalibrateAreShownByDefault() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { MonitorScreen(MonitorState(), MonitorActions()) } }

            onNodeWithText("Practice").assertExists()
            onNodeWithText("Calibrate timing").assertExists()
        }

    @Test
    fun nullCallbacksHideTheButtons() =
        runComposeUiTest {
            setContent {
                PianoAnchorTheme {
                    MonitorScreen(MonitorState(), MonitorActions(onPractice = null, onCalibrate = null))
                }
            }

            onNodeWithText("Practice").assertDoesNotExist()
            onNodeWithText("Calibrate timing").assertDoesNotExist()
        }

    @Test
    fun backButtonShowsOnlyWithACallbackAndGoesBack() =
        runComposeUiTest {
            var back = 0
            var actions by mutableStateOf(MonitorActions())
            setContent { PianoAnchorTheme { MonitorScreen(MonitorState(), actions) } }
            onNodeWithContentDescription("Back").assertDoesNotExist()

            actions = MonitorActions(onBack = { back++ })
            onNodeWithContentDescription("Back").performClick()

            assertEquals(1, back)
        }

    @Test
    fun forgetIsOnlyOnTheRememberedPianosRowAndForgets() =
        runComposeUiTest {
            val remembered = MidiDevice("ble:AA", "FP-30X")
            val other = MidiDevice("usb:1", "Digital Piano")
            var forgotten = 0
            setContent {
                PianoAnchorTheme {
                    MonitorScreen(
                        MonitorState(devices = listOf(other, remembered), lastPiano = remembered),
                        MonitorActions(onForget = { forgotten++ }),
                    )
                }
            }
            onAllNodesWithText("Forget").assertCountEquals(1)

            onNodeWithContentDescription("Forget FP-30X").performClick()

            assertEquals(1, forgotten)
        }

    @Test
    fun forgetIsHiddenWhenTheRememberedPianoIsNotListed() =
        runComposeUiTest {
            setContent {
                PianoAnchorTheme {
                    MonitorScreen(MonitorState(lastPiano = MidiDevice("ble:AA", "FP-30X")), MonitorActions())
                }
            }
            onNodeWithText("Forget").assertDoesNotExist()
        }

    @Test
    fun kitButtonIsDisabledWithoutARecording() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { MonitorScreen(MonitorState(), MonitorActions()) } }

            onNodeWithText("Save contribution kit").assertIsNotEnabled()
        }

    @Test
    fun kitButtonSavesAfterARecording() =
        runComposeUiTest {
            var saved = 0
            setContent {
                PianoAnchorTheme {
                    MonitorScreen(MonitorState(recordedCount = 3), MonitorActions(onSaveKit = { saved++ }))
                }
            }

            scrollLeftPaneToTheKitButton()
            onNodeWithText("Save contribution kit").assertIsEnabled().performClick()
            assertEquals(1, saved)
        }

    @Test
    fun atDoubleFontScaleTheLeftPaneScrollsToTheKitButton() =
        runComposeUiTest {
            val log = List(LOG_LINES) { LogLine("C4 ↑ 64", it * 10L) }
            setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = LARGE_FONT_SCALE)) {
                    PianoAnchorTheme { MonitorScreen(MonitorState(log = log), MonitorActions()) }
                }
            }

            scrollLeftPaneToTheKitButton()
            onNodeWithText("Save contribution kit").assertIsDisplayed()
        }
}

private const val LARGE_FONT_SCALE = 2f
private const val LOG_LINES = 20

private const val SDK = 36
