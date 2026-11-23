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
    fun `a skill without a scene yet gives none`() = assertNull(Curriculum.scene(Skills.C_CHORD.id))

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
