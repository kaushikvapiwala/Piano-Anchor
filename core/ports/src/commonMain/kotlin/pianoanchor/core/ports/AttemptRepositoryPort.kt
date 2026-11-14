package pianoanchor.core.ports

import kotlinx.coroutines.flow.Flow
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.StorageUnavailableException
import kotlin.time.Instant

/**
 * Driven port: the learner's completed attempts, kept across app restarts and keyed by [Attempt.id].
 * Contract: `pianoanchor.testing.AttemptRepositoryPortContractTest`.
 */
public interface AttemptRepositoryPort {
    /**
     * Stores [attempt], events included. Throws [IllegalArgumentException] when its id is already stored.
     * Throws [StorageUnavailableException] when the store can't be read or written.
     */
    public suspend fun save(attempt: Attempt)

    /**
     * The stored attempt with [id], or null if none is stored. Throws [StorageUnavailableException] when the store
     * can't be read or written.
     */
    public suspend fun get(id: String): Attempt?

    /**
     * The attempts of [passageId], newest first by [Attempt.startedAt] (ties in any order), at most [limit] of them.
     * Emits the current list on collection and again after every save. Throws [IllegalArgumentException] when [limit]
     * is not positive. The flow fails with [StorageUnavailableException] when the store can't be read.
     */
    public fun recent(
        passageId: String,
        limit: Int,
    ): Flow<List<Attempt>>

    /**
     * How many attempts of [passageId] started strictly before [before]; 0 for an unknown passage. Adapters that store
     * [Attempt.startedAt] coarser compare at that precision. Throws [StorageUnavailableException] when the store
     * can't be read or written.
     */
    public suspend fun countStartedBefore(
        passageId: String,
        before: Instant,
    ): Int
}
