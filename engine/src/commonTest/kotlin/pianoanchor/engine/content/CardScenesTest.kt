package pianoanchor.engine.content

import pianoanchor.core.model.CARD_SCENE_CAPTION_MAX_CHARS
import pianoanchor.core.model.CARD_SCENE_DESCRIPTION_MAX_CHARS
import pianoanchor.core.model.CARD_SCENE_LOOP_MS
import pianoanchor.core.model.SceneAction
import pianoanchor.engine.Tunables
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardScenesTest {
    private val unit0 =
        listOf(
            Skills.HAND_SHAPE,
            Skills.MIDDLE_C,
            Skills.BLACK_KEY_GROUPS,
            Skills.HIGH_AND_LOW,
            Skills.STEADY_BEAT,
            Skills.QUARTER_AND_HALF,
            Skills.WHITE_KEY_NAMES,
        ).map { it.id }

    private val unit1 =
        listOf(
            Skills.RH_C_POSITION,
            Skills.STEPS_AND_SKIPS,
            Skills.FINGER_NUMBER_TUNES,
            Skills.BARS_AND_COUNTING,
            Skills.STAFF_STEPS,
            Skills.TREBLE_CLEF,
            Skills.SOFT_AND_LOUD,
        ).map { it.id }

    private val unit2 =
        listOf(
            Skills.LH_C_POSITION,
            Skills.BASS_CLEF_WHOLE_NOTE,
            Skills.GRAND_STAFF_TURNS_RESTS,
            Skills.THREE_FOUR_TIME,
            Skills.HANDS_TOGETHER_HELD,
        ).map { it.id }

    private val unit3 =
        listOf(
            Skills.C_CHORD,
            Skills.G_AND_F_CHORDS,
            Skills.MELODY_AND_CHORDS,
            Skills.SHARPS_AND_FLATS,
        ).map { it.id }

    @Test
    fun `every Unit 3 skill has a scene with a description`() =
        unit3.forEach { assertTrue(checkNotNull(Curriculum.scene(it), { it }).description.isNotBlank(), it) }

    @Test
    fun `g and f chords keep the shared G lit from the C chord into the G chord`() {
        val scene = checkNotNull(Curriculum.scene(Skills.G_AND_F_CHORDS.id))
        val g = scene.cues.filter { (it.action as? SceneAction.LightKey)?.note == 67 }
        val gChordAt = scene.cues.first { (it.action as? SceneAction.Caption)?.text == "G" }.atMs
        assertEquals(1, g.size)
        assertTrue(g.single().atMs < gChordAt && g.single().atMs + g.single().lengthMs > gChordAt)
    }

    @Test
    fun `every Unit 4 skill has a scene with a description and a caption`() =
        listOf("eighth-notes", "eighth-pairs", "dotted-quarter", "rhythm-song").forEach { id ->
            val scene = checkNotNull(Curriculum.scene(id), { id })
            assertTrue(scene.description.isNotBlank(), id)
            assertTrue(scene.cues.any { it.action is SceneAction.Caption }, id)
        }

    @Test
    fun `eighth notes light the key twice per pulse`() {
        val cues = checkNotNull(Curriculum.scene("eighth-notes")).cues
        assertEquals(4, cues.count { it.action is SceneAction.Pulse })
        assertEquals(8, cues.count { it.action is SceneAction.LightKey })
    }

    @Test
    fun `dotted quarter lights a beat and a half then half a beat, twice`() {
        val lights = checkNotNull(Curriculum.scene("dotted-quarter")).cues.filter { it.action is SceneAction.LightKey }
        assertEquals(listOf(1100, 300, 1100, 300), lights.map { it.lengthMs })
        assertEquals(listOf(300, 1500, 1900, 3100), lights.map { it.atMs })
    }

    @Test
    fun `rhythm song lights E E E then E G C D with fingers`() {
        val lights =
            checkNotNull(Curriculum.scene("rhythm-song")).cues.mapNotNull { it.action as? SceneAction.LightKey }
        assertEquals(listOf(64, 64, 64, 64, 67, 60, 62), lights.map { it.note })
        assertEquals(listOf(3, 3, 3, 3, 5, 1, 2), lights.map { it.finger })
    }

    @Test
    fun `every Unit 0 skill has a scene`() = unit0.forEach { assertNotNull(Curriculum.scene(it), it) }

    @Test
    fun `every Unit 1 skill has a scene with a description`() =
        unit1.forEach { assertTrue(checkNotNull(Curriculum.scene(it), { it }).description.isNotBlank(), it) }

    @Test
    fun `bars and counting shows eight beats with an accent on 1 and 5`() {
        val pulses =
            checkNotNull(
                Curriculum.scene(Skills.BARS_AND_COUNTING.id),
            ).cues.filter { it.action is SceneAction.Pulse }
        assertEquals(
            listOf(0, 4),
            pulses.withIndex().filter { (it.value.action as SceneAction.Pulse).accent }.map { it.index },
        )
        assertEquals(8, pulses.size)
    }

    @Test
    fun `every Unit 2 skill has a scene with a description`() =
        unit2.forEach { assertTrue(checkNotNull(Curriculum.scene(it), { it }).description.isNotBlank(), it) }

    @Test
    fun `three four time shows six beats with an accent on 1 and 4`() {
        val pulses =
            checkNotNull(
                Curriculum.scene(Skills.THREE_FOUR_TIME.id),
            ).cues.filter { it.action is SceneAction.Pulse }
        assertEquals(
            listOf(0, 3),
            pulses.withIndex().filter { (it.value.action as SceneAction.Pulse).accent }.map { it.index },
        )
        assertEquals(6, pulses.size)
    }

    @Test
    fun `no scene names an unknown skill`() =
        CardScenes.all.keys.forEach { assertNotNull(Curriculum.graph.byId(it), it) }

    @Test
    fun `a skill without a scene gives none`() = assertNull(Curriculum.scene("no-such-skill"))

    @Test
    fun `every scene has a description and something to see at the midpoint frame`() =
        CardScenes.all.forEach { (id, scene) ->
            assertTrue(scene.description.isNotBlank(), id)
            val mid = CARD_SCENE_LOOP_MS / 2
            assertTrue(scene.cues.any { mid > it.atMs && mid < it.atMs + it.lengthMs }, id)
        }

    @Test
    fun `the model limits match the tunables`() {
        assertEquals(Tunables.CARD_SCENE_LOOP_MS, CARD_SCENE_LOOP_MS)
        assertEquals(Tunables.CARD_SCENE_CAPTION_MAX_CHARS, CARD_SCENE_CAPTION_MAX_CHARS)
        assertEquals(Tunables.CARD_SCENE_DESCRIPTION_MAX_CHARS, CARD_SCENE_DESCRIPTION_MAX_CHARS)
        assertEquals(Tunables.CONCEPT_CARD_FIELD_MAX_CHARS, Tunables.CARD_SCENE_DESCRIPTION_MAX_CHARS)
    }

    @Test
    fun `every C is lit by the midpoint frame of high and low`() {
        val cues = checkNotNull(Curriculum.scene(Skills.HIGH_AND_LOW.id)).cues
        val mid = CARD_SCENE_LOOP_MS / 2
        listOf(36, 48, 60, 72, 84).forEach { c ->
            val lit = cues.filter { (it.action as? SceneAction.LightKey)?.note == c }
            assertTrue(lit.any { mid > it.atMs && mid < it.atMs + it.lengthMs }, "C $c")
        }
    }

    @Test
    fun `white key names caption each letter`() {
        val captions =
            checkNotNull(Curriculum.scene(Skills.WHITE_KEY_NAMES.id))
                .cues
                .filter { it.action is SceneAction.Caption }
                .sortedBy { it.atMs }
                .map { (it.action as SceneAction.Caption).text }
        assertEquals(listOf("C", "D", "E", "F", "G", "A", "B"), captions)
    }
}
