package pianoanchor.engine.scheduler

import pianoanchor.core.model.MemoryState
import pianoanchor.core.model.Rating
import pianoanchor.engine.Tunables
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FsrsTest {
    private val fsrs = Fsrs()

    @Test
    fun `initial difficulty falls as the rating rises`() {
        val difficulties = Rating.entries.map { fsrs.initial(it).difficulty }
        assertEquals(difficulties.sortedDescending(), difficulties)
        assertEquals(difficulties.distinct(), difficulties)
    }

    @Test
    fun `AGAIN shrinks stability, GOOD grows it`() {
        val state = MemoryState(10.0, 5.0)
        assertTrue(fsrs.next(state, 10, Rating.AGAIN).stability < state.stability)
        assertTrue(fsrs.next(state, 10, Rating.GOOD).stability > state.stability)
    }

    @Test
    fun `retrievability is 1 at day 0 and falls`() {
        val state = fsrs.initial(Rating.GOOD)
        assertEquals(1.0, fsrs.retrievability(state, 0))
        assertTrue(fsrs.retrievability(state, 1) < 1.0)
        assertTrue(fsrs.retrievability(state, 30) < fsrs.retrievability(state, 1))
    }

    @Test
    fun `retrievability equals desired retention at the interval (plus or minus 1 day)`() {
        val state = MemoryState(40.0, 5.0)
        val days = fsrs.intervalDays(state)
        for (d in days - 1..days + 1) {
            assertTrue(abs(fsrs.retrievability(state, d) - Tunables.DESIRED_RETENTION) < 0.01)
        }
    }

    @Test
    fun `interval clamps to 1 and MAX_INTERVAL_DAYS`() {
        assertEquals(1, fsrs.intervalDays(MemoryState(0.001, 5.0)))
        assertEquals(Tunables.MAX_INTERVAL_DAYS, fsrs.intervalDays(MemoryState(1e9, 5.0)))
    }

    @Test
    fun `rejects 20 weights`() {
        assertFailsWith<IllegalArgumentException> { FsrsParameters(List(20) { 1.0 }) }
    }

    @Test
    fun `rejects elapsedDays 0 in next`() {
        assertFailsWith<IllegalArgumentException> { fsrs.next(MemoryState(1.0, 5.0), 0, Rating.GOOD) }
    }

    @Test
    fun `rejects negative elapsedDays in retrievability`() {
        assertFailsWith<IllegalArgumentException> { fsrs.retrievability(MemoryState(1.0, 5.0), -1) }
    }

    @Test
    fun `rejects retention 1_0`() {
        assertFailsWith<IllegalArgumentException> { fsrs.intervalDays(MemoryState(1.0, 5.0), 1.0) }
        assertFailsWith<IllegalArgumentException> { fsrs.intervalDays(MemoryState(1.0, 5.0), 0.0) }
    }

    @Test
    fun `HARD grows stability less than GOOD and EASY more`() {
        val state = MemoryState(10.0, 5.0)
        val hard = fsrs.next(state, 10, Rating.HARD).stability
        val good = fsrs.next(state, 10, Rating.GOOD).stability
        val easy = fsrs.next(state, 10, Rating.EASY).stability
        assertTrue(hard < good && good < easy)
    }
}
