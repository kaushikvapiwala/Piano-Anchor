package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertFailsWith

class CardSceneTest {
    private val handStage = SceneStage(60..67, hand = SceneHand.RIGHT)
    private val staffStage = SceneStage(60..67, staff = SceneStaff.TREBLE)

    private fun scene(
        vararg cues: SceneCue,
        stage: SceneStage = handStage,
        description: String = "A scene.",
    ) = CardScene(stage, cues.toList(), description)

    private fun cue(
        action: SceneAction,
        at: Int = 0,
        length: Int = 500,
    ) = SceneCue(at, length, action)

    private fun invalid(
        vararg cues: SceneCue,
        stage: SceneStage = handStage,
        description: String = "A scene.",
    ) = assertFailsWith<IllegalArgumentException> { scene(*cues, stage = stage, description = description) }

    @Test
    fun `a valid scene using every action passes`() {
        scene(
            cue(SceneAction.LightKey(60, finger = 1, strong = true)),
            cue(SceneAction.ShowFinger(5)),
            cue(SceneAction.HandPose(curved = true, overNote = 60)),
            cue(SceneAction.Highlight(setOf(61, 63))),
            cue(SceneAction.Pulse(accent = true)),
            cue(SceneAction.Caption("Middle C")),
            cue(SceneAction.StaffNote(62), at = CARD_SCENE_LOOP_MS - 500),
            stage = SceneStage(60..67, SceneStaff.GRAND, SceneHand.LEFT),
        )
    }

    @Test
    fun `an empty keyboard fails`() {
        assertFailsWith<IllegalArgumentException> { SceneStage(IntRange.EMPTY) }
    }

    @Test
    fun `a cue starting before the loop or at its end fails`() {
        invalid(cue(SceneAction.Pulse(), at = -1))
        invalid(cue(SceneAction.Pulse(), at = CARD_SCENE_LOOP_MS))
    }

    @Test
    fun `a cue that is empty or runs past the loop fails`() {
        invalid(cue(SceneAction.Pulse(), length = 0))
        invalid(cue(SceneAction.Pulse(), at = CARD_SCENE_LOOP_MS - 100, length = 101))
    }

    @Test
    fun `a cue ending exactly at the loop end passes`() {
        scene(cue(SceneAction.Pulse(), at = CARD_SCENE_LOOP_MS - 100, length = 100))
    }

    @Test
    fun `a note off the keyboard fails in every action that names one`() {
        invalid(cue(SceneAction.LightKey(59)))
        invalid(cue(SceneAction.HandPose(curved = false, overNote = 68)))
        invalid(cue(SceneAction.StaffNote(59)), stage = staffStage)
        invalid(cue(SceneAction.Highlight(setOf(61, 70))))
    }

    @Test
    fun `a finger outside 1 to 5 fails`() {
        invalid(cue(SceneAction.LightKey(60, finger = 0)))
        invalid(cue(SceneAction.LightKey(60, finger = 6)))
        invalid(cue(SceneAction.ShowFinger(6)))
        invalid(cue(SceneAction.ShowFinger(0)))
    }

    @Test
    fun `a long caption fails and one of the longest length passes`() {
        invalid(cue(SceneAction.Caption("x".repeat(CARD_SCENE_CAPTION_MAX_CHARS + 1))))
        scene(cue(SceneAction.Caption("x".repeat(CARD_SCENE_CAPTION_MAX_CHARS))))
    }

    @Test
    fun `overlapping captions fail and back to back ones pass`() {
        invalid(
            cue(SceneAction.Caption("One"), at = 0, length = 600),
            cue(SceneAction.Caption("Two"), at = 500, length = 600),
        )
        scene(
            cue(SceneAction.Caption("Two"), at = 600, length = 600),
            cue(SceneAction.Caption("One"), at = 0, length = 600),
        )
    }

    @Test
    fun `a blank or long description fails`() {
        invalid(description = " ")
        invalid(description = "x".repeat(CARD_SCENE_DESCRIPTION_MAX_CHARS + 1))
        scene(description = "x".repeat(CARD_SCENE_DESCRIPTION_MAX_CHARS))
    }

    @Test
    fun `a hand action without a hand fails`() {
        val bare = SceneStage(60..67)
        invalid(cue(SceneAction.ShowFinger(1)), stage = bare)
        invalid(cue(SceneAction.HandPose(curved = true, overNote = 60)), stage = bare)
        invalid(cue(SceneAction.LightKey(60, finger = 1)), stage = bare)
        scene(cue(SceneAction.LightKey(60)), stage = bare)
    }

    @Test
    fun `a staff note without a staff or of no length fails`() {
        invalid(cue(SceneAction.StaffNote(60)))
        invalid(cue(SceneAction.StaffNote(60, beats = 0)), stage = staffStage)
    }
}
