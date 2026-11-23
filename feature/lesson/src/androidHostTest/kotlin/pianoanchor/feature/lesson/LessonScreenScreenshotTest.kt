package pianoanchor.feature.lesson

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pianoanchor.core.model.LessonStep
import pianoanchor.engine.content.Skills
import pianoanchor.ui.shell.PianoAnchorTheme

/**
 * Golden screenshots of the lesson screen in light and dark, on the mock's landscape phone (915×412 dp).
 * `./gradlew :feature:lesson:recordRoborazziAndroidHostTest` rewrites them under `feature/lesson/screenshots/`;
 * fastCheck verifies them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land-xxhdpi")
class LessonScreenScreenshotTest {
    private fun capture(
        name: String,
        state: LessonState,
    ) {
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            captureRoboImage("screenshots/LessonScreen_${name}_$theme.png") {
                PianoAnchorTheme(darkTheme = dark) { LessonScreen(state, LessonActions()) }
            }
        }
    }

    @Test
    fun why() = capture("why", lessonState(LessonStep.WHY))

    @Test
    fun seeWhileTheDemoSounds() = capture("see", lessonState(LessonStep.SEE, litKeys = setOf(62)).copy(playing = true))

    @Test
    fun tryJustOpened() = capture("try_start", lessonState(LessonStep.TRY, progress = sequenceProgress()))

    @Test
    fun tryInProgress() =
        capture(
            "try_in_progress",
            lessonState(LessonStep.TRY, progress = sequenceProgress(60, 62, 64), litKeys = setOf(64)),
        )

    @Test
    fun tryWithAWrongKey() =
        capture("try_wrong_key", lessonState(LessonStep.TRY, progress = sequenceProgress(60, 63), litKeys = setOf(63)))

    @Test
    fun tryPassed() = capture("try_done", lessonState(LessonStep.TRY, Skills.MIDDLE_C, finishedMiddleC()))

    @Test
    fun use() = capture("use", lessonState(LessonStep.USE))

    @Test
    fun recap() =
        capture(
            "recap",
            lessonState(LessonStep.RECAP, recap = RecapText("Right-hand C position", "Steps and skips", "x")),
        )
}
