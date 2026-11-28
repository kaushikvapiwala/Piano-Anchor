package pianoanchor.feature.today

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pianoanchor.core.model.BarRange
import pianoanchor.core.model.Skill
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.session.Card
import pianoanchor.engine.session.SessionStep
import pianoanchor.ui.shell.PianoAnchorTheme

/** Golden screenshots of the between-steps card and the recap, light and dark, with the ring held still. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w915dp-h412dp-land-xxhdpi")
class SessionFlowScreenshotTest {
    private val passage = checkNotNull(Curriculum.passage("builtin:hot-cross-buns"))
    private val steps =
        listOf<SessionStep>(
            SessionStep.Learn(Card.Lesson(passage, TempoRung.WAIT, 9, LEARNED, emptyList()), 9),
            SessionStep.Practise(Card.Song(passage, TempoRung.P70, 6), 6),
        )

    private fun capture(
        name: String,
        state: SessionFlowState,
    ) {
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            captureRoboImage("screenshots/SessionFlow_${name}_$theme.png") {
                PianoAnchorTheme(darkTheme = dark) {
                    SessionFlowScreen(state, SessionFlowActions(), reduceMotion = true)
                }
            }
        }
    }

    @Test
    fun between() =
        capture("between", SessionFlowState(steps, 0, Between(steps[1], 7, paused = false), pedalHint = true))

    @Test
    fun betweenPaused() = capture("between_paused", SessionFlowState(steps, 0, Between(steps[1], 4, paused = true)))

    @Test
    fun recap() =
        capture(
            "recap",
            SessionFlowState(
                steps,
                1,
                recap = SessionRecap(15, 2, listOf(LEARNED), RecapTip("Hot Cross Buns", BarRange(3, 4))),
            ),
        )
}

private val LEARNED = Skill("rh-steps", "Right hand steps", emptySet())
