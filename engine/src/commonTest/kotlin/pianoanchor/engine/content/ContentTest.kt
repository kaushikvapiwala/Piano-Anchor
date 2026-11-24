package pianoanchor.engine.content

import pianoanchor.engine.onboarding.PlacementTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentTest {
    @Test
    fun builtInGraphIsValid() {
        assertEquals(34, Curriculum.graph.skills.size)
        assertEquals(setOf("finger-number-tunes"), Curriculum.graph.byId("bars-and-counting")!!.prerequisites)
    }

    @Test
    fun cdeExercisesRhCPosition() = assertEquals(setOf("rh-c-position"), Passages.CDE.skills)

    @Test
    fun placementPassagesExistAndAreInTheirUnits() {
        assertEquals(4, PlacementTest.passageIds.size)
        PlacementTest.passageIds.forEachIndexed { unit, id ->
            assertTrue(Curriculum.units[unit].passages.any { it.id == id }, "$id is not in unit $unit")
        }
    }
}
