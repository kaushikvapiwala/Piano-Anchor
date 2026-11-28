package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChordMarkTest {
    @Test
    fun keepsItsFields() {
        val mark = ChordMark(960, 7, ChordQuality.DOMINANT_7)
        assertEquals(960, mark.onsetTicks)
        assertEquals(7, mark.root)
        assertEquals(ChordQuality.DOMINANT_7, mark.quality)
    }

    @Test
    fun acceptsTheBounds() {
        assertEquals(0, ChordMark(0, 0, ChordQuality.MINOR).root)
        assertEquals(11, ChordMark(0, 11, ChordQuality.MINOR).root)
    }

    @Test
    fun rejectsANegativeOnset() {
        assertFailsWith<IllegalArgumentException> { ChordMark(-1, 0, ChordQuality.MAJOR) }
    }

    @Test
    fun rejectsARootOutsideThePitchClasses() {
        assertFailsWith<IllegalArgumentException> { ChordMark(0, 12, ChordQuality.MAJOR) }
        assertFailsWith<IllegalArgumentException> { ChordMark(0, -1, ChordQuality.MAJOR) }
    }
}
