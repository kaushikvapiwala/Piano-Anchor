package pianoanchor.feature.lesson

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.engine.lesson.NoteLabels
import pianoanchor.engine.lesson.NoteNameStage
import pianoanchor.ui.shell.PianoAnchorTheme
import kotlin.test.assertEquals

private const val C4 = 60
private const val D4 = 62
private const val B4 = 71
private val LETTER = Regex("[A-G][♯♭]?")

/** The letters the lesson key strip writes on its keys at each stage, on Robolectric. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land")
class KeyLettersTest {
    /** The letters on show, in key order (the letters hide from TalkBack, so the unmerged tree is read). */
    private fun ComposeUiTest.lettersOf(
        labels: NoteLabels,
        glow: Set<Int> = emptySet(),
        wrong: Int? = null,
    ): List<String> {
        setContent {
            PianoAnchorTheme { KeyStrip(C4..B4, emptySet(), glow, wrong, labels = labels) }
        }
        return onAllNodes(
            SemanticsMatcher("a key letter") {
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> LETTER.matches(text.text) } == true
            },
            useUnmergedTree = true,
        ).fetchSemanticsNodes().flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
    }

    @Test
    fun allWhiteKeysAreLetteredBeforeKeyNamesArePassed() =
        runComposeUiTest {
            assertEquals(
                listOf("C", "D", "E", "F", "G", "A", "B"),
                lettersOf(NoteLabels(NoteNameStage.ALL_WHITE, sharpsPassed = false, hints = true)),
            )
        }

    @Test
    fun blackKeysGetSharpNamesOnceSharpsArePassed() =
        runComposeUiTest {
            val letters = lettersOf(NoteLabels(NoteNameStage.ALL_WHITE, sharpsPassed = true, hints = true))

            assertEquals(12, letters.size)
            assertEquals(true, "C♯" in letters)
        }

    @Test
    fun onlyTheKeyToPlayKeepsItsLetterUntilKeyNamesAreLearned() =
        runComposeUiTest {
            assertEquals(
                listOf("D"),
                lettersOf(NoteLabels(NoteNameStage.TARGET_ONLY, sharpsPassed = false, hints = true), glow = setOf(D4)),
            )
        }

    @Test
    fun noLettersOnceKeyNamesAreLearned() =
        runComposeUiTest {
            assertEquals(emptyList(), lettersOf(NoteLabels(NoteNameStage.NONE, sharpsPassed = true, hints = true)))
        }

    @Test
    fun aWrongKeyLettersItselfAndTheRightKeyAtAnyStage() =
        runComposeUiTest {
            assertEquals(
                listOf("C", "D"),
                lettersOf(
                    NoteLabels(NoteNameStage.NONE, sharpsPassed = false, hints = true),
                    glow = setOf(D4),
                    wrong = C4,
                ),
            )
        }

    @Test
    fun aStaleWrongKeyLettersNothingOnceTheCheckIsOver() =
        runComposeUiTest {
            assertEquals(
                emptyList(),
                lettersOf(NoteLabels(NoteNameStage.NONE, sharpsPassed = false, hints = true), wrong = C4),
            )
        }

    @Test
    fun offLettersNothingEvenForAWrongKey() =
        runComposeUiTest {
            assertEquals(
                emptyList(),
                lettersOf(
                    NoteLabels(NoteNameStage.NONE, sharpsPassed = false, hints = false),
                    glow = setOf(D4),
                    wrong = C4,
                ),
            )
        }
}
