package pianoanchor.core.ports

import pianoanchor.core.model.Review
import pianoanchor.core.model.SkillState
import pianoanchor.core.model.StorageUnavailableException

/**
 * Driven port: the learner's review log (the source of truth) and the cached state per skill, which a replay of the
 * log can always rebuild. Throws [StorageUnavailableException] when the store can't be read or written (#55).
 * Contract: `pianoanchor.testing.LearnerRepositoryPortContractTest`.
 */
public interface LearnerRepositoryPort {
    /**
     * Appends [review] and replaces the cached [state] of its skill in one transaction. Throws
     * [IllegalArgumentException] when the skill already has a review on that day or [SkillState.skillId] differs.
     */
    public suspend fun record(
        review: Review,
        state: SkillState,
    )

    /** Cached states of every skill seen, keyed by id. */
    public suspend fun states(): Map<String, SkillState>

    /** Reviews of [skillId] in day order. */
    public suspend fun reviews(skillId: String): List<Review>

    /** Replaces every cached state (after a replay); the log is untouched. */
    public suspend fun replaceStates(states: Collection<SkillState>)
}
