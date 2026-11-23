package pianoanchor.feature.today

import com.github.takahirom.roborazzi.captureRoboImage
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

    @Test
    fun storageError() = capture("storage_error", TodayState(storageFailed = true))

    @Test
    fun connectingPiano() = capture("connecting", TodayState(connecting = true, daysThisWeek = 2))

    @Test
    fun earCard() =
        capture(
            "ear_card",
            TodayState(
                session =
                    Session(
                        cards =
                            listOf(
                                Card.Song(passage("hot-cross-buns"), TempoRung.P70, 6),
                                Card.Ear(listOf(EarSkills.HIGHER_LOWER, EarSkills.STEP_SKIP, EarSkills.THIRDS), 3),
                            ),
                        reviewMinutes = 4,
                        lessonMinutes = 7,
                        songMinutes = 6,
                        earMinutes = 3,
                        moreDue = emptyList(),
                    ),
                piano = piano,
                daysThisWeek = 3,
                practicedDays = 9,
            ),
        )

    @Test
    fun threeCards() =
        capture(
            "three_cards",
            TodayState(
                session =
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
                            ),
                        reviewMinutes = 5,
                        lessonMinutes = 9,
                        songMinutes = 6,
                        earMinutes = 0,
                        moreDue = emptyList(),
                    ),
                piano = piano,
                daysThisWeek = 3,
                practicedDays = 9,
            ),
        )

    @Test
    fun allCaughtUp() =
        capture(
            "all_caught_up",
            TodayState(
                session =
                    Session(
                        cards = listOf(Card.Song(passage("hot-cross-buns"), TempoRung.P85, 20)),
                        reviewMinutes = 0,
                        lessonMinutes = 0,
                        songMinutes = 20,
                        earMinutes = 0,
                        moreDue = emptyList(),
                    ),
                greeting = Greeting.EVENING,
                piano = piano,
                daysThisWeek = 4,
                practicedDays = 12,
            ),
        )

    @Test
    fun noPiano() =
        capture(
            "no_piano",
            TodayState(
                session =
                    Session(
                        cards =
                            listOf(
                                Card.Lesson(passage("all-the-cs"), TempoRung.WAIT, 20, FIND_EVERY_C, emptyList()),
                            ),
                        reviewMinutes = 0,
                        lessonMinutes = 20,
                        songMinutes = 0,
                        earMinutes = 0,
                        moreDue = emptyList(),
                    ),
                greeting = Greeting.AFTERNOON,
            ),
        )

    @Test
    fun updateBanner() =
        capture(
            "update_banner",
            TodayState(
                session =
                    Session(
                        cards = listOf(Card.Song(passage("hot-cross-buns"), TempoRung.P85, 20)),
                        reviewMinutes = 0,
                        lessonMinutes = 0,
                        songMinutes = 20,
                        earMinutes = 0,
                        moreDue = emptyList(),
                    ),
                piano = piano,
                update = Release("0.2.0", "https://github.com/kaushikvapiwala/Piano-Anchor/releases/tag/v0.2.0"),
            ),
        )
}

/** Android API level the screenshots render with (the newest Robolectric supports). */
private const val SDK = 36

// Skills as the goldens show them, fixed here so renaming curriculum skills doesn't redraw the Today screen.
private val RIGHT_HAND_STEPS = Skill("rh-steps", "Right hand steps", emptySet())
private val RIGHT_HAND_SKIPS = Skill("rh-skips", "Right hand skips (thirds)", setOf(RIGHT_HAND_STEPS.id))
private val FIND_EVERY_C = Skill("kb-find-c", "Find every C", emptySet())
