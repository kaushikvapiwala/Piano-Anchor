package pianoanchor.feature.today

import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.Release
import pianoanchor.core.model.RunMode
import pianoanchor.core.model.Skill
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.EarSkills
import pianoanchor.engine.content.Skills
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.session.Card
import pianoanchor.engine.session.Session
import pianoanchor.engine.session.SessionRunner
import pianoanchor.engine.session.WeekStatus
import pianoanchor.engine.sight.SightReadingGenerator
import pianoanchor.ui.shell.PianoAnchorTheme
import kotlin.time.Instant

/**
 * Golden screenshots of Today in light and dark. `./gradlew :feature:today:recordRoborazziAndroidHostTest` rewrites
 * them under `feature/today/screenshots/`; fastCheck verifies them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land-xxhdpi")
class TodayScreenScreenshotTest {
    private fun capture(
        name: String,
        state: TodayState,
    ) {
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            captureRoboImage("screenshots/TodayScreen_${name}_$theme.png") {
                PianoAnchorTheme(darkTheme = dark) { TodayScreen(state, TodayActions()) }
            }
        }
    }

    private fun passage(id: String) = checkNotNull(Curriculum.passage("builtin:$id"))

    private val piano = PianoChip(MidiDevice("ble:AA:BB", "FP-30X"), Transport.BLE, latencyMs = 18)

    private fun state(
        session: Session,
        week: WeekStatus? = week(),
        piano: PianoChip? = this.piano,
        pedalControl: Boolean = false,
        doneToday: Boolean = false,
        greeting: Greeting = Greeting.MORNING,
    ) = TodayState(
        session = session,
        steps = SessionRunner.steps(session),
        doneToday = doneToday,
        greeting = greeting,
        piano = piano,
        week = week,
        practicedDays = 9,
        pedalControl = pedalControl,
    )

    private fun week(
        played: Int = 3,
        paused: Boolean = false,
        pausesLeft: Int = 2,
    ) = WeekStatus(LocalDate(2026, 11, 16), played, 4, paused, pausesLeft, !paused && pausesLeft > 0, 3)

    private val threeSteps =
        Session(
            cards =
                listOf(
                    Card.ColdOpen(
                        passage("middle-c-march"),
                        TempoRung.WAIT,
                        5,
                        Skills.MIDDLE_C,
                        Instant.parse("2026-11-10T09:00:00Z"),
                        RunMode.PRACTICE,
                    ),
                    Card.Lesson(
                        passage("skipping-stones"),
                        TempoRung.WAIT,
                        9,
                        RIGHT_HAND_SKIPS,
                        listOf(RIGHT_HAND_STEPS),
                    ),
                    Card.Song(passage("hot-cross-buns"), TempoRung.P70, 6),
                    Card.Ear(listOf(EarSkills.HIGHER_LOWER, EarSkills.STEP_SKIP), 3),
                    Card.SightReading(2, SightReadingGenerator.passage(2, SIGHT_SEED), 2),
                ),
            reviewMinutes = 5,
            lessonMinutes = 9,
            songMinutes = 6,
            earMinutes = 3,
            moreDue = emptyList(),
            sightMinutes = 2,
        )

    private val songOnly =
        Session(
            cards = listOf(Card.Song(passage("hot-cross-buns"), TempoRung.P85, 20)),
            reviewMinutes = 0,
            lessonMinutes = 0,
            songMinutes = 20,
            earMinutes = 0,
            moreDue = emptyList(),
        )

    @Test
    fun storageError() = capture("storage_error", TodayState(storageFailed = true))

    @Test
    fun connectingPiano() = capture("connecting", TodayState(connecting = true, week = week(2)))

    @Test
    fun sessionHero() = capture("session_hero", state(threeSteps))

    @Test
    fun heroWithThePedalHint() = capture("pedal_hint", state(threeSteps, pedalControl = true))

    @Test
    fun allCaughtUp() = capture("all_caught_up", state(songOnly, greeting = Greeting.EVENING, week = week(4)))

    @Test
    fun doneForToday() = capture("done_today", state(songOnly, doneToday = true, week = week(4)))

    @Test
    fun pausedWeek() = capture("paused_week", state(threeSteps, week = week(0, paused = true, pausesLeft = 1)))

    @Test
    fun noPausesLeft() = capture("no_pauses_left", state(threeSteps, week = week(1, pausesLeft = 0)))

    @Test
    fun noPiano() = capture("no_piano", state(songOnly, piano = null, greeting = Greeting.AFTERNOON))

    @Test
    fun updateBanner() =
        capture(
            "update_banner",
            state(songOnly).copy(
                update = Release("0.2.0", "https://github.com/kaushikvapiwala/Piano-Anchor/releases/tag/v0.2.0"),
            ),
        )
}

/** Android API level the screenshots render with (the newest Robolectric supports). */
private const val SDK = 36

private const val SIGHT_SEED = 20_000L

// Skills as the goldens show them, fixed here so renaming curriculum skills doesn't redraw the Today screen.
private val RIGHT_HAND_STEPS = Skill("rh-steps", "Right hand steps", emptySet())
private val RIGHT_HAND_SKIPS = Skill("rh-skips", "Right hand skips (thirds)", setOf(RIGHT_HAND_STEPS.id))
private val FIND_EVERY_C = Skill("kb-find-c", "Find every C", emptySet())
