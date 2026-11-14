package pianoanchor.feature.practice

import pianoanchor.engine.content.Passages
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PracticeStateTest {
    @Test
    fun `step views must match the passage's steps`() {
        val start = PracticeState.start(Passages.CDE)

        assertFailsWith<IllegalArgumentException> { start.copy(steps = start.steps.drop(1)) }
    }
}
