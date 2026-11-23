package pianoanchor.feature.lesson

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pianoanchor.core.model.CARD_SCENE_LOOP_MS
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
import pianoanchor.ui.shell.PianoAnchorTheme
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

/** The card animation's frames and semantics, on Robolectric. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land-xxhdpi")
class CardAnimationTest {
    private val scene = checkNotNull(Curriculum.scene(Skills.MIDDLE_C.id))

    private fun androidx.compose.ui.test.ComposeUiTest.pixels(tag: String): IntArray {
        val bitmap = onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        return IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }
    }

    /** Sets up one stage box and returns a function that shows [content] in it and gives its pixels. */
    private fun androidx.compose.ui.test.ComposeUiTest.frames(): (@Composable () -> Unit) -> IntArray {
        val shown = mutableStateOf<@Composable () -> Unit>({})
        setContent { PianoAnchorTheme { Box(Modifier.size(320.dp, 200.dp).testTag("frame")) { shown.value() } } }
        return { content ->
            shown.value = content
            waitForIdle()
            pixels("frame")
        }
    }

    @Test
    fun theFrameAtATimeShowsTheLitKeysOfTheCuesActiveThen() =
        runComposeUiTest {
            val frame = frames()
            val lit = frame { CardFrame(scene, CARD_SCENE_MIDPOINT_MS) }
            val rest = frame { CardFrame(scene, CARD_SCENE_LOOP_MS) }
            assertFalse(lit.contentEquals(rest))
        }

    @Test
    fun reduceMotionShowsTheMidpointFrame() =
        runComposeUiTest {
            val frame = frames()
            val held = frame { CardAnimation(scene, reduceMotion = true) }
            val mid = frame { CardFrame(scene, CARD_SCENE_MIDPOINT_MS) }
            assertContentEquals(mid, held)
        }

    @Test
    fun talkBackReadsTheDescription() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { CardAnimation(scene, reduceMotion = true) } }
            onNodeWithContentDescription(scene.description).assertIsDisplayed()
        }

    @Test
    fun theAnimationRunsWhenMotionIsAllowed() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent { PianoAnchorTheme { CardAnimation(scene, Modifier.testTag("moving"), reduceMotion = false) } }
            mainClock.advanceTimeBy(1000)
            onNodeWithContentDescription(scene.description).assertIsDisplayed()
        }
}
