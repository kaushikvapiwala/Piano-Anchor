package pianoanchor.feature.today

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.core.model.Release
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.session.Card
import pianoanchor.engine.session.Session
import pianoanchor.ui.shell.PianoAnchorTheme

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

    @Test
    fun atDoubleFontScaleBothPanesScrollTheirLastElementIntoView() =
        runComposeUiTest {
            val passage = checkNotNull(Curriculum.passage("builtin:hot-cross-buns"))
            val session =
                Session(
                    cards = List(3) { Card.Song(passage, TempoRung.P70, 6) },
                    reviewMinutes = 0,
                    lessonMinutes = 0,
                    songMinutes = 18,
                    earMinutes = 0,
                    moreDue = emptyList(),
                )
            val state = TodayState(session = session, update = Release("0.2.0", "https://example.org/release"))
            setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = LARGE_FONT_SCALE)) {
                    PianoAnchorTheme { TodayScreen(state, TodayActions()) }
                }
            }

            onNodeWithText("Not now").performScrollTo().assertIsDisplayed()
            onNodeWithText("Today's 18 minutes", substring = true).performScrollTo().assertIsDisplayed()
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
