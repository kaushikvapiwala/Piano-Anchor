package pianoanchor.feature.today

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.Release
import pianoanchor.engine.content.Curriculum
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
@Config(sdk = [SDK], qualifiers = "w411dp-h891dp-xxhdpi")
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
                                    Skills.KB_MIDDLE_C,
                                    Instant.parse("2026-11-10T09:00:00Z"),
                                ),
                                Card.Lesson(
                                    passage("skipping-stones"),
                                    TempoRung.WAIT,
                                    9,
                                    Skills.RH_SKIPS,
                                    listOf(Skills.RH_STEPS),
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
                                Card.Lesson(passage("all-the-cs"), TempoRung.WAIT, 20, Skills.KB_FIND_C, emptyList()),
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
