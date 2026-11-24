package pianoanchor.ui.shell

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.engine.guidance.wrongKeyHint
import kotlin.test.assertEquals

/** The shared wrong-key line: one case per landmark, plus the same letter in another octave. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [HINT_SDK])
class WrongKeyHintTextTest {
    private fun text(
        played: Int,
        expected: Int,
    ): String {
        var result = ""
        runComposeUiTest { setContent { result = wrongKeyHintText(wrongKeyHint(played, expected)) } }
        return result
    }

    @Test
    fun leftOfTwoNamesTheKeyBesideTheTwoBlackKeys() =
        assertEquals("That was D4. C is just left of the two black keys.", text(D4, C4))

    @Test
    fun betweenTwoNamesTheKeyBetweenTheTwoBlackKeys() =
        assertEquals("That was D♯4. D is between the two black keys.", text(D4 + 1, D4))

    @Test
    fun rightOfTwo() = assertEquals("That was D4. E is just right of the two black keys.", text(D4, E4))

    @Test
    fun leftOfThree() = assertEquals("That was G4. F is just left of the three black keys.", text(G4, F4))

    @Test
    fun inThreeLow() =
        assertEquals("That was F4. G is in the three black keys, just left of the middle one.", text(F4, G4))

    @Test
    fun inThreeHigh() =
        assertEquals("That was G4. A is in the three black keys, just right of the middle one.", text(G4, A4))

    @Test
    fun rightOfThree() = assertEquals("That was A4. B is just right of the three black keys.", text(A4, B4))

    @Test
    fun blackKeyIsNamedFromTheWhiteKeyBelow() =
        assertEquals("That was D4. C♯ is the black key just right of C.", text(D4, C4 + 1))

    @Test
    fun sameLetterInAnotherOctaveSaysSo() =
        assertEquals("That was C4. Same letter, other octave: try C5.", text(C4, C4 + 12))
}

private const val HINT_SDK = 36
private const val C4 = 60
private const val D4 = 62
private const val E4 = 64
private const val F4 = 65
private const val G4 = 67
private const val A4 = 69
private const val B4 = 71
