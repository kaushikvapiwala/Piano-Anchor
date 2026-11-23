package pianoanchor.feature.lesson

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
import pianoanchor.core.model.TryCheck
import pianoanchor.engine.lesson.ChordName
import pianoanchor.engine.lesson.Quiz
import pianoanchor.engine.lesson.QuizPrompt
import pianoanchor.engine.lesson.TryRun
import pianoanchor.ui.shell.PianoAnchorTheme
import kotlin.test.assertEquals

/** The quiz screen's content and actions, on Robolectric in landscape. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land")
class QuizScreenTest {
    @Test
    fun theHeaderPromptAndProgressShow() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { QuizScreen(quizState(everyFQuiz), QuizActions()) } }

            onNodeWithText("Quiz 2 of 3").assertIsDisplayed()
            onNodeWithText("Play every F").assertIsDisplayed()
            onNodeWithText("0 of 3").assertIsDisplayed()
            onNodeWithText("Skip").assertIsDisplayed()
        }

    @Test
    fun theOtherPromptsAreWorded() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { QuizScreen(quizState(skipQuiz), QuizActions()) } }
            onNodeWithText("A skip up from E4").assertIsDisplayed()
        }

    @Test
    fun aStaffQuizShowsTheNoteOnAMiniStaff() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { QuizScreen(quizState(staffQuiz), QuizActions()) } }
            onNodeWithText("Play the note on the staff").assertIsDisplayed()
            onNodeWithContentDescription("A note on the staff: G4").assertIsDisplayed()
        }

    @Test
    fun aWrongKeyGivesTheLandmarkHint() =
        runComposeUiTest {
            val wrong = TryRun(skipQuiz.check).onNoteOn(63, 64, 0).progress
            setContent { PianoAnchorTheme { QuizScreen(quizState(skipQuiz, progress = wrong), QuizActions()) } }
            onNodeWithText("That was D♯4. E is just right of the two black keys.").assertIsDisplayed()
        }

    @Test
    fun aChordGivesAPlainTryAgain() =
        runComposeUiTest {
            val chord =
                Quiz(
                    "c-chord",
                    TryCheck.Chord(setOf(60, 64, 67)),
                    QuizPrompt.ChordOf(ChordName.C),
                )
            val wrong = TryRun(chord.check).onNoteOn(61, 64, 0).progress
            setContent { PianoAnchorTheme { QuizScreen(quizState(chord, progress = wrong), QuizActions()) } }
            onNodeWithText("Play the C chord").assertIsDisplayed()
            onNodeWithText("That was C#4. Try again.").assertIsDisplayed()
        }

    @Test
    fun skipAndBackAreActions() =
        runComposeUiTest {
            var skips = 0
            var backs = 0
            setContent {
                PianoAnchorTheme {
                    QuizScreen(quizState(everyFQuiz), QuizActions(onBack = { backs++ }, onSkip = { skips++ }))
                }
            }
            onNodeWithText("Skip").performClick()
            onNodeWithContentDescription("Back").performClick()
            assertEquals(1, skips)
            assertEquals(1, backs)
        }

    @Test
    fun theSummaryHasADoneButton() =
        runComposeUiTest {
            var done = 0
            val summary = quizState(everyFQuiz).copy(quiz = null, progress = null, summary = QuizSummary(3, 2))
            setContent { PianoAnchorTheme { QuizScreen(summary, QuizActions(onDone = { done++ })) } }
            onNodeWithText("3 quizzes: 2 clean").assertIsDisplayed()
            onNodeWithText("Done").performClick()
            assertEquals(1, done)
        }

    @Test
    fun aSingleQuizSummaryAndAFailedSave() =
        runComposeUiTest {
            val summary =
                quizState(everyFQuiz).copy(
                    quiz = null,
                    progress = null,
                    summary = QuizSummary(1, 1),
                    storageFailed = true,
                )
            setContent { PianoAnchorTheme { QuizScreen(summary, QuizActions()) } }
            onNodeWithText("1 quiz: 1 clean").assertIsDisplayed()
            onNodeWithText("Your results couldn't be saved.").assertIsDisplayed()
        }

    @Test
    fun aDisconnectedPianoIsSaid() =
        runComposeUiTest {
            val state =
                quizState(
                    everyFQuiz,
                ).copy(connection = LessonConnection.DISCONNECTED, countIn = 3, storageFailed = true)
            setContent { PianoAnchorTheme { QuizScreen(state, QuizActions()) } }
            onNodeWithText("The piano isn't connected. Go back and connect it to try this step.").assertIsDisplayed()
            onNodeWithText("Your results couldn't be saved.").assertIsDisplayed()
        }

    @Test
    fun sharpsAndFlatsAreNamedWithTheirSign() =
        runComposeUiTest {
            val flat =
                QuizPrompt
                    .PlayKey(63, flat = true)
            val quiz =
                Quiz(
                    "sharps-and-flats",
                    TryCheck.PlayKey(63, lit = false),
                    flat,
                )
            setContent { PianoAnchorTheme { QuizScreen(quizState(quiz), QuizActions()) } }
            onNodeWithText("Play E♭4").assertIsDisplayed()
        }
}
