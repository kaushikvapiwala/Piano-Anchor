package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MemoryStateTest {
    @Test
    fun `keeps valid values`() {
        assertEquals(2.5, MemoryState(2.5, 1.0).stability)
        assertEquals(10.0, MemoryState(0.001, 10.0).difficulty)
    }

    @Test
    fun `rejects zero stability`() {
        assertFailsWith<IllegalArgumentException> { MemoryState(0.0, 5.0) }
    }

    @Test
    fun `rejects difficulty outside 1 to 10`() {
        assertFailsWith<IllegalArgumentException> { MemoryState(1.0, 0.9) }
        assertFailsWith<IllegalArgumentException> { MemoryState(1.0, 10.1) }
    }

    @Test
    fun `ratings run from AGAIN to EASY`() {
        assertEquals(listOf("AGAIN", "HARD", "GOOD", "EASY"), Rating.entries.map { it.name })
    }
}
