package pianoanchor.feature.songs

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Pieces
import pianoanchor.engine.content.Skills
import pianoanchor.ui.shell.PianoAnchorTheme
import kotlin.test.assertEquals

/** The Songs grid in landscape: it lists the pieces and scrolls to the last one at double font scale. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land")
class SongsScreenTest {
    // Nothing played, so each card reads "<title>. New" and can be found by that description.
    private val pieces = Pieces.list(Curriculum, emptyMap())

    @Test
    fun theHeadingAndTheFirstPieceShow() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { SongsScreen(SongsState(pieces = pieces), SongsActions()) } }

            // The rail label and the heading.
            onAllNodesWithText("Songs").assertCountEquals(2)
            onNodeWithContentDescription("${pieces.first().passage.title}. New").assertIsDisplayed()
        }

    @Test
    fun everyRowOpensAndNoRowIsLocked() =
        runComposeUiTest {
            val opened = mutableListOf<String>()
            setContent {
                PianoAnchorTheme {
                    SongsScreen(SongsState(pieces = pieces), SongsActions(onOpen = { opened += it.passage.id }))
                }
            }

            val first = onNodeWithContentDescription("${pieces.first().passage.title}. New")
            first.assertHasClickAction()
            first.performClick()
            assertEquals(listOf(pieces.first().passage.id), opened)
            onAllNodesWithText("Needs", substring = true).assertCountEquals(0)
            onAllNodesWithText("Locked", substring = true).assertCountEquals(0)
            onAllNodesWithText("\uD83D\uDD12", substring = true).assertCountEquals(0)
        }

    @Test
    fun aPassedPieceShowsATickAndTheSuggestedConceptsPiecesShowANextChip() =
        runComposeUiTest {
            val hot = pieces.first { it.passage.id == "builtin:hot-cross-buns" }
            val mary = pieces.first { it.passage.id == "builtin:mary-had-a-little-lamb" }
            val state =
                SongsState(
                    pieces = pieces,
                    ticked =
                        setOf(
                            Skills.FINGER_NUMBER_TUNES.id,
                            Skills.STEPS_AND_SKIPS.id,
                            Skills.QUARTER_AND_HALF.id,
                        ),
                    next = Skills.BARS_AND_COUNTING.id,
                )
            setContent { PianoAnchorTheme { SongsScreen(state, SongsActions()) } }

            onNodeWithContentDescription("${hot.passage.title}. New. Passed").assertIsDisplayed()
            onNodeWithContentDescription("${mary.passage.title}. New. Passed").assertIsDisplayed()
            val lightly = pieces.first { it.passage.id == "builtin:lightly-row" }
            onNodeWithContentDescription("${lightly.passage.title}. New. Suggested next").assertIsDisplayed()
        }

    @Test
    fun atDoubleFontScaleTheLastPieceScrollsIntoView() =
        runComposeUiTest {
            setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = LARGE_FONT_SCALE)) {
                    PianoAnchorTheme { SongsScreen(SongsState(pieces = pieces), SongsActions()) }
                }
            }

            val last = "${pieces.last().passage.title}. New"
            onNode(hasScrollAction()).performScrollToNode(hasContentDescription(last))
            onNodeWithContentDescription(last).assertIsDisplayed()
        }
}

private const val LARGE_FONT_SCALE = 2f
private const val SDK = 36
