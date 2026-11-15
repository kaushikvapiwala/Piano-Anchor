package pianoanchor.platform.storage

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import pianoanchor.core.model.MemoryState
import pianoanchor.core.model.Rating
import pianoanchor.core.model.Review
import pianoanchor.core.model.SkillState
import pianoanchor.core.ports.LearnerRepositoryPort
import pianoanchor.platform.storage.db.LearnerQueries
import pianoanchor.platform.storage.db.PianoAnchorDatabase

/**
 * [LearnerRepositoryPort] in the on-device SQLite database. [driver] comes from `createPianoAnchorDatabaseDriver`
 * (schema created, foreign keys on); every query runs on [dispatcher] (`Dispatchers.IO` in the app). A review and its
 * skill's state are written in one transaction. Days are stored as ISO `yyyy-mm-dd` text.
 */
public class SqlLearnerRepositoryAdapter(
    driver: SqlDriver,
    private val dispatcher: CoroutineDispatcher,
) : LearnerRepositoryPort {
    private val queries = PianoAnchorDatabase(driver).learnerQueries

    override suspend fun record(
        review: Review,
        state: SkillState,
    ): Unit =
        withContext(dispatcher) {
            require(state.skillId == review.skillId) { "state is of ${state.skillId}, review of ${review.skillId}" }
            storage("record") {
                queries.transaction {
                    require(!queries.hasReview(review.skillId, review.day.toString()).executeAsOne()) {
                        "${review.skillId} already has a review on ${review.day}"
                    }
                    queries.insertReview(review.skillId, review.day.toString(), review.rating.name, review.attemptId)
                    queries.writeState(state)
                }
            }
        }

    override suspend fun states(): Map<String, SkillState> =
        withContext(dispatcher) {
            storage("states") {
                queries
                    .selectStates(::toState)
                    .executeAsList()
                    .associateBy { it.skillId }
            }
        }

    override suspend fun reviews(skillId: String): List<Review> =
        withContext(dispatcher) {
            storage("reviews") {
                queries
                    .selectReviews(skillId) { skill, day, rating, attemptId ->
                        Review(skill, LocalDate.parse(day), Rating.valueOf(rating), attemptId)
                    }.executeAsList()
            }
        }

    override suspend fun replaceStates(states: Collection<SkillState>): Unit =
        withContext(dispatcher) {
            storage("replaceStates") {
                queries.transaction {
                    queries.deleteStates()
                    states.forEach(queries::writeState)
                }
            }
        }
}

private fun LearnerQueries.writeState(state: SkillState) =
    upsertState(
        skill_id = state.skillId,
        stability = state.memory.stability,
        difficulty = state.memory.difficulty,
        introduced_day = state.introducedDay.toString(),
        last_review_day = state.lastReviewDay.toString(),
        due_day = state.dueDay.toString(),
        clean_cold_checks = state.cleanColdChecks.toLong(),
        recalled_cold_checks = state.recalledColdChecks.toLong(),
        learned = if (state.learned) 1L else 0L,
    )

@Suppress("LongParameterList")
private fun toState(
    skillId: String,
    stability: Double,
    difficulty: Double,
    introducedDay: String,
    lastReviewDay: String,
    dueDay: String,
    cleanColdChecks: Long,
    recalledColdChecks: Long,
    learned: Long,
) = SkillState(
    skillId = skillId,
    memory = MemoryState(stability, difficulty),
    introducedDay = LocalDate.parse(introducedDay),
    lastReviewDay = LocalDate.parse(lastReviewDay),
    dueDay = LocalDate.parse(dueDay),
    cleanColdChecks = cleanColdChecks.toInt(),
    recalledColdChecks = recalledColdChecks.toInt(),
    learned = learned != 0L,
)
