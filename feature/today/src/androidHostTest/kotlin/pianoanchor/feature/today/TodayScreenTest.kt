package pianoanchor.feature.today

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.core.model.Release
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.session.Card
import pianoanchor.engine.session.Session
import pianoanchor.engine.session.SessionRunner
import pianoanchor.engine.session.WeekStatus
import pianoanchor.ui.shell.PianoAnchorTheme
import kotlin.test.assertEquals

/** The piano chip's text and TalkBack description in the states with no connected piano. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [CHIP_TEST_SDK], qualifiers = "w915dp-h412dp-land-xxhdpi")
class TodayScreenTest {
    @Test
    fun theConnectingChipSaysConnectingAndOffersPianos() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { TodayScreen(TodayState(connecting = true), TodayActions()) } }

            onNodeWithText("Connecting…").assertExists()
            onNodeWithContentDescription("Connecting to your piano. Opens Pianos.").assertExists()
        }

    @Test
    fun theChipWithNoPianoOffersPianos() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { TodayScreen(TodayState(), TodayActions()) } }

            onNodeWithText("No piano").assertExists()
            onNodeWithContentDescription("No piano. Opens Pianos.").assertExists()
        }

    private val passage = checkNotNull(Curriculum.passage("builtin:hot-cross-buns"))

    private val lesson = Card.Lesson(passage, TempoRung.WAIT, 5, Skills.HAND_SHAPE, emptyList())
    private val song = Card.Song(passage, TempoRung.P70, 6)
    private val ear = Card.Ear(listOf(Skills.MIDDLE_C), 3)
    private val session = Session(listOf(lesson, song, ear), 0, 5, 6, 3, emptyList())

    private fun state(
        week: WeekStatus? = null,
        doneToday: Boolean = false,
    ) = TodayState(
        session = session,
        steps = SessionRunner.steps(session),
        doneToday = doneToday,
        week = week,
        update = Release("0.2.0", "https://example.org/release"),
    )

    private fun week(
        paused: Boolean = false,
        pausesLeft: Int = 2,
    ) = WeekStatus(LocalDate(2026, 11, 16), 2, 4, paused, pausesLeft, !paused && pausesLeft > 0, 3)

    @Test
    fun atDoubleFontScaleBothPanesScrollTheirLastElementIntoView() =
        runComposeUiTest {
            setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = LARGE_FONT_SCALE)) {
                    PianoAnchorTheme { TodayScreen(state(week()), TodayActions()) }
                }
            }

            onNodeWithText("Not now").performScrollTo().assertIsDisplayed()
            onNodeWithText("Choose steps").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun theHeroShowsTheMinutesAndStartOpensTheSession() =
        runComposeUiTest {
            val starts = mutableListOf<Set<Int>>()
            setContent {
                PianoAnchorTheme { TodayScreen(state(), TodayActions(onStartSession = { starts += it })) }
            }

            onNodeWithText("Today · about 14 minutes").assertIsDisplayed()
            onNodeWithText("Start session").performClick()

            assertEquals(listOf<Set<Int>>(emptySet()), starts)
        }

    @Test
    fun theTrimViewUpdatesTheStartButtonAndSkipsTheSwitchedOffStep() =
        runComposeUiTest {
            val starts = mutableListOf<Set<Int>>()
            setContent {
                PianoAnchorTheme { TodayScreen(state(), TodayActions(onStartSession = { starts += it })) }
            }

            onNodeWithText("Choose steps").performClick()
            onNodeWithText("Start 3 steps · 14 min").assertIsDisplayed()
            onNodeWithContentDescription("Learn: ${Skills.HAND_SHAPE.title}, 5 minutes").performClick()
            onNodeWithText("Start 2 steps · 9 min").assertIsDisplayed()
            onNodeWithText("Start 2 steps · 9 min").performClick()

            assertEquals(listOf(setOf(0)), starts)
        }

    @Test
    fun withEveryStepSwitchedOffStartIsDisabled() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { TodayScreen(state(), TodayActions()) } }

            onNodeWithText("Choose steps").performClick()
            onNodeWithContentDescription("Learn: ${Skills.HAND_SHAPE.title}, 5 minutes").performClick()
            onNodeWithContentDescription("Practise: ${passage.title}, 6 minutes").performClick()
            onNodeWithContentDescription("Ear training, 3 minutes").performClick()

            onNodeWithText("Start 0 steps · 0 min").assertIsNotEnabled()
            onNodeWithText("Back to the whole session").performClick()
            onNodeWithText("Start session").assertIsDisplayed()
        }

    @Test
    fun theWeekCardOffersAPauseThatAsksToConfirm() =
        runComposeUiTest {
            var paused = 0
            setContent {
                PianoAnchorTheme { TodayScreen(state(week()), TodayActions(onPauseWeek = { paused++ })) }
            }

            onNodeWithText("This week · 2 of 4 days").assertIsDisplayed()
            onNodeWithText("3-week streak").assertIsDisplayed()
            onNodeWithText("Paused weeks never break your streak.").assertIsDisplayed()
            onNodeWithText("Pause this week").performClick()
            onNodeWithText("Cancel").performClick()
            assertEquals(0, paused)
            onNodeWithText("Pause this week").performClick()
            onNodeWithText("Pause", substring = false).performClick()

            assertEquals(1, paused)
        }

    @Test
    fun aPausedWeekHidesThePauseButton() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { TodayScreen(state(week(paused = true, pausesLeft = 1)), TodayActions()) } }

            onNodeWithText("This week is paused").assertIsDisplayed()
            onNodeWithText("Pause this week").assertDoesNotExist()
        }

    @Test
    fun noPauseButtonWhenNoPauseIsLeft() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { TodayScreen(state(week(pausesLeft = 0)), TodayActions()) } }

            onNodeWithText("Pause this week").assertDoesNotExist()
            onNodeWithContentDescription("0 of 2 pauses left").assertExists()
        }

    @Test
    fun afterTheRecapTodayIsDoneForToday() =
        runComposeUiTest {
            val starts = mutableListOf<Set<Int>>()
            setContent {
                PianoAnchorTheme {
                    TodayScreen(state(week(), doneToday = true), TodayActions(onStartSession = { starts += it }))
                }
            }

            onNodeWithText("Done for today").assertIsDisplayed()
            onNodeWithText("Choose steps").assertDoesNotExist()
            onNodeWithText("Practise more").performClick()

            assertEquals(1, starts.size)
        }

    @Test
    fun theErrorCardTopsThePlan() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { TodayScreen(TodayState(storageFailed = true), TodayActions()) } }

            onNodeWithText("Retry").assertIsDisplayed()
        }
}

private const val LARGE_FONT_SCALE = 2f
private const val CHIP_TEST_SDK = 36
