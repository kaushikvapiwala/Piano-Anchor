package pianoanchor.feature.lesson

import pianoanchor.core.model.LessonStep
import pianoanchor.core.model.Skill
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
import kotlin.test.Test
import kotlin.test.assertEquals

class LessonKeyRangeTest {
    private fun range(skill: Skill) = lessonKeyRange(lessonState(LessonStep.TRY, skill))

    @Test
    fun aKeyNearMiddleCShowsTwoOctavesBelowIt() = assertEquals(48..71, range(Skills.MIDDLE_C))

    @Test
    fun theWholeKeyboardWhenTheTryFindsEveryC() = assertEquals(21..108, range(Skills.HIGH_AND_LOW))

    @Test
    fun anyKeyShowsTheOctavesAroundMiddleC() = assertEquals(48..71, range(Skills.HAND_SHAPE))

    @Test
    fun aPassageTryShowsTheKeysOfThePassage() {
        val keys = range(Skills.FINGER_NUMBER_TUNES)
        assertEquals(true, 60 in keys && 64 in keys)
    }

    @Test
    fun aKeyBelowMiddleCStillShowsTwoOctaves() = assertEquals(36..59, range(Skills.LH_C_POSITION))

    @Test
    fun everyConceptWithAHandShowsItsWholeHandPosition() {
        val withHand = Curriculum.graph.skills.filter { Curriculum.scene(it.id)?.stage?.hand != null }
        assertEquals(true, withHand.isNotEmpty())
        withHand.forEach { skill ->
            val stage = Curriculum.scene(skill.id)!!.stage.keys
            val keys = range(skill)
            assertEquals(true, stage.first in keys && stage.last in keys, "${skill.id}: $stage not in $keys")
        }
    }
}
