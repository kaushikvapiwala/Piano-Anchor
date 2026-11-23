package pianoanchor.feature.songs

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.PieceStatus
import pianoanchor.engine.content.Pieces
import pianoanchor.ui.shell.PianoAnchorTheme

/** The Songs grid in landscape: it lists the pieces and scrolls to the last one at double font scale. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land")
class SongsScreenTest {
    // All unlocked, so each card reads "<title>. New" and can be found by that description.
    private val pieces =
        Pieces.list(Curriculum, Curriculum.graph, emptyMap(), emptyMap()).map { it.copy(status = PieceStatus.NEW) }

    @Test
    fun theHeadingAndTheFirstPieceShow() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { SongsScreen(SongsState(pieces = pieces), SongsActions()) } }

            // The rail label and the heading.
            onAllNodesWithText("Songs").assertCountEquals(2)
            onNodeWithContentDescription("${pieces.first().passage.title}. New").assertIsDisplayed()
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
