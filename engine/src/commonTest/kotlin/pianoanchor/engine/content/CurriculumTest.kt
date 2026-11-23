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

    private val chordPassages get() =
        Curriculum.units
            .filter {
                it.id in setOf("unit-3", "unit-more-songs")
            }.flatMap { it.passages }

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

    private val courseIds =
        listOf(
            "hand-shape",
            "middle-c",
            "black-key-groups",
            "high-and-low",
            "steady-beat",
            "quarter-and-half",
            "white-key-names",
            "rh-c-position",
            "steps-and-skips",
            "finger-number-tunes",
            "bars-and-counting",
            "staff-steps",
            "treble-clef",
            "soft-and-loud",
            "lh-c-position",
            "bass-clef-whole-note",
            "grand-staff-turns-rests",
            "three-four-time",
            "hands-together-held",
            "c-chord",
            "g-and-f-chords",
            "melody-and-chords",
            "sharps-and-flats",
        )

    private val courseUnits get() = Curriculum.units.take(4)

    @Test
    fun `the course is the 23 concepts in map order across units 0 to 3`() {
        assertEquals(listOf("unit-0", "unit-1", "unit-2", "unit-3"), courseUnits.map { it.id })
        assertEquals(courseIds, courseUnits.flatMap { it.skills })
        assertEquals(listOf(7, 7, 5, 4), courseUnits.map { it.skills.size })
        assertEquals(
            listOf("Meet the piano", "Five-finger tunes", "Left hand, then both hands", "Chords and songs"),
            courseUnits.map { it.title },
        )
        courseIds.forEach { assertEquals(Track.CORE, Curriculum.graph.byId(it)!!.track, it) }
    }

    @Test
    fun `each concept needs the one before it and the first needs nothing`() {
        courseIds.forEachIndexed { index, id ->
            val expected = if (index == 0) emptySet() else setOf(courseIds[index - 1])
            assertEquals(expected, Curriculum.graph.byId(id)!!.prerequisites, id)
        }
    }

    @Test
    fun `every course skill has a passage and every passage stays in its own unit unless it is a piece`() {
        courseUnits.forEach { unit ->
            unit.skills.forEach { skill -> assertTrue(unit.passages.any { skill in it.skills }, skill) }
            unit.passages.forEach { passage ->
                val allowed =
                    if (passage.piece) {
                        courseUnits
                            .take(
                                courseUnits.indexOf(unit) + 1,
                            ).flatMap { it.skills }
                    } else {
                        unit.skills
                    }
                assertTrue(passage.skills.all { it in allowed }, passage.id)
                assertTrue(passage.skills.any { it in unit.skills }, passage.id)
            }
        }
    }

    @Test
    fun `More songs is the last unit and teaches the songs track`() {
        val more = Curriculum.units.last()
        assertEquals(5, Curriculum.units.size)
        assertEquals("unit-more-songs", more.id)
        assertEquals("More songs", more.title)
        assertEquals(listOf("more-inversions", "more-a-minor", "more-four-chords"), more.skills)
        more.skills.forEach { assertEquals(Track.SONGS, Curriculum.graph.byId(it)!!.track, it) }
        assertEquals(
            setOf("melody-and-chords", "sharps-and-flats"),
            Curriculum.graph.byId("more-inversions")!!.prerequisites,
        )
        assertEquals(setOf("c-chord", "sharps-and-flats"), Curriculum.graph.byId("more-a-minor")!!.prerequisites)
        assertEquals(
            setOf("more-inversions", "more-a-minor"),
            Curriculum.graph.byId("more-four-chords")!!.prerequisites,
        )
        assertEquals(
            listOf("close-moves", "minor-colour", "when-the-saints-go-marching-in", "four-chord-walk"),
            more.passages.map { it.id.removePrefix("builtin:") },
        )
    }

    @Test
    fun `More songs only opens once the course is done`() {
        val graph = Curriculum.graph
        val moreIds = setOf("more-inversions", "more-a-minor", "more-four-chords")
        val upToCChord = courseIds.take(courseIds.indexOf("c-chord") + 1).toSet()
        assertEquals(
            listOf("g-and-f-chords"),
            graph.unlocked(upToCChord).filter { it.track != Track.EAR }.map { it.id },
        )
        val upToMelody = courseIds.take(courseIds.indexOf("melody-and-chords") + 1).toSet()
        assertEquals(emptySet(), graph.unlocked(upToMelody).map { it.id }.toSet() intersect moreIds)
        assertEquals(
            setOf("more-inversions", "more-a-minor"),
            graph.unlocked(courseIds.toSet()).map { it.id }.toSet() intersect moreIds,
        )
    }

    @Test
    fun `the new passages are original and sit with their concept`() {
        val expected =
            mapOf(
                "five-fingers" to "hand-shape",
                "every-f-every-b" to "white-key-names",
                "staff-steps" to "staff-steps",
                "soft-then-loud" to "soft-and-loud",
                "sharp-phrase" to "sharps-and-flats",
                "c-chord-hold" to "c-chord",
            )
        expected.forEach { (id, skill) ->
            val passage = Curriculum.passage("builtin:$id")!!
            assertEquals(setOf(skill), passage.skills, id)
            assertEquals(RightsStatus.ORIGINAL, passage.rights.status, id)
            assertTrue(passage.steps.size >= 3 && !passage.piece, id)
            val last = passage.steps.last()
            val ticks = last.onsetTicks + last.notes.maxOf { it.lengthTicks }
            assertTrue(
                ticks / passage.timeSignature.barTicks in 2..4,
                "$id is ${ticks / passage.timeSignature.barTicks} bars",
            )
        }
    }

    @Test
    fun `existing passages keep their ids and take the concept the course gives them`() {
        val concepts =
            mapOf(
                "middle-c" to listOf("middle-c-march"),
                "black-key-groups" to listOf("twos-and-threes", "twos-and-threes-down", "which-c-is-middle"),
                "high-and-low" to listOf("all-the-cs", "cs-up-and-down"),
                "steady-beat" to listOf("steady-quarters"),
                "quarter-and-half" to listOf("half-and-quarter"),
                "rh-c-position" to listOf("cde", "five-finger-walk"),
                "steps-and-skips" to listOf("step-up-step-down", "skipping-stones"),
                "finger-number-tunes" to listOf("hot-cross-buns", "mary-had-a-little-lamb", "au-clair-de-la-lune"),
                "bars-and-counting" to listOf("steady-skipping", "lightly-row"),
                "treble-clef" to listOf("ode-to-joy"),
                "lh-c-position" to listOf("left-hand-walk", "left-hand-steps"),
                "bass-clef-whole-note" to listOf("long-notes", "low-whole-notes"),
                "grand-staff-turns-rests" to listOf("turn-taking", "echo"),
                "three-four-time" to listOf("waltz-in-c", "three-beat-steps"),
                "hands-together-held" to listOf("held-bass", "ode-to-joy-held-bass"),
                "g-and-f-chords" to listOf("three-chords", "change-on-the-beat"),
                "melody-and-chords" to listOf("hold-and-sing", "lightly-row-with-chords", "ode-to-joy-with-chords"),
            )
        concepts.forEach { (skill, ids) ->
            ids.forEach { id ->
                assertTrue(skill in Curriculum.passage("builtin:$id")!!.skills, "$id teaches $skill")
            }
        }
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
