package pianoanchor.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.AttemptRepositoryPort
import kotlin.time.Instant

/** In-memory [AttemptRepositoryPort]: safe to call from any thread, forgets everything when the process ends. */
class FakeAttemptRepository : AttemptRepositoryPort {
    private val attempts = MutableStateFlow(emptyMap<String, Attempt>())

    /** While true, every call throws [StorageUnavailableException] (`recent`'s flow fails on collection). */
    var failing: Boolean = false

    override suspend fun save(attempt: Attempt) {
        failIfFailing("save")
        attempts.update { all ->
            require(attempt.id !in all) { "attempt ${attempt.id} is already stored" }
            all + (attempt.id to attempt)
        }
    }

    override suspend fun get(id: String): Attempt? {
        failIfFailing("get")
        return attempts.value[id]
    }

    override fun recent(
        passageId: String,
        limit: Int,
    ): Flow<List<Attempt>> {
        require(limit > 0) { "limit ($limit) must be positive" }
        return attempts.map { all ->
            failIfFailing("recent")
            all.values
                .filter { it.passageId == passageId }
                .sortedByDescending { it.startedAt }
                .take(limit)
        }
    }

    override suspend fun countStartedBefore(
        passageId: String,
        before: Instant,
    ): Int {
        failIfFailing("countStartedBefore")
        return attempts.value.values.count { it.passageId == passageId && it.startedAt < before }
    }

    override suspend fun cleanTempos(passageId: String): Set<Int> {
        failIfFailing("cleanTempos")
        return attempts.value.values
            .filter { it.passageId == passageId && it.band == Band.CLEAN }
            .mapNotNullTo(LinkedHashSet()) { it.tempoBpm }
    }

    private fun failIfFailing(operation: String) {
        if (failing) throw StorageUnavailableException("$operation failed")
    }
}
