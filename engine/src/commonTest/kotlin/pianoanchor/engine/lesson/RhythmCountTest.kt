package pianoanchor.engine.lesson

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RhythmCountTest {
    @Test
    fun `eighths are counted one and two and`() = assertEquals("1 & 2 & 3 & 4 &", rhythmCount(List(8) { 480 }))

    @Test
    fun `a pair among quarters skips the empty halves`() =
        assertEquals("1 2 3 & 4", rhythmCount(listOf(960, 960, 480, 480, 960)))

    @Test
    fun `a dotted quarter is held through the next beat`() =
        assertEquals("1 (2) & 3", rhythmCount(listOf(1440, 480, 1920)))

    @Test
    fun `the beat numbers start again after four`() = assertEquals("1 2 3 4 1", rhythmCount(List(5) { 960 }))

    @Test
    fun `no lengths cannot be counted`() {
        assertFailsWith<IllegalArgumentException> { rhythmCount(emptyList()) }
    }

    @Test
    fun `lengths shorter than an eighth cannot be counted`() {
        assertFailsWith<IllegalArgumentException> { rhythmCount(listOf(240, 240, 480)) }
    }

    @Test
    fun `a zero length cannot be counted`() {
        assertFailsWith<IllegalArgumentException> { rhythmCount(listOf(0, 480)) }
    }
}
