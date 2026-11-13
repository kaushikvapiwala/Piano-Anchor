package pianoanchor.core.ports

import kotlinx.coroutines.flow.Flow
import pianoanchor.core.model.Attempt

/**
 * Driven port: the learner's completed attempts, kept across app restarts and keyed by [Attempt.id].
 * Contract: `pianoanchor.testing.AttemptRepositoryPortContractTest`.
 */
public interface AttemptRepositoryPort {
    /** Stores [attempt], events included. Throws [IllegalArgumentException] when its id is already stored. */
    public suspend fun save(attempt: Attempt)

    /** The stored attempt with [id], or null if none is stored. */
    public suspend fun get(id: String): Attempt?

    /**
     * The attempts of [passageId], newest first by [Attempt.startedAt] (ties in any order), at most [limit] of them.
     * Emits the current list on collection and again after every save. Throws [IllegalArgumentException] when [limit]
     * is not positive.
     */
    public fun recent(
        passageId: String,
        limit: Int,
    ): Flow<List<Attempt>>
}
