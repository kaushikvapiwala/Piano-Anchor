package pianoanchor.engine.learner

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import pianoanchor.core.model.Band
import pianoanchor.core.model.Rating
import pianoanchor.engine.content.Curriculum
import pianoanchor.testing.FakeLearnerRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearnerRecordEarSetTest {
    private val repository = FakeLearnerRepository()
    private val model = LearnerModel(Curriculum.graph, repository)
    private val monday = LocalDate(2026, 11, 16)

    @Test
    fun `records one review per day`() =
        runTest {
            val review = assertNotNull(model.recordEarSet("ear-echo-note", Band.GOOD, monday, "1"))
            assertEquals(Rating.HARD, review.rating)
            assertEquals("ear:1", review.attemptId)
            assertTrue(review.unaided)
            assertEquals(1, repository.states().getValue("ear-echo-note").unaidedRecalledChecks)
            assertEquals(monday, repository.states().getValue("ear-echo-note").lastReviewDay)
        }

    @Test
    fun `the second set that day records nothing`() =
        runTest {
            model.recordEarSet("ear-echo-note", Band.KEEP_PRACTISING, monday, "1")
            assertNull(model.recordEarSet("ear-echo-note", Band.CLEAN, monday, "2"))
            assertEquals(1, repository.reviews("ear-echo-note").size)
        }

    @Test
    fun `replay rebuilds the state from ear reviews`() =
        runTest {
            model.recordEarSet("ear-echo-note", Band.CLEAN, monday, "1")
            val before = repository.states()
            model.replay()
            assertEquals(before, repository.states())
        }

    @Test
    fun `a skill that is not in the graph throws and records nothing`() =
        runTest {
            assertFailsWith<IllegalArgumentException> { model.recordEarSet("ear-old", Band.CLEAN, monday, "1") }
            assertEquals(emptyMap(), repository.states())
        }
}
