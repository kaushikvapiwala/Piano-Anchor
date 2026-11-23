package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConceptCardTest {
    private fun card(
        what: String = "w",
        why: String = "y",
        how: String = "h",
        whenMet: String = "n",
    ) = ConceptCard("x", what, why, how, whenMet)

    @Test
    fun `a card with four lines is valid`() = assertEquals("w", card().what)

    @Test
    fun `a blank what throws`() {
        assertFailsWith<IllegalArgumentException> { card(what = " ") }
    }

    @Test
    fun `a blank why throws`() {
        assertFailsWith<IllegalArgumentException> { card(why = "") }
    }

    @Test
    fun `a blank how throws`() {
        assertFailsWith<IllegalArgumentException> { card(how = "") }
    }

    @Test
    fun `a blank whenMet throws`() {
        assertFailsWith<IllegalArgumentException> { card(whenMet = "\n") }
    }
}
