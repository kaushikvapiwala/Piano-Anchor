package pianoanchor.feature.lesson

import pianoanchor.core.model.CARD_SCENE_LOOP_MS
import pianoanchor.core.model.CardScene
import pianoanchor.core.model.SceneAction
import pianoanchor.core.model.SceneCue
import pianoanchor.core.model.SceneStaff
import pianoanchor.core.model.SceneStage
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardSceneTimelineTest {
    private val cue = SceneCue(1000, 1000, SceneAction.Pulse())
    private val middleC = checkNotNull(Curriculum.scene(Skills.MIDDLE_C.id))

    @Test
    fun aCueIsInvisibleBeforeAndAfterItsTime() {
        assertEquals(0f, cueAlpha(cue, 999))
        assertEquals(0f, cueAlpha(cue, 1000))
        assertEquals(0f, cueAlpha(cue, 2000))
    }

    @Test
    fun aCueFadesInThenHoldsThenFadesOut() {
        val fade = Tunables.CARD_SCENE_FADE_MS
        assertTrue(cueAlpha(cue, 1000 + fade / 2) in 0.01f..0.99f)
        assertEquals(1f, cueAlpha(cue, 1000 + fade))
        assertEquals(1f, cueAlpha(cue, 1500))
        assertTrue(cueAlpha(cue, 2000 - fade / 2) in 0.01f..0.99f)
    }

    @Test
    fun aShortCueNeverReachesFullAlphaButStillShows() {
        val short = SceneCue(0, 100, SceneAction.Pulse())
        assertTrue(cueAlpha(short, 50) in 0.01f..0.99f)
    }

    @Test
    fun theFrameAtTheMidpointLightsTheKeysOfTheCuesActiveThen() {
        assertEquals(setOf(60), litKeysAt(middleC, CARD_SCENE_MIDPOINT_MS))
    }

    @Test
    fun noKeyIsLitAtTheStartOrDuringTheRest() {
        assertEquals(emptySet(), litKeysAt(middleC, 0))
        assertEquals(emptySet(), litKeysAt(middleC, CARD_SCENE_LOOP_MS))
        assertEquals(emptyList(), activeActions(middleC, CARD_SCENE_CYCLE_MS - 1))
    }

    @Test
    fun theHighCsStayLitOnceLit() {
        val high = checkNotNull(Curriculum.scene(Skills.HIGH_AND_LOW.id))
        assertEquals(setOf(36, 48, 60, 72, 84), litKeysAt(high, CARD_SCENE_MIDPOINT_MS))
        assertEquals(setOf(36, 48, 60, 72, 84), litKeysAt(high, 3500))
    }

    @Test
    fun repeatedStaffNotesGetTheirOwnSlots() {
        val cues =
            listOf(
                SceneCue(1000, 500, SceneAction.StaffNote(60)),
                SceneCue(0, 500, SceneAction.StaffNote(60)),
                SceneCue(2000, 500, SceneAction.StaffNote(67)),
                SceneCue(2000, 500, SceneAction.Pulse()),
            )
        val scene = CardScene(SceneStage(60..72, staff = SceneStaff.TREBLE), cues, "A test scene.")
        assertEquals(listOf(1f / 6, 3f / 6, 5f / 6), listOf(cues[1], cues[0], cues[2]).map { staffSlot(scene, it) })
    }

    @Test
    fun theCycleIsTheLoopPlusTheRest() = assertEquals(4600, CARD_SCENE_CYCLE_MS)

    @Test
    fun ledgerLinesReachFromTheStaffToTheNote() {
        val treble = listOf(0, 2, 4, 6, 8)
        val grand = treble + listOf(-12, -10, -8, -6, -4)
        assertEquals(listOf(-2), ledgerLines(-2, grand))
        assertEquals(listOf(-2), ledgerLines(-2, treble))
        assertEquals(listOf(-4, -2), ledgerLines(-4, treble))
        assertEquals(emptyList(), ledgerLines(-1, treble))
        assertEquals(emptyList(), ledgerLines(5, treble))
        assertEquals(listOf(10), ledgerLines(11, treble))
        assertEquals(listOf(12, 10), ledgerLines(12, treble))
        assertEquals(emptyList(), ledgerLines(9, treble))
    }
}
