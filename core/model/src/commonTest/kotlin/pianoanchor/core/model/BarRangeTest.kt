package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BarRangeTest {
    @Test
    fun `bars counts both ends`() {
        assertEquals(2, BarRange(5, 6).bars)
        assertEquals(1, BarRange(3, 3).bars)
    }

    @Test
    fun `a range must start at bar 1 or later`() {
        assertFailsWith<IllegalArgumentException> { BarRange(0, 2) }
    }

    @Test
    fun `a range must not end before it starts`() {
        assertFailsWith<IllegalArgumentException> { BarRange(4, 3) }
    }
}
