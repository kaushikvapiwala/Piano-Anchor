package pianoanchor.ui.shell

import androidx.compose.ui.graphics.Color
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val MIN_CONTRAST = 4.5f

/** The colour a key letter takes so it reads on every key fill, in both themes. */
class KeyLettersTest {
    private val ink = Color(0xFF1B1B1F)
    private val inkSecondary = Color(0xFF5E5A66)

    @Test
    fun thePreferredColourStaysWhenItContrastsEnough() {
        assertEquals(inkSecondary, keyLetterColor(Color.White, inkSecondary))
    }

    @Test
    fun aLightLetterOnALightKeyFallsBackToDark() {
        val mist = Color(0xFFCFC8D3)
        assertEquals(ink, keyLetterColor(Color(0xFFF4F0F7), mist))
    }

    @Test
    fun aDarkLetterOnADarkKeyFallsBackToWhite() {
        assertEquals(Color.White, keyLetterColor(Color(0xFF2A2730), ink))
    }

    @Test
    fun everyLitFillOfEitherThemeKeepsFourPointFiveToOne() {
        val fills =
            listOf(
                Color(0xFF1F5F8B), // correct, light
                Color(0xFF8CC4E8), // correct, dark
                Color(0xFF9A9A9A), // wrong key, light
                Color(0xFF8A8A8A), // wrong key, dark
                Color(0xFFFBE3B0), // target band
            )
        for (fill in fills) {
            assertTrue(contrast(keyLetterColor(fill, Color.Gray), fill) >= MIN_CONTRAST, "fill $fill")
        }
    }
}
