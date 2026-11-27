package pianoanchor.engine.content

import pianoanchor.core.model.Track
import pianoanchor.engine.Tunables
import pianoanchor.engine.lesson.CourseProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConceptCardsTest {
    private val courseIds =
        Curriculum.graph.skills
            .filter { it.track == Track.CORE }
            .map { it.id }

    @Test
    fun `every course skill has exactly one card`() {
        assertEquals(32, courseIds.size)
        courseIds.forEach { id -> assertEquals(1, ConceptCards.all.count { it.skillId == id }, id) }
        assertEquals(courseIds.toSet(), ConceptCards.all.map { it.skillId }.toSet())
        assertEquals(courseIds.size, ConceptCards.all.size)
    }

    @Test
    fun `no card names an unknown skill`() =
        ConceptCards.all.forEach { assertNotNull(Curriculum.graph.byId(it.skillId), it.skillId) }

    @Test
    fun `cards follow map order`() =
        assertEquals(CourseProgress.skills(Curriculum).map { it.id }, ConceptCards.all.map { it.skillId })

    @Test
    fun `every field is one non-blank line within the length limit`() {
        ConceptCards.all.forEach { card ->
            listOf(card.what, card.why, card.how, card.whenMet).forEach {
                assertTrue(it.isNotBlank(), card.skillId)
                assertTrue(it.length <= Tunables.CONCEPT_CARD_FIELD_MAX_CHARS, "${card.skillId}: $it")
                assertTrue('\n' !in it, card.skillId)
            }
        }
    }

    @Test
    fun `card finds a course skill's card`() = assertEquals("middle-c", Curriculum.card("middle-c")?.skillId)

    @Test
    fun `card is null for a skill without one`() {
        assertNull(Curriculum.card("more-inversions"))
        assertNull(Curriculum.card("nope"))
    }
}
