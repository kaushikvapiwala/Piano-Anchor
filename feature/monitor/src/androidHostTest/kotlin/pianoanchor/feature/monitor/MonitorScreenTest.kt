package pianoanchor.feature.monitor

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.ui.shell.PianoAnchorTheme
import kotlin.test.assertEquals

/** The monitor's Practice and Calibrate buttons: shown unless the composition root passes null. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w411dp-h891dp-xxhdpi")
class MonitorScreenTest {
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

            onNodeWithText("Save contribution kit").assertIsEnabled().performClick()
            assertEquals(1, saved)
        }
}

private const val SDK = 36
