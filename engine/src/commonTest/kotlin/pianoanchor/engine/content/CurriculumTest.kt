package pianoanchor.engine.content

import pianoanchor.core.model.KeySignature
import pianoanchor.core.model.PedalSpan
import pianoanchor.core.model.RightsStatus
import pianoanchor.core.model.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private val unit4 get() = Curriculum.units.first { it.id == "unit-4" }

    @Test
    fun `unit 4 comes after unit 3 and before More songs with four core skills`() {
        assertEquals(
            listOf("unit-0", "unit-1", "unit-2", "unit-3", "unit-4", "unit-5", "unit-6", "unit-more-songs"),
            Curriculum.units.map { it.id },
        )
        assertEquals("Eighth notes and the dotted quarter", unit4.title)
        assertEquals(listOf("eighth-notes", "eighth-pairs", "dotted-quarter", "rhythm-song"), unit4.skills)
        unit4.skills.forEach { assertEquals(Track.CORE, Curriculum.graph.byId(it)!!.track, it) }
    }

    @Test
    fun `unit 4 skills have the prerequisites that steer Next`() {
        val prerequisites = unit4.skills.associateWith { Curriculum.graph.byId(it)!!.prerequisites }
        assertEquals(
            mapOf(
                "eighth-notes" to setOf("bars-and-counting", "melody-and-chords"),
                "eighth-pairs" to setOf("eighth-notes"),
                "dotted-quarter" to setOf("eighth-pairs"),
                "rhythm-song" to setOf("dotted-quarter", "melody-and-chords"),
            ),
            prerequisites,
        )
    }

    @Test
    fun `unit 4 passages are the four of the spec and only Jingle Bells is a public domain piece`() {
        assertEquals(
            listOf("running-steps", "skipping-eighths", "long-short", "jingle-bells"),
            unit4.passages.map { it.id.removePrefix("builtin:") },
        )
        assertEquals(listOf(72, 72, 66, 80), unit4.passages.map { it.tempoBpm })
        assertEquals(listOf(false, false, false, true), unit4.passages.map { it.piece })
        val jingle = unit4.passages.last()
        assertEquals(RightsStatus.PUBLIC_DOMAIN, jingle.rights.status)
        assertEquals(setOf("rhythm-song", "eighth-notes", "dotted-quarter"), jingle.skills)
        unit4.passages.dropLast(1).forEach { assertEquals(RightsStatus.ORIGINAL, it.rights.status, it.id) }
        unit4.passages.forEach { passage ->
            val notes = passage.steps.flatMap { it.notes }
            notes.filter { it.pitch.number >= C4 }.let { right ->
                assertTrue(right.isNotEmpty(), passage.id)
                right.forEach { assertTrue(it.pitch.number in C4..G4, passage.id) }
            }
        }
    }

    @Test
    fun `unit 4 passages use the rhythm their concept teaches`() {
        fun lengths(id: String) =
            Curriculum
                .passage("builtin:$id")!!
                .steps
                .flatMap { s -> s.notes.map { it.lengthTicks } }
                .toSet()
        assertTrue(480 in lengths("running-steps") && 1440 !in lengths("running-steps"))
        assertTrue(480 in lengths("skipping-eighths"))
        assertTrue(1440 in lengths("long-short") && 480 in lengths("long-short"))
        assertTrue(1440 in lengths("jingle-bells") && 480 in lengths("jingle-bells"))
        val jingle = Curriculum.passage("builtin:jingle-bells")!!
        val end = jingle.steps.maxOf { s -> s.onsetTicks + s.notes.maxOf { it.lengthTicks } }
        assertEquals(16 * jingle.timeSignature.barTicks, end)
    }

    private val unit5 get() = Curriculum.units.first { it.id == "unit-5" }

    @Test
    fun `unit 5 comes after unit 4 and before More songs with five core skills`() {
        val ids = Curriculum.units.map { it.id }
        assertEquals(ids.indexOf("unit-4") + 1, ids.indexOf("unit-5"))
        assertEquals(ids.indexOf("unit-6"), ids.indexOf("unit-5") + 1)
        assertEquals("G position and the key of G", unit5.title)
        assertEquals(
            listOf("rh-g-position", "lh-g-position", "key-of-g", "g-and-d7-chords", "melody-chords-in-g"),
            unit5.skills,
        )
        unit5.skills.forEach { assertEquals(Track.CORE, Curriculum.graph.byId(it)!!.track, it) }
    }

    @Test
    fun `unit 5 skills have the prerequisites of the spec`() {
        assertEquals(
            mapOf(
                "rh-g-position" to setOf("rh-c-position", "rhythm-song"),
                "lh-g-position" to setOf("lh-c-position", "rh-g-position"),
                "key-of-g" to setOf("sharps-and-flats", "rh-g-position"),
                "g-and-d7-chords" to setOf("lh-g-position", "key-of-g"),
                "melody-chords-in-g" to setOf("g-and-d7-chords"),
            ),
            unit5.skills.associateWith { Curriculum.graph.byId(it)!!.prerequisites },
        )
    }

    @Test
    fun `unit 5 passages are the five of the spec, all in the key of G`() {
        assertEquals(
            listOf("g-position-walk", "left-hand-in-g", "f-sharp-in-g", "g-and-d7", "go-tell-aunt-rhody"),
            unit5.passages.map { it.id.removePrefix("builtin:") },
        )
        assertEquals(listOf(72, 72, 72, 66, 88), unit5.passages.map { it.tempoBpm })
        assertEquals(listOf(false, false, false, false, true), unit5.passages.map { it.piece })
        unit5.passages.forEach { assertEquals(KeySignature.G, it.keySignature, it.id) }
        unit5.passages.forEach { assertEquals(4, it.timeSignature.beats, it.id) }
        unit5.passages.dropLast(1).forEach { assertEquals(RightsStatus.ORIGINAL, it.rights.status, it.id) }
    }

    @Test
    fun `go tell aunt rhody is a public domain piece with an origin and a basis`() {
        val rhody = unit5.passages.last()
        assertEquals(RightsStatus.PUBLIC_DOMAIN, rhody.rights.status)
        assertTrue(rhody.rights.origin.contains("Rousseau"))
        assertTrue(rhody.rights.basis.isNotBlank())
        assertEquals(setOf("melody-chords-in-g", "g-and-d7-chords", "rh-g-position"), rhody.skills)
        val end = rhody.steps.maxOf { s -> s.onsetTicks + s.notes.maxOf { it.lengthTicks } }
        assertEquals(8 * rhody.timeSignature.barTicks, end)
    }

    @Test
    fun `unit 5 passages stay in their hand position and use the D7 shortcut`() {
        fun pitches(id: String) =
            Curriculum
                .passage("builtin:$id")!!
                .steps
                .flatMap { s -> s.notes.map { it.pitch.number } }
        assertEquals(setOf(67, 69, 71, 72, 74), pitches("g-position-walk").toSet())
        assertEquals(setOf(43, 45, 47, 48, 50), pitches("left-hand-in-g").toSet())
        assertEquals(setOf(66, 67, 69, 71), pitches("f-sharp-in-g").toSet())
        assertTrue(listOf(42, 48, 50).all { it in pitches("g-and-d7") })
        assertTrue(listOf(42, 48, 50).all { it in pitches("go-tell-aunt-rhody") })
    }

    private val unit6 get() = Curriculum.units.first { it.id == "unit-6" }

    @Test
    fun `unit 6 comes after unit 5 and before More songs with four core skills`() {
        val ids = Curriculum.units.map { it.id }
        assertEquals(ids.indexOf("unit-5") + 1, ids.indexOf("unit-6"))
        assertEquals(ids.indexOf("unit-more-songs"), ids.indexOf("unit-6") + 1)
        assertEquals("Legato and the pedal", unit6.title)
        assertEquals(listOf("legato-touch", "sustain-pedal", "pedal-changes", "pedal-song"), unit6.skills)
        unit6.skills.forEach { assertEquals(Track.CORE, Curriculum.graph.byId(it)!!.track, it) }
        assertEquals(
            mapOf(
                "legato-touch" to setOf("rh-c-position", "melody-chords-in-g"),
                "sustain-pedal" to setOf("legato-touch", "c-chord"),
                "pedal-changes" to setOf("sustain-pedal", "g-and-f-chords"),
                "pedal-song" to setOf("pedal-changes", "three-four-time"),
            ),
            unit6.skills.associateWith { Curriculum.graph.byId(it)!!.prerequisites },
        )
    }

    @Test
    fun `unit 6 passages are the four of the spec and only Amazing Grace is a piece`() {
        assertEquals(
            listOf("joined-steps", "ringing-chords", "pedal-changes", "amazing-grace"),
            unit6.passages.map { it.id.removePrefix("builtin:") },
        )
        assertEquals(listOf(66, 60, 60, 72), unit6.passages.map { it.tempoBpm })
        assertEquals(listOf(false, false, false, true), unit6.passages.map { it.piece })
        assertEquals(listOf(4, 4, 4, 3), unit6.passages.map { it.timeSignature.beats })
        unit6.passages.dropLast(1).forEach { assertEquals(RightsStatus.ORIGINAL, it.rights.status, it.id) }
        assertEquals(
            listOf(
                setOf("legato-touch"),
                setOf("sustain-pedal"),
                setOf("pedal-changes"),
                setOf("pedal-song", "pedal-changes", "legato-touch"),
            ),
            unit6.passages.map { it.skills },
        )
    }

    @Test
    fun `Amazing Grace is a public domain piece of 15 bars in 3 4 with a pedal change each bar from bar 2`() {
        val grace = unit6.passages.last()
        assertEquals(RightsStatus.PUBLIC_DOMAIN, grace.rights.status)
        assertTrue(grace.rights.origin.contains("New Britain"))
        assertTrue(grace.rights.basis.isNotBlank())
        val bar = grace.timeSignature.barTicks
        assertEquals(2880, bar)
        val end = grace.steps.maxOf { s -> s.onsetTicks + s.notes.maxOf { it.lengthTicks } }
        assertEquals(15 * bar, end)
        assertEquals(14, grace.pedal.size)
        assertEquals((1..14).map { PedalSpan(it * bar, (it + 1) * bar) }, grace.pedal)
        assertTrue(grace.steps.none { it.onsetTicks == 0 && it.notes.size > 1 })
    }

    @Test
    fun `unit 6 pedal marks are where the spec puts them`() {
        fun pedal(id: String) = Curriculum.passage("builtin:$id")!!.pedal
        assertTrue(pedal("joined-steps").isEmpty())
        val bar = 3840
        assertEquals(listOf(PedalSpan(0, 2 * bar), PedalSpan(2 * bar, 3 * bar)), pedal("ringing-chords"))
        assertEquals((0..3).map { PedalSpan(it * bar, (it + 1) * bar) }, pedal("pedal-changes"))
    }

    @Test
    fun `pedal is music for the pedal skills and not for legato`() {
        assertTrue(Curriculum.pedalIsMusic("sustain-pedal"))
        assertTrue(Curriculum.pedalIsMusic("pedal-changes"))
        assertTrue(Curriculum.pedalIsMusic("pedal-song"))
        assertFalse(Curriculum.pedalIsMusic("legato-touch"))
    }

    @Test
    fun `More songs is the last unit and teaches the songs track`() {
        val more = Curriculum.units.last()
        assertEquals(8, Curriculum.units.size)
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
