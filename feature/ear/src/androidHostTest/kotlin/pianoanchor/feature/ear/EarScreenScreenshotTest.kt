package pianoanchor.feature.ear

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pianoanchor.ui.shell.PianoAnchorTheme

/**
 * Golden screenshots of the ear screen in light and dark. `./gradlew :feature:ear:recordRoborazziAndroidHostTest`
 * rewrites them under `feature/ear/screenshots/`; fastCheck verifies them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SDK], qualifiers = "w411dp-h891dp-xxhdpi")
class EarScreenScreenshotTest {
    private fun capture(
        name: String,
        state: EarState,
    ) {
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            captureRoboImage("screenshots/EarScreen_${name}_$theme.png") {
                PianoAnchorTheme(darkTheme = dark) { EarScreen(state, EarActions()) }
            }
        }
    }

    @Test
    fun identify() = capture("identify", IDENTIFY_STATE)

    @Test
    fun echo() = capture("echo", ECHO_STATE)

    @Test
    fun wrongAnswer() = capture("wrong_answer", IDENTIFY_STATE.copy(feedback = EarFeedback.TRY_AGAIN, replaysLeft = 1))

    @Test
    fun echoRevealed() = capture("echo_revealed", ECHO_STATE.copy(feedback = EarFeedback.REVEALED))

    @Test
    fun summary() = capture("summary", EarState(skillCount = 3, summary = EarSummary(3, 10, 12)))
}

private const val SDK = 36
