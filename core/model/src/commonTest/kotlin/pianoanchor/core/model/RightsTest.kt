package pianoanchor.core.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RightsTest {
    private val day = LocalDate(2026, 11, 15)

    @Test
    fun rightsKeepsItsRecord() {
        val rights = Rights(RightsStatus.PUBLIC_DOMAIN, "Ode to Joy, Beethoven, 1824", "Author died in 1827", day)
        assertEquals(RightsStatus.PUBLIC_DOMAIN, rights.status)
        assertEquals("Ode to Joy, Beethoven, 1824", rights.origin)
        assertEquals("Author died in 1827", rights.basis)
        assertEquals(day, rights.checkedOn)
    }

    @Test
    fun curriculumUnitKeepsItsParts() {
        val unit = CurriculumUnit("unit-0", "First notes", listOf("rh-c-position"), emptyList())
        assertEquals("unit-0", unit.id)
        assertEquals("First notes", unit.title)
        assertEquals(listOf("rh-c-position"), unit.skills)
        assertEquals(emptyList(), unit.passages)
    }

    @Test
    fun rightsStatusHasTwoValues() =
        assertEquals(listOf(RightsStatus.ORIGINAL, RightsStatus.PUBLIC_DOMAIN), RightsStatus.entries)
}
