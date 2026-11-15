package pianoanchor.engine.content

import pianoanchor.core.model.RightsStatus
import pianoanchor.core.model.TICKS_PER_QUARTER
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CurriculumTest {
    private val passages = Curriculum.units.flatMap { it.passages }

    @Test
    fun `unit ids are unique`() =
        assertEquals(
            Curriculum.units.size,
            Curriculum.units
                .map { it.id }
                .toSet()
                .size,
        )

    @Test
    fun `skill ids are unique`() =
        assertEquals(
            Curriculum.graph.skills.size,
            Curriculum.graph.skills
                .map { it.id }
                .toSet()
                .size,
        )

    @Test
    fun `passage ids are unique`() = assertEquals(passages.size, passages.map { it.id }.toSet().size)

    @Test
    fun `every passage has skills that exist in the graph`() {
        passages.forEach { passage ->
            assertTrue(passage.skills.isNotEmpty(), passage.id)
            passage.skills.forEach { assertNotNull(Curriculum.graph.byId(it), "${passage.id}: $it") }
        }
    }

    @Test
    fun `every unit skill exists in the graph`() {
        Curriculum.units.forEach { unit ->
            unit.skills.forEach { assertNotNull(Curriculum.graph.byId(it), "${unit.id}: $it") }
        }
    }

    @Test
    fun `every passage has a rights record, and public domain ones state origin and basis`() {
        passages.filter { it.rights.status == RightsStatus.PUBLIC_DOMAIN }.forEach {
            assertTrue(it.rights.origin.isNotBlank(), "${it.id}: origin")
            assertTrue(it.rights.basis.isNotBlank(), "${it.id}: basis")
        }
        passages.forEach { assertTrue(it.rights.basis.isNotBlank(), "${it.id}: every record says why it may ship") }
    }

    @Test
    fun `every passage parses and its bars fit`() {
        passages.forEach { passage ->
            val bar = passage.timeSignature.beats * 4 * TICKS_PER_QUARTER / passage.timeSignature.beatUnit
            val last = passage.steps.last()
            val end = last.onsetTicks + last.notes.maxOf { it.lengthTicks }
            assertEquals(0, end % bar, "${passage.id} ends mid-bar")
        }
    }

    @Test
    fun `passage finds a passage by id`() = assertEquals(Passages.CDE, Curriculum.passage("builtin:cde"))

    @Test
    fun `passage is null for an unknown id`() = assertNull(Curriculum.passage("builtin:gone"))
}
