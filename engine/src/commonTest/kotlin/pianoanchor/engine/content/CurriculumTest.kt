package pianoanchor.engine.content

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
    fun `every passage has a rights record that says why it may ship`() {
        // Rights itself rejects a public domain record without an origin and a basis.
        passages.forEach { assertTrue(it.rights.basis.isNotBlank(), "${it.id}: every record says why it may ship") }
    }

    @Test
    fun `every passage parses and its bars fit`() {
        // Each passage's steps come from NoteText.parse with its own time signature, which checks the bars, so
        // loading the curriculum parses every passage; here every unit also has passages to load.
        assertTrue(Curriculum.units.all { it.passages.isNotEmpty() })
        passages.forEach { assertTrue(it.steps.isNotEmpty(), it.id) }
    }

    @Test
    fun `passage finds a passage by id`() = assertEquals(Passages.CDE, Curriculum.passage("builtin:cde"))

    @Test
    fun `passage is null for an unknown id`() = assertNull(Curriculum.passage("builtin:gone"))
}
