package pianoanchor.engine.sight

import pianoanchor.engine.content.Skills
import kotlin.test.Test
import kotlin.test.assertEquals

class SightLevelsTest {
    @Test
    fun noStaffSkillOpensNoLevel() {
        assertEquals(0, SightLevels.open(emptySet()))
        assertEquals(0, SightLevels.open(setOf(Skills.TREBLE_CLEF.id)))
    }

    @Test
    fun staffStepsOpensLevel1() {
        assertEquals(1, SightLevels.open(setOf(Skills.STAFF_STEPS.id)))
    }

    @Test
    fun levelsOpenInOrder() {
        val learned = setOf(Skills.STAFF_STEPS.id, Skills.TREBLE_CLEF.id, Skills.BASS_CLEF_WHOLE_NOTE.id)
        assertEquals(2, SightLevels.open(learned))
    }

    @Test
    fun everySkillOpensAll8() {
        assertEquals(8, SightLevels.all.size)
        assertEquals((1..8).toList(), SightLevels.all.map { it.number })
        assertEquals(8, SightLevels.open(SightLevels.all.map { it.skill }.toSet()))
    }
}
