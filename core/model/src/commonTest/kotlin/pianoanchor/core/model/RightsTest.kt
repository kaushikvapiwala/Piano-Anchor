package pianoanchor.core.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
    fun publicDomainNeedsAnOrigin() {
        assertFailsWith<IllegalArgumentException> {
            Rights(
                RightsStatus.PUBLIC_DOMAIN,
                " ",
                "Author died in 1827",
                day,
            )
        }
    }

    @Test
    fun publicDomainNeedsABasis() {
        assertFailsWith<IllegalArgumentException> { Rights(RightsStatus.PUBLIC_DOMAIN, "Ode to Joy", "", day) }
    }

    @Test
    fun originalMayLeaveOriginBlank() =
        assertEquals("", Rights(RightsStatus.ORIGINAL, "", "Written for us", day).origin)

    @Test
    fun rightsStatusHasTwoValues() =
        assertEquals(listOf(RightsStatus.ORIGINAL, RightsStatus.PUBLIC_DOMAIN), RightsStatus.entries)
}
