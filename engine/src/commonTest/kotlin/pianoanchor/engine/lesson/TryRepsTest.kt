package pianoanchor.engine.lesson

import pianoanchor.core.model.TryCheck
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
import kotlin.test.Test
import kotlin.test.assertEquals

class TryRepsTest {
    private fun needed(skill: pianoanchor.core.model.Skill) = tryRepsNeeded(checkNotNull(Curriculum.tryCheck(skill.id)))

    @Test
    fun aThreeNoteCheckNeedsThree() {
        assertEquals(3, needed(Skills.STAFF_STEPS))
        assertEquals(3, needed(Skills.C_CHORD))
        assertEquals(3, needed(Skills.MIDDLE_C))
    }

    @Test
    fun aLongCheckNeedsTwo() {
        assertEquals(2, needed(Skills.HIGH_AND_LOW))
        assertEquals(2, needed(Skills.STEADY_BEAT))
    }

    @Test
    fun aCheckTheAppCannotVerifyNeedsOne() {
        assertEquals(1, tryRepsNeeded(TryCheck.AnyKey))
        assertEquals(1, tryRepsNeeded(TryCheck.Passage("c-d-e")))
    }
}
