package pianoanchor.feature.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.core.model.BarRange
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.session.Card
import pianoanchor.engine.session.SessionStep
import pianoanchor.ui.shell.PianoAnchorTheme
import kotlin.test.assertEquals

/** The between-steps card and the recap. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w915dp-h412dp-land-xxhdpi")
class SessionFlowScreenTest {
    private val passage = checkNotNull(Curriculum.passage("builtin:hot-cross-buns"))
    private val steps =
        listOf<SessionStep>(
            SessionStep.Learn(Card.Lesson(passage, TempoRung.WAIT, 5, Skills.HAND_SHAPE, emptyList()), 5),
            SessionStep.Practise(Card.Song(passage, TempoRung.P70, 6), 6),
        )

    private fun between(
        secondsLeft: Int = 7,
        paused: Boolean = false,
    ) = SessionFlowState(steps, 0, Between(steps[1], secondsLeft, paused), pedalHint = true)

    @Test
    fun theBetweenCardNamesTheNextStepAndPausesAndResumes() =
        runComposeUiTest {
            var state by mutableStateOf(between())
            val log = mutableListOf<String>()
            setContent {
                PianoAnchorTheme {
                    SessionFlowScreen(
                        state,
                        SessionFlowActions(
                            onPause = {
                                log += "pause"
                                state = between(paused = true)
                            },
                            onResume = {
                                log += "resume"
                                state = between()
                            },
                            onSkip = { log += "skip" },
                        ),
                        reduceMotion = true,
                    )
                }
            }

            onNodeWithText("NEXT · STEP 2 OF 2").assertIsDisplayed()
            onNodeWithText("Practise: ${passage.title}").assertIsDisplayed()
            onNodeWithContentDescription("7 seconds until the next step").assertExists()
            onNodeWithText("Pause").performClick()
            onNodeWithText("Resume").assertIsDisplayed()
            onNodeWithText("Resume").performClick()
            onNodeWithText("Skip step").performClick()

            assertEquals(listOf("pause", "resume", "skip"), log)
        }

    @Test
    fun tappingTheCardGoesNow() =
        runComposeUiTest {
            var goes = 0
            setContent {
                PianoAnchorTheme {
                    SessionFlowScreen(between(), SessionFlowActions(onGoNow = { goes++ }), reduceMotion = false)
                }
            }

            onNodeWithText("NEXT · STEP 2 OF 2").performClick()

            assertEquals(1, goes)
        }

    @Test
    fun theRecapShowsTimeStepsLearnedAndATip() =
        runComposeUiTest {
            var done = 0
            val recap = SessionRecap(14, 3, listOf(Skills.HAND_SHAPE), RecapTip("Hot Cross Buns", BarRange(2, 3)))
            setContent {
                PianoAnchorTheme {
                    SessionFlowScreen(
                        SessionFlowState(steps, 1, recap = recap),
                        SessionFlowActions(onDone = { done++ }),
                    )
                }
            }

            onNodeWithText("14 min").assertIsDisplayed()
            onNodeWithText("3").assertIsDisplayed()
            onNodeWithText(Skills.HAND_SHAPE.title).assertIsDisplayed()
            onNodeWithText("Work on bars 2 to 3 of Hot Cross Buns.").assertIsDisplayed()
            onNodeWithText("Done").performClick()

            assertEquals(1, done)
        }

    @Test
    fun theRecapWithNothingLearnedAndNoTipSaysSo() =
        runComposeUiTest {
            setContent {
                PianoAnchorTheme {
                    SessionFlowScreen(
                        SessionFlowState(steps, 1, recap = SessionRecap(1, 0, emptyList(), null)),
                        SessionFlowActions(),
                    )
                }
            }

            onNodeWithText("Nothing new today. Reviews are how it sticks.").assertIsDisplayed()
            onNodeWithText("No weak spot stood out. Nicely played.").assertIsDisplayed()
        }

    @Test
    fun aTipOnOneBarNamesTheBar() =
        runComposeUiTest {
            val recap = SessionRecap(1, 1, emptyList(), RecapTip("Hot Cross Buns", BarRange(2, 2)))
            setContent {
                PianoAnchorTheme { SessionFlowScreen(SessionFlowState(steps, 1, recap = recap), SessionFlowActions()) }
            }

            onNodeWithText("Work on bar 2 of Hot Cross Buns.").assertIsDisplayed()
        }

    @Test
    fun theLeaveDialogOffersKeepGoingAndLeave() =
        runComposeUiTest {
            val log = mutableListOf<String>()
            setContent {
                PianoAnchorTheme {
                    LeaveSessionDialog(onKeepGoing = { log += "keep" }, onLeave = { log += "leave" })
                }
            }

            onNodeWithText("Leave today's session?").assertIsDisplayed()
            onNodeWithText("Keep going").performClick()
            onNodeWithText("Leave").performClick()

            assertEquals(listOf("keep", "leave"), log)
        }
}
