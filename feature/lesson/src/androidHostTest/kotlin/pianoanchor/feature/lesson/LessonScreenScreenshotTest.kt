package pianoanchor.feature.lesson

import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pianoanchor.core.model.LessonStep
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
import pianoanchor.engine.lesson.TryRun
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
    /** Animations off, so a card animation holds its midpoint frame instead of keeping the screen from going idle. */
    @Before
    fun animationsOff() {
        Settings.Global.putFloat(
            RuntimeEnvironment.getApplication().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
    }

    private fun capture(
        name: String,
        state: LessonState,
        moreOpen: Boolean = false,
        reduceMotion: Boolean = true,
    ) {
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            captureRoboImage("screenshots/LessonScreen_${name}_$theme.png") {
                PianoAnchorTheme(darkTheme = dark) {
                    LessonScreen(state, LessonActions(), startMoreOpen = moreOpen, reduceMotion = reduceMotion)
                }
            }
        }
    }

    @Test
    fun why() = capture("why", lessonState(LessonStep.WHY))

    @Test
    fun whyWithTheCardOpened() = capture("why_more_open", lessonState(LessonStep.WHY), moreOpen = true)

    @Test
    fun whyWithThePedalHint() = capture("why_pedal_hint", lessonState(LessonStep.WHY).copy(pedalControl = true))

    @Test
    fun seeWhileTheDemoSounds() = capture("see", lessonState(LessonStep.SEE, litKeys = setOf(62)).copy(playing = true))

    @Test
    fun seeWithTheHandMidDemoFingerThreeDown() =
        capture(
            "see_hand",
            lessonState(
                LessonStep.SEE,
                litKeys = setOf(64),
                seeHand = cPositionHand(),
                seeElapsedMs = 2 * Tunables.PLAYBACK_NOTE_GAP_MS,
                playing = true,
            ),
            reduceMotion = false,
        )

    @Test
    fun seeWithTheHandAndReducedMotion() =
        capture(
            "see_hand_reduced_motion",
            lessonState(
                LessonStep.SEE,
                litKeys = setOf(64),
                seeHand = cPositionHand(),
                seeElapsedMs = 2 * Tunables.PLAYBACK_NOTE_GAP_MS,
                playing = true,
            ),
            reduceMotion = true,
        )

    @Test
    fun seeAfterTheDemoSaysYourTurn() {
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            captureRoboImage("screenshots/LessonKeyStrip_your_turn_$theme.png") {
                PianoAnchorTheme(darkTheme = dark) {
                    LessonKeyStrip(
                        lessonState(LessonStep.SEE, seeHand = cPositionHand()),
                        Modifier.padding(8.dp),
                        reduceMotion = true,
                        fade = HandFade(0f, cue = true),
                    )
                }
            }
        }
    }

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
    fun tryWithTwoCleanRepsOfThree() =
        capture(
            "try_clean_reps",
            lessonState(
                LessonStep.TRY,
                Skills.C_CHORD,
                TryRun(checkNotNull(Curriculum.tryCheck(Skills.C_CHORD.id))).progress,
            ).copy(cleanReps = 2, repsNeeded = 3),
        )

    @Test
    fun tryRhythmMidWay() =
        capture(
            "try_rhythm",
            lessonState(
                LessonStep.TRY,
                Skills.EIGHTH_NOTES,
                rhythmProgress(0, 500, 1_000, 1_500),
                litKeys = setOf(60),
            ),
        )

    @Test
    fun tryRhythmAfterAnEarlyTap() =
        capture(
            "try_rhythm_early",
            lessonState(LessonStep.TRY, Skills.EIGHTH_NOTES, rhythmProgress(0, 500, 700), litKeys = setOf(60)),
        )

    @Test
    fun seeARhythmNamesTheCount() =
        capture("see_rhythm", lessonState(LessonStep.SEE, Skills.EIGHTH_NOTES, litKeys = setOf(60)))

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
