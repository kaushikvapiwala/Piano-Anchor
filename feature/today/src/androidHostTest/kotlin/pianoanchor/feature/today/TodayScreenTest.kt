package pianoanchor.feature.today

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.ui.shell.PianoAnchorTheme

/** The piano chip's text and TalkBack description in the states with no connected piano. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [CHIP_TEST_SDK], qualifiers = "w411dp-h891dp-xxhdpi")
class TodayScreenTest {
    @Test
    fun theConnectingChipSaysConnectingAndOffersPianos() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { TodayScreen(TodayState(connecting = true), TodayActions()) } }

            onNodeWithText("Connecting…").assertExists()
            onNodeWithContentDescription("Connecting to your piano. Opens Pianos.").assertExists()
        }

    @Test
    fun theChipWithNoPianoOffersPianos() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { TodayScreen(TodayState(), TodayActions()) } }

            onNodeWithText("No piano").assertExists()
            onNodeWithContentDescription("No piano. Opens Pianos.").assertExists()
        }
}

private const val CHIP_TEST_SDK = 36
