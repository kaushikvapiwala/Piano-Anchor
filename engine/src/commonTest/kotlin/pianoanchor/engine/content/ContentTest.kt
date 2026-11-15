package pianoanchor.engine.content

import kotlin.test.Test
import kotlin.test.assertEquals

class ContentTest {
    @Test
    fun builtInGraphIsValid() {
        assertEquals(listOf("rh-c-position"), Curriculum.graph.skills.map { it.id })
        assertEquals(emptySet(), Skills.RH_C_POSITION.prerequisites)
    }

    @Test
    fun cdeExercisesRhCPosition() = assertEquals(setOf("rh-c-position"), Passages.CDE.skills)
}
