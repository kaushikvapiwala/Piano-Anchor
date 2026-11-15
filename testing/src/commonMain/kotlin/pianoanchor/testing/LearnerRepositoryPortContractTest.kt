package pianoanchor.testing

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import pianoanchor.core.model.MemoryState
import pianoanchor.core.model.Rating
import pianoanchor.core.model.Review
import pianoanchor.core.model.SkillState
import pianoanchor.core.ports.LearnerRepositoryPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val SKILL = "pitch:c4"
private const val OTHER_SKILL = "pitch:d4"
private const val STABILITY = 3.5
private const val DIFFICULTY = 6.25
private const val LATER_STABILITY = 8.0
private const val CLEAN_CHECKS = 2
private const val RECALLED_CHECKS = 3
private const val UNAIDED_RECALLED_CHECKS = 1

private val FIRST_DAY = LocalDate(2026, 11, 1)
private val SECOND_DAY = LocalDate(2026, 11, 3)
private val THIRD_DAY = LocalDate(2026, 11, 9)

/** A review of [skillId] on [day]. */
internal fun review(
    skillId: String = SKILL,
    day: LocalDate = FIRST_DAY,
    rating: Rating = Rating.GOOD,
    attemptId: String = "a-${day.toEpochDays()}",
    unaided: Boolean = true,
): Review = Review(skillId, day, rating, attemptId, unaided)

/** A cached state of [skillId] with distinguishable values. */
internal fun skillState(
    skillId: String = SKILL,
    stability: Double = STABILITY,
    lastReviewDay: LocalDate = FIRST_DAY,
    learned: Boolean = false,
): SkillState =
    SkillState(
        skillId = skillId,
        memory = MemoryState(stability, DIFFICULTY),
        introducedDay = FIRST_DAY,
        lastReviewDay = lastReviewDay,
        dueDay = THIRD_DAY,
        cleanColdChecks = CLEAN_CHECKS,
        recalledColdChecks = RECALLED_CHECKS,
        unaidedRecalledChecks = UNAIDED_RECALLED_CHECKS,
        learned = learned,
    )

/** Behaviour every [LearnerRepositoryPort] adapter must satisfy. Subclass it once per adapter. */
abstract class LearnerRepositoryPortContractTest {
    protected abstract fun createSubject(): LearnerRepositoryPort

    @Test
    fun emptyRepositoryHasNoStates() =
        runTest {
            val repository = createSubject()
            assertTrue(repository.states().isEmpty())
            assertTrue(repository.reviews(SKILL).isEmpty())
        }

    @Test
    fun recordThenStatesAndReviewsRoundTrip() =
        runTest {
            val repository = createSubject()
            repository.record(review(), skillState())
            repository.record(review(OTHER_SKILL, rating = Rating.AGAIN), skillState(OTHER_SKILL, learned = true))

            assertEquals(
                mapOf(SKILL to skillState(), OTHER_SKILL to skillState(OTHER_SKILL, learned = true)),
                repository.states(),
            )
            assertEquals(listOf(review()), repository.reviews(SKILL))
            assertEquals(listOf(review(OTHER_SKILL, rating = Rating.AGAIN)), repository.reviews(OTHER_SKILL))
        }

    @Test
    fun theUnaidedFlagOfAReviewRoundTrips() =
        runTest {
            val repository = createSubject()
            repository.record(review(unaided = false), skillState())
            repository.record(review(OTHER_SKILL, unaided = true), skillState(OTHER_SKILL))

            assertEquals(listOf(review(unaided = false)), repository.reviews(SKILL))
            assertEquals(listOf(review(OTHER_SKILL, unaided = true)), repository.reviews(OTHER_SKILL))
            assertEquals(UNAIDED_RECALLED_CHECKS, repository.states().getValue(SKILL).unaidedRecalledChecks)
        }

    @Test
    fun aSecondReviewOfASkillOnTheSameDayIsRejected() =
        runTest {
            val repository = createSubject()
            repository.record(review(), skillState())

            val again = review(rating = Rating.EASY, attemptId = "other")
            val newer = skillState(stability = LATER_STABILITY)

            assertFailsWith<IllegalArgumentException> { repository.record(again, newer) }
            assertEquals(listOf(review()), repository.reviews(SKILL))
            assertEquals(skillState(), repository.states().getValue(SKILL))
        }

    @Test
    fun aStateOfAnotherSkillIsRejected() =
        runTest {
            val repository = createSubject()

            assertFailsWith<IllegalArgumentException> { repository.record(review(), skillState(OTHER_SKILL)) }
            assertTrue(repository.reviews(SKILL).isEmpty())
            assertTrue(repository.states().isEmpty())
        }

    @Test
    fun recordReplacesTheCachedState() =
        runTest {
            val repository = createSubject()
            repository.record(review(), skillState())
            val later = skillState(stability = LATER_STABILITY, lastReviewDay = SECOND_DAY)
            repository.record(review(day = SECOND_DAY), later)

            assertEquals(mapOf(SKILL to later), repository.states())
        }

    @Test
    fun reviewsComeInDayOrder() =
        runTest {
            val repository = createSubject()
            repository.record(review(day = THIRD_DAY), skillState())
            repository.record(review(day = FIRST_DAY), skillState())
            repository.record(review(day = SECOND_DAY), skillState())

            assertEquals(listOf(FIRST_DAY, SECOND_DAY, THIRD_DAY), repository.reviews(SKILL).map { it.day })
        }

    @Test
    fun replaceStatesSwapsStatesAndKeepsTheLog() =
        runTest {
            val repository = createSubject()
            repository.record(review(), skillState())
            repository.record(review(OTHER_SKILL), skillState(OTHER_SKILL))

            val rebuilt = skillState(stability = LATER_STABILITY, learned = true)
            repository.replaceStates(listOf(rebuilt))

            assertEquals(mapOf(SKILL to rebuilt), repository.states())
            assertEquals(listOf(review()), repository.reviews(SKILL))
            assertEquals(listOf(review(OTHER_SKILL)), repository.reviews(OTHER_SKILL))
        }
}
