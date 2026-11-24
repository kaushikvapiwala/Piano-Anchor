package pianoanchor.core.ports

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.AttemptSummary
import pianoanchor.core.model.StorageUnavailableException
import kotlin.time.Instant

/**
 * Driven port: the learner's completed attempts, kept across app restarts and keyed by [Attempt.id].
 * Contract: `pianoanchor.testing.AttemptRepositoryPortContractTest`.
 */
public interface AttemptRepositoryPort {
    /**
     * Stores [attempt], with its events and played notes. Throws [IllegalArgumentException] when its id is already
     * stored. Throws [StorageUnavailableException] when the store can't be read or written.
     */
    public suspend fun save(attempt: Attempt)

    /**
     * The stored attempt with [id], or null if none is stored. Throws [StorageUnavailableException] when the store
     * can't be read or written.
     */
    public suspend fun get(id: String): Attempt?

    /**
     * The full-run attempts ([Attempt.spot] null) of [passageId], newest first by [Attempt.startedAt] (ties in any
     * order), at most [limit] of them. Emits the current list on collection and again after every save. Throws
     * [IllegalArgumentException] when [limit] is not positive. The flow fails with [StorageUnavailableException] when
     * the store can't be read.
     */
    public fun recent(
        passageId: String,
        limit: Int,
    ): Flow<List<Attempt>>

    /**
     * How many full-run attempts ([Attempt.spot] null) of [passageId] started strictly before [before]; 0 for an
     * unknown passage. Adapters that store [Attempt.startedAt] coarser compare at that precision. Throws
     * [StorageUnavailableException] when the store can't be read or written.
     */
    public suspend fun countStartedBefore(
        passageId: String,
        before: Instant,
    ): Int

    /**
     * Tempos (BPM) of [passageId]'s timed attempts whose band is CLEAN, spot-loop attempts ([Attempt.spot]) excluded;
     * empty when none. Throws [StorageUnavailableException] when the store can't be read or written.
     */
    public suspend fun cleanTempos(passageId: String): Set<Int>

    /**
     * Whether [passageId] has a wait-mode attempt (no tempo) whose band is CLEAN, spot-loop attempts excluded. Throws
     * [StorageUnavailableException] when the store can't be read or written.
     */
    public suspend fun hasCleanWaitRun(passageId: String): Boolean

    /**
     * When [passageId] was last played: the latest [Attempt.startedAt] of its attempts, spot-loop ones included, or
     * null for a passage never played. Adapters that store [Attempt.startedAt] coarser answer at that precision. Throws
     * [StorageUnavailableException] when the store can't be read or written.
     */
    public suspend fun lastPlayed(passageId: String): Instant?

    /**
     * The calendar days, in [zone], from [from] on (inclusive) on which at least one attempt of any passage (spot
     * loops included) started; empty when none. Throws [StorageUnavailableException] when the store can't be read or
     * written.
     */
    public suspend fun practiceDays(
        from: LocalDate,
        zone: TimeZone,
    ): Set<LocalDate>

    /**
     * Summaries of the attempts whose passage id starts with [prefix], oldest first by [AttemptSummary.startedAt],
     * ties by id; empty when none. Throws [IllegalArgumentException] for a blank prefix, [StorageUnavailableException]
     * when the store can't be read.
     */
    public suspend fun summariesWithPassagePrefix(prefix: String): List<AttemptSummary>
}
