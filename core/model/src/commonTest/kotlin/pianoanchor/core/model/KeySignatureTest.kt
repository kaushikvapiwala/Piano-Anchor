package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KeySignatureTest {
    @Test
    fun sharpsOutOfRangeThrow() {
        assertFailsWith<IllegalArgumentException> { KeySignature(8) }
        assertFailsWith<IllegalArgumentException> { KeySignature(-8) }
        assertEquals(7, KeySignature(7).sharps)
        assertEquals(-7, KeySignature(-7).sharps)
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
        assertEquals((0..6).toSet(), KeySignature(7).alteredLetters.toSet())
        assertEquals((0..6).toSet(), KeySignature(-7).alteredLetters.toSet())
    }

    @Test
    fun aPassageIsInCUnlessToldOtherwise() {
        val steps = listOf(Step(0, setOf(ScoreNote(MidiNote.MIDDLE_C, TICKS_PER_QUARTER))))
        val rights = Rights(RightsStatus.ORIGINAL, "test", "test", kotlinx.datetime.LocalDate(2026, 1, 1))
        val passage = Passage("p", "P", steps, TimeSignature(4, 4), 80, rights)
        assertEquals(KeySignature.C, passage.keySignature)
        assertEquals(KeySignature.G, passage.copy(keySignature = KeySignature.G).keySignature)
    }
}
