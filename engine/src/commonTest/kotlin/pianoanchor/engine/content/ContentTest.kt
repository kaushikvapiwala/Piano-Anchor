package pianoanchor.engine.content

import kotlin.test.Test
import kotlin.test.assertEquals

class ContentTest {
    @Test
    fun builtInGraphIsValid() {
        assertEquals(24, Curriculum.graph.skills.size)
        assertEquals(setOf("kb-middle-c"), Skills.RH_C_POSITION.prerequisites)
    }

    @Test
    fun cdeExercisesRhCPosition() = assertEquals(setOf("rh-c-position"), Passages.CDE.skills)
}
