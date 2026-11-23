package pianoanchor.engine.learner

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import pianoanchor.core.model.Rating
import pianoanchor.engine.content.Curriculum
import pianoanchor.testing.FakeLearnerRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearnerRecordQuizTest {
    private val repository = FakeLearnerRepository()
    private val model = LearnerModel(Curriculum.graph, repository)
    private val monday = LocalDate(2026, 11, 16)

    @Test
    fun `records the rating as one review a day`() =
        runTest {
            val review = assertNotNull(model.recordQuiz("middle-c", Rating.HARD, monday, "1"))
            assertEquals(Rating.HARD, review.rating)
            assertEquals("quiz:1", review.attemptId)
            assertTrue(review.unaided)
            assertEquals(monday, repository.states().getValue("middle-c").lastReviewDay)
            assertNull(model.recordQuiz("middle-c", Rating.GOOD, monday, "2"))
            assertEquals(1, repository.reviews("middle-c").size)
        }

    @Test
    fun `replay rebuilds the state from quiz reviews`() =
        runTest {
            model.recordQuiz("middle-c", Rating.GOOD, monday, "1")
            val before = repository.states()
            model.replay()
            assertEquals(before, repository.states())
        }

    @Test
    fun `a skill that is not in the graph throws and records nothing`() =
        runTest {
            assertFailsWith<IllegalArgumentException> { model.recordQuiz("nope", Rating.GOOD, monday, "1") }
            assertEquals(emptyMap(), repository.states())
        }
}
