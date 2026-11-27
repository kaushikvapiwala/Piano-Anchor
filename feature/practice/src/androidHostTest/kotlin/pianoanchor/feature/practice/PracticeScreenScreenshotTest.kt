package pianoanchor.feature.practice

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pianoanchor.core.model.BarRange
import pianoanchor.core.model.HintLevel
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.RunMode
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Passages
import pianoanchor.engine.guidance.wrongKeyHint
import pianoanchor.engine.lesson.NoteLabels
import pianoanchor.engine.lesson.NoteNameStage
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
    fun everyWhiteKeyLettered() =
        capture(
            "letters_all_white",
            start.copy(noteLabels = NoteLabels(NoteNameStage.ALL_WHITE, sharpsPassed = true, hints = true)),
        )

    @Test
    fun onlyTheKeyToPlayLettered() =
        capture(
            "letters_target_only",
            start.copy(noteLabels = NoteLabels(NoteNameStage.TARGET_ONLY, sharpsPassed = true, hints = true)),
        )

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

    @Test
    fun wrongKeyInWaitModeWithTheLandmarkHint() =
        capture(
            "wrong_key_hint",
            start.copy(
                keysDown = setOf(f4),
                flash = KeyFlash(f4, correct = false),
                lastKey = KeyFlash(f4, correct = false),
                wrongKeys = setOf(f4),
                hint = wrongKeyHint(played = 65, expected = 60),
                hintGlow = setOf(MidiNote(60)),
            ),
        )

    @Test
    fun unitZeroPracticeWithoutAStaff() =
        capture(
            "unit_zero",
            PracticeState
                .start(
                    Curriculum.units
                        .first { it.id == "unit-0" }
                        .passages
                        .first(),
                ).copy(
                    hintLevel = HintLevel.OFF,
                    hintVisible = false,
                    staffHidden = true,
                ),
        )

    @Test
    fun hearItFirst() =
        capture(
            "hear_first",
            start.copy(
                canListen = true,
                listening = true,
                demoStep = 1,
                demoKeys = setOf(MidiNote(62)),
            ),
        )

    private val off = start.copy(hintLevel = HintLevel.OFF, hintVisible = false)

    @Test
    fun hintsOffWithTheHintButton() = capture("hint_off", off)

    @Test
    fun delayedBeforeTheHint() = capture("hint_delayed_before", off.copy(hintLevel = HintLevel.DELAYED))

    @Test
    fun delayedAfterTheHint() =
        capture("hint_delayed_after", off.copy(hintLevel = HintLevel.DELAYED, hintVisible = true))

    private val timed = start.copy(rung = TempoRung.P70, doneRungs = setOf(TempoRung.P60))

    @Test
    fun timedRungReady() = capture("timed_ready", timed)

    @Test
    fun timedRunStoppedByAWrongNote() =
        capture("restart_reason", timed.copy(restartReason = RestartReason(RestartKind.WRONG, f4)))

    @Test
    fun timedRunTooHardOffersASlowerRung() =
        capture(
            "too_hard",
            timed.copy(restartReason = RestartReason(RestartKind.MISSED, MidiNote(62)), offerSlower = true),
        )

    @Test
    fun countInShowsTheNumeralTwo() = capture("count_in", timed.copy(phase = RunPhase.COUNT_IN, beat = 3))

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
    fun modeSwitchInPracticeAtATimedRung() = capture("mode_practice", timed)

    @Test
    fun performTakeMidRunWithNeutralKeys() =
        capture(
            "perform_playing",
            timed.copy(
                mode = RunMode.PERFORM,
                hintVisible = false,
                phase = RunPhase.PLAYING,
                beat = 2,
                currentStep = 1,
                steps =
                    start.steps.mapIndexed { i, step ->
                        step.copy(status = if (i == 1) StepStatus.WAITING else StepStatus.UPCOMING)
                    },
                keysDown = setOf(MidiNote(60), f4),
            ),
        )

    private val loop = start.copy(spotLoop = SpotLoopState(BarRange(5, 6), fullRung = TempoRung.P85))

    @Test
    fun spotLoopRunningAfterANotCleanLoop() =
        capture(
            "spot_loop",
            loop.copy(
                spotLoop =
                    loop.spotLoop?.copy(
                        lastLoop = LoopResult(clean = false, wrongNotes = 2, incompleteChords = 0),
                    ),
            ),
        )

    @Test
    fun spotLoopBarsAreClean() =
        capture(
            "spot_loop_clean",
            loop.copy(
                steps = start.steps.map { it.copy(status = StepStatus.DONE) },
                currentStep = start.steps.lastIndex,
                spotLoop =
                    loop.spotLoop?.copy(
                        cleanLoops = 2,
                        offerFull = true,
                        lastLoop = LoopResult(clean = true, wrongNotes = 0, incompleteChords = 0),
                    ),
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
