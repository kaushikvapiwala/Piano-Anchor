package pianoanchor.platform.storage

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.ChordIncomplete
import pianoanchor.core.model.MatchEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.model.StepCorrect
import pianoanchor.core.model.WrongNote
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.platform.storage.db.AttemptQueries
import pianoanchor.platform.storage.db.Attempt_event
import pianoanchor.platform.storage.db.PianoAnchorDatabase
import kotlin.time.Instant
import pianoanchor.platform.storage.db.Attempt as AttemptRow

// attempt_event.kind values, one per MatchEvent subtype (the table's CHECK relies on them).
private const val STEP_CORRECT = "step_correct"
private const val WRONG_NOTE = "wrong_note"
private const val CHORD_INCOMPLETE = "chord_incomplete"
private const val PASSAGE_COMPLETE = "passage_complete"
private const val NOTE_SEPARATOR = ","

/**
 * [AttemptRepositoryPort] in the on-device SQLite database. [driver] comes from `createPianoAnchorDatabaseDriver`
 * (schema created, foreign keys on); every query runs on [dispatcher] (`Dispatchers.IO` in the app). An attempt and
 * its events are written in one transaction. [Attempt.startedAt] is stored as epoch milliseconds, so anything finer
 * is dropped.
 */
public class SqlAttemptRepositoryAdapter(
    driver: SqlDriver,
    private val dispatcher: CoroutineDispatcher,
) : AttemptRepositoryPort {
    private val queries = PianoAnchorDatabase(driver).attemptQueries

    override suspend fun save(attempt: Attempt): Unit =
        withContext(dispatcher) {
            storage("save") {
                queries.transaction {
                    require(queries.selectById(attempt.id).executeAsOneOrNull() == null) {
                        "attempt ${attempt.id} is already stored"
                    }
                    queries.insertAttempt(
                        id = attempt.id,
                        passage_id = attempt.passageId,
                        device_id = attempt.deviceId,
                        started_at_ms = attempt.startedAt.toEpochMilliseconds(),
                        duration_nanos = attempt.durationNanos,
                        step_count = attempt.stepCount.toLong(),
                        wrong_notes = attempt.wrongNotes.toLong(),
                        incomplete_chords = attempt.incompleteChords.toLong(),
                        accuracy = attempt.accuracy,
                        band = attempt.band.name,
                    )
                    attempt.events.forEachIndexed { seq, event -> queries.insertMatchEvent(attempt.id, seq, event) }
                }
            }
        }

    override suspend fun get(id: String): Attempt? =
        withContext(dispatcher) {
            storage("get") {
                queries.transactionWithResult { queries.selectById(id).executeAsOneOrNull()?.let(::withEvents) }
            }
        }

    override fun recent(
        passageId: String,
        limit: Int,
    ): Flow<List<Attempt>> {
        require(limit > 0) { "limit ($limit) must be positive" }
        // Re-runs after every write to `attempt`; one transaction reads the rows and their events together.
        return queries
            .selectRecent(passageId, limit.toLong())
            .asFlow()
            .map { query ->
                withContext(dispatcher) {
                    queries.transactionWithResult { query.executeAsList().map(::withEvents) }
                }
            }.storage("recent")
    }

    override suspend fun countStartedBefore(
        passageId: String,
        before: Instant,
    ): Int =
        withContext(dispatcher) {
            storage("countStartedBefore") {
                queries.countStartedBefore(passageId, before.toEpochMilliseconds()).executeAsOne().toInt()
            }
        }

    private fun withEvents(row: AttemptRow) =
        Attempt(
            id = row.id,
            passageId = row.passage_id,
            deviceId = row.device_id,
            startedAt = Instant.fromEpochMilliseconds(row.started_at_ms),
            durationNanos = row.duration_nanos,
            stepCount = row.step_count.toInt(),
            wrongNotes = row.wrong_notes.toInt(),
            incompleteChords = row.incomplete_chords.toInt(),
            accuracy = row.accuracy,
            band = Band.valueOf(row.band),
            events = queries.selectEvents(row.id).executeAsList().map(::toMatchEvent),
        )
}

private fun AttemptQueries.insertMatchEvent(
    attemptId: String,
    seq: Int,
    event: MatchEvent,
) {
    val (kind, note, missing) =
        when (event) {
            is StepCorrect -> {
                Triple(STEP_CORRECT, null, null)
            }

            is WrongNote -> {
                Triple(WRONG_NOTE, event.note.number.toLong(), null)
            }

            is ChordIncomplete -> {
                Triple(
                    CHORD_INCOMPLETE,
                    null,
                    event.missing
                        .map { it.number }
                        .sorted()
                        .joinToString(NOTE_SEPARATOR),
                )
            }

            is PassageComplete -> {
                Triple(PASSAGE_COMPLETE, null, null)
            }
        }
    insertEvent(attemptId, seq.toLong(), kind, event.timestampNanos, event.stepIndex.toLong(), note, missing)
}

// The table's CHECK sets note exactly for wrong notes and missing exactly for incomplete chords.
private fun toMatchEvent(row: Attempt_event): MatchEvent {
    val t = row.t_nanos
    val step = row.step_index.toInt()
    return when (row.kind) {
        STEP_CORRECT -> StepCorrect(t, step)
        WRONG_NOTE -> WrongNote(t, step, MidiNote(row.note!!.toInt()))
        CHORD_INCOMPLETE -> ChordIncomplete(t, step, notes(row.missing!!))
        PASSAGE_COMPLETE -> PassageComplete(t, step)
        else -> error("attempt ${row.attempt_id} event ${row.seq}: unknown kind ${row.kind}")
    }
}

private fun notes(missing: String): Set<MidiNote> =
    if (missing.isEmpty()) emptySet() else missing.split(NOTE_SEPARATOR).map { MidiNote(it.toInt()) }.toSet()
