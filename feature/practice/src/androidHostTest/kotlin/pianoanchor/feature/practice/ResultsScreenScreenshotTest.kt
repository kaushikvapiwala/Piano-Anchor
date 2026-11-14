package pianoanchor.feature.practice

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pianoanchor.core.model.Band
import pianoanchor.ui.shell.PianoAnchorTheme

/**
 * Golden screenshots of the results screen in light and dark, on the mock's landscape phone (915×412 dp).
 * `./gradlew :feature:practice:recordRoborazziAndroidHostTest` rewrites them under `feature/practice/screenshots/`;
 * fastCheck verifies them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land-xxhdpi")
class ResultsScreenScreenshotTest {
    private val clean =
        ResultsState.Ready(
            passageTitle = "C-D-E",
            runNumber = 3,
            band = Band.CLEAN,
            correctSteps = 3,
            stepCount = 3,
            wrongNotes = 0,
            incompleteChords = 0,
        )

    private fun capture(
        name: String,
        state: ResultsState,
    ) {
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            captureRoboImage("screenshots/ResultsScreen_${name}_$theme.png") {
                PianoAnchorTheme(darkTheme = dark) { ResultsScreen(state, onBack = {}, onTryAgain = {}, onRetry = {}) }
            }
        }
    }

    @Test
    fun everyNoteRight() = capture("clean", clean)

    @Test
    fun oneWrongNote() = capture("good", clean.copy(band = Band.GOOD, wrongNotes = 1))

    @Test
    fun keepPractising() =
        capture("keep_practising", clean.copy(band = Band.KEEP_PRACTISING, wrongNotes = 2, incompleteChords = 1))

    @Test
    fun notFound() = capture("not_found", ResultsState.NotFound)

    @Test
    fun unavailable() = capture("unavailable", ResultsState.Unavailable)
}
