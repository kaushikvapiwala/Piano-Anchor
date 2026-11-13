package pianoanchor.platform.storage

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.ChordIncomplete
import pianoanchor.core.model.MatchEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.testing.AttemptRepositoryPortContractTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

class SqlAttemptRepositoryAdapterTest : AttemptRepositoryPortContractTest() {
    private val driver: SqlDriver = inMemoryDriver()
    private val repository = SqlAttemptRepositoryAdapter(driver, Dispatchers.Unconfined)

    override fun createSubject(): AttemptRepositoryPort =
        SqlAttemptRepositoryAdapter(inMemoryDriver(), Dispatchers.Unconfined)

    private fun attempt(events: List<MatchEvent>) =
        Attempt(
            id = "a-1",
            passageId = "c-d-e",
            deviceId = "usb-1",
            startedAt = Instant.fromEpochMilliseconds(1_234),
            durationNanos = 5_000,
            stepCount = 3,
            wrongNotes = 0,
            incompleteChords = 2,
            accuracy = 1.0,
            band = Band.CLEAN,
            events = events,
        )

    @Test
    fun incompleteChordsWithOneOrNoMissingNotesRoundTrip() =
        runTest {
            val stored =
                attempt(
                    listOf(
                        ChordIncomplete(0, 0, setOf(MidiNote(0))),
                        ChordIncomplete(1, 0, emptySet()),
                        PassageComplete(2, 0),
                    ),
                )
            repository.save(stored)
            assertEquals(stored, repository.get("a-1"))
        }

    @Test
    fun aFailedSaveLeavesNoRows() =
        runTest {
            driver.exec(
                "CREATE TRIGGER fail_second_event BEFORE INSERT ON attempt_event WHEN NEW.seq = 1 " +
                    "BEGIN SELECT RAISE(ABORT, 'test failure'); END",
            )
            assertFails { repository.save(attempt(listOf(PassageComplete(0, 0), PassageComplete(1, 0)))) }
            assertNull(repository.get("a-1"))
            assertEquals(0, driver.count("attempt"))
            assertEquals(0, driver.count("attempt_event"))
        }

    @Test
    fun deletingAnAttemptDeletesItsEvents() =
        runTest {
            repository.save(attempt(listOf(PassageComplete(0, 0))))
            driver.exec("DELETE FROM attempt WHERE id = 'a-1'")
            assertEquals(0, driver.count("attempt_event"))
        }

    @Test
    fun anUnknownEventKindFailsTheRead() =
        runTest {
            repository.save(attempt(emptyList()))
            driver.exec(
                "INSERT INTO attempt_event(attempt_id, seq, kind, t_nanos, step_index) " +
                    "VALUES ('a-1', 0, 'mystery', 0, 0)",
            )
            assertFailsWith<IllegalStateException> { repository.get("a-1") }
        }
}
