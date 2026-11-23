package pianoanchor.feature.lesson

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pianoanchor.engine.lesson.TryRun
import pianoanchor.ui.shell.PianoAnchorTheme

/**
 * Golden screenshots of the quiz screen in light and dark, on the mock's landscape phone (915×412 dp).
 * `./gradlew :feature:lesson:recordRoborazziAndroidHostTest` rewrites them under `feature/lesson/screenshots/`;
 * fastCheck verifies them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land-xxhdpi")
class QuizScreenScreenshotTest {
    private fun capture(
        name: String,
        state: QuizState,
    ) {
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            captureRoboImage("screenshots/QuizScreen_${name}_$theme.png") {
                PianoAnchorTheme(darkTheme = dark) { QuizScreen(state, QuizActions()) }
            }
        }
    }

    @Test
    fun inProgress() {
        val run = TryRun(everyFQuiz.check).onNoteOn(53, 64, 0).onNoteOn(65, 64, 0)
        capture("in_progress", quizState(everyFQuiz, progress = run.progress, litKeys = setOf(65)))
    }

    @Test
    fun aWrongKey() {
        val run = TryRun(skipQuiz.check).onNoteOn(64, 64, 0).onNoteOn(65, 64, 0)
        capture("wrong_key", quizState(skipQuiz, progress = run.progress, litKeys = setOf(65)))
    }

    @Test
    fun onAStaff() = capture("staff", quizState(staffQuiz, number = 3))

    @Test
    fun summary() =
        capture(
            "summary",
            quizState(everyFQuiz).copy(quiz = null, progress = null, summary = QuizSummary(3, 2)),
        )
}
