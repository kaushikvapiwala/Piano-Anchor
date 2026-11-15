package pianoanchor.core.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LearnerTest {
    private val day = LocalDate(2026, 11, 1)
    private val review = Review("pitch:c4", day, Rating.GOOD, "a-1", unaided = true)
    private val state =
        SkillState(
            "pitch:c4",
            MemoryState(3.0, 5.0),
            day,
            day,
            LocalDate(2026, 11, 4),
            cleanColdChecks = 1,
            recalledColdChecks = 2,
            unaidedRecalledChecks = 1,
            learned = false,
        )

    @Test
    fun `a review keeps its fields and compares by value`() {
        assertEquals(Review("pitch:c4", day, Rating.GOOD, "a-1", unaided = true), review)
        assertNotEquals(review, review.copy(rating = Rating.AGAIN))
        assertEquals("a-1", review.attemptId)
    }

    @Test
    fun `a skill state keeps its fields and compares by value`() {
        assertEquals(state, state.copy())
        assertNotEquals(state, state.copy(learned = true))
        assertEquals(LocalDate(2026, 11, 4), state.dueDay)
        assertEquals(1, state.cleanColdChecks)
        assertEquals(2, state.recalledColdChecks)
        assertEquals(1, state.unaidedRecalledChecks)
        assertNotEquals(review, review.copy(unaided = false))
    }

    @Test
    fun `a skill state rejects recalled checks below clean checks and negatives`() {
        val low = assertFailsWith<IllegalArgumentException> { state.copy(recalledColdChecks = 0) }
        assertTrue("pitch:c4" in low.message.orEmpty())
        assertFailsWith<IllegalArgumentException> { state.copy(cleanColdChecks = -1, recalledColdChecks = -1) }
        state.copy(cleanColdChecks = 0, recalledColdChecks = 0, unaidedRecalledChecks = 0)
    }

    @Test
    fun `a skill state keeps unaided recalled checks within 0 and the recalled checks`() {
        assertTrue(
            "pitch:c4" in
                assertFailsWith<IllegalArgumentException> { state.copy(unaidedRecalledChecks = 3) }.message.orEmpty(),
        )
        assertFailsWith<IllegalArgumentException> { state.copy(unaidedRecalledChecks = -1) }
        state.copy(unaidedRecalledChecks = 2)
    }
}
