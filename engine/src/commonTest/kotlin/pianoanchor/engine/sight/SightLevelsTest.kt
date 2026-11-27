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
    fun everySkillOpensAll10() {
        assertEquals(10, SightLevels.all.size)
        assertEquals((1..10).toList(), SightLevels.all.map { it.number })
        assertEquals(10, SightLevels.open(SightLevels.all.map { it.skill }.toSet()))
    }

    @Test
    fun eighthNotesWithoutMelodyAndChordsStaysAtLevel8() {
        val learned =
            SightLevels.all
                .take(8)
                .map { it.skill }
                .toSet() - Skills.MELODY_AND_CHORDS.id
        assertEquals(7, SightLevels.open(learned + Skills.EIGHTH_NOTES.id))
        val upToChords =
            SightLevels.all
                .take(8)
                .map { it.skill }
                .toSet()
        assertEquals(8, SightLevels.open(upToChords + Skills.DOTTED_QUARTER.id))
        assertEquals(9, SightLevels.open(upToChords + Skills.EIGHTH_NOTES.id))
        assertEquals(10, SightLevels.open(upToChords + Skills.EIGHTH_NOTES.id + Skills.DOTTED_QUARTER.id))
    }
}
