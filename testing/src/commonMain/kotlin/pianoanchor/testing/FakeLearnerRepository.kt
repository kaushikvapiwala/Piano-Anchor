package pianoanchor.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import pianoanchor.core.model.Review
import pianoanchor.core.model.SkillState
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.LearnerRepositoryPort

/** In-memory [LearnerRepositoryPort]: safe to call from any thread, forgets everything when the process ends. */
class FakeLearnerRepository : LearnerRepositoryPort {
    private val log = MutableStateFlow(emptyList<Review>())
    private val cached = MutableStateFlow(emptyMap<String, SkillState>())

    /** While true, every call throws [StorageUnavailableException]. */
    var failing: Boolean = false

    override suspend fun record(
        review: Review,
        state: SkillState,
    ) {
        failIfFailing("record")
        require(state.skillId == review.skillId) { "state is of ${state.skillId}, review of ${review.skillId}" }
        log.update { reviews ->
            require(reviews.none { it.skillId == review.skillId && it.day == review.day }) {
                "${review.skillId} already has a review on ${review.day}"
            }
            reviews + review
        }
        cached.update { it + (state.skillId to state) }
    }

    override suspend fun states(): Map<String, SkillState> {
        failIfFailing("states")
        return cached.value
    }

    override suspend fun reviews(skillId: String): List<Review> {
        failIfFailing("reviews")
        return log.value.filter { it.skillId == skillId }.sortedBy { it.day }
    }

    override suspend fun replaceStates(states: Collection<SkillState>) {
        failIfFailing("replaceStates")
        cached.value = states.associateBy { it.skillId }
    }

    private fun failIfFailing(operation: String) {
        if (failing) throw StorageUnavailableException("$operation failed")
    }
}
