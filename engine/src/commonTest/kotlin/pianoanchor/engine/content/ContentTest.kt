package pianoanchor.engine.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ContentTest {
    @Test
    fun builtInGraphIsValid() {
        assertEquals(listOf("rh-c-position"), Skills.GRAPH.skills.map { it.id })
        assertEquals(emptySet(), Skills.RH_C_POSITION.prerequisites)
    }

    @Test
    fun everyBuiltInPassageHasAtLeastOneSkillAndAllAreInTheBuiltInGraph() {
        Passages.all.forEach { passage ->
            assertTrue(passage.skills.isNotEmpty(), passage.id)
            passage.skills.forEach { assertNotNull(Skills.GRAPH.byId(it), "${passage.id}: $it") }
        }
    }

    @Test
    fun cdeExercisesRhCPosition() = assertEquals(setOf("rh-c-position"), Passages.CDE.skills)
}
