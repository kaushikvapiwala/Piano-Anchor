package pianoanchor.feature.lesson

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.core.model.LessonStep
import pianoanchor.engine.content.Skills
import pianoanchor.ui.shell.PianoAnchorTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The lesson screen's content and actions, on Robolectric in landscape. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land")
class LessonScreenTest {
    @Test
    fun theCardFieldsAndTheStepDotsShow() =
        runComposeUiTest {
            val state = lessonState(LessonStep.TRY, progress = sequenceProgress(60, 62))
            setContent { PianoAnchorTheme { LessonScreen(state, LessonActions()) } }

            // The card animation fills the top of the scrolling pane, so the text scrolls into view first.
            onNodeWithText(state.card.what).performScrollTo().assertIsDisplayed()
            onNodeWithText("What").assertIsDisplayed()
            onNodeWithContentDescription("Step 3 of 5, Try, current").assertIsDisplayed()
            onNodeWithContentDescription("Step 1 of 5, Why").assertIsDisplayed()
            onNodeWithContentDescription("Step 5 of 5, Recap").assertIsDisplayed()
        }

    @Test
    fun aDotJumpsToItsStep() =
        runComposeUiTest {
            val jumps = mutableListOf<LessonStep>()
            val state = lessonState(LessonStep.WHY)
            setContent { PianoAnchorTheme { LessonScreen(state, LessonActions(onGoTo = { jumps += it })) } }

            onNodeWithContentDescription("Step 4 of 5, Use").performClick()

            assertEquals(listOf(LessonStep.USE), jumps)
        }

    @Test
    fun theTryDotSaysInUseWhenTheTryIsAPassage() =
        runComposeUiTest {
            val state = lessonState(LessonStep.USE, skill = Skills.FINGER_NUMBER_TUNES)
            setContent { PianoAnchorTheme { LessonScreen(state, LessonActions()) } }

            onNodeWithContentDescription("Step 3 of 5, Try, in Use").assertIsDisplayed()
        }

    @Test
    fun whyShowsNextWhichMovesOn() =
        runComposeUiTest {
            var next = 0
            val state = lessonState(LessonStep.WHY)
            setContent { PianoAnchorTheme { LessonScreen(state, LessonActions(onNext = { next++ })) } }

            onNodeWithText("Next").performClick()

            assertEquals(1, next)
        }

    @Test
    fun seeOffersPlayAgainAndSkip() =
        runComposeUiTest {
            val calls = mutableListOf<String>()
            val state = lessonState(LessonStep.SEE)
            setContent {
                PianoAnchorTheme {
                    LessonScreen(state, LessonActions(onPlayAgain = { calls += "again" }, onSkip = { calls += "skip" }))
                }
            }

            onNodeWithText("Play again").performClick()
            onNodeWithText("Skip").performClick()

            assertEquals(listOf("again", "skip"), calls)
        }

    @Test
    fun playAgainWaitsWhileTheDemoSounds() =
        runComposeUiTest {
            val state = lessonState(LessonStep.SEE).copy(playing = true)
            setContent { PianoAnchorTheme { LessonScreen(state, LessonActions()) } }

            onNodeWithText("Play again").assertIsNotEnabled()
        }

    @Test
    fun seeOfAnyKeySaysWatchTheHand() =
        runComposeUiTest {
            val state = lessonState(LessonStep.SEE, skill = Skills.HAND_SHAPE)
            setContent { PianoAnchorTheme { LessonScreen(state, LessonActions()) } }

            onNodeWithText("Watch the hand").assertIsDisplayed()
        }

    @Test
    fun tryShowsTheInstructionTheProgressAndAlwaysSkip() =
        runComposeUiTest {
            var skipped = 0
            val state = lessonState(LessonStep.TRY, progress = sequenceProgress(60, 62, 64))
            setContent { PianoAnchorTheme { LessonScreen(state, LessonActions(onSkip = { skipped++ })) } }

            onNodeWithText("3 of 9").assertIsDisplayed()
            onNodeWithText("Play these notes in order", substring = true).assertIsDisplayed()
            onNodeWithText("Skip").performClick()
            assertEquals(1, skipped)
        }

    @Test
    fun aWrongKeyNamesItAndTheKeyTheAppWaitsFor() =
        runComposeUiTest {
            val state = lessonState(LessonStep.TRY, progress = sequenceProgress(60, 63))
            setContent { PianoAnchorTheme { LessonScreen(state, LessonActions()) } }

            onNodeWithText("That was D♯4. D is between the two black keys.").assertIsDisplayed()
        }

    @Test
    fun aPassedTryShowsATick() =
        runComposeUiTest {
            val state = lessonState(LessonStep.TRY, skill = Skills.MIDDLE_C)
            val done = state.copy(tryProgress = state.tryProgress ?: finishedMiddleC())
            setContent { PianoAnchorTheme { LessonScreen(done, LessonActions()) } }

            onNodeWithText("✓ Well done").assertIsDisplayed()
        }

    @Test
    fun theCleanRepsShowBesideTheChecksOwnProgressWhenMoreThanOneIsNeeded() =
        runComposeUiTest {
            val state = lessonState(LessonStep.TRY, skill = Skills.MIDDLE_C, progress = finishedMiddleC())
            setContent { PianoAnchorTheme { LessonScreen(state.copy(cleanReps = 2, repsNeeded = 3), LessonActions()) } }

            onNodeWithText("Clean 2 of 3").assertIsDisplayed()
        }

    @Test
    fun aCheckThatNeedsOneRepShowsNoCleanCount() =
        runComposeUiTest {
            val state = lessonState(LessonStep.TRY, skill = Skills.MIDDLE_C, progress = finishedMiddleC())
            setContent { PianoAnchorTheme { LessonScreen(state, LessonActions()) } }

            onNodeWithText("Clean", substring = true).assertDoesNotExist()
        }

    @Test
    fun theCountInNumeralShows() =
        runComposeUiTest {
            val state = lessonState(LessonStep.TRY, skill = Skills.STEADY_BEAT, countIn = 3)
            setContent { PianoAnchorTheme { LessonScreen(state, LessonActions()) } }

            onNodeWithText("3").assertIsDisplayed()
        }

    @Test
    fun useNamesThePassageAndPlayItStartsPractice() =
        runComposeUiTest {
            var played = 0
            val state = lessonState(LessonStep.USE)
            setContent { PianoAnchorTheme { LessonScreen(state, LessonActions(onPlayIt = { played++ })) } }

            onNodeWithText("Play it").performClick()

            assertEquals(1, played)
            assertTrue(state.usePassage != null)
        }

    @Test
    fun recapSaysWhatYouCanNowDoAndOffersTheNextLessonAndDone() =
        runComposeUiTest {
            val calls = mutableListOf<String>()
            val state = lessonState(LessonStep.RECAP, recap = RecapText("Steps and skips", "First tunes", "x"))
            setContent {
                PianoAnchorTheme {
                    LessonScreen(
                        state,
                        LessonActions(onNextLesson = { calls += "next" }, onDone = { calls += "done" }),
                    )
                }
            }

            onNodeWithText("You’ve learned Steps and skips. Next: First tunes.").assertIsDisplayed()
            onNodeWithText("Next lesson").performClick()
            onNodeWithText("Done").performClick()
            assertEquals(listOf("next", "done"), calls)
        }

    @Test
    fun recapAtTheEndOfTheCourseSaysSoAndOffersOnlyDone() =
        runComposeUiTest {
            val state = lessonState(LessonStep.RECAP, recap = RecapText("Sharps and flats", null))
            setContent { PianoAnchorTheme { LessonScreen(state, LessonActions()) } }

            onNodeWithText("You've finished the course!").assertIsDisplayed()
            onNodeWithText("Done").assertIsDisplayed()
            onNodeWithText("Next lesson").assertDoesNotExist()
        }

    @Test
    fun aDroppedPianoIsExplainedInTry() =
        runComposeUiTest {
            val state =
                lessonState(
                    LessonStep.TRY,
                    progress = sequenceProgress(),
                ).copy(connection = LessonConnection.DISCONNECTED)
            setContent { PianoAnchorTheme { LessonScreen(state, LessonActions()) } }

            onNodeWithText("The piano isn't connected", substring = true).assertIsDisplayed()
        }

    @Test
    fun backCallsItsAction() =
        runComposeUiTest {
            var back = 0
            setContent {
                PianoAnchorTheme { LessonScreen(lessonState(LessonStep.WHY), LessonActions(onBack = { back++ })) }
            }

            onNodeWithContentDescription("Back").performClick()

            assertEquals(1, back)
        }
}
