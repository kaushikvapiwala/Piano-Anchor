package pianoanchor.engine.scheduler

import pianoanchor.core.model.MemoryState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FsrsVectorsTest {
    private val fsrs = Fsrs()

    private fun assertClose(
        expected: Double,
        actual: Double,
        relative: Double,
        what: String,
    ) {
        assertTrue(abs(expected - actual) <= relative * abs(expected), "$what: expected $expected, was $actual")
    }

    @Test
    fun `every py-fsrs vector matches`() {
        assertEquals(25, FSRS6_VECTORS.size)
        for ((sequence, steps) in FSRS6_VECTORS.withIndex()) {
            var state: MemoryState? = null
            for ((index, step) in steps.withIndex()) {
                val label = "sequence $sequence review $index"
                val before = state
                state =
                    if (before == null) {
                        fsrs.initial(step.rating)
                    } else {
                        val recall = fsrs.retrievability(before, step.gapDays)
                        assertClose(checkNotNull(step.retrievability), recall, 1e-6, "$label retrievability")
                        fsrs.next(before, step.gapDays, step.rating)
                    }
                assertClose(step.stability, state.stability, 1e-4, "$label stability")
                assertClose(step.difficulty, state.difficulty, 1e-4, "$label difficulty")
                assertEquals(step.intervalDays, fsrs.intervalDays(state), "$label interval")
            }
        }
    }
}
