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

    private fun unitOf(id: String) = Curriculum.units.first { id in it.skills }.id

    private fun notes(unitId: String) =
        Curriculum.units
            .first { it.id == unitId }
            .passages
            .flatMap { p -> p.steps.flatMap { it.notes } }

    @Test
    fun `every skill has at least one passage`() {
        Curriculum.graph.skills.forEach { skill ->
            assertTrue(passages.any { skill.id in it.skills }, skill.id)
        }
    }

    @Test
    fun `every skill belongs to exactly one unit`() =
        assertEquals(
            Curriculum.graph.skills
                .map { it.id }
                .sorted(),
            Curriculum.units.flatMap { it.skills }.sorted(),
        )

    @Test
    fun `unit order follows the graph`() {
        val order = Curriculum.units.map { it.id }
        Curriculum.units.forEach { unit ->
            val skills = unit.passages.flatMap { it.skills } + unit.skills
            skills.forEach { assertTrue(order.indexOf(unitOf(it)) <= order.indexOf(unit.id), "${unit.id}: $it") }
            unit.skills.forEach { skill ->
                Curriculum.graph.byId(skill)!!.prerequisites.forEach {
                    assertTrue(order.indexOf(unitOf(it)) <= order.indexOf(unit.id), "$skill needs $it")
                }
            }
        }
    }

    @Test
    fun `every unit 1 passage stays within C4-G4`() =
        notes("unit-1").forEach { assertTrue(it.pitch.number in C4..G4, it.pitch.name) }

    @Test
    fun `every unit 2 left-hand note stays within C3-G3`() {
        val leftHand = notes("unit-2").filter { it.pitch.number < C4 }
        assertTrue(leftHand.isNotEmpty())
        leftHand.forEach { assertTrue(it.pitch.number in C3..G3, it.pitch.name) }
    }

    @Test
    fun `every unit 0 passage is written at 50-92 BPM`() =
        Curriculum.units
            .first { it.id == "unit-0" }
            .passages
            .forEach { assertTrue(it.tempoBpm in 50..92, it.id) }

    @Test
    fun `every unit 1 and 2 passage is written at 60-92 BPM`() =
        Curriculum.units.filter { it.id != "unit-0" }.flatMap { it.passages }.forEach {
            assertTrue(it.tempoBpm in 60..92, it.id)
        }

    @Test
    fun `Ode to Joy with a held bass holds a left-hand whole note in the step it starts on`() {
        val ode = Curriculum.passage("builtin:ode-to-joy-held-bass")!!
        val first =
            ode.steps
                .first()
                .notes
                .map { it.pitch.number to it.lengthTicks }
                .toSet()
        assertEquals(setOf(C3 to ode.timeSignature.barTicks, E4 to 960), first)
        assertEquals(1, ode.steps[1].notes.size)
    }
}

private const val C3 = 48
private const val G3 = 55
private const val C4 = 60
private const val E4 = 64
private const val G4 = 67
