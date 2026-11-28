package pianoanchor.engine.content

import pianoanchor.core.model.KeySignature
import pianoanchor.core.model.RightsStatus
import pianoanchor.core.model.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Unit7Test {
    private val unit7 get() = Curriculum.units.first { it.id == "unit-7" }

    @Test
    fun `unit 7 closes course 2 after unit 6 and before More songs with five core skills`() {
        val ids = Curriculum.units.map { it.id }
        assertEquals(ids.indexOf("unit-6") + 1, ids.indexOf("unit-7"))
        assertEquals(ids.indexOf("unit-more-songs"), ids.indexOf("unit-7") + 1)
        assertEquals("Scales with thumb-under", unit7.title)
        assertEquals(
            listOf("thumb-under", "c-major-scale", "g-major-scale", "lh-c-scale", "scale-song"),
            unit7.skills,
        )
        unit7.skills.forEach { assertEquals(Track.CORE, Curriculum.graph.byId(it)!!.track, it) }
        assertEquals(
            mapOf(
                "thumb-under" to setOf("rh-c-position", "pedal-song"),
                "c-major-scale" to setOf("thumb-under"),
                "g-major-scale" to setOf("c-major-scale", "key-of-g"),
                "lh-c-scale" to setOf("c-major-scale", "lh-c-position"),
                "scale-song" to setOf("c-major-scale", "dotted-quarter"),
            ),
            unit7.skills.associateWith { Curriculum.graph.byId(it)!!.prerequisites },
        )
    }

    @Test
    fun `unit 7 passages are the five of the spec and only Joy to the World is a piece`() {
        assertEquals(
            listOf("thumb-under-walk", "c-scale", "g-scale", "left-hand-c-scale", "joy-to-the-world"),
            unit7.passages.map { it.id.removePrefix("builtin:") },
        )
        assertEquals(listOf(66, 66, 66, 66, 80), unit7.passages.map { it.tempoBpm })
        assertEquals(listOf(false, false, false, false, true), unit7.passages.map { it.piece })
        assertTrue(unit7.passages.all { it.timeSignature.beats == 4 })
        unit7.passages.dropLast(1).forEach { assertEquals(RightsStatus.ORIGINAL, it.rights.status, it.id) }
        assertEquals(
            listOf(
                setOf("thumb-under"),
                setOf("c-major-scale"),
                setOf("g-major-scale"),
                setOf("lh-c-scale"),
                setOf("scale-song", "c-major-scale"),
            ),
            unit7.passages.map { it.skills },
        )
    }

    @Test
    fun `the G scale is in the key of G and the other scales in C`() {
        assertEquals(KeySignature.G, Curriculum.passage("builtin:g-scale")!!.keySignature)
        assertEquals(KeySignature.C, Curriculum.passage("builtin:c-scale")!!.keySignature)
        assertEquals(
            listOf(67, 69, 71, 72, 74, 76, 78, 79, 78, 76, 74, 72, 71, 69, 67),
            Curriculum.passage("builtin:g-scale")!!.steps.map {
                it.notes
                    .single()
                    .pitch.number
            },
        )
    }

    @Test
    fun `Joy to the World is a public domain piece of 7 bars with the left hand chords`() {
        val joy = Curriculum.passage("builtin:joy-to-the-world")!!
        assertEquals(RightsStatus.PUBLIC_DOMAIN, joy.rights.status)
        assertTrue(joy.rights.origin.contains("Antioch"))
        assertTrue(joy.rights.basis.isNotBlank())
        val end = joy.steps.maxOf { s -> s.onsetTicks + s.notes.maxOf { it.lengthTicks } }
        assertEquals(7 * joy.timeSignature.barTicks, end)
        val right =
            joy.steps.mapNotNull { s ->
                s.notes
                    .firstOrNull { it.pitch.number >= 60 }
                    ?.pitch
                    ?.number
            }
        assertEquals(listOf(72, 71, 69, 67, 65, 64, 62, 60, 67, 69, 69, 71, 71, 72), right)
        assertTrue(
            joy.steps
                .first()
                .notes
                .map { it.pitch.number }
                .containsAll(listOf(48, 52, 55, 72)),
        )
        assertTrue(joy.pedal.isEmpty())
    }
}
