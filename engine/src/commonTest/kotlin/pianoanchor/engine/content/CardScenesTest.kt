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

    @Test
    fun `every Unit 0 skill has a scene`() = unit0.forEach { assertNotNull(Curriculum.scene(it), it) }

    @Test
    fun `no scene names an unknown skill`() =
        CardScenes.all.keys.forEach { assertNotNull(Curriculum.graph.byId(it), it) }

    @Test
    fun `a skill without a scene yet gives none`() = assertNull(Curriculum.scene(Skills.RH_C_POSITION.id))

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
        assertEquals(Tunables.CONCEPT_CARD_FIELD_MAX_CHARS, CARD_SCENE_DESCRIPTION_MAX_CHARS)
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
