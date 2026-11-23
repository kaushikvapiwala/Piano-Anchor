package pianoanchor.engine.content

import kotlin.test.Test
import kotlin.test.assertEquals

class ContentTest {
    @Test
    fun builtInGraphIsValid() {
        assertEquals(34, Curriculum.graph.skills.size)
        assertEquals(setOf("finger-number-tunes"), Curriculum.graph.byId("bars-and-counting")!!.prerequisites)
    }

    @Test
    fun cdeExercisesRhCPosition() = assertEquals(setOf("rh-c-position"), Passages.CDE.skills)
}
