package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CurriculumUnitTest {
    @Test
    fun curriculumUnitKeepsItsParts() {
        val unit = CurriculumUnit("unit-0", "First notes", listOf("rh-c-position"), emptyList())
        assertEquals("unit-0", unit.id)
        assertEquals("First notes", unit.title)
        assertEquals(listOf("rh-c-position"), unit.skills)
        assertEquals(emptyList(), unit.passages)
    }
}
