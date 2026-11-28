package pianoanchor.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.AttemptSummary
import pianoanchor.core.model.Band
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.model.summary
import pianoanchor.core.ports.AttemptRepositoryPort
import kotlin.time.Instant

private const val NANOS_PER_MINUTE = 60_000_000_000L
private const val NANOS_PER_HALF_MINUTE = NANOS_PER_MINUTE / 2

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
                .filter { it.passageId == passageId && it.spot == null }
                .sortedByDescending { it.startedAt }
                .take(limit)
        }
    }

    override suspend fun countStartedBefore(
        passageId: String,
        before: Instant,
    ): Int {
        failIfFailing("countStartedBefore")
        return attempts.value.values.count { it.passageId == passageId && it.spot == null && it.startedAt < before }
    }

    override suspend fun cleanTempos(passageId: String): Set<Int> {
        failIfFailing("cleanTempos")
        return attempts.value.values
            .filter { it.passageId == passageId && it.band == Band.CLEAN && it.spot == null }
            .mapNotNullTo(LinkedHashSet()) { it.tempoBpm }
    }

    override suspend fun hasCleanWaitRun(passageId: String): Boolean {
        failIfFailing("hasCleanWaitRun")
        return attempts.value.values.any {
            it.passageId == passageId && it.band == Band.CLEAN && it.tempoBpm == null &&
                it.spot == null
        }
    }

    override suspend fun firstCleanUnaidedWaitRun(passageId: String): Instant? {
        failIfFailing("firstCleanUnaidedWaitRun")
        return attempts.value.values
            .filter {
                it.passageId == passageId && it.band == Band.CLEAN && it.tempoBpm == null && it.spot == null &&
                    !it.hintShown
            }.minOfOrNull { it.startedAt }
    }

    override suspend fun lastPlayed(passageId: String): Instant? {
        failIfFailing("lastPlayed")
        return attempts.value.values
            .filter { it.passageId == passageId }
            .maxOfOrNull { it.startedAt }
    }

    override suspend fun practiceDays(
        from: LocalDate,
        zone: TimeZone,
    ): Set<LocalDate> {
        failIfFailing("practiceDays")
        return attempts.value.values
            .map { it.startedAt.toLocalDateTime(zone).date }
            .filterTo(LinkedHashSet()) { it >= from }
    }

    override suspend fun firstFullRun(passageId: String): Attempt? {
        failIfFailing("firstFullRun")
        return fullRuns(passageId).minWithOrNull(compareBy({ it.startedAt }, { it.id }))
    }

    override suspend fun latestFullRun(passageId: String): Attempt? {
        failIfFailing("latestFullRun")
        return fullRuns(passageId).maxWithOrNull(compareBy({ it.startedAt }, { it.id }))
    }

    override suspend fun minutesByDay(
        from: LocalDate,
        zone: TimeZone,
    ): Map<LocalDate, Int> {
        failIfFailing("minutesByDay")
        return attempts.value.values
            .groupBy { it.startedAt.toLocalDateTime(zone).date }
            .filterKeys { it >= from }
            .mapValues { (_, day) ->
                ((day.sumOf { it.durationNanos } + NANOS_PER_HALF_MINUTE) / NANOS_PER_MINUTE).toInt()
            }
    }

    private fun fullRuns(passageId: String) =
        attempts.value.values.filter {
            it.passageId == passageId && it.spot == null && it.events.any { event -> event is PassageComplete }
        }

    override suspend fun summariesWithPassagePrefix(prefix: String): List<AttemptSummary> {
        failIfFailing("summariesWithPassagePrefix")
        require(prefix.isNotBlank()) { "prefix must not be blank" }
        return attempts.value.values
            .filter { it.passageId.startsWith(prefix) }
            .sortedWith(compareBy({ it.startedAt }, { it.id }))
            .map { it.summary }
    }

    private fun failIfFailing(operation: String) {
        if (failing) throw StorageUnavailableException("$operation failed")
    }
}
