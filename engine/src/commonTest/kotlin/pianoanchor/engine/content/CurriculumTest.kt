package pianoanchor.engine.content

import pianoanchor.core.model.RightsStatus
import pianoanchor.core.model.Track
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

    private val playingSkills get() = Curriculum.graph.skills.filter { it.track != Track.EAR }

    @Test
    fun `every skill has at least one passage`() {
        playingSkills.forEach { skill ->
            assertTrue(passages.any { skill.id in it.skills }, skill.id)
        }
    }

    @Test
    fun `every skill belongs to exactly one unit`() =
        assertEquals(
            playingSkills
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

    private val chordPassages get() = Curriculum.units.first { it.id == "unit-3" }.passages

    private fun chordNotes() = chordPassages.flatMap { p -> p.steps.flatMap { it.notes } }

    @Test
    fun `every chord-unit right-hand note stays within C4-G4`() {
        val rightHand = chordNotes().filter { it.pitch.number >= C4 }
        assertTrue(rightHand.isNotEmpty())
        rightHand.forEach { assertTrue(it.pitch.number in C4..G4, it.pitch.name) }
    }

    @Test
    fun `every chord-unit left-hand note stays within F2-G3`() {
        val leftHand = chordNotes().filter { it.pitch.number < C4 }
        assertTrue(leftHand.isNotEmpty())
        leftHand.forEach { assertTrue(it.pitch.number in F2..G3, it.pitch.name) }
    }

    @Test
    fun `every chord-unit melody ends with its chords`() =
        chordPassages.forEach { passage ->
            val ends =
                passage.steps
                    .flatMap { step -> step.notes.map { (it.pitch.number >= C4) to step.onsetTicks + it.lengthTicks } }
                    .groupBy({ it.first }, { it.second })
                    .mapValues { (_, hand) -> hand.max() }
            ends[true]?.let { assertEquals(ends[false], it, passage.id) }
        }

    @Test
    fun `every chord-unit passage is written at 60-88 BPM`() =
        chordPassages.forEach { assertTrue(it.tempoBpm in 60..88, it.id) }

    @Test
    fun `every songs skill has a passage`() =
        Curriculum.graph.skills.filter { it.track == Track.SONGS }.forEach { skill ->
            assertTrue(chordPassages.any { skill.id in it.skills }, skill.id)
        }

    @Test
    fun `chord voicings match the table`() {
        val voicings =
            setOf(
                setOf(48, 52, 55), // C: C3 E3 G3
                setOf(41, 45, 48), // F: F2 A2 C3
                setOf(45, 48, 53), // F first inversion: A2 C3 F3
                setOf(43, 47, 50), // G: G2 B2 D3
                setOf(47, 50, 55), // G first inversion: B2 D3 G3
                setOf(45, 48, 52), // Am: A2 C3 E3
            )
        chordPassages.forEach { passage ->
            passage.steps.forEach { step ->
                val left =
                    step.pitches
                        .map { it.number }
                        .filter { it < C4 }
                        .toSet()
                assertTrue(left.isEmpty() || left in voicings, "${passage.id} at ${step.onsetTicks}: $left")
            }
        }
        val close = chordPassages.first { it.id == "builtin:close-moves" }
        assertEquals(
            setOf(45, 48, 53),
            close.steps[1]
                .pitches
                .map { it.number }
                .toSet(),
        )
    }

    @Test
    fun `every chord and melody note share a step and keep their own length`() {
        val hold = chordPassages.first { it.id == "builtin:hold-and-sing" }
        val first =
            hold.steps
                .first()
                .notes
                .associate { it.pitch.number to it.lengthTicks }
        assertEquals(960, first.getValue(C4))
        assertEquals(hold.timeSignature.barTicks, first.getValue(48))
        assertEquals(4, first.size)
    }

    @Test
    fun `every piece has a rights record`() {
        val pieces = passages.filter { it.piece }
        assertTrue(pieces.size >= 10)
        pieces.forEach { assertTrue(it.rights.origin.isNotBlank() && it.rights.basis.isNotBlank(), it.id) }
        assertTrue(chordPassages.filter { it.piece }.size == 4)
        assertTrue(passages.filter { !it.piece }.all { it.rights.status == RightsStatus.ORIGINAL })
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

private const val F2 = 41
private const val C3 = 48
private const val G3 = 55
private const val C4 = 60
private const val E4 = 64
private const val G4 = 67
