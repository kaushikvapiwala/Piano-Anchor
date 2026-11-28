package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TryCheckTest {
    @Test
    fun `every kind keeps its parts`() {
        assertEquals(60, TryCheck.PlayKey(60, lit = true).note)
        assertEquals(true, TryCheck.PlayKey(60, lit = true).lit)
        TryCheck.FindAll(0, 21..108).let {
            assertEquals(0, it.pitchClass)
            assertEquals(21..108, it.range)
        }
        assertEquals(listOf(60, 62), TryCheck.Sequence(listOf(60, 62)).notes)
        TryCheck.Repeat(60, 8, 60).let { assertEquals(Triple(60, 8, 60), Triple(it.note, it.times, it.bpm)) }
        TryCheck.Hold(60, 2, 70).let { assertEquals(Triple(60, 2, 70), Triple(it.note, it.clicks, it.bpm)) }
        assertEquals(60, TryCheck.SoftThenLoud(60).note)
        TryCheck.StressBeatOne(60, 2, 60).let { assertEquals(Triple(60, 2, 60), Triple(it.note, it.bars, it.bpm)) }
        TryCheck.Rhythm(60, listOf(480, 480), 60).let {
            assertEquals(Triple(60, listOf(480, 480), 60), Triple(it.note, it.lengths, it.bpm))
        }
        assertEquals(setOf(60, 64, 67), TryCheck.Chord(setOf(60, 64, 67)).notes)
        assertEquals("hot-cross-buns", TryCheck.Passage("hot-cross-buns").passageId)
        assertEquals("AnyKey", TryCheck.AnyKey.toString())
    }

    @Test
    fun `a rhythm needs two lengths and every length above zero`() {
        assertFailsWith<IllegalArgumentException> { TryCheck.Rhythm(60, listOf(480), 60) }
        assertFailsWith<IllegalArgumentException> { TryCheck.Rhythm(60, listOf(480, 0), 60) }
        assertFailsWith<IllegalArgumentException> { TryCheck.Rhythm(60, listOf(480, -480), 60) }
    }

    @Test
    fun pedalAndLegatoChecksKeepTheirPartsAndValidate() {
        assertEquals(listOf(60, 62), TryCheck.Legato(listOf(60, 62)).notes)
        assertEquals(setOf(48), TryCheck.PedalHold(setOf(48)).chord)
        assertEquals(listOf(setOf(48), setOf(53)), TryCheck.PedalChange(listOf(setOf(48), setOf(53))).chords)
        assertFailsWith<IllegalArgumentException> { TryCheck.Legato(listOf(60)) }
        assertFailsWith<IllegalArgumentException> { TryCheck.PedalHold(emptySet()) }
        assertFailsWith<IllegalArgumentException> { TryCheck.PedalChange(listOf(setOf(48))) }
        assertFailsWith<IllegalArgumentException> { TryCheck.PedalChange(listOf(setOf(48), emptySet())) }
    }
}
