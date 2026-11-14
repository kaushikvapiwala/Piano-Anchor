package pianoanchor.feature.practice

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.TimeSignature
import pianoanchor.engine.content.Passages
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.ui.shell.PianoAnchorTheme
import kotlin.test.assertEquals

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
                    PracticeScreen(PracticeState.start(Passages.CDE), onBack = {
                    }, onSelectRung = {}, onToggleClick = {}, onRetrySave = {}, onDiscard = {})
                }
            }

            onNodeWithContentDescription("Treble staff, 4/4. Notes C4, D4, E4. Waiting for C4.").assertExists()
        }

    @Test
    fun finishedStaffReadsDoneWithItsTimeSignature() =
        runComposeUiTest {
            val start = PracticeState.start(Passages.CDE.copy(timeSignature = TimeSignature(3, 4)))
            val done = start.copy(steps = start.steps.map { it.copy(status = StepStatus.DONE) })
            setContent {
                PianoAnchorTheme {
                    PracticeScreen(
                        done,
                        onBack = {},
                        onSelectRung = {},
                        onToggleClick = {},
                        onRetrySave = {},
                        onDiscard = {},
                    )
                }
            }

            onNodeWithContentDescription("Treble staff, 3/4. Notes C4, D4, E4. Done.").assertExists()
        }

    private fun ComposeUiTest.show(
        state: PracticeState,
        onSelectRung: (TempoRung) -> Unit = {},
        onToggleClick: () -> Unit = {},
    ) = setContent {
        PianoAnchorTheme { PracticeScreen(state, {}, onSelectRung, onToggleClick, {}, {}) }
    }

    @Test
    fun tappingARungSelectsIt() =
        runComposeUiTest {
            var picked: TempoRung? = null
            show(PracticeState.start(Passages.CDE), onSelectRung = { picked = it })

            onNodeWithContentDescription("85%").performClick()

            assertEquals(TempoRung.P85, picked)
        }

    @Test
    fun aDoneRungIsReadAsDoneAndTheSelectedOneIsSelected() =
        runComposeUiTest {
            show(PracticeState.start(Passages.CDE).copy(rung = TempoRung.P70, doneRungs = setOf(TempoRung.P60)))

            onNodeWithContentDescription("60%, done").assertExists()
            onNodeWithContentDescription("70%").assertIsSelected()
            onNodeWithContentDescription("Wait").assertIsNotSelected()
        }

    @Test
    fun theClickSwitchNamesItsStateAndToggles() =
        runComposeUiTest {
            var toggles = 0
            val start = PracticeState.start(Passages.CDE)
            val state = mutableStateOf(start)
            setContent {
                PianoAnchorTheme { PracticeScreen(state.value, {}, {}, { toggles++ }, {}, {}) }
            }

            onNodeWithContentDescription("Click on").performClick()
            assertEquals(1, toggles)

            state.value = start.copy(clickOn = false)
            onNodeWithContentDescription("Click off").assertExists()
        }

    @Test
    fun readyTimedRunSaysToPlayAKey() =
        runComposeUiTest {
            show(PracticeState.start(Passages.CDE).copy(rung = TempoRung.P60))

            onNodeWithText("Play any key to count in").assertExists()
            onNodeWithText("Tempo run · 48 BPM").assertExists()
        }

    @Test
    fun countInShowsTheBeatAndTheTimedStaffReadsTheCursor() =
        runComposeUiTest {
            show(PracticeState.start(Passages.CDE).copy(rung = TempoRung.P60, phase = RunPhase.COUNT_IN, beat = 2))

            onNodeWithContentDescription("Beat 2 of 4").assertExists()
            onNodeWithText("Count-in").assertExists()
            onNodeWithContentDescription("Treble staff, 4/4. Notes C4, D4, E4. Cursor on C4.").assertExists()
        }

    @Test
    fun timingOffNotesAreAnnouncedLateOrEarly() =
        runComposeUiTest {
            val start = PracticeState.start(Passages.CDE).copy(rung = TempoRung.P60, phase = RunPhase.PLAYING)
            val c4 = MidiNote(60)
            val late = start.copy(lastKey = KeyFlash(c4, correct = true, deviationMs = 92))
            val state = mutableStateOf(late)
            setContent { PianoAnchorTheme { PracticeScreen(state.value, {}, {}, {}, {}, {}) } }

            onNodeWithContentDescription("Late, 92 ms").assertExists()
            onNodeWithText("C4 · late, 92 ms").assertExists()

            state.value = start.copy(lastKey = KeyFlash(c4, correct = true, deviationMs = -40))
            onNodeWithContentDescription("Early, 40 ms").assertExists()

            state.value = start.copy(lastKey = KeyFlash(c4, correct = true))
            onNodeWithText("C4 · on time").assertExists()

            state.value = start.copy(lastKey = KeyFlash(c4, correct = false))
            onNodeWithText("C4 · wrong note").assertExists()
        }
}
