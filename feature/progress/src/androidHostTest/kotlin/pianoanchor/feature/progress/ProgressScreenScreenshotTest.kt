package pianoanchor.feature.progress

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pianoanchor.ui.shell.PianoAnchorTheme

/**
 * Golden screenshots of Progress in light and dark. `./gradlew :feature:progress:recordRoborazziAndroidHostTest`
 * rewrites them under `feature/progress/screenshots/`; fastCheck verifies them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land-xxhdpi")
class ProgressScreenScreenshotTest {
    private fun capture(
        name: String,
        state: ProgressState,
    ) {
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            captureRoboImage("screenshots/ProgressScreen_${name}_$theme.png") {
                PianoAnchorTheme(darkTheme = dark) { ProgressScreen(state, ProgressActions()) }
            }
        }
    }

    @Test
    fun weekWithComparison() = capture("compare", STATE)

    @Test
    fun weekPlayingDay1() = capture("playing", STATE.copy(playing = Take.DAY_1))

    @Test
    fun weekWithoutComparison() = capture("no_compare", STATE.copy(compare = null, learnedThisWeek = emptyList()))

    @Test
    fun storageError() = capture("storage_error", ProgressState(storageFailed = true))
}
