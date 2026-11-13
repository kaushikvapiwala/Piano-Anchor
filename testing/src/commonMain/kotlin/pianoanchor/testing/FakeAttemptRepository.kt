package pianoanchor.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import pianoanchor.core.model.Attempt
import pianoanchor.core.ports.AttemptRepositoryPort

/** In-memory [AttemptRepositoryPort]: safe to call from any thread, forgets everything when the process ends. */
class FakeAttemptRepository : AttemptRepositoryPort {
    private val attempts = MutableStateFlow(emptyMap<String, Attempt>())

    override suspend fun save(attempt: Attempt) {
        attempts.update { all ->
            require(attempt.id !in all) { "attempt ${attempt.id} is already stored" }
            all + (attempt.id to attempt)
        }
    }

    override suspend fun get(id: String): Attempt? = attempts.value[id]

    override fun recent(
        passageId: String,
        limit: Int,
    ): Flow<List<Attempt>> {
        require(limit > 0) { "limit ($limit) must be positive" }
        return attempts.map { all ->
            all.values
                .filter { it.passageId == passageId }
                .sortedByDescending { it.startedAt }
                .take(limit)
        }
    }
}
