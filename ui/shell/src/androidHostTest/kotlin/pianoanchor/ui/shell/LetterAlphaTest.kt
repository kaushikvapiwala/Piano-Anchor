package pianoanchor.ui.shell

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.engine.Tunables
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The fade of a key strip's letter layer when the note labels load. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land-xxhdpi")
class LetterAlphaTest {
    @Test
    fun lettersLoadedLaterFadeInOverTheTunableDuration() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            var loaded by mutableStateOf(false)
            lateinit var alpha: State<Float>
            setContent { alpha = rememberLetterAlpha(loaded, reduceMotion = false) }
            assertEquals(0f, alpha.value)

            runOnUiThread { loaded = true }
            Snapshot.sendApplyNotifications()
            mainClock.advanceTimeBy(FRAMES_TO_START)
            mainClock.advanceTimeBy(Tunables.KEY_LETTERS_FADE_MS / 2L)
            assertTrue(alpha.value > 0f && alpha.value < 1f, alpha.value.toString())

            mainClock.advanceTimeBy(Tunables.KEY_LETTERS_FADE_MS.toLong())
            assertEquals(1f, alpha.value)
        }

    @Test
    fun withReducedMotionLettersShowAtOnce() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            var loaded by mutableStateOf(false)
            lateinit var alpha: State<Float>
            setContent { alpha = rememberLetterAlpha(loaded, reduceMotion = true) }

            runOnUiThread { loaded = true }
            Snapshot.sendApplyNotifications()
            mainClock.advanceTimeBy(FRAMES_TO_START)

            assertEquals(1f, alpha.value)
        }

    @Test
    fun lettersLoadedAtFirstCompositionShowAtOnce() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            lateinit var alpha: State<Float>
            setContent { alpha = rememberLetterAlpha(true, reduceMotion = false) }

            assertEquals(1f, alpha.value)
        }
}

private const val SDK = 36

/** Time for the recomposition and the animation start after the label state changes. */
private const val FRAMES_TO_START = 50L
