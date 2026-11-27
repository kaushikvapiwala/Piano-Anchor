package pianoanchor.core.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KeySignatureTest {
    @Test
    fun sharpsOutOfRangeThrow() {
        assertFailsWith<IllegalArgumentException> { KeySignature(8) }
        assertFailsWith<IllegalArgumentException> { KeySignature(-8) }
    }

    @Test
    fun cAltersNothing() {
        assertEquals(emptyList(), KeySignature.C.alteredLetters)
    }

    @Test
    fun gAltersF() {
        assertEquals(listOf(3), KeySignature.G.alteredLetters)
    }

    @Test
    fun dAltersFAndC() {
        assertEquals(listOf(3, 0), KeySignature(2).alteredLetters)
    }

    @Test
    fun oneFlatAltersB() {
        assertEquals(listOf(6), KeySignature(-1).alteredLetters)
    }

    @Test
    fun sevenSharpsAlterEveryLetter() {
        assertEquals(listOf(3, 0, 4, 1, 5, 2, 6), KeySignature(7).alteredLetters)
    }

    @Test
    fun passageDefaultsToCMajor() {
        val rights = Rights(RightsStatus.ORIGINAL, "test", "test", LocalDate(2026, 1, 1))
        val step = Step(0, setOf(ScoreNote(MidiNote.MIDDLE_C, TICKS_PER_QUARTER)))
        val passage = Passage("p", "P", listOf(step), TimeSignature(4, 4), 80, rights)
        assertEquals(KeySignature.C, passage.keySignature)
    }
}
