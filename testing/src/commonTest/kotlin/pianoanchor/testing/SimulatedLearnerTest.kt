package pianoanchor.testing

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import pianoanchor.core.model.Band
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SimulatedLearnerTest {
    private val ids = (0 until SKILLS).map { "s-$it" }
    private val graph = skillGraphOf(*ids.map { it to emptySet<String>() }.toTypedArray())
    private val day0 = LocalDate(2026, 1, 1)

    private fun day(offset: Int) = day0.plus(offset, DateTimeUnit.DAY)

    @Test
    fun `same seed gives the same checks`() {
        fun run(seed: Long): List<Band> {
            val learner = SimulatedLearner(seed, graph)
            return ids.map { learner.coldCheck(it, day(0)) } + ids.map { learner.coldCheck(it, day(20)) }
        }

        assertEquals(run(7), run(7))
        assertTrue(run(7) != run(8))
    }

    @Test
    fun `first check of a skill is recalled and both recalled bands occur`() {
        val learner = SimulatedLearner(1, graph)
        val bands = ids.map { learner.coldCheck(it, day(0)) }

        assertTrue(Band.KEEP_PRACTISING !in bands)
        assertTrue(Band.CLEAN in bands && Band.GOOD in bands)
    }

    @Test
    fun `skills left for ten years are mostly forgotten`() {
        val learner = SimulatedLearner(1, graph)
        ids.forEach { learner.coldCheck(it, day(0)) }

        val forgotten = ids.count { learner.coldCheck(it, day(DAYS_IN_TEN_YEARS)) == Band.KEEP_PRACTISING }

        assertTrue(forgotten > SKILLS / 2, "only $forgotten of $SKILLS forgotten")
    }

    @Test
    fun `practice makes a skill last longer`() {
        fun recalled(practiceRuns: Int): Int {
            val learner = SimulatedLearner(3, graph)
            ids.forEach { learner.coldCheck(it, day(0)) }
            ids.forEach { id -> repeat(practiceRuns) { learner.practice(id) } }
            return ids.count { learner.coldCheck(it, day(CHECK_DAY)) != Band.KEEP_PRACTISING }
        }

        assertTrue(recalled(PRACTICE_RUNS) > recalled(0))
    }

    @Test
    fun `practice of an unchecked skill is refused`() {
        assertFailsWith<IllegalStateException> { SimulatedLearner(1, graph).practice("s-0") }
    }

    private companion object {
        const val SKILLS = 200
        const val DAYS_IN_TEN_YEARS = 3_650
        const val CHECK_DAY = 30
        const val PRACTICE_RUNS = 40
    }
}
