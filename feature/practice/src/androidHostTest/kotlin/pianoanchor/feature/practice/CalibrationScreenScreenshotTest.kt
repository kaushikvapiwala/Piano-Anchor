package pianoanchor.feature.practice

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pianoanchor.engine.calibration.CalibrationResult
import pianoanchor.ui.shell.PianoAnchorTheme

/**
 * Golden screenshots of the calibration screen in light and dark, on the mock's landscape phone (915×412 dp).
 * `./gradlew :feature:practice:recordRoborazziAndroidHostTest` rewrites them under `feature/practice/screenshots/`;
 * fastCheck verifies them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land-xxhdpi")
class CalibrationScreenScreenshotTest {
    private fun capture(
        name: String,
        state: CalibrationState,
    ) {
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            captureRoboImage("screenshots/CalibrationScreen_${name}_$theme.png") {
                PianoAnchorTheme(darkTheme = dark) { CalibrationScreen(state, {}, {}, {}) }
            }
        }
    }

    @Test
    fun running() =
        capture(
            "running",
            CalibrationState(phase = CalibrationPhase.RUNNING, click = 11, pulse = true, countedTaps = 7),
        )

    @Test
    fun accepted() =
        capture(
            "accepted",
            CalibrationState(
                phase = CalibrationPhase.DONE,
                click = 16,
                countedTaps = 12,
                result = CalibrationResult.Accepted(38_000_000, 6_000_000),
            ),
        )

    @Test
    fun uneven() =
        capture(
            "uneven",
            CalibrationState(
                phase = CalibrationPhase.DONE,
                click = 16,
                countedTaps = 12,
                result = CalibrationResult.TooUneven(60_000_000),
            ),
        )
}
