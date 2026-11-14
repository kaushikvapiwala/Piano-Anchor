package pianoanchor.feature.practice

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.engine.content.Passages
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.ui.shell.PianoAnchorTheme

/**
 * Golden screenshots of the practice screen in light and dark, on the mock's landscape phone (915×412 dp).
 * `./gradlew :feature:practice:recordRoborazziAndroidHostTest` rewrites them under `feature/practice/screenshots/`;
 * fastCheck verifies them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land-xxhdpi")
class PracticeScreenScreenshotTest {
    private val start = PracticeState.start(Passages.CDE)
    private val f4 = MidiNote(65)

    private fun capture(
        name: String,
        state: PracticeState,
    ) {
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            captureRoboImage("screenshots/PracticeScreen_${name}_$theme.png") {
                PianoAnchorTheme(
                    darkTheme = dark,
                ) { PracticeScreen(state, {}, {}, {}, {}, {}) }
            }
        }
    }

    @Test
    fun waitingForTheFirstNote() = capture("start", start)

    @Test
    fun midRunWithAWrongKey() =
        capture(
            "mid_run",
            start.copy(
                steps =
                    start.steps.mapIndexed { i, step ->
                        step.copy(status = listOf(StepStatus.DONE, StepStatus.WAITING, StepStatus.UPCOMING)[i])
                    },
                currentStep = 1,
                keysDown = setOf(f4),
                flash = KeyFlash(f4, correct = false),
                lastKey = KeyFlash(f4, correct = false),
            ),
        )

    private val timed = start.copy(rung = TempoRung.P70, doneRungs = setOf(TempoRung.P60))

    @Test
    fun timedRungReady() = capture("timed_ready", timed)

    @Test
    fun countIn() = capture("count_in", timed.copy(phase = RunPhase.COUNT_IN, beat = 3))

    @Test
    fun playingWithOneTimingOffMark() =
        capture(
            "timed_playing",
            timed.copy(
                phase = RunPhase.PLAYING,
                beat = 2,
                currentStep = 1,
                steps =
                    start.steps.mapIndexed { i, step ->
                        step.copy(status = if (i == 1) StepStatus.WAITING else StepStatus.UPCOMING)
                    },
                liveMarks =
                    mapOf(
                        ScoreNoteRef(0, MidiNote(60)) to LiveMark.TimingOff(92),
                    ),
                keysDown = setOf(MidiNote(60)),
                flash = KeyFlash(MidiNote(60), correct = true, deviationMs = 92),
                lastKey = KeyFlash(MidiNote(60), correct = true, deviationMs = 92),
            ),
        )

    @Test
    fun disconnected() = capture("disconnected", start.copy(connection = PracticeConnection.DISCONNECTED))

    @Test
    fun saveFailed() =
        capture(
            "save_failed",
            start.copy(
                steps = start.steps.map { it.copy(status = StepStatus.DONE) },
                currentStep = start.steps.lastIndex,
                saveFailed = true,
            ),
        )
}

/** Android API level the Robolectric tests run with (the newest Robolectric supports). */
internal const val SDK = 36
