package pianoanchor.ui.shell

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The shared back button: TalkBack reads "Back" once, the arrow mirrors right to left, the size follows the font. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land-xxhdpi")
class BackButtonTest {
    @Test
    fun readsBackAndHidesTheGlyphFromTalkBack() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { BackButton(onClick = {}) } }

            onNodeWithContentDescription("Back").assertHasClickAction()
            onNodeWithText("←").assertDoesNotExist()
        }

    @Test
    fun clickCallsOnClick() =
        runComposeUiTest {
            var clicks = 0
            setContent { PianoAnchorTheme { BackButton(onClick = { clicks++ }) } }

            onNodeWithContentDescription("Back").performClick()

            assertEquals(1, clicks)
        }

    @Test
    fun theArrowPointsRightInRightToLeftLayouts() =
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    PianoAnchorTheme { BackButton(onClick = {}) }
                }
            }

            onNodeWithText("→", useUnmergedTree = true).assertExists()
            onNodeWithText("←", useUnmergedTree = true).assertDoesNotExist()
        }

    @Test
    fun theArrowPointsLeftInLeftToRightLayouts() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { BackButton(onClick = {}) } }

            onNodeWithText("←", useUnmergedTree = true).assertExists()
        }

    @Test
    fun growsWithTheFontAndKeepsTheGlyphInside() =
        runComposeUiTest {
            setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                    PianoAnchorTheme { BackButton(onClick = {}) }
                }
            }

            val button = onNodeWithContentDescription("Back")
            button.assertWidthIsAtLeast(44.dp).assertHeightIsAtLeast(44.dp)
            val box = button.getUnclippedBoundsInRoot()
            val glyph = onNodeWithText("←", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue(glyph.left >= box.left && glyph.right <= box.right, "glyph $glyph outside $box")
            assertTrue(glyph.top >= box.top && glyph.bottom <= box.bottom, "glyph $glyph outside $box")
        }
}

private const val SDK = 36
