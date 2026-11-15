package pianoanchor.feature.monitor

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.ui.shell.PianoAnchorTheme

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
}

private const val SDK = 36
