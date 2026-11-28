package pianoanchor.ui.shell

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.engine.lesson.KeyAccidental
import pianoanchor.engine.lesson.KeyName
import pianoanchor.engine.lesson.Letter
import kotlin.test.assertEquals

/** The key name text: a letter, with the accidental's format around it. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [NAME_SDK])
class KeyNameTextTest {
    private fun text(name: KeyName): String {
        var result = ""
        runComposeUiTest { setContent { result = keyNameText(name) } }
        return result
    }

    @Test
    fun naturalIsTheLetter() = assertEquals("C", text(KeyName(Letter.C, KeyAccidental.NATURAL)))

    @Test
    fun sharpPutsTheSignAfterTheLetter() = assertEquals("C♯", text(KeyName(Letter.C, KeyAccidental.SHARP)))

    @Test
    fun flatPutsTheSignAfterTheLetter() = assertEquals("D♭", text(KeyName(Letter.D, KeyAccidental.FLAT)))

    @Test
    fun everyLetterHasItsText() =
        assertEquals("CDEFGAB", Letter.entries.joinToString("") { text(KeyName(it, KeyAccidental.NATURAL)) })
}

private const val NAME_SDK = 36
