package pianoanchor.feature.practice

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.core.model.TimeSignature
import pianoanchor.engine.content.Passages
import pianoanchor.ui.shell.PianoAnchorTheme

/** The practice screen's accessibility text, on Robolectric. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land")
class PracticeScreenTest {
    @Test
    fun staffDescriptionIncludesTheTimeSignature() =
        runComposeUiTest {
            setContent {
                PianoAnchorTheme {
                    PracticeScreen(PracticeState.start(Passages.CDE), onBack = {}, onRetrySave = {}, onDiscard = {})
                }
            }

            onNodeWithContentDescription("Treble staff, 4/4. Notes C4, D4, E4. Waiting for C4.").assertExists()
        }

    @Test
    fun finishedStaffReadsDoneWithItsTimeSignature() =
        runComposeUiTest {
            val start = PracticeState.start(Passages.CDE.copy(timeSignature = TimeSignature(3, 4)))
            val done = start.copy(steps = start.steps.map { it.copy(status = StepStatus.DONE) })
            setContent { PianoAnchorTheme { PracticeScreen(done, onBack = {}, onRetrySave = {}, onDiscard = {}) } }

            onNodeWithContentDescription("Treble staff, 3/4. Notes C4, D4, E4. Done.").assertExists()
        }
}
